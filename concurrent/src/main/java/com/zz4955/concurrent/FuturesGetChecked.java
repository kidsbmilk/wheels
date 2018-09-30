package com.zz4955.concurrent;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static com.zz4955.concurrent.Tools.checkArgument;

final class FuturesGetChecked {

    static <V, X extends Exception> V getChecked(Future<V> future, Class<X> exceptionClass) throws X {
        return getChecked(bestGetCheckedTypeValidator(), future, exceptionClass);
    }

    private static GetCheckedTypeValidator bestGetCheckedTypeValidator() {
        return GetCheckedTypeValidatorHolder.BEST_VALIDATOR;
    }

    static GetCheckedTypeValidator weakSetValidator() {
        return GetCheckedTypeValidatorHolder.WeakSetValidator.INSTANCE;
    }

    static GetCheckedTypeValidator classValueValidator() {
        return GetCheckedTypeValidatorHolder.ClassValueValidator.INSTANCE;
    }

    static <V, X extends Exception> V getChecked(GetCheckedTypeValidator validator, Future<V> future, Class<X> exceptionClass) throws X {
        validator.validateClass(exceptionClass);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw newWithCause(exceptionClass, e);
        } catch (ExecutionException e) {
            wrapAndThrowExceptionOrError(e.getCause(), exceptionClass);
            throw new AssertionError();
        }
    }

    static <V, X extends Exception> V getChecked(Future<V> future, Class<X> exceptionClass, long timeout, TimeUnit unit) throws X {
        bestGetCheckedTypeValidator().validateClass(exceptionClass);
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw newWithCause(exceptionClass, e);
        } catch (TimeoutException e) {
            throw newWithCause(exceptionClass, e);
        } catch (ExecutionException e) {
            wrapAndThrowExceptionOrError(e.getCause(), exceptionClass);
            throw new AssertionError();
        }
    }

    private static <X extends Exception> void wrapAndThrowExceptionOrError(Throwable cause, Class<X> exceptionClass) throws X {
        if(cause instanceof Error) {
            throw new ExecutionError((Error) cause);
        }
        if(cause instanceof RuntimeException) {
            throw new UncheckedExecutionException(cause);
        }
        throw newWithCause(exceptionClass, cause);
    }

    static boolean isCheckedException(Class<? extends Exception> type) {
        return !RuntimeException.class.isAssignableFrom(type);
    }

    private static boolean hasConstructorUsableByGetChecked(Class<? extends Exception> exceptionClass) {
        try {
            Exception unused = newWithCause(exceptionClass, new Exception());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static <X extends Exception> X newWithCause(Class<X> exceptionClass, Throwable cause) {
        List<Constructor<X>> constructors = (List) Arrays.asList(exceptionClass.getConstructors());
        for(Constructor<X> constructor : preferringStrings(constructors)) {
            X instance = newFromConstructor(constructor, cause);
            if(instance != null) {
                if(instance.getCause() == null) {
                    instance.initCause(cause);
                }
                return instance;
            }
        }
        throw new IllegalArgumentException(
                "No appropriate constructor for exception of type "
                + exceptionClass
                + " in respnse to chained exception",
                cause);
    }

    private static <X> X newFromConstructor(Constructor<X> constructor, Throwable cause) {
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for(int i = 0; i < paramTypes.length; i ++) {
            Class<?> paramType = paramTypes[i];
            if(paramType.equals(String.class)) {
                params[i] = cause.toString();
            } else if(paramType.equals(Throwable.class)) {
                params[i] = cause;
            } else {
                return null;
            }
        }

        try {
            return constructor.newInstance(params);
        } catch (IllegalArgumentException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            return null;
        }
    }

    private static <X extends Exception> List<Constructor<X>> preferringStrings(List<Constructor<X>> constructors) {
        return WITH_STRING_PARAM_FIRST.sortedCopy(constructors);
    }

    private static final Ordering<Constructor<?>> WITH_STRING_PARAM_FIRST = Ordering.natural()
            .onResultOf(new Function<Constructor<?>, Boolean>() {
                @Override
                public Boolean apply(Constructor<?> constructor) {
                    return Arrays.asList(constructor.getParameterTypes()).contains(String.class);
                }
            })
            .reverse();

    static void checkExceptionClassValidity(Class<? extends Exception> exceptionClass) {
        checkArgument(
                isCheckedException(exceptionClass),
                "Futures.getChecked exception type (%s) must not be a RuntimeException",
                exceptionClass);

        checkArgument(
                hasConstructorUsableByGetChecked(exceptionClass),
                "Futures.getChecked exception type (%s) must be an accessibly class with an accessible "
                + "constructor whose parameters (if any) must be of type String and/or Throwable",
                exceptionClass);
    }

    interface GetCheckedTypeValidator {
        void validateClass(Class<? extends Exception> exceptionClass);
    }

    static class GetCheckedTypeValidatorHolder {
        static final String CLASS_VALUE_VALIDATOR_NAME = GetCheckedTypeValidatorHolder.class.getName() + "$ClassValueValidator";

        static final GetCheckedTypeValidator BEST_VALIDATOR = getBestValidator();

        enum ClassValueValidator implements GetCheckedTypeValidator {
            INSTANCE;

            private static final ClassValue<Boolean> isValidClass = new ClassValue<Boolean>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    checkExceptionClassValidity(type.asSubclass(Exception.class));
                    return true;
                }
            };

            @Override
            public void validateClass(Class<? extends Exception> exceptionClass) {
                isValidClass.get(exceptionClass);
            }
        }

        enum WeakSetValidator implements GetCheckedTypeValidator {
            INSTANCE;

            private static final Set<WeakReference<Class<? extends Exception>>> validClasses = new CopyOnWriteArraySet<>();

            @Override
            public void validateClass(Class<? extends Exception> exceptionClass) {
                for(WeakReference<Class<? extends Exception>> knownGood : validClasses) {
                    if(exceptionClass.equals(knownGood.get())) {
                        return ;
                    }
                }
                checkExceptionClassValidity(exceptionClass);
                if(validClasses.size() > 1000) {
                    validClasses.clear();
                }

                validClasses.add(new WeakReference<>(exceptionClass));
            }
        }

        static GetCheckedTypeValidator getBestValidator() {
            try {
                Class<?> theClass = Class.forName(CLASS_VALUE_VALIDATOR_NAME);
                return (GetCheckedTypeValidator) theClass.getEnumConstants()[0];
            } catch (Throwable t) {
                return weakSetValidator();
            }
        }
    }
}

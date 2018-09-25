package com.zz4955.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static com.zz4955.concurrent.Futures.getDone;
import static com.zz4955.concurrent.Tools.checkNotNull;
import static com.zz4955.concurrent.Tools.isInstanceOfThrowableClass;
import static com.zz4955.concurrent.Tools.rejectionPropagatingExecutor;

abstract class AbstractCatchingFuture<V, X extends Throwable, F, T>
        extends AbstractFuture.TrustedFuture<V> implements Runnable {

    static <V, X extends Throwable> ListenableFuture<V> create(
            ListenableFuture<? extends V> input,
            Class<X> exceptionType,
            Function<? super X, ? extends V> fallback,
            Executor executor) {
        CatchingFuture<V, X> future = new CatchingFuture<>(input, exceptionType, fallback);
        input.addListener(future, rejectionPropagatingExecutor(executor, future));
        return future;
    }

    static <X extends Throwable, V> ListenableFuture<V> create(
            ListenableFuture<? extends V> input,
            Class<X> exceptionType,
            AsyncFunction<? super X, ? extends V> fallback,
            Executor executor) {
        AsyncCatchingFuture<V, X> future = new AsyncCatchingFuture<>(input, exceptionType, fallback);
        input.addListener(future, rejectionPropagatingExecutor(executor, future));
        return future;
    }

    ListenableFuture<? extends V> inputFuture;
    Class<X> exceptionType;
    F fallback;

    AbstractCatchingFuture(
            ListenableFuture<? extends V> inputFuture,
            Class<X> exceptionType,
            F fallback) {
        this.inputFuture = checkNotNull(inputFuture);
        this.exceptionType = checkNotNull(exceptionType);
        this.fallback = checkNotNull(fallback);
    }

    @Override
    public final void run() {
        ListenableFuture<? extends V> localInputFuture = inputFuture;
        Class<X> localExceptionType = exceptionType;
        F localFallback = fallback;
        if(localInputFuture == null
            | localExceptionType == null
            | localFallback == null
            | isCancelled()) {
            return ;
        }
        inputFuture = null;

        V sourceResult = null;
        Throwable throwable = null;
        try {
            sourceResult = getDone(localInputFuture);
        } catch (ExecutionException e) {
            throwable = checkNotNull(e.getCause());
        } catch (Throwable e) {
            throwable = e;
        }

        if(throwable == null) {
            set(sourceResult);
            return ;
        }

        if(!isInstanceOfThrowableClass(throwable, localExceptionType)) {
            setException(throwable);
            return ;
        }

        @SuppressWarnings("unchecked")
        X castThrowable = (X) throwable;
        T fallbackResult;
        try {
            fallbackResult = doFallback(localFallback, castThrowable);
        } catch (Throwable t) {
            setException(t);
            return ;
        } finally {
            exceptionType = null;
            fallback = null;
        }

        setResult(fallbackResult);
    }

    @Override
    protected String pendingToString() {
        ListenableFuture<? extends V> localInputFuture = inputFuture;
        Class<X> localExceptionType = exceptionType;
        F localFallback = fallback;
        String superString = super.pendingToString();
        String resultString = "";
        if(localInputFuture != null) {
            resultString = "inputFuture=[" + localInputFuture + "], ";
        }
        if(localExceptionType != null && localFallback != null) {
            return resultString
                    + "exceptionType=["
                    + localExceptionType
                    + "], fallback=["
                    + localFallback
                    + "]";
        } else if(superString != null) {
            return resultString + superString;
        }
        return null;
    }

    abstract T doFallback(F fallback, X throwable) throws Exception;

    abstract void setResult(T result);

    @Override
    protected final void afterDone() {
        maybePropagateCancellationTo(inputFuture);
        this.inputFuture = null;
        this.exceptionType = null;
        this.fallback = null;
    }

    private static final class CatchingFuture<V, X extends Throwable>
            extends AbstractCatchingFuture<V, X, Function<? super X, ? extends V>, V> {

        CatchingFuture(
                ListenableFuture<? extends V> input,
                Class<X> exceptionType,
                Function<? super X, ? extends V> fallback) {
            super(input, exceptionType, fallback);
        }

        @Override
        V doFallback(Function<? super X, ? extends V> fallback, X cause) throws Exception {
            return fallback.apply(cause);
        }

        @Override
        void setResult(V result) {
            set(result);
        }
    }

    private static final class AsyncCatchingFuture<V, X extends Throwable>
            extends AbstractCatchingFuture<V, X, AsyncFunction<? super X, ? extends V>, ListenableFuture<? extends V>> {

        AsyncCatchingFuture(
                ListenableFuture<? extends V> input,
                Class<X> exceptionType,
                AsyncFunction<? super X, ? extends V> fallback) {
            super(input, exceptionType, fallback);
        }

        @Override
        ListenableFuture<? extends V> doFallback(
                AsyncFunction<? super X, ? extends V> fallback, X cause) throws Exception {
            ListenableFuture<? extends V> replacement = fallback.apply(cause);
            checkNotNull(
                    replacement,
                    "AsyncFunction.apply returned null instead of a Future. "
                    + "Did you mean to return immediateFuture(null)?"
            );
            return replacement;
        }

        @Override
        void setResult(ListenableFuture<? extends V> result) {
            setFuture(result);
        }
    }
}

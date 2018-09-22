package com.zz4955.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public final class Futures {

    public static <V> V getDone(Future<V> future) throws ExecutionException {
        checkState(future.isDone(), "Future was expected to be done: %s", future);
        return getUninterruptibly(future);
    }

    public static void checkState(boolean b, String errorMessageTemplate, Object p1) {
        if(!b) {
            throw new IllegalStateException(lenientFormat(errorMessageTemplate, p1));
        }
    }

    public static <V> V getUninterruptibly(Future<V> future) throws ExecutionException {
        boolean interrupted = false;
        try {
            while(true) {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if(interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String lenientFormat(String template, Object... args) {
        template = String.valueOf(template); // null -> "null"

        if (args == null) {
            args = new Object[] {"(Object[])null"};
        } else {
            for (int i = 0; i < args.length; i++) {
                args[i] = lenientToString(args[i]);
            }
        }

        // start substituting the arguments into the '%s' placeholders
        StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
        int templateStart = 0;
        int i = 0;
        while (i < args.length) {
            int placeholderStart = template.indexOf("%s", templateStart);
            if (placeholderStart == -1) {
                break;
            }
            builder.append(template, templateStart, placeholderStart);
            builder.append(args[i++]);
            templateStart = placeholderStart + 2;
        }
        builder.append(template, templateStart, template.length());

        // if we run out of placeholders, append the extra args in square braces
        if (i < args.length) {
            builder.append(" [");
            builder.append(args[i++]);
            while (i < args.length) {
                builder.append(", ");
                builder.append(args[i++]);
            }
            builder.append(']');
        }

        return builder.toString();
    }

    private static String lenientToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Exception e) {
            // Default toString() behavior - see Object.toString()
            String objectToString =
                    o.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(o));
            // Logger is created inline with fixed name to avoid forcing Proguard to create another class.
            Logger.getLogger("com.zz4955.concurrent.Futures")
                    .log(WARNING, "Exception during lenientFormat for " + objectToString, e);
            return "<" + objectToString + " threw " + e.getClass().getName() + ">";
        }
    }
}

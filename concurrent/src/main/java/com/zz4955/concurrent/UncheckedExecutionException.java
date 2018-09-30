package com.zz4955.concurrent;

public class UncheckedExecutionException extends RuntimeException {

    protected UncheckedExecutionException() {}

    protected UncheckedExecutionException(String message) {
        super(message);
    }

    public UncheckedExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckedExecutionException(Throwable cause) {
        super(cause);
    }

    private static final long serialVersionUID = 0;
}

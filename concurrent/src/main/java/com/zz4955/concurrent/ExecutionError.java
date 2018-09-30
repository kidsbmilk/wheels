package com.zz4955.concurrent;

public class ExecutionError extends Error {

    protected ExecutionError() {}

    protected ExecutionError(String message) {
        super(message);
    }

    public ExecutionError(String message, Error cause) {
        super(message, cause);
    }

    public ExecutionError(Error cause) {
        super(cause);
    }

    private static final long serialVersionUID = 0;
}

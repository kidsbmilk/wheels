package com.zz4955.example;

public class MyExceptionForEx extends Exception {

    protected MyExceptionForEx() {}

    protected MyExceptionForEx(String message) {
        super(message);
    }

    public MyExceptionForEx(String message, Throwable cause) {
        super(message, cause);
    }

    public MyExceptionForEx(Throwable cause) {
        super(cause);
    }

    private static final long serialVersionUID = 0;
}

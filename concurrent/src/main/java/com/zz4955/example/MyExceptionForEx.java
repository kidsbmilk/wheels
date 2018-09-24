package com.zz4955.example;

public class MyExceptionForEx extends Exception {

    MyExceptionForEx() {
        super();
    }

    MyExceptionForEx(String message) {
        super(message);
    }

    MyExceptionForEx(String message, Throwable cause) {
        super(message, cause);
    }

    MyExceptionForEx(Throwable cause) {
        super(cause);
    }
}

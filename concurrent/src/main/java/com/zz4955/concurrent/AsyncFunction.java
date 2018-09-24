package com.zz4955.concurrent;

public interface AsyncFunction<I, O> {

    ListenableFuture<O> apply(I input) throws Exception;
}

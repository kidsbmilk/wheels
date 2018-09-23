package com.zz4955.concurrent;

public interface AsyncCallable<V> {

    ListenableFuture<V> call() throws Exception;
}

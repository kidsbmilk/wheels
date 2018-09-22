package com.zz4955.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public interface ListenableFuture<V> extends Future<V> {

    void addListener(Runnable listener, Executor executor);
}

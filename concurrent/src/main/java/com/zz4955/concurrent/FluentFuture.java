package com.zz4955.concurrent;

import java.util.concurrent.Executor;
import java.util.function.Function;

public abstract class FluentFuture<V> implements ListenableFuture<V> {

    FluentFuture() {}

    public static <V> FluentFuture<V> from(ListenableFuture<V> future) {
        return future instanceof FluentFuture
                ? (FluentFuture<V>) future
                : new ForwardingFluentFuture<V>(future);
    }

    public final <X extends Throwable> FluentFuture<V> catching(
            Class<X> exceptionType, Function<? super X, ? extends V> fallback, Executor executor) {
        return (FluentFuture<V>) Futures.catching(this, exceptionType, fallback, executor);
    }
}

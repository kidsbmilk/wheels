package com.zz4955.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    public final <X extends Throwable> FluentFuture<V> catchingAsync(
            Class<X> exceptionType, AsyncFunction<? super X, ? extends V> fallback, Executor executor) {
        return (FluentFuture<V>) Futures.catchingAsync(this, exceptionType, fallback, executor);
    }

    public final FluentFuture<V> withTimeout(
            long timeout, TimeUnit unit, ScheduledExecutorService scheduledExecutor) {
        return (FluentFuture<V>) Futures.withTimeout(this, timeout, unit, scheduledExecutor);
    }

    public final <T> FluentFuture<T> transform(Function<? super V, T> function, Executor executor) {
        return (FluentFuture<T>) Futures.transform(this, function, executor);
    }

    public final <T> FluentFuture<T> transformAsync(AsyncFunction<? super V, T> function, Executor executor) {
        return (FluentFuture<T>) Futures.transformAsync(this, function, executor);
    }

    public final void addCallback(FutureCallback<? super V> callback, Executor executor) {
        Futures.addCallback(this, callback, executor);
    }
}

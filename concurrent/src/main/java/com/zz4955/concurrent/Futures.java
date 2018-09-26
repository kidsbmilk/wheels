package com.zz4955.concurrent;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.zz4955.concurrent.Tools.checkNotNull;
import static com.zz4955.concurrent.Tools.checkState;

public final class Futures {

    public static <V> V getDone(Future<V> future) throws ExecutionException {
        checkState(future.isDone(), "Future was expected to be done: %s", future);
        return getUninterruptibly(future);
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

    public static <V, X extends Throwable> ListenableFuture<V> catching(
            ListenableFuture<? extends V> input,
            Class<X> exceptionType,
            Function<? super X, ? extends V> fallback,
            Executor executor) {
        return AbstractCatchingFuture.create(input, exceptionType, fallback, executor);
    }

    public static <V, X extends Throwable> ListenableFuture<V> catchingAsync(
            ListenableFuture<? extends V> input,
            Class<X> exceptionType,
            AsyncFunction<? super X, ? extends V> fallback,
            Executor executor) {
        return AbstractCatchingFuture.create(input, exceptionType, fallback, executor);
    }

    public static <V> ListenableFuture<V> withTimeout(
            ListenableFuture<V> delegate,
            long time,
            TimeUnit unit,
            ScheduledExecutorService scheduledExecutor) {
        return TimeoutFuture.create(delegate, time, unit, scheduledExecutor);
    }

    public static <I, O> ListenableFuture<O> transform(
            ListenableFuture<I> input,
            Function<? super I, ? extends O> function,
            Executor executor) {
        return AbstractTransformFuture.create(input, function, executor);
    }

    public static <I, O> ListenableFuture<O> transformAsync(
            ListenableFuture<I> input,
            AsyncFunction<? super I, ? extends O> function,
            Executor executor) {
        return AbstractTransformFuture.create(input, function, executor);
    }

    public static <V> void addCallback(
            final ListenableFuture<V> future,
            final FutureCallback<? super V> callback,
            Executor executor) {
        checkNotNull(callback);
        future.addListener(new CallbackListener<V>(future, callback), executor);
    }

    private static final class CallbackListener<V> implements Runnable {

        final Future<V> future;
        final FutureCallback<? super V> callback;

        CallbackListener(Future<V> future, FutureCallback<? super V> callback) {
            this.future = future;
            this.callback = callback;
        }

        @Override
        public void run() {
            final V value;
            try {
                value = getDone(future);
            } catch (ExecutionException e) {
                callback.onFailure(e.getCause());
                return ;
            } catch (RuntimeException | Error e) {
                callback.onFailure(e);
                return ;
            }
            callback.onSuccess(value);
        }

        @Override
        public String toString() {
            return this.getClass().getCanonicalName() + "[callback= " + callback + "]"; // 这里有个MoreObjects的工具类，TODO.
        }
    }

    public static <V> ListenableFuture<List<V>> allAsList(ListenableFuture<? extends V>... futures) {
        return new ListFuture<V>(ImmutableList.copyOf(futures), true);
    }
}

package com.zz4955.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableFuture;

import static com.zz4955.concurrent.Tools.checkNotNull;

class TrustedListenableFutureTask<V> extends AbstractFuture.TrustedFuture<V>
                implements RunnableFuture<V> {

    static <V> TrustedListenableFutureTask<V> create(AsyncCallable<V> callable) {
        return new TrustedListenableFutureTask<V>(callable);
    }

    static <V> TrustedListenableFutureTask<V> create(Callable<V> callable) {
        return new TrustedListenableFutureTask<V>(callable);
    }

    static <V> TrustedListenableFutureTask<V> create(Runnable runnable, V result) {
        return new TrustedListenableFutureTask<V>(Executors.callable(runnable, result));
    }

    private volatile InterruptibleTask<?> task;

    TrustedListenableFutureTask(Callable<V> callable) {
        this.task = new TrustedFutureInterruptibleTask(callable);
    }

    TrustedListenableFutureTask(AsyncCallable<V> callable) {
        this.task = new TrustedFutureInterruptibleAsyncTask(callable);
    }

    @Override
    public void run() {
        InterruptibleTask localTask = task;
        if(localTask != null) {
            localTask.run();
        }

        this.task = null;
    }

    @Override
    protected void afterDone() {
        super.afterDone();

        if(wasInterrupted()) {
            InterruptibleTask localTask = task;
            if(localTask != null) {
                localTask.interruptTask();
            }
        }

        this.task = null;
    }

    @Override
    protected String pendingToString() {
        InterruptibleTask localTask = task;
        if(localTask != null) {
            return "task=[" + localTask + "]";
        }
        return super.pendingToString();
    }

    private final class TrustedFutureInterruptibleTask extends InterruptibleTask<V> {

        private final Callable<V> callable;

        TrustedFutureInterruptibleTask(Callable<V> callable) {
            this.callable = checkNotNull(callable);
        }

        @Override
        final boolean isDone() {
            return TrustedListenableFutureTask.this.isDone();
        }

        @Override
        V runInterruptibly() throws Exception {
            return callable.call();
        }

        @Override
        void afterRanInterruptibly(V result, Throwable error) {
            if(error == null) {
                TrustedListenableFutureTask.this.set(result);
            } else {
                setException(error);
            }
        }

        @Override
        String toPendingString() {
            return callable.toString();
        }
    }

    private final class TrustedFutureInterruptibleAsyncTask extends InterruptibleTask<ListenableFuture<V>> {

        private final AsyncCallable<V> callable;

        TrustedFutureInterruptibleAsyncTask(AsyncCallable<V> callable) {
            this.callable = checkNotNull(callable);
        }

        @Override
        final boolean isDone() {
            return TrustedListenableFutureTask.this.isDone();
        }

        @Override
        ListenableFuture<V> runInterruptibly() throws Exception {
            return checkNotNull(
                    callable.call(),
                    "AsyncCallable.call returned null instead of a Future. "
                    + "Did you mean to return immediateFuture(null)?"
            );
        }

        @Override
        void afterRanInterruptibly(ListenableFuture<V> result, Throwable error) {
            if(error == null) {
                setFuture(result);
            } else {
                setException(error);
            }
        }

        @Override
        String toPendingString() {
            return callable.toString();
        }
    }
}

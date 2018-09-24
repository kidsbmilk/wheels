package com.zz4955.concurrent;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.zz4955.concurrent.MoreExecutors.directExecutor;
import static com.zz4955.concurrent.Tools.checkNotNull;

final class TimeoutFuture<V> extends AbstractFuture.TrustedFuture<V> {

    static <V> ListenableFuture<V> create(
            ListenableFuture<V> delegate,
            long time,
            TimeUnit unit,
            ScheduledExecutorService scheduledExecutor) {
        TimeoutFuture<V> result = new TimeoutFuture<>(delegate);
        Fire<V> fire = new Fire<>(result);
        result.timer = scheduledExecutor.schedule(fire, time, unit);
        delegate.addListener(fire, directExecutor());
        return result;
    }

    private ListenableFuture<V> delegateRef;
    private Future<?> timer;

    private TimeoutFuture(ListenableFuture<V> delegate) {
        this.delegateRef = checkNotNull(delegate);
    }

    private static final class Fire<V> implements Runnable {
        TimeoutFuture<V> timeoutFutureRef;

        Fire(TimeoutFuture<V> timeoutFuture) {
            this.timeoutFutureRef = timeoutFuture;
        }

        @Override
        public void run() {
            TimeoutFuture<V> timeoutFuture = timeoutFutureRef;
            if(timeoutFuture == null) {
                return ;
            }
            ListenableFuture<V> delegate = timeoutFuture.delegateRef;
            if(delegate == null) {
                return ;
            }
            timeoutFutureRef = null;
            if(delegate.isDone()) {
                timeoutFuture.setFuture(delegate);
            } else {
                try {
                    timeoutFuture.setException(new TimeoutException("Future timed out: " + delegate));
                } finally {
                    delegate.cancel(true);
                }
            }
        }
    }

    @Override
    protected String pendingToString() {
        ListenableFuture<? extends V> localInputFuture = delegateRef;
        if(localInputFuture != null) {
            return "inputFuture=[" + localInputFuture + "]";
        }
        return null;
    }

    @Override
    protected void afterDone() {
        maybePropagateCancellationTo(delegateRef);

        Future<?> localTimer = timer;
        if(localTimer != null) {
            localTimer.cancel(false);
        }

        delegateRef = null;
        timer = null;
    }
}

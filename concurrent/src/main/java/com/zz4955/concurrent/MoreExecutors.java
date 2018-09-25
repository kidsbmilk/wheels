package com.zz4955.concurrent;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.zz4955.concurrent.Tools.checkNotNull;

public final class MoreExecutors {

    public static Executor directExecutor() {
        return DirectExecutor.INSTANCE;
    }

    public static ListeningExecutorService listeningDecorator(ExecutorService delegate) {
        return (delegate instanceof ListeningExecutorService)
                ? (ListeningExecutorService) delegate
                : new ListeningDecorator(delegate);
    }

    private static class ListeningDecorator extends AbstractListeningExecutorService {
        private final ExecutorService delegate;

        ListeningDecorator(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public final boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public final boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public final boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public final void shutdown() {
            delegate.shutdown();
        }

        @Override
        public final List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public final void execute(Runnable command) {
            delegate.execute(command);
        }
    }

    private enum DirectExecutor implements Executor {
        INSTANCE;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public String toString() {
            return "MoreExecutors.directExecutor()";
        }
    }
}

package com.zz4955.concurrent;

import java.util.concurrent.atomic.AtomicReference;

abstract class InterruptibleTask<T> extends AtomicReference<Runnable> implements Runnable {

    private static final class DoNothingRunnable implements Runnable {
        @Override
        public void run() {}
    }

    private static final Runnable DONE = new DoNothingRunnable();
    private static final Runnable INTERRUPTING = new DoNothingRunnable();

    @Override
    public final void run() {
        Thread currentThread = Thread.currentThread();
        if(!compareAndSet(null, currentThread)) {
            return ;
        }

        boolean run = !isDone();
        T result = null;
        Throwable error = null;
        try {
            if(run) {
                result = runInterruptibly();
            }
        } catch (Throwable t) {
            error = t;
        } finally {
            if(!compareAndSet(currentThread, DONE)) {
                while(get() == INTERRUPTING) {
                    Thread.yield();
                }
            }
            if(run) {
                afterRanInterruptibly(result, error);
            }
        }
    }

    abstract boolean isDone();

    abstract T runInterruptibly() throws Exception;

    abstract void afterRanInterruptibly(T result, Throwable error);

    final void interruptTask() {
        Runnable currentRunner = get();
        if(currentRunner instanceof Thread && compareAndSet(currentRunner, INTERRUPTING)) {
            ((Thread) currentRunner).interrupt();
            set(DONE);
        }
    }

    @Override
    public final String toString() {
        Runnable state = get();
        final String result;
        if(state == DONE) {
            result = "running=[DONE]";
        } else if(state == INTERRUPTING) {
            result = "running=[INTERRUPTING";
        } else if(state instanceof Thread) {
            result = "running=[RUNNING ON " + ((Thread) state).getName() + "]";
        } else {
            result = "running=[NOT STARTED YET]";
        }
        return result + ", " + toPendingString();
    }

    abstract String toPendingString();
}

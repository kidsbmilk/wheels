package com.zz4955.concurrent;

import java.util.concurrent.Executor;

public final class MoreExecutors {

    public static Executor directExecutor() {
        return DirectExecutor.INSTANCE;
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

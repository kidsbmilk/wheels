package com.zz4955.concurrent;

import com.google.common.collect.ImmutableCollection;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.zz4955.concurrent.Futures.getDone;
import static com.zz4955.concurrent.MoreExecutors.directExecutor;
import static com.zz4955.concurrent.Tools.checkNotNull;
import static com.zz4955.concurrent.Tools.checkState;

abstract class AggregateFuture<InputT, OutputT> extends AbstractFuture.TrustedFuture<OutputT> {

    private static final Logger logger = Logger.getLogger(AggregateFuture.class.getName());

    private RunningState runningState;

    @Override
    protected final void afterDone() {
        super.afterDone();
        RunningState localRunningState = runningState;
        if(localRunningState != null) {
            this.runningState = null;
            ImmutableCollection<? extends ListenableFuture<? extends InputT>> futures = localRunningState.futures;
            boolean wasInterrupted = wasInterrupted();

            if(wasInterrupted) {
                localRunningState.interruptTask();;
            }

            if(isCancelled() & futures != null) {
                for(ListenableFuture<?> future : futures) {
                    future.cancel(wasInterrupted);
                }
            }
        }
    }

    @Override
    protected String pendingToString() {
        RunningState localRunningState = runningState;
        if(localRunningState == null) {
            return null;
        }
        ImmutableCollection<? extends ListenableFuture<? extends InputT>> localFutures = localRunningState.futures;
        if(localFutures != null) {
            return "futures=[" + localFutures + "]";
        }
        return null;
    }

    final void init(RunningState runningState) {
        this.runningState = runningState;
        runningState.init();
    }

    private static boolean addCausalChain(Set<Throwable> seen, Throwable t) {
        for(; t != null; t = t.getCause()) {
            boolean firstTimeSeen = seen.add(t);
            if(!firstTimeSeen) {
                return false;
            }
        }
        return true;
    }

    abstract class RunningState extends AggregateFutureState implements Runnable {
        private ImmutableCollection<? extends ListenableFuture<? extends InputT>> futures;
        private final boolean allMustSucceed;
        private final boolean collectsValues;

        RunningState(
                ImmutableCollection<? extends ListenableFuture<? extends InputT>> futures,
                boolean allMustSucceed,
                boolean collectsValues) {
            super(futures.size());
            this.futures = checkNotNull(futures);
            this.allMustSucceed = allMustSucceed;
            this.collectsValues = collectsValues;
        }

        @Override
        public final void run() {
            decrementCountAndMaybeComplete();
        }

        private void init() {
            if (futures.isEmpty()) {
                handleAllCompleted();
                return ;
            }
            if(allMustSucceed) {
                int i = 0;
                for(final ListenableFuture<? extends InputT> listenableFuture : futures) {
                    final int index = i ++;
                    listenableFuture.addListener(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                handleOneInputDone(index, listenableFuture);
                            } finally {
                                decrementCountAndMaybeComplete();
                            }
                        }
                    }, directExecutor());
                }
            } else {
                for(ListenableFuture<? extends InputT> listenableFuture : futures) {
                    listenableFuture.addListener(this, directExecutor());
                }
            }
        }

        private void handleException(Throwable throwable) {
            checkNotNull(throwable);

            boolean completedWithFailure = false;
            boolean firstTimeSeeingThisException = true;
            if(allMustSucceed) {
                completedWithFailure = setException(throwable);
                if(completedWithFailure) {
                    releaseResourcesAfterFailure();
                } else {
                    firstTimeSeeingThisException = addCausalChain(getOrInitSeenExceptions(), throwable);
                }
            }

            if(throwable instanceof Error | (allMustSucceed & !completedWithFailure & firstTimeSeeingThisException)) {
                String message = (throwable instanceof Error)
                        ? "input Future failed with Error"
                        : "Got more than one input Future failure. Logging failures after the first";
                logger.log(Level.SEVERE, message, throwable);
            }
        }

        @Override
        final void addInitialException(Set<Throwable> seen) {
            if(!isCancelled()) {
                boolean unused = addCausalChain(seen, trustedGetException());
            }
        }

        private void handleOneInputDone(int index, Future<? extends InputT> future) {
            checkState(allMustSucceed || !isDone() || isCancelled(),
                    "Future was done before all dependencies completed");

            try {
                checkState(future.isDone(), "Tried to set value from future which is not done");
                if(allMustSucceed) {
                    if(future.isCancelled()) {
                        runningState = null;
                        cancel(false);
                    } else {
                        InputT result = getDone(future);
                        if(collectsValues) {
                            collectOneValue(allMustSucceed, index, result);
                        }
                    }
                } else if(collectsValues && !future.isCancelled()) {
                    collectOneValue(allMustSucceed, index, getDone(future));
                }
            } catch (ExecutionException e) {
                handleException(e.getCause());
            } catch (Throwable t) {
                handleException(t);
            }
        }

        private void decrementCountAndMaybeComplete() {
            int newRemaining = decrementRemainingAndGet();
            checkState(newRemaining >= 0, "Less than 0 remaining futures");
            if(newRemaining == 0) {
                processCompleted();
            }
        }

        private void processCompleted() {
            if(collectsValues & !allMustSucceed) {
                int i = 0;
                for(ListenableFuture<? extends InputT> listenableFuture : futures) {
                    handleOneInputDone(i++, listenableFuture);
                }
            }
            handleAllCompleted();
        }

        void releaseResourcesAfterFailure() {
            this.futures = null;
        }

        abstract void collectOneValue(boolean allMustSucceed, int index, InputT returnValue);

        abstract void handleAllCompleted();

        void interruptTask() {}
    }
}

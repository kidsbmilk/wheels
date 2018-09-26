package com.zz4955.concurrent;

import com.google.common.collect.ImmutableCollection;

import java.util.logging.Logger;

import static com.zz4955.concurrent.Tools.checkNotNull;

public class AggregateFuture<InputT, OutputT> extends AbstractFuture.TrustedFuture<OutputT> {

    private static final Logger logger = Logger.getLogger(AggregateFuture.class.getName());

    private RunningState runningState;

    abstract class RunningState extends AggregateFuture implements Runnable {
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

            }
        }
    }
}

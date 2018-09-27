package com.zz4955.concurrent;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import static com.zz4955.concurrent.Tools.checkState;

abstract class CollectionFuture<V, C> extends AggregateFuture<V, C> {

    abstract class CollectionFutureRunningState extends RunningState {
        private List<Optional<V>> values;

        CollectionFutureRunningState(
                ImmutableCollection<? extends ListenableFuture<? extends V>> futures,
                boolean allMustSucceed) {
            super(futures, allMustSucceed, true);

            this.values = futures.isEmpty()
                    ? ImmutableList.<Optional<V>>of()
                    : Lists.<Optional<V>>newArrayListWithCapacity(futures.size());

            for(int i = 0; i < futures.size(); i ++) {
                values.add(null);
            }
        }

        @Override
        final void collectOneValue(boolean allMustSucceed, int index, V returnValue) {
            List<Optional<V>> localValues = values;

            if(localValues != null) {
                localValues.set(index, Optional.fromNullable(returnValue));
            } else {
                checkState(allMustSucceed || isCancelled(), "Future was done before all dependencies completed");
            }
        }

        @Override
        final void handleAllCompleted() {
            List<Optional<V>> localValues = values;
            if(localValues != null) {
                set(combine(localValues));
            } else {
                checkState(isDone());
            }
        }

        @Override
        void releaseResourcesAfterFailure() {
            super.releaseResourcesAfterFailure();
            this.values = null;
        }

        abstract C combine(List<Optional<V>> values);
    }

    static final class ListFuture<V> extends CollectionFuture<V, List<V>> {
        ListFuture(ImmutableCollection<? extends ListenableFuture<? extends V>> futures, boolean allMustSucceed) {
            init(new ListFutureRunningState(futures, allMustSucceed));
        }

        private final class ListFutureRunningState extends CollectionFutureRunningState {
            ListFutureRunningState(ImmutableCollection<? extends ListenableFuture<? extends V>> futures, boolean allMustSucceed) {
                super(futures, allMustSucceed);
            }

            @Override
            public List<V> combine(List<Optional<V>> values) {
                List<V> result = Lists.newArrayListWithCapacity(values.size());
                for(Optional<V> element : values) {
                    result.add(element != null ? element.orNull() : null);
                }
                return Collections.unmodifiableList(result);
            }
        }
    }
}

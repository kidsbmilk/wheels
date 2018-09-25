package com.zz4955.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static com.zz4955.concurrent.Futures.getDone;
import static com.zz4955.concurrent.Tools.checkNotNull;
import static com.zz4955.concurrent.Tools.rejectionPropagatingExecutor;

abstract class AbstractTransformFuture<I, O, F, T> extends AbstractFuture.TrustedFuture<O>
        implements Runnable {

    static <I, O> ListenableFuture<O> create(
            ListenableFuture<I> input,
            Function<? super I, ? extends O> function,
            Executor executor) {
        checkNotNull(executor);
        TransformFuture<I, O> output = new TransformFuture<>(input, function);
        input.addListener(output, rejectionPropagatingExecutor(executor, output));
        return output;
    }

    static <I, O> ListenableFuture<O> create(
            ListenableFuture<I> input,
            AsyncFunction<? super I, ? extends O> function,
            Executor executor) {
        checkNotNull(executor);
        AsyncTransformFuture<I, O> output = new AsyncTransformFuture<>(input, function);
        input.addListener(output, rejectionPropagatingExecutor(executor, output));
        return output;
    }

    ListenableFuture<? extends I> inputFuture;
    F function;

    AbstractTransformFuture(ListenableFuture<? extends I> inputFuture, F function) {
        this.inputFuture = checkNotNull(inputFuture);
        this.function = checkNotNull(function);
    }

    @Override
    public final void run() {
        ListenableFuture<? extends I> localInputFuture = inputFuture;
        F localFunction = function;
        if(isCancelled() | localFunction == null | localFunction == null) {
            return ;
        }
        inputFuture = null;
        I sourceResult;
        try {
            sourceResult = getDone(localInputFuture);
        } catch (CancellationException e) {
            cancel(false);
            return;
        } catch (ExecutionException e) {
            setException(e.getCause());
            return ;
        } catch (RuntimeException e) {
            setException(e);
            return;
        } catch (Error e) {
            setException(e);
            return ;
        }
        T transformResult;
        try {
            transformResult = doTransform(localFunction, sourceResult);
        } catch (Throwable t) {
            setException(t);
            return ;
        } finally {
            function = null;
        }
        setResult(transformResult);
    }

    abstract T doTransform(F function, I result) throws Exception;

    abstract void setResult(T result);

    @Override
    protected final void afterDone() {
        maybePropagateCancellationTo(inputFuture);
        this.inputFuture = null;
        this.function = null;
    }

    @Override
    protected String pendingToString() {
        ListenableFuture<? extends I> localInputFuture = inputFuture;
        F localFunction = function;
        String superString = super.pendingToString();
        String resultString = "";
        if(localInputFuture != null) {
            resultString = "inputFuture=[" + localInputFuture + "], ";
        }
        if(localFunction != null) {
            return resultString + "function=[" + localFunction + "]";
        } else if(superString != null) {
            return resultString + superString;
        }
        return null;
    }

    private static final class TransformFuture<I, O>
            extends AbstractTransformFuture<I, O, Function<? super I, ? extends O>, O> {

        TransformFuture(
                ListenableFuture<? extends I> inputFuture, Function<? super I, ? extends O> function) {
            super(inputFuture, function);
        }

        @Override
        O doTransform(Function<? super I, ? extends O> function, I input) {
            return function.apply(input);
        }

        @Override
        void setResult(O result) {
            set(result);
        }
    }

    private static final class AsyncTransformFuture<I, O>
            extends AbstractTransformFuture<I, O, AsyncFunction<? super I, ? extends O>, ListenableFuture<? extends O>> {

        AsyncTransformFuture(
                ListenableFuture<? extends I> inputFuture, AsyncFunction<? super I, ? extends O> function) {
            super(inputFuture, function);
        }

        @Override
        ListenableFuture<? extends O> doTransform(AsyncFunction<? super I, ? extends O> function, I input) throws Exception {
            ListenableFuture<? extends O> outputFuture = function.apply(input);
            checkNotNull(
                    outputFuture,
                    "AsyncFunction apply returned null instead of a Future. "
                    + "Did you mean to return immediateFuture(null)?"
            );
            return outputFuture;
        }

        @Override
        void setResult(ListenableFuture<? extends O> result) {
            setFuture(result);
        }
    }
}

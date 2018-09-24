package com.zz4955.concurrent;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.zz4955.concurrent.Futures.getDone;
import static com.zz4955.concurrent.MoreExecutors.directExecutor;
import static com.zz4955.concurrent.Tools.checkNotNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

public abstract class AbstractFuture<V> extends FluentFuture<V> {

    private static final Logger log = Logger.getLogger(AbstractFuture.class.getName());

    private static final boolean GENERATE_CANCELLATION_CAUSE = Boolean.parseBoolean(
            System.getProperty("concurrent.generate_cancellation_cause", "false")
    );

    private static final long SPIN_THREADHOLD_NANOS = 1000L;
    private static final AtomicHelper ATOMIC_HELPER;
    private static final Object NULL = new Object();

    private volatile Object value;
    private volatile Listener listeners;
    private volatile Waiter waiters;

    protected AbstractFuture() {}

    static {
        AtomicHelper helper;
        Throwable thrownUnsafeFailure = null;
        Throwable thrownAtomicReferenceFieldUpdaterFailure =  null;

//        try {
//            helper = new UnsafeAtomicHelper();
//        } catch (Throwable unsafeFailure) {
//            thrownUnsafeFailure = unsafeFailure;
            try {
                helper = new SafeAtomicHelper(
                        newUpdater(Waiter.class, Thread.class, "thread"),
                        newUpdater(Waiter.class, Waiter.class, "next"),
                        newUpdater(AbstractFuture.class, Waiter.class, "waiters"),
                        newUpdater(AbstractFuture.class, Listener.class, "listeners"),
                        newUpdater(AbstractFuture.class, Object.class, "value")
                );
            } catch (Throwable atomicReferenceFieldUpdaterFailure) {
                thrownAtomicReferenceFieldUpdaterFailure = atomicReferenceFieldUpdaterFailure;
                helper = new SynchronizedHelper();
            }
//        }
        ATOMIC_HELPER = helper;
        Class<?> ensureLoaded = LockSupport.class;
        if(thrownAtomicReferenceFieldUpdaterFailure != null) {
            log.log(Level.SEVERE, "UnsafeAtomicHelper is broken!", thrownUnsafeFailure);
            log.log(Level.SEVERE, "SafeAtomicHelper is broken!", thrownAtomicReferenceFieldUpdaterFailure);
        }
    }

    private void removeWaiter(Waiter node) {
        node.thread = null;
        restart:
        while(true) {
            Waiter pred = null;
            Waiter curr = waiters;
            if(curr == Waiter.TOMBSTONE) {
                return ;
            }
            Waiter succ;
            while(curr != null) {
                succ = curr.next;
                if(curr.thread != null) {
                    pred = curr;
                } else if(pred != null) {
                    pred.next = succ;
                    if(pred.thread == null) {
                        continue restart;
                    }
                } else if(!ATOMIC_HELPER.casWaiters(this, curr, succ)) {
                    continue restart;
                }
                curr = succ;
            }
            break ;
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
        long remainingNanos = unit.toNanos(timeout);
        if(Thread.interrupted()) {
            throw new InterruptedException();
        }
        Object localValue = value;
        if(localValue != null & !(localValue instanceof SetFuture)) {
            return getDoneValue(localValue);
        }
        final long endNanos = remainingNanos > 0 ? System.nanoTime() + remainingNanos : 0;
        long_wait_loop:
        if(remainingNanos >= SPIN_THREADHOLD_NANOS) {
            Waiter oldHead = waiters;
            if(oldHead != Waiter.TOMBSTONE) {
                Waiter node = new Waiter();
                do {
                    node.setNext(oldHead);
                    if(ATOMIC_HELPER.casWaiters(this, oldHead, node)) {
                        while(true) {
                            LockSupport.parkNanos(this, remainingNanos);
                            if(Thread.interrupted()) {
                                removeWaiter(node);
                                throw new InterruptedException();
                            }
                            localValue = value;
                            if(localValue != null & !(localValue instanceof SetFuture)) {
                                return getDoneValue(localValue);
                            }
                            remainingNanos = endNanos - System.nanoTime();
                            if(remainingNanos < SPIN_THREADHOLD_NANOS) {
                                removeWaiter(node);
                                break long_wait_loop;
                            }
                        }
                    }
                    oldHead = waiters;
                } while (oldHead != Waiter.TOMBSTONE);
            }
            return getDoneValue(value);
        }
        while(remainingNanos > 0) {
            localValue = value;
            if(localValue != null & !(localValue instanceof SetFuture)) {
                return getDoneValue(localValue);
            }
            if(Thread.interrupted()) {
                throw new InterruptedException();
            }
            remainingNanos = endNanos - System.nanoTime();
        }
        String futureToString = toString();
        if(isDone()) {
            throw new TimeoutException(
                    "Waited "
                            + timeout
                            + " "
                            + unit.toString().toLowerCase(Locale.ROOT)
                            + " but future completed as timeout expired"
            );
        }
        throw new TimeoutException(
                "Waited "
                        + timeout
                        + " "
                        + unit.toString().toLowerCase(Locale.ROOT)
                        + " for "
                        + futureToString
        );
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        if(Thread.interrupted()) {
            throw new InterruptedException();
        }
        Object localValue = value;
        if(localValue != null & !(localValue instanceof SetFuture)) {
            return getDoneValue(localValue);
        }
        Waiter oldHead = waiters;
        if(oldHead != Waiter.TOMBSTONE) {
            Waiter node = new Waiter();
            do {
                node.setNext(oldHead);
                if(ATOMIC_HELPER.casWaiters(this, oldHead, node)) {
                    while (true) {
                        LockSupport.park(this);
                        if(Thread.interrupted()) {
                            removeWaiter(node);
                            throw new InterruptedException();
                        }
                        localValue = value;
                        if(localValue != null & !(localValue instanceof SetFuture)) {
                            return getDoneValue(localValue);
                        }
                    }
                }
                oldHead = waiters;
            } while (oldHead != Waiter.TOMBSTONE);
        }
        return getDoneValue(value);
    }

    private V getDoneValue(Object obj) throws ExecutionException {
        if(obj instanceof Cancellation) {
            throw cancellationExceptionWithCause("Task was cancelled.", ((Cancellation) obj).cause);
        } else if(obj instanceof Failure) {
            throw new ExecutionException(((Failure) obj).exception);
        } else if(obj == NULL) {
            return null;
        } else {
            @SuppressWarnings("unchecked")
            V asV = (V) obj;
            return asV;
        }
    }

    private static Object getFutureValue(ListenableFuture<?> future) {
        Object valueToSet;
        if(future instanceof TrustedFuture) {
            Object v = ((AbstractFuture<?>) future).value;
            if(v instanceof Cancellation) {
                Cancellation c = (Cancellation) v;
                if(c.wasInterrupted) {
                    v = c.cause != null
                            ? new Cancellation(false, c.cause)
                            : Cancellation.CAUSELESS_CANCELLED;
                }
            }
            return v;
        } else {
            try {
                Object v = getDone(future);
                valueToSet = v == null ? NULL : v;
            } catch (ExecutionException exception) {
                valueToSet = new Failure(exception.getCause());
            } catch (CancellationException cancellation) {
                valueToSet = new Cancellation(false, cancellation);
            } catch (Throwable t) {
                valueToSet = new Failure(t);
            }
        }
        return valueToSet;
    }

    @Override
    public boolean isDone() {
        final Object localValue = value;
        return localValue != null & !(localValue instanceof SetFuture);
    }

    @Override
    public boolean isCancelled() {
        final Object localValue = value;
        return localValue instanceof Cancellation;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Object localValue = value;
        boolean rValue = false;
        if(localValue == null | localValue instanceof SetFuture) {
            Object valueToSet =
                    GENERATE_CANCELLATION_CAUSE ?
                            new Cancellation(mayInterruptIfRunning, new CancellationException("Future.cancel() was called."))
                            : (mayInterruptIfRunning ?
                                                        Cancellation.CAUSELESS_INTERRUPTED
                                                        : Cancellation.CAUSELESS_CANCELLED
                            );
            AbstractFuture<?> abstractFuture = this;
            while(true) {
                if(ATOMIC_HELPER.casValue(abstractFuture, localValue, valueToSet)) {
                    rValue = true;
                    if(mayInterruptIfRunning) {
                        abstractFuture.interruptTask();
                    }
                    complete(abstractFuture);
                    if(localValue instanceof SetFuture) {
                        ListenableFuture<?> futureToPropagateTo = ((SetFuture) localValue).future;
                        if(futureToPropagateTo instanceof TrustedFuture) {
                            AbstractFuture<?> trusted = (AbstractFuture<?>) futureToPropagateTo;
                            localValue = trusted.value;
                            if(localValue == null | localValue instanceof SetFuture) {
                                abstractFuture = trusted;
                                continue;
                            }
                        } else {
                            futureToPropagateTo.cancel(mayInterruptIfRunning);
                        }
                    }
                    break;
                }
                localValue = abstractFuture.value;
                if(!(localValue instanceof SetFuture)) {
                    break;
                }
            }
        }
        return rValue;
    }

    protected void interruptTask() {}

    protected final boolean wasInterrupted() {
        final Object localValue = value;
        return (localValue instanceof Cancellation) && ((Cancellation) localValue).wasInterrupted;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        checkNotNull(listener, "Runnable was null.");
        checkNotNull(executor, "Executor was null.");
        Listener oldHead = listeners;
        if(oldHead != Listener.TOMBOSTONE) {
            Listener newNode = new Listener(listener, executor);
            do {
                newNode.next = oldHead;
                if(ATOMIC_HELPER.casListeners(this, oldHead, newNode)) {
                    return ;
                }
                oldHead = listeners;
            } while (oldHead != Listener.TOMBOSTONE);
        }
        executeListener(listener, executor);
    }

    protected boolean set(V value) {
        Object valueToSet = value == null ? NULL : value;
        if(ATOMIC_HELPER.casValue(this, null, valueToSet)) {
            complete(this);
            return true;
        }
        return false;
    }

    protected boolean setException(Throwable throwable) {
        Object valutToSet = new Failure(checkNotNull(throwable));
        if(ATOMIC_HELPER.casValue(this, null, valutToSet)) {
            complete(this);
            return true;
        }
        return false;
    }

    protected boolean setFuture(ListenableFuture<? extends V> future) {
        checkNotNull(future);
        Object localValue = value;
        if(localValue == null) {
            if(future.isDone()) {
                Object value = getFutureValue(future);
                if(ATOMIC_HELPER.casValue(this, null, value)) {
                    complete(this);
                    return true;
                }
                return false;
            }
            SetFuture valueToSet = new SetFuture(this, future);
            if(ATOMIC_HELPER.casValue(this, null, valueToSet)) {
                try {
                    future.addListener(valueToSet, directExecutor());
                } catch (Throwable t) {
                    Failure failure;
                    try {
                        failure = new Failure(t);
                    } catch (Throwable oomMostLikely) {
                        failure = Failure.FALLBACK_INSTANCE;
                    }
                    boolean unused = ATOMIC_HELPER.casValue(this, valueToSet, failure);
                }
                return true;
            }
            localValue = value;
        }
        if(localValue instanceof Cancellation) {
            future.cancel(((Cancellation) localValue).wasInterrupted);
        }
        return false;
    }

    private static void complete(AbstractFuture<?> future) {
        Listener next = null;
        outer:
        while(true) {
            future.releaseWaiters();
            future.afterDone();
            next = future.clearListeners(next);
            future = null;
            while(next != null) {
                Listener curr = next;
                next = next.next;
                Runnable task = curr.task;
                if(task instanceof SetFuture) {
                    SetFuture<?> setFuture = (SetFuture<?>) task;
                    future = setFuture.owner;
                    if(future.value == setFuture) {
                        Object valueToSet = getFutureValue(setFuture.future);
                        if(ATOMIC_HELPER.casValue(future, setFuture, valueToSet)) {
                            continue outer;
                        }
                    }
                } else {
                    executeListener(task, curr.executor);
                }
            }
            break;
        }
    }

    protected void afterDone() {}

    final Throwable trustedGetException() {
        return ((Failure) value).exception;
    }

    final void maybePropagateCancellationTo(Future<?> related) {
        if(related != null & isCancelled()) {
            related.cancel(wasInterrupted());
        }
    }

    private void releaseWaiters() {
        Waiter head;
        do {
            head = waiters;
        } while (!ATOMIC_HELPER.casWaiters(this, head, Waiter.TOMBSTONE));

        for(Waiter currentWaiter = head; currentWaiter != null; currentWaiter = currentWaiter.next) {
            currentWaiter.unpark();
        }
    }

    private Listener clearListeners(Listener onto) {
        Listener head;
        do {
            head = listeners;
        } while (!ATOMIC_HELPER.casListeners(this, head, Listener.TOMBOSTONE));

        Listener reversedList = onto;
        while(head != null) {
            Listener tmp = head;
            head = head.next;
            tmp.next = reversedList;
            reversedList = tmp;
        }
        return reversedList;
    }

    private static void executeListener(Runnable runnable, Executor executor) {
        try {
            executor.execute(runnable);
        } catch (RuntimeException e) {
            log.log(
                    Level.SEVERE,
                    "RuntimeException while executing runnable " + runnable + " with executor " + executor,
                    e
            );
        }
    }

    private static CancellationException cancellationExceptionWithCause(String message, Throwable cause) {
        CancellationException exception = new CancellationException(message);
        exception.initCause(cause);
        return exception;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append(super.toString())
                .append("[status=");
        if(isCancelled()) {
            builder.append("CANCELLED");
        } else if(isDone()) {
            addDoneString(builder);
        } else {
            String pendingDescription;
            try {
                pendingDescription = pendingToString();
            } catch (RuntimeException e) {
                pendingDescription = "Exception thrown from implementation: " + e.getCause();
            }
            if(!(pendingDescription == null || pendingDescription.isEmpty())) {
                builder.append("PENDING, info=[")
                        .append(pendingDescription)
                        .append("]");
            } else if(isDone()) {
                addDoneString(builder);
            } else {
                builder.append("PENDING");
            }
        }
        return builder.append("]").toString();
    }

    protected String pendingToString() {
        Object localValue = value;
        if(localValue instanceof SetFuture) {
            return "setFuture=[" + userObjectToString(((SetFuture) localValue).future) + "]";
        } else if(this instanceof ScheduledFuture) {
            return "remaining delay=["
                    + ((ScheduledFuture) this).getDelay(TimeUnit.MILLISECONDS.MICROSECONDS)
                    + " ms]";
        }
        return null;
    }

    private void addDoneString(StringBuilder builder) {
        try {
            V value = getDone(this);
            builder.append("SUCCESS, result=")
                    .append(userObjectToString(value))
                    .append("]");
        } catch (ExecutionException e) {
            builder.append("FAILURE, cause=[")
                    .append(e.getClass())
                    .append("]");
        } catch (CancellationException e) {
            builder.append("CANCELLED");
        } catch (RuntimeException e) {
            builder.append("UNKNOWN, cause=[")
                    .append(e.getClass())
                    .append(" thrown from get()]");
        }
    }

    private String userObjectToString(Object o) {
        if(o == this) {
            return "this future";
        }
        return String.valueOf(o);
    }

    abstract static class TrustedFuture<V> extends AbstractFuture<V> {
        @Override
        public final V get() throws InterruptedException, ExecutionException {
            return super.get();
        }

        @Override
        public final V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return super.get(timeout, unit);
        }

        @Override
        public final boolean isDone() {
            return super.isDone();
        }

        @Override
        public final boolean isCancelled() {
            return super.isCancelled();
        }

        @Override
        public final void addListener(Runnable listener, Executor executor) {
            super.addListener(listener, executor);
        }

        @Override
        public final boolean cancel(boolean mayInterruptIfRunning) {
            return super.cancel(mayInterruptIfRunning);
        }
    }

    private static final class Waiter {
        static final Waiter TOMBSTONE = new Waiter(false);

        volatile Thread thread;
        volatile Waiter next;

        Waiter(boolean unused) {}

        Waiter() {
            ATOMIC_HELPER.putThread(this, Thread.currentThread());
        }

        void setNext(Waiter next) {
            ATOMIC_HELPER.putNext(this, next);
        }

        void unpark() {
            Thread w = thread;
            if(w != null) {
                thread = null;
                LockSupport.unpark(w);
            }
        }
    }

    private static final class Listener {
        static final Listener TOMBOSTONE = new Listener(null, null);
        final Runnable task;
        final Executor executor;

        Listener next;

        Listener(Runnable task, Executor executor) {
            this.task = task;
            this.executor = executor;
        }
    }

    private static final class Failure {
        static final Failure FALLBACK_INSTANCE = new Failure(
                new Throwable("Failure occurred while trying to finish a future.") {
                    @Override
                    public synchronized Throwable fillInStackTrace() {
                        return this;
                    }
                }
        );

        final Throwable exception;

        Failure(Throwable exception) {
            this.exception = checkNotNull(exception);
        }
    }

    private static final class Cancellation {
        static final Cancellation CAUSELESS_INTERRUPTED;
        static final Cancellation CAUSELESS_CANCELLED;

        static {
            if(GENERATE_CANCELLATION_CAUSE) {
                CAUSELESS_CANCELLED = null;
                CAUSELESS_INTERRUPTED = null;
            } else {
                CAUSELESS_CANCELLED = new Cancellation(false, null);
                CAUSELESS_INTERRUPTED = new Cancellation(true, null);
            }
        }

        final boolean wasInterrupted;
        final Throwable cause;

        Cancellation(boolean wasInterrupted, Throwable cause) {
            this.wasInterrupted = wasInterrupted;
            this.cause = cause;
        }
    }

    private static final class SetFuture<V> implements Runnable {
        final AbstractFuture<V> owner;
        final ListenableFuture<? extends V> future;

        SetFuture(AbstractFuture<V> owner, ListenableFuture<? extends V> future) {
            this.owner = owner;
            this.future = future;
        }

        @Override
        public void run() {
            if(owner.value != this) {
                return ;
            }
            Object valueToSet = getFutureValue(future);
            if(ATOMIC_HELPER.casValue(owner, this, valueToSet)) {
                complete(owner);
            }
        }
    }

    private abstract static class AtomicHelper {
        abstract void putThread(Waiter waiter, Thread newValue);
        abstract void putNext(Waiter waiter, Waiter newValue);
        abstract boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update);
        abstract boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update);
        abstract boolean casValue(AbstractFuture<?> future, Object expect, Object update);
    }

    /*
    private static final class UnsafeAtomicHelper extends AtomicHelper {
        static final sun.misc.Unsafe UNSAFE;
        static final long LISTENERS_OFFSET;
        static final long WAITERS_OFFSET;
        static final long VALUE_OFFSET;
        static final long WAITER_THREAD_OFFSET;
        static final long WAITER_NEXT_OFFSET;

        static {
            sun.misc.Unsafe unsafe = null;
            try {
                unsafe = sun.misc.Unsafe.getUnsafe();
            } catch (SecurityException tryReflectionInstead) {
                try {
                    unsafe = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<sun.misc.Unsafe>() {
                                @Override
                                public sun.misc.Unsafe run() throws Exception {
                                    Class<sun.misc.Unsafe> k = sun.misc.Unsafe.class;
                                    for(java.lang.reflect.Field f : k.getDeclaredFields()) {
                                        f.setAccessible(true);
                                        Object x = f.get(null);
                                        if(k.isInstance(x)) {
                                            return k.cast(x);
                                        }
                                    }
                                    throw new NoSuchFieldError("the Unsafe");
                                }
                            }
                    );
                } catch (PrivilegedActionException e) {
                    throw new RuntimeException("Could not initialize intrinsics", e.getCause());
                }
            }
            try {
                Class<?> abstractFuture = AbstractFuture.class;
                WAITERS_OFFSET = unsafe.objectFieldOffset(abstractFuture.getDeclaredField("waiters"));
                LISTENERS_OFFSET = unsafe.objectFieldOffset(abstractFuture.getDeclaredField("listeners"));
                VALUE_OFFSET = unsafe.objectFieldOffset(abstractFuture.getDeclaredField("value"));
                WAITER_THREAD_OFFSET = unsafe.objectFieldOffset(Waiter.class.getDeclaredField("thread"));
                WAITER_NEXT_OFFSET = unsafe.objectFieldOffset(Waiter.class.getDeclaredField("next"));
                UNSAFE = unsafe;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        void putThread(Waiter waiter, Thread newValue) {
            UNSAFE.putObject(waiter, WAITER_THREAD_OFFSET, newValue);
        }

        @Override
        void putNext(Waiter waiter, Waiter newValue) {
            UNSAFE.putObject(waiter, WAITER_NEXT_OFFSET, newValue);
        }

        @Override
        boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update) {
            return UNSAFE.compareAndSwapObject(future, WAITERS_OFFSET, expect, update);
        }

        @Override
        boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update) {
            return UNSAFE.compareAndSwapObject(future, LISTENERS_OFFSET, expect, update);
        }

        @Override
        boolean casValue(AbstractFuture<?> future, Object expect, Object update) {
            return UNSAFE.compareAndSwapObject(future, VALUE_OFFSET, expect, update);
        }
    }
    */

    private static final class SafeAtomicHelper extends AtomicHelper {
        final AtomicReferenceFieldUpdater<Waiter, Thread> waiterThreadUpdater;
        final AtomicReferenceFieldUpdater<Waiter, Waiter> waiterNextUpdater;
        final AtomicReferenceFieldUpdater<AbstractFuture, Waiter> waitersUpdater;
        final AtomicReferenceFieldUpdater<AbstractFuture, Listener> listenersUpdater;
        final AtomicReferenceFieldUpdater<AbstractFuture, Object> valueUpdater;

        SafeAtomicHelper(
                AtomicReferenceFieldUpdater<Waiter, Thread> waiterThreadUpdater,
                AtomicReferenceFieldUpdater<Waiter, Waiter> waiterNextUpdater,
                AtomicReferenceFieldUpdater<AbstractFuture, Waiter> waitersUpdater,
                AtomicReferenceFieldUpdater<AbstractFuture, Listener> listenersUpdater,
                AtomicReferenceFieldUpdater<AbstractFuture, Object> valueUpdater) {
            this.waiterThreadUpdater = waiterThreadUpdater;
            this.waiterNextUpdater = waiterNextUpdater;
            this.waitersUpdater = waitersUpdater;
            this.listenersUpdater = listenersUpdater;
            this.valueUpdater = valueUpdater;
        }

        @Override
        void putThread(Waiter waiter, Thread newValue) {
            waiterThreadUpdater.lazySet(waiter, newValue);
        }

        @Override
        void putNext(Waiter waiter, Waiter newValue) {
            waiterNextUpdater.lazySet(waiter, newValue);
        }

        @Override
        boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update) {
            return waitersUpdater.compareAndSet(future, expect, update);
        }

        @Override
        boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update) {
            return listenersUpdater.compareAndSet(future, expect, update);
        }

        @Override
        boolean casValue(AbstractFuture<?> future, Object expect, Object update) {
            return valueUpdater.compareAndSet(future, expect, update);
        }
    }

    private static final class SynchronizedHelper extends AtomicHelper {

        @Override
        void putThread(Waiter waiter, Thread newValue) {
            waiter.thread = newValue;
        }

        @Override
        void putNext(Waiter waiter, Waiter newValue) {
            waiter.next = newValue;
        }

        @Override
        boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update) {
            synchronized (future) {
                if(future.waiters == expect) {
                    future.waiters = update;
                    return true;
                }
                return false;
            }
        }

        @Override
        boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update) {
            synchronized (future) {
                if(future.listeners == expect) {
                    future.listeners = update;
                    return true;
                }
                return false;
            }
        }

        @Override
        boolean casValue(AbstractFuture<?> future, Object expect, Object update) {
            synchronized (future) {
                if(future.value == expect) {
                    future.value = update;
                    return true;
                }
                return false;
            }
        }
    }
}
/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Correct bridge between a plain Java worker and the app's (obfuscated) Kotlin coroutine Flow
 * machinery, used by the local Game Review.
 *
 * <p>{@code Flow.collect(collector, continuation)} is a suspend function. The old code ran the
 * whole review synchronously inside {@code collect} and emitted with a fake continuation
 * (EmptyCoroutineContext, resumeWith ignored). Chess.com 4.10.17 collects the review inside a
 * {@code flow {}} builder (SafeCollector) that rejects emissions from a foreign coroutine
 * context, and its skills step suspends and must be resumed. Neither can work with a fake
 * continuation, so the review never finished and the screen stayed black.
 *
 * <p>This bridge follows the real protocol:
 * <ul>
 *   <li>{@code collect} starts the work on a background thread and returns
 *       {@code COROUTINE_SUSPENDED}; the caller is resumed through its intercepted continuation
 *       (i.e. on its own dispatcher) with Unit or the failure;</li>
 *   <li>every {@code emit} carries the collector's real CoroutineContext; if the downstream
 *       suspends, the worker waits to be resumed and rethrows any failure;</li>
 *   <li>the caller's Job is polled, so closing the review screen stops the analysis.</li>
 * </ul>
 * Only non-obfuscated Kotlin stdlib members are used by name, so it works on every supported
 * Chess.com version.
 */
final class FlowBridge {
    private static final String TAG = "FlowBridge";

    private FlowBridge() {}

    /** Work that emits values through an {@link Emitter}. */
    interface Body {
        void run(Emitter emitter) throws Throwable;
    }

    /** Emits values into the downstream collector with correct suspension handling. */
    static final class Emitter {
        private final AppTypes types;
        private final Object collector;
        private final Object context;
        private final Object job;

        Emitter(AppTypes types, Object collector, Object context) {
            this.types = types;
            this.collector = collector;
            this.context = context;
            this.job = findJob(context);
        }

        /** Throws CancellationException if the collecting coroutine was cancelled. */
        void ensureActive() {
            if (isCancelled(job)) throw new CancellationException("Game review cancelled");
        }

        void emit(Object value) throws Throwable {
            ensureActive();
            final CountDownLatch done = new CountDownLatch(1);
            final AtomicReference<Object> result = new AtomicReference<>();
            Object cont = newContinuation(types.continuationClass, context, r -> {
                result.set(r);
                done.countDown();
            });
            Object ret;
            try {
                ret = types.emitMethod.invoke(collector, value, cont);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
            if (ret != null && ret == suspended()) {
                // Downstream suspended (dispatcher hop, channel, awaiting skills, ...).
                while (!done.await(250, TimeUnit.MILLISECONDS)) ensureActive();
                ret = result.get();
            }
            Throwable failure = failureOf(ret);
            if (failure != null) throw failure;
        }
    }

    private interface ResumeCallback {
        void onResume(Object result);
    }

    /**
     * Implements {@code Flow.collect(collector, continuation)}: runs {@code body} on a worker
     * thread and returns what the suspend function must return to its caller.
     */
    static Object collect(final AppTypes types, final Object collector, final Object callerCont,
                          final String threadName, final Body body) {
        final Emitter emitter = new Emitter(types, collector, contextOf(callerCont));
        // 0 = undecided, 1 = collect() returned SUSPENDED, 2 = worker finished first.
        final AtomicInteger decision = new AtomicInteger(0);
        final AtomicReference<Throwable> earlyFailure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            Throwable failure = null;
            try {
                body.run(emitter);
            } catch (Throwable t) {
                failure = t;
            }
            if (decision.compareAndSet(0, 2)) {
                earlyFailure.set(failure); // collect() has not returned yet and reports it itself
                return;
            }
            resume(callerCont, failure);
        }, threadName);
        worker.setDaemon(true);
        worker.start();

        Object marker = suspended();
        if (marker != null && decision.compareAndSet(0, 1)) return marker;
        if (marker == null) {
            // Should never happen; fall back to blocking so the review still completes.
            try { worker.join(); } catch (InterruptedException ignored) {}
            decision.set(2);
        }
        Throwable f = earlyFailure.get();
        if (f != null) sneakyThrow(f);
        return unit();
    }

    // ── Kotlin runtime helpers ────────────────────────────────────────────────────────────

    private static volatile Object suspendedMarker;
    private static volatile Object unitInstance;

    /** {@code kotlin.coroutines.intrinsics.CoroutineSingletons.COROUTINE_SUSPENDED}. */
    static Object suspended() {
        Object s = suspendedMarker;
        if (s != null) return s;
        try {
            Class<?> c = Class.forName("kotlin.coroutines.intrinsics.CoroutineSingletons");
            for (Object e : c.getEnumConstants()) {
                if ("COROUTINE_SUSPENDED".equals(((Enum<?>) e).name())) {
                    suspendedMarker = e;
                    return e;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "COROUTINE_SUSPENDED not found", t);
        }
        return null;
    }

    static Object unit() {
        Object u = unitInstance;
        if (u != null) return u;
        try {
            Class<?> c = Class.forName("kotlin.Unit");
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == c) {
                    f.setAccessible(true);
                    unitInstance = f.get(null);
                    return unitInstance;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "kotlin.Unit not found", t);
        }
        return null;
    }

    /** Exception carried by a boxed {@code kotlin.Result.Failure}, or null. */
    static Throwable failureOf(Object result) {
        if (result == null || !"kotlin.Result$Failure".equals(result.getClass().getName())) return null;
        try {
            return (Throwable) result.getClass().getField("exception").get(result);
        } catch (Throwable t) {
            return new IllegalStateException("Unreadable Result.Failure", t);
        }
    }

    private static Object failureResult(Throwable t) throws Exception {
        return Class.forName("kotlin.Result$Failure").getConstructor(Throwable.class).newInstance(t);
    }

    static Object contextOf(Object continuation) {
        if (continuation != null) {
            try {
                Method m = continuation.getClass().getMethod("getContext");
                m.setAccessible(true);
                Object ctx = m.invoke(continuation);
                if (ctx != null) return ctx;
            } catch (Throwable t) {
                Log.e(TAG, "getContext failed", t);
            }
        }
        return emptyContext();
    }

    static Object emptyContext() {
        try {
            Class<?> c = Class.forName("kotlin.coroutines.EmptyCoroutineContext");
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == c) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Resumes the suspended caller of {@code collect} through its intercepted continuation, so
     * it continues on its own dispatcher (like {@code suspendCoroutine}).
     */
    static void resume(Object callerCont, Throwable failure) {
        if (callerCont == null) return;
        try {
            Object target = callerCont;
            try {
                Method intercepted = callerCont.getClass().getMethod("intercepted");
                intercepted.setAccessible(true);
                Object i = intercepted.invoke(callerCont);
                if (i != null) target = i;
            } catch (NoSuchMethodException ignored) {
                // Not a ContinuationImpl: resume directly.
            }
            Object value = failure == null ? unit() : failureResult(failure);
            Method resumeWith = null;
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals("resumeWith") && m.getParameterTypes().length == 1) { resumeWith = m; break; }
            }
            if (resumeWith == null) throw new NoSuchMethodException("resumeWith on " + target.getClass());
            resumeWith.setAccessible(true);
            resumeWith.invoke(target, value);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to resume the review collector", t);
        }
    }

    /** A Continuation (the app's obfuscated interface) with the given context. */
    private static Object newContinuation(final Class<?> contClass, final Object context, final ResumeCallback cb) {
        return Proxy.newProxyInstance(contClass.getClassLoader(), new Class<?>[]{contClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getContext": return context;
                            case "resumeWith": cb.onResume(args != null ? args[0] : null); return null;
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return args != null && proxy == args[0];
                            case "toString": return "LocalReviewContinuation";
                            default: return null;
                        }
                    }
                });
    }

    /** The coroutine Job of a context: the element exposing isCancelled() and isCompleted(). */
    static Object findJob(Object context) {
        if (context == null) return null;
        final Object[] found = new Object[1];
        try {
            Class<?> f2 = Class.forName("kotlin.jvm.functions.Function2");
            Object op = Proxy.newProxyInstance(f2.getClassLoader(), new Class<?>[]{f2}, (proxy, method, args) -> {
                if ("invoke".equals(method.getName()) && args != null && args.length == 2) {
                    Object element = args[1];
                    if (found[0] == null && element != null && hasJobShape(element.getClass())) found[0] = element;
                    return args[0];
                }
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return args != null && proxy == args[0];
                return null;
            });
            for (Method m : context.getClass().getMethods()) {
                if (m.getName().equals("fold") && m.getParameterTypes().length == 2) {
                    m.setAccessible(true);
                    m.invoke(context, unit(), op);
                    break;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not look up the coroutine Job: " + t);
        }
        return found[0];
    }

    private static boolean hasJobShape(Class<?> c) {
        try {
            return c.getMethod("isCancelled").getReturnType() == boolean.class
                    && c.getMethod("isCompleted").getReturnType() == boolean.class;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isCancelled(Object job) {
        if (job == null) return false;
        try {
            Method m = job.getClass().getMethod("isCancelled");
            m.setAccessible(true);
            return (Boolean) m.invoke(job);
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}

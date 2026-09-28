/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Resolves obfuscated Chess.com classes that the local Game Review needs, by structure rather
 * than by obfuscated name, so small renames inside a release do not break it.
 *
 * <p>Chess.com 4.10.17: review model in {@code com.chess.compengine.entities.AnalyzedGameData},
 * result sealed class {@code gamereview.repository.g}, source sealed class
 * {@code gamereview.repository.i}.
 */
final class AppTypes {
    private static final String TAG = "StockfishAppTypes";

    private static final String AGD_CLASS = "com.chess.compengine.entities.AnalyzedGameData";

    private static volatile AppTypes cached;

    // Coroutine Flow plumbing (obfuscated interfaces)
    final Class<?> flowClass;
    final Class<?> collectorClass;
    final Class<?> continuationClass;
    final Method emitMethod;

    // Review data model
    final String agdPrefix;           // e.g. "com.chess.compengine.entities.AnalyzedGameData"

    // Repository result types
    final Constructor<?> inProgressCtor;  // (float, AnalysisDepth, Source)
    final Constructor<?> completedCtor;   // (AnalyzedGameData, GameAnalysisPermissions, AnalysisDepth)
    final Constructor<?> failureCtor;     // (Throwable)
    final Object ceacSource;              // Source "Ceac" singleton

    private AppTypes(Class<?> flowClass) throws Exception {
        this.flowClass = flowClass;
        Method collect = null;
        for (Method m : flowClass.getMethods()) {
            if (m.getName().equals("collect") && m.getParameterTypes().length == 2) {
                collect = m;
                break;
            }
        }
        if (collect == null) throw new IllegalStateException("Flow.collect not found on " + flowClass);
        collectorClass = collect.getParameterTypes()[0];
        continuationClass = collect.getParameterTypes()[1];
        emitMethod = collectorClass.getMethod("emit", Object.class, continuationClass);

        Class<?> agd = load(AGD_CLASS);
        if (agd == null) throw new ClassNotFoundException("AnalyzedGameData not found");
        agdPrefix = AGD_CLASS;
        Class<?> depth = Class.forName("com.chess.entities.AnalysisDepth");
        Class<?> perms = Class.forName("com.chess.entities.GameAnalysisPermissions");

        // The repository result sealed class: find its subclasses among single-letter nested
        // classes of the repository package, identified purely by constructor shape.
        Constructor<?> inProgress = null, completed = null, failure = null;
        Class<?> sourceType = null;
        for (char c = 'a'; c <= 'z' && (inProgress == null || completed == null || failure == null); c++) {
            Class<?> base = load("com.chess.gamereview.repository." + c);
            if (base == null || !Modifier.isAbstract(base.getModifiers())) continue;
            Constructor<?> ip = null, cp = null, fl = null;
            Class<?> src = null;
            for (char s = 'a'; s <= 'e'; s++) {
                Class<?> sub = load("com.chess.gamereview.repository." + c + "$" + s);
                if (sub == null || sub.getSuperclass() != base) continue;
                for (Constructor<?> k : sub.getConstructors()) {
                    Class<?>[] p = k.getParameterTypes();
                    if (p.length == 3 && p[0] == float.class && p[1] == depth) { ip = k; src = p[2]; }
                    else if (p.length == 3 && p[0] == agd && p[1] == perms && p[2] == depth) cp = k;
                    else if (p.length == 1 && p[0] == Throwable.class) fl = k;
                }
            }
            // The two-state skills wrapper (j) has a 5-arg completed ctor, so it never matches.
            if (ip != null && cp != null && fl != null) {
                inProgress = ip; completed = cp; failure = fl; sourceType = src;
            }
        }
        if (inProgress == null) throw new IllegalStateException("Game review result types not found");
        inProgressCtor = inProgress;
        completedCtor = completed;
        failureCtor = failure;
        ceacSource = findCeacSource(sourceType);
    }

    /** The "Ceac" (computer engine analysis) singleton of the analysis source sealed class. */
    private static Object findCeacSource(Class<?> sourceType) throws Exception {
        String base = sourceType.getName();
        for (char s = 'a'; s <= 'e'; s++) {
            Class<?> sub = load(base + "$" + s);
            if (sub == null || !sourceType.isAssignableFrom(sub)) continue;
            for (Field f : sub.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == sub) {
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v != null && "Ceac".equals(String.valueOf(v))) return v;
                }
            }
        }
        // Fall back to any object singleton of the sealed class.
        for (char s = 'a'; s <= 'e'; s++) {
            Class<?> sub = load(base + "$" + s);
            if (sub == null || !sourceType.isAssignableFrom(sub)) continue;
            for (Field f : sub.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == sub) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        }
        throw new IllegalStateException("Analysis source singleton not found in " + base);
    }

    static synchronized AppTypes get(Class<?> flowClass) throws Exception {
        AppTypes t = cached;
        if (t != null && (flowClass == null || t.flowClass == flowClass)) return t;
        if (flowClass == null) throw new IllegalStateException("AppTypes not initialised");
        t = new AppTypes(flowClass);
        cached = t;
        Log.i(TAG, "Resolved review types: model=" + t.agdPrefix + " result=" + t.completedCtor.getDeclaringClass().getName()
                + " flow=" + flowClass.getName());
        return t;
    }

    static AppTypes getCached() {
        return cached;
    }

    /** Nested class of AnalyzedGameData, e.g. {@code agd("$AnalyzedPosition$Eval")}. */
    Class<?> agd(String nested) throws ClassNotFoundException {
        return Class.forName(agdPrefix + nested);
    }

    /** Public constructor with the most parameters (the primary data-class constructor). */
    static Constructor<?> primaryCtor(Class<?> cls) {
        Constructor<?> best = null;
        for (Constructor<?> k : cls.getConstructors()) {
            Class<?>[] p = k.getParameterTypes();
            if (p.length > 0 && p[p.length - 1].getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")) continue;
            if (best == null || p.length > best.getParameterTypes().length) best = k;
        }
        return best;
    }

    /**
     * Invoke {@code ctor} filling parameters by type from the given values: each value is used
     * for the first still-unfilled parameter whose type accepts it, in order. Parameters left
     * over get a neutral default (null / 0 / false / empty list).
     */
    static Object construct(Constructor<?> ctor, Object... ordered) throws Exception {
        Class<?>[] p = ctor.getParameterTypes();
        Object[] args = new Object[p.length];
        boolean[] set = new boolean[p.length];
        int from = 0;
        for (Object v : ordered) {
            for (int i = from; i < p.length; i++) {
                if (set[i]) continue;
                if (accepts(p[i], v)) {
                    args[i] = v;
                    set[i] = true;
                    from = i + 1;
                    break;
                }
            }
        }
        for (int i = 0; i < p.length; i++) {
            if (!set[i]) args[i] = defaultFor(p[i]);
        }
        return ctor.newInstance(args);
    }

    private static boolean accepts(Class<?> type, Object v) {
        if (v == Null.INSTANCE) return !type.isPrimitive();
        if (v == null) return false;
        if (type.isPrimitive()) {
            if (type == float.class) return v instanceof Float;
            if (type == int.class) return v instanceof Integer;
            if (type == boolean.class) return v instanceof Boolean;
            if (type == long.class) return v instanceof Long;
            return false;
        }
        return type.isInstance(v);
    }

    static Object defaultFor(Class<?> t) {
        if (!t.isPrimitive()) return t == List.class ? new java.util.ArrayList<>() : null;
        if (t == boolean.class) return false;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == long.class) return 0L;
        return 0;
    }

    /** Marker for an explicit null argument in {@link #construct}. */
    enum Null { INSTANCE }

    static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
        }
        try {
            android.content.Context ctx = StockfishExtension.getContext();
            if (ctx != null) return ctx.getClassLoader().loadClass(name);
        } catch (Throwable ignored) {
        }
        return null;
    }
}

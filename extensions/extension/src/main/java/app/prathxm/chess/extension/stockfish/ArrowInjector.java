/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ArrowInjector {
    private static final String TAG = "ArrowInjector";

    public static final List<Object> lastEngineArrows = new ArrayList<>();
    
    public static final ThreadLocal<Boolean> isInjecting = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };


    // ── Access to CBViewModelStateImpl.moveArrows ─────────────────────────────────────────
    //
    // The members are obfuscated (4.10.17: setter G2 / getter f5 / HintArrow g0), so they are
    // resolved from the generic signatures instead of by name: moveArrows is the only List<HintArrow> property, and HintArrow is the class
    // in the movesinput package whose constructor starts with (Square, Square).

    private static volatile Method cachedSetter, cachedGetter;
    private static volatile Class<?> cachedHintArrowClass;

    /** Element type of a List-typed generic signature, or null. */
    private static Class<?> listElementType(java.lang.reflect.Type t) {
        if (t instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.Type[] a = ((java.lang.reflect.ParameterizedType) t).getActualTypeArguments();
            if (a.length == 1) {
                java.lang.reflect.Type e = a[0];
                if (e instanceof java.lang.reflect.WildcardType) {
                    java.lang.reflect.Type[] ub = ((java.lang.reflect.WildcardType) e).getUpperBounds();
                    if (ub.length == 1) e = ub[0];
                }
                if (e instanceof Class) return (Class<?>) e;
            }
        }
        return null;
    }

    private static boolean isHintArrowClass(Class<?> c) {
        if (c == null || !c.getName().startsWith("com.chess.chessboard.vm.movesinput.")) return false;
        Class<?> square = squareClass();
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            Class<?>[] p = k.getParameterTypes();
            if (p.length >= 5 && p[0] == square && p[1] == square) return true;
        }
        return false;
    }

    private static Class<?> squareClass() {
        try {
            return Class.forName("com.chess.chessboard.t");
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized void resolveMembers(Class<?> stateClass) {
        if (cachedSetter != null && cachedSetter.getDeclaringClass().isAssignableFrom(stateClass)) return;
        Method setter = null, getter = null;
        Class<?> arrow = null;
        for (Method m : stateClass.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == void.class && p.length == 1 && p[0] == List.class) {
                Class<?> e = listElementType(m.getGenericParameterTypes()[0]);
                if (isHintArrowClass(e)) { setter = m; arrow = e; }
            }
        }
        for (Method m : stateClass.getMethods()) {
            if (m.getParameterTypes().length == 0 && m.getReturnType() == List.class
                    && arrow != null && listElementType(m.getGenericReturnType()) == arrow) {
                getter = m;
            }
        }
        if (setter == null) {
            // Last resort: legacy names.
            try { setter = stateClass.getMethod("a2", List.class); } catch (Throwable ignored) {}
            try { getter = stateClass.getMethod("k4"); } catch (Throwable ignored) {}
            try { arrow = Class.forName("com.chess.chessboard.vm.movesinput.k0"); } catch (Throwable ignored) {}
        }
        cachedSetter = setter;
        cachedGetter = getter;
        cachedHintArrowClass = arrow;
        Log.i(TAG, "moveArrows setter=" + (setter != null ? setter.getName() : null)
                + " getter=" + (getter != null ? getter.getName() : null)
                + " hintArrow=" + (arrow != null ? arrow.getName() : null));
    }

    /** Calls setMoveArrows(list) on the board state. */
    static void setMoveArrows(Object stateImpl, List<?> arrows) throws Exception {
        resolveMembers(stateImpl.getClass());
        if (cachedSetter == null) throw new NoSuchMethodException("setMoveArrows");
        cachedSetter.invoke(stateImpl, arrows);
    }

    /** Current moveArrows of the board state, or null. */
    static List<?> getMoveArrows(Object stateImpl) {
        try {
            resolveMembers(stateImpl.getClass());
            if (cachedGetter != null) return (List<?>) cachedGetter.invoke(stateImpl);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Creates a HintArrow: (from, to, Integer color, Float opacity, Z persistent, Z animated).
     */
    private static Object newArrow(Class<?> arrowClass, Object from, Object to, int color, float opacity) throws Exception {
        Constructor<?> best = null;
        for (Constructor<?> k : arrowClass.getDeclaredConstructors()) {
            Class<?>[] p = k.getParameterTypes();
            if (p.length > 0 && p[p.length - 1].getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")) continue;
            if (best == null || p.length > best.getParameterTypes().length) best = k;
        }
        if (best == null) throw new NoSuchMethodException("HintArrow constructor");
        best.setAccessible(true);
        Class<?>[] p = best.getParameterTypes();
        Object[] args = new Object[p.length];
        args[0] = from;
        args[1] = to;
        int bools = 0;
        for (int i = 2; i < p.length; i++) {
            if (p[i] == Integer.class) args[i] = color;
            else if (p[i] == Float.class) args[i] = opacity;
            else if (p[i] == Boolean.class) args[i] = null;               // isKnight (auto)
            else if (p[i] == boolean.class) args[i] = (bools++ == 1);     // persistent=false, animated=true
            else args[i] = AppTypes.defaultFor(p[i]);
        }
        return best.newInstance(args);
    }

    public static boolean isEngineArrow(Object arrow) {
        if (arrow == null) return false;
        try {
            for (Field field : arrow.getClass().getDeclaredFields()) {
                if (field.getType().equals(Integer.class)) {
                    field.setAccessible(true);
                    Integer color = (Integer) field.get(arrow);
                    if (color != null) {
                        int c = color.intValue();
                        if (c == 0xFF00C853 || c == 0xFF2196F3 || c == 0xFFFF9800 || c == 0xFF9C27B0 || c == 0xFFE53935 || c == 0xFFD50000) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void clearEngineArrows(Object stateImpl) {
        if (stateImpl == null) return;

        try {
            List<?> currentArrows = getMoveArrows(stateImpl);

            List<Object> cleanArrows = new ArrayList<>();
            if (currentArrows != null) {
                for (Object arrow : currentArrows) {
                    if (arrow != null && !isEngineArrow(arrow)) {
                        cleanArrows.add(arrow);
                    }
                }
            }

            final List<Object> finalCleanArrows = cleanArrows;
            final Object finalStateImpl = stateImpl;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        isInjecting.set(true);
                        setMoveArrows(finalStateImpl, finalCleanArrows);
                        StockfishExtension.invalidateAllBoards();
                    } catch (Throwable t) {
                        Log.e(TAG, "clearEngineArrows invoke failed: " + t.getMessage(), t);
                    } finally {
                        isInjecting.set(false);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "clearEngineArrows failed: " + t.getMessage());
        }
    }

    public static void injectEngineArrows(Context context, Object stateImpl, List<String> uciMoves, String ponderMove) {
        if (stateImpl == null || context == null) return;

        try {
            Class<?> uClass = Class.forName("com.chess.chessboard.u");
            Object uInstance = uClass.getField("a").get(null); // u.a = INSTANCE
            Method cMethod = uClass.getMethod("c", String.class);

            resolveMembers(stateImpl.getClass());
            final Class<?> hintArrowClass = cachedHintArrowClass;
            if (hintArrowClass == null) throw new ClassNotFoundException("HintArrow");

            List<Object> arrowList = new ArrayList<>();
            float baseOpacity = 0.85f;
            float opacityStep = 0.25f;

            for (int i = 0; i < uciMoves.size(); i++) {
                String uciMove = uciMoves.get(i);
                if (uciMove == null || !uciMove.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) continue;

                String fromStr = uciMove.substring(0, 2);
                String toStr   = uciMove.substring(2, 4);

                Object fromSquare = cMethod.invoke(uInstance, fromStr);
                Object toSquare   = cMethod.invoke(uInstance, toStr);

                if (fromSquare == null || toSquare == null) continue;

                float opacity = Math.max(0.2f, baseOpacity - (i * opacityStep));

                int moveColor;
                if (i == 0) {
                    moveColor = 0xFF00C853; // Green for 1st best move
                } else if (i == 1) {
                    moveColor = 0xFF2196F3; // Blue for 2nd best move
                } else if (i == 2) {
                    moveColor = 0xFFFF9800; // Orange for 3rd best move
                } else if (i == 3) {
                    moveColor = 0xFF9C27B0; // Purple for 4th best move
                } else {
                    moveColor = 0xFFE53935; // Red for other moves
                }

                Object arrow = newArrow(hintArrowClass, fromSquare, toSquare, moveColor, opacity);
                arrowList.add(arrow);
            }

            // Injects Threat Arrow if enabled and ponderMove is valid
            if (StockfishSettings.isThreatArrowsEnabled(context) && ponderMove != null && ponderMove.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
                String fromStr = ponderMove.substring(0, 2);
                String toStr   = ponderMove.substring(2, 4);

                Object fromSquare = cMethod.invoke(uInstance, fromStr);
                Object toSquare   = cMethod.invoke(uInstance, toStr);

                if (fromSquare != null && toSquare != null) {
                    Object threatArrow = newArrow(hintArrowClass, fromSquare, toSquare,
                            0xFFD50000 /* crimson threat */, 0.90f);
                    arrowList.add(threatArrow);
                }
            }

            synchronized (lastEngineArrows) {
                lastEngineArrows.clear();
                lastEngineArrows.addAll(arrowList);
            }

            // Read the current arrows from stateImpl, merge, and call a2
            List<?> currentArrows = getMoveArrows(stateImpl);

            List<Object> merged = new ArrayList<>();
            if (currentArrows != null) {
                for (Object arrow : currentArrows) {
                    if (arrow != null && !isEngineArrow(arrow)) {
                        merged.add(arrow);
                    }
                }
            }
            merged.addAll(arrowList);

            final List<Object> finalMerged = merged;
            final Object finalStateImpl = stateImpl;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        isInjecting.set(true);
                        setMoveArrows(finalStateImpl, finalMerged);
                        StockfishExtension.invalidateAllBoards();
                    } catch (Throwable t) {
                        Log.e(TAG, "injectEngineArrows invoke failed: " + t.getMessage(), t);
                    } finally {
                        isInjecting.set(false);
                    }
                }
            });

        } catch (Throwable t) {
            Log.e(TAG, "injectEngineArrows failed: " + t.getMessage(), t);
        }
    }
}

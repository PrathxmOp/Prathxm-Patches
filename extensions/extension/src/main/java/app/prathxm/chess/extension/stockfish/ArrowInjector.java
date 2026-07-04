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
            List<?> currentArrows = null;
            try {
                Method getMoveArrowsMethod = stateImpl.getClass().getMethod("k4");
                currentArrows = (List<?>) getMoveArrowsMethod.invoke(stateImpl);
            } catch (Throwable ignored) {}

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
                        Method a2 = finalStateImpl.getClass().getMethod("a2", List.class);
                        a2.invoke(finalStateImpl, finalCleanArrows);
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

            Class<?> hintArrowClass = Class.forName("com.chess.chessboard.vm.movesinput.k0");
            Class<?> squareClass = Class.forName("com.chess.chessboard.t");

            Constructor<?> ctor = hintArrowClass.getDeclaredConstructor(
                squareClass,          // fromSquare
                squareClass,          // toSquare
                Boolean.class,        // isKnight (nullable)
                Integer.class,        // color (nullable)
                Float.class,          // opacity (nullable)
                boolean.class,        // persistent
                boolean.class         // animated
            );
            ctor.setAccessible(true);

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

                Object arrow = ctor.newInstance(
                    fromSquare,
                    toSquare,
                    null,                 // isKnight
                    moveColor,
                    Float.valueOf(opacity),
                    false,                // not persistent
                    true                  // animated
                );
                arrowList.add(arrow);
            }

            // Injects Threat Arrow if enabled and ponderMove is valid
            if (StockfishSettings.isThreatArrowsEnabled(context) && ponderMove != null && ponderMove.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
                String fromStr = ponderMove.substring(0, 2);
                String toStr   = ponderMove.substring(2, 4);

                Object fromSquare = cMethod.invoke(uInstance, fromStr);
                Object toSquare   = cMethod.invoke(uInstance, toStr);

                if (fromSquare != null && toSquare != null) {
                    Object threatArrow = ctor.newInstance(
                        fromSquare,
                        toSquare,
                        null,                 // isKnight
                        0xFFD50000,           // Crimson Red for Threat
                        Float.valueOf(0.90f), // High visibility opacity
                        false,                // not persistent
                        true                  // animated
                    );
                    arrowList.add(threatArrow);
                }
            }

            synchronized (lastEngineArrows) {
                lastEngineArrows.clear();
                lastEngineArrows.addAll(arrowList);
            }

            // Read the current arrows from stateImpl, merge, and call a2
            List<?> currentArrows = null;
            try {
                Method getMoveArrowsMethod = stateImpl.getClass().getMethod("k4");
                currentArrows = (List<?>) getMoveArrowsMethod.invoke(stateImpl);
            } catch (Throwable ignored) {}

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
                        Method a2 = finalStateImpl.getClass().getMethod("a2", List.class);
                        a2.invoke(finalStateImpl, finalMerged);
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

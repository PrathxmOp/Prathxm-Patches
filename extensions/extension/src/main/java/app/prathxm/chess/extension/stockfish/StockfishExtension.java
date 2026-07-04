package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public class StockfishExtension {

    private static final String TAG = "StockfishExt";

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stockfish-analysis");
                t.setDaemon(true);
                return t;
            });

    private static volatile Future<?> currentJob = null;

    private static final AtomicReference<WeakReference<Object>> stateImplRef =
            new AtomicReference<>(new WeakReference<>(null));

    private static volatile boolean engineReady = false;
    private static volatile boolean lifecycleCallbacksRegistered = false;
    private static volatile boolean isInitializing = false;
    public static volatile boolean isReviewMode = false;
    public static volatile boolean isDeveloperMode = false;

    public static void registerChessBoardView(Object view) {
        // no-op, kept for compatibility
    }

    public static void invalidateAllBoards() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) {
                        Log.d(TAG, "invalidateAllBoards: no current activity");
                        return;
                    }
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    findAndInvalidateChessBoardViews(decorView);
                } catch (Throwable t) {
                    Log.e(TAG, "invalidateAllBoards failed: " + t.getMessage());
                }
            }
        });
    }

    public static Activity getCurrentActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesMap = activitiesField.get(activityThread);
            if (activitiesMap instanceof java.util.Map) {
                for (Object activityRecord : ((java.util.Map<?, ?>) activitiesMap).values()) {
                    Class<?> recordClass = activityRecord.getClass();
                    Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = recordClass.getDeclaredField("activity");
                        activityField.setAccessible(true);
                        return (Activity) activityField.get(activityRecord);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getCurrentActivity failed: " + t.getMessage());
        }
        return null;
    }

    private static void findAndInvalidateChessBoardViews(View view) {
        if (view == null) return;
        if (view.getClass().getName().equals("com.chess.chessboard.view.ChessBoardView")) {
            view.invalidate();
            Log.d(TAG, "invalidated ChessBoardView: " + view);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAndInvalidateChessBoardViews(group.getChildAt(i));
            }
        }
    }

    public static Object[] ensureHintArrowsEnabled(Object[] optionalPainters) {
        if (optionalPainters == null) {
            return null;
        }
        try {
            boolean hasKeyMoveHints = false;
            for (Object type : optionalPainters) {
                if (type != null && "KEY_MOVE_HINTS".equals(type.toString())) {
                    hasKeyMoveHints = true;
                    break;
                }
            }
            if (!hasKeyMoveHints) {
                Log.d(TAG, "injecting KEY_MOVE_HINTS into optional painters array");
                Class<?> optionalPainterClass = Class.forName("com.chess.internal.utils.chessboard.ChessBoardViewOptionalPainterType");
                Object gField = null;
                for (Field field : optionalPainterClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                        field.getType().equals(optionalPainterClass)) {
                        try {
                            field.setAccessible(true);
                            Object val = field.get(null);
                            if (val != null && "KEY_MOVE_HINTS".equals(val.toString())) {
                                gField = val;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                if (gField == null) {
                    Log.e(TAG, "Could not find KEY_MOVE_HINTS field in ChessBoardViewOptionalPainterType");
                    return optionalPainters;
                }

                Object[] newPainters = (Object[]) java.lang.reflect.Array.newInstance(optionalPainterClass, optionalPainters.length + 1);
                System.arraycopy(optionalPainters, 0, newPainters, 0, optionalPainters.length);
                newPainters[optionalPainters.length] = gField;
                return newPainters;
            }
        } catch (Throwable t) {
            Log.e(TAG, "ensureHintArrowsEnabled failed: " + t.getMessage(), t);
        }
        return optionalPainters;
    }

    public static void ensureEngineReady() {
        if (engineReady || isInitializing) return;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            final Context ctx = (Context) currentApplicationMethod.invoke(null);
            if (ctx == null) {
                return;
            }

            Application app = (Application) ctx.getApplicationContext();
            if (!lifecycleCallbacksRegistered && app != null) {
                registerLifecycleCallbacks(app);
                lifecycleCallbacksRegistered = true;
            }

            isInitializing = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean ok = StockfishBridge.init(ctx);
                        engineReady = ok;
                        Log.i(TAG, ok
                            ? "Stockfish engine initialised asynchronously."
                            : "Stockfish engine failed to initialise asynchronously.");
                        if (ok) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    triggerAnalysisForCurrentState();
                                }
                            });
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Exception in async Stockfish init: " + t.getMessage());
                    } finally {
                        isInitializing = false;
                    }
                }
            }).start();

        } catch (Throwable t) {
            Log.e(TAG, "Exception initialising Stockfish: " + t.getMessage());
            isInitializing = false;
        }
    }

    public static void onBoardChanged(Object stateImplObject, Object positionObject) {
        if (stateImplObject == null || positionObject == null) return;
        
        stateImplRef.set(new WeakReference<>(stateImplObject));

        final Activity activity = getCurrentActivity();
        if (activity != null) {
            if (isLiveMatch(activity)) {
                isReviewMode = false;
            }
        }

        GestureInterceptor.ensureGestureInterceptorRegistered();
        ensureEngineReady();
        if (!engineReady) return;

        Context ctx = getContext();
        if (ctx != null && !StockfishSettings.isEngineEnabled(ctx)) {
            Log.d(TAG, "Engine is disabled in settings.");
            ArrowInjector.clearEngineArrows(stateImplObject);
            OverlayManager.hideEvalBar();
            OverlayManager.hideWdlBar();
            OverlayManager.hideMateAnnouncement();
            return;
        }

        ArrowInjector.clearEngineArrows(stateImplObject);

        String fen = extractFen(positionObject);
        if (fen == null) {
            Log.w(TAG, "onBoardChanged: could not extract FEN");
            return;
        }

        Log.d(TAG, "Position changed → FEN: " + fen);
        MoveClassifier.updateHistory(fen);
        scheduleAnalysis(fen);
    }

    public static void onArrowsChanged(Object stateImplObject, List<?> arrows) {
        if (stateImplObject == null) return;
        stateImplRef.set(new WeakReference<>(stateImplObject));

        GestureInterceptor.ensureGestureInterceptorRegistered();

        if (ArrowInjector.isInjecting.get()) {
            return;
        }

        Context context = getContext();
        if (context == null) return;

        boolean enabled = StockfishSettings.isEngineEnabled(context);
        boolean visible = StockfishSettings.isArrowsVisible(context);

        if (enabled && visible) {
            boolean showArrows = true;
            if (StockfishSettings.isMySideOnly(context)) {
                Boolean userWhite = isUserWhite(stateImplObject);
                if (userWhite != null) {
                    try {
                        Method getPositionMethod = stateImplObject.getClass().getMethod("getPosition");
                        Object positionObject = getPositionMethod.invoke(stateImplObject);
                        if (positionObject != null) {
                            Method getSideToMove = positionObject.getClass().getMethod("getSideToMove");
                            Object sideToMove = getSideToMove.invoke(positionObject);
                            if (sideToMove != null) {
                                Method isWhiteMethod = sideToMove.getClass().getMethod("isWhite");
                                boolean isWhiteMove = (boolean) isWhiteMethod.invoke(sideToMove);
                                if (isWhiteMove != userWhite) {
                                    showArrows = false;
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error checking side-aware turn in onArrowsChanged: " + t.getMessage());
                    }
                }
            }

            final List<Object> finalAppArrows = new ArrayList<>();
            if (arrows != null) {
                for (Object arrow : arrows) {
                    if (arrow != null && !ArrowInjector.isEngineArrow(arrow)) {
                        finalAppArrows.add(arrow);
                    }
                }
            }

            if (showArrows) {
                synchronized (ArrowInjector.lastEngineArrows) {
                    finalAppArrows.addAll(ArrowInjector.lastEngineArrows);
                }
            }

            final Object finalStateImpl = stateImplObject;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ArrowInjector.isInjecting.set(true);
                        Method a2 = finalStateImpl.getClass().getMethod("a2", List.class);
                        a2.invoke(finalStateImpl, finalAppArrows);
                        invalidateAllBoards();
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to inject merged arrows in onArrowsChanged: " + t.getMessage());
                    } finally {
                        ArrowInjector.isInjecting.set(false);
                    }
                }
            });
        }
    }

    private static void scheduleAnalysis(String fen) {
        Future<?> prev = currentJob;
        if (prev != null && !prev.isDone()) {
            prev.cancel(true);
            StockfishBridge.stopSearch();
        }

        currentJob = executor.submit(() -> {
            try {
                Context context = getContext();
                if (context == null) return;

                int depth = StockfishSettings.getDepth(context);
                int multiPV = StockfishSettings.getMultiPV(context);

                Log.d(TAG, "Analysing FEN at depth " + depth + " with MultiPV=" + multiPV + "…");
                StockfishProcess.AnalysisResult result = StockfishBridge.analyze(fen, depth, multiPV);

                if (result.moves.isEmpty()) {
                    Log.d(TAG, "Engine returned no best moves.");
                    return;
                }

                String key = MoveClassifier.getFenKey(fen);
                if (key != null) {
                    MoveClassifier.getFenToEvalMap().put(key, result.score);
                    MoveClassifier.getFenToBestMovesMap().put(key, result.moves);
                }

                MoveClassifier.classifyMoveIfPossible(context, fen, result);

                Log.i(TAG, "Best moves: " + result.moves + ", Score: " + result.score);
                
                boolean isLive = false;
                Activity activity = getCurrentActivity();
                if (activity != null && isLiveMatch(activity)) {
                    isLive = true;
                }
                boolean disableOverlays = isLive && !isReviewMode;

                boolean showArrows = !disableOverlays && StockfishSettings.isArrowsVisible(context);
                if (showArrows && StockfishSettings.isMySideOnly(context)) {
                    Boolean userWhite = isUserWhite(getStateImpl());
                    if (userWhite != null) {
                        boolean isWhiteTurn = isWhiteTurnFromFen(fen);
                        if (isWhiteTurn != userWhite) {
                            showArrows = false;
                        }
                    }
                }

                if (showArrows) {
                    ArrowInjector.injectEngineArrows(context, getStateImpl(), result.moves, result.ponder);
                } else {
                    ArrowInjector.clearEngineArrows(getStateImpl());
                }

                if (!disableOverlays && StockfishSettings.isEvalBarEnabled(context)) {
                    OverlayManager.updateEvalBar(result.score, result.hasMate, result.mateIn, getStateImpl());
                } else {
                    OverlayManager.hideEvalBar();
                }

                if (!disableOverlays && StockfishSettings.isWdlEnabled(context)) {
                    OverlayManager.updateWdlBar(result.wdlWin, result.wdlDraw, result.wdlLoss);
                } else {
                    OverlayManager.hideWdlBar();
                }

                if (!disableOverlays && result.hasMate && StockfishSettings.isMateAnnouncementEnabled(context)) {
                    OverlayManager.showMateAnnouncement(result.mateIn);
                } else {
                    OverlayManager.hideMateAnnouncement();
                }

            } catch (Throwable t) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.e(TAG, "Analysis error: " + t.getMessage());
                }
            }
        });
    }

    private static boolean isWhiteTurnFromFen(String fen) {
        if (fen == null) return true;
        String[] parts = fen.split("\\s+");
        return parts.length > 1 && parts[1].equals("w");
    }

    private static void registerLifecycleCallbacks(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
                String name = activity.getClass().getName();
                if (name.startsWith("com.chess.features.puzzles.")) {
                    activity.finish();
                    activity.overridePendingTransition(0, 0);
                    
                    try {
                        android.content.Intent intent = new android.content.Intent(activity, Class.forName("app.prathxm.chess.extension.lichesspuzzle.LichessPuzzleJourneyActivity"));
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to redirect puzzle activity: " + t.getMessage(), t);
                    }
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                GestureInterceptor.registerGestureInterceptor(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    public static void toggleEverything(Activity activity) {
        boolean enabled = !StockfishSettings.isEngineEnabled(activity);
        StockfishSettings.setEngineEnabled(activity, enabled);
        
        if (!enabled) {
            Future<?> prev = currentJob;
            if (prev != null && !prev.isDone()) {
                prev.cancel(true);
            }
            StockfishBridge.stopSearch();
            
            ArrowInjector.clearEngineArrows(getStateImpl());
            OverlayManager.hideEvalBar();
            OverlayManager.hideWdlBar();
            OverlayManager.hideMateAnnouncement();
        } else {
            triggerAnalysisForCurrentState();
        }
    }

    public static void triggerAnalysisForCurrentState() {
        Object state = getStateImpl();
        if (state != null) {
            try {
                Method getPosition = state.getClass().getMethod("getPosition");
                Object positionObject = getPosition.invoke(state);
                if (positionObject != null) {
                    String fen = extractFen(positionObject);
                    if (fen != null) {
                        scheduleAnalysis(fen);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "triggerAnalysisForCurrentState failed: " + t.getMessage());
            }
        }
    }

    public static Context getContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            Context ctx = (Context) currentApplicationMethod.invoke(null);
            if (ctx != null && !engineReady) {
                ensureEngineReady();
            }
            return ctx;
        } catch (Throwable t) {
            Log.e(TAG, "getContext failed: " + t.getMessage());
        }
        return null;
    }

    public static Object getStateImpl() {
        WeakReference<Object> ref = stateImplRef.get();
        return ref != null ? ref.get() : null;
    }

    public static Boolean isUserWhite(Object stateImplObject) {
        try {
            Field field = null;
            try {
                field = stateImplObject.getClass().getDeclaredField("sideToPlaySelfEffects");
            } catch (NoSuchFieldException e) {
                for (Field f : stateImplObject.getClass().getDeclaredFields()) {
                    if (f.getType().getName().equals("kotlin.jvm.functions.Function0")) {
                        field = f;
                        break;
                    }
                }
            }
            
            if (field != null) {
                field.setAccessible(true);
                Object sideToPlaySelfEffects = field.get(stateImplObject);
                if (sideToPlaySelfEffects == null) return null;
                
                Method invokeMethod = sideToPlaySelfEffects.getClass().getMethod("invoke");
                Object side = invokeMethod.invoke(sideToPlaySelfEffects);
                if (side == null) return null;
                
                Method getColorMethod = null;
                try {
                    getColorMethod = side.getClass().getMethod("getColor");
                } catch (NoSuchMethodException e) {
                    getColorMethod = side.getClass().getMethod("d");
                }
                Object color = getColorMethod.invoke(side);
                if (color == null) return null;
                
                String colorName = color.toString();
                if ("WHITE".equalsIgnoreCase(colorName)) {
                    return true;
                } else if ("BLACK".equalsIgnoreCase(colorName)) {
                    return false;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "isUserWhite failed: " + t.getMessage(), t);
        }
        return null;
    }

    public static String extractFen(Object position) {
        try {
            Class<?> posExtKt = Class.forName(
                "com.chess.chessboard.variants.standard.bitboard.FenUtilsKt");
            
            String board = null;
            String castling = null;
            String enPassant = null;
            
            for (Method m : posExtKt.getMethods()) {
                if (m.getParameterCount() == 1) {
                    if (m.getName().equals("b")) {
                        Object res = m.invoke(null, position);
                        if (res != null) board = res.toString();
                    } else if (m.getName().equals("c")) {
                        Object res = m.invoke(null, position);
                        if (res != null) castling = res.toString();
                    } else if (m.getName().equals("e")) {
                        Object res = m.invoke(null, position);
                        if (res != null) enPassant = res.toString();
                    }
                }
            }
            
            if (board == null) return null;
            if (castling == null) castling = "-";
            if (enPassant == null) enPassant = "-";
            
            Method getSideToMove = position.getClass().getMethod("getSideToMove");
            Object sideToMove = getSideToMove.invoke(position);
            Method isWhiteMethod = sideToMove.getClass().getMethod("isWhite");
            boolean isWhite = (boolean) isWhiteMethod.invoke(sideToMove);
            String turn = isWhite ? "w" : "b";
            
            return board + " " + turn + " " + castling + " " + enPassant + " 0 1";
        } catch (Throwable t) {
            Log.e(TAG, "extractFen failed: " + t.getMessage(), t);
        }
        return null;
    }

    public static boolean shouldShowAds(boolean defaultValue) {
        Context context = getContext();
        if (context == null) return defaultValue;
        return !StockfishSettings.isAdsRemoved(context);
    }

    public static Boolean shouldShowAdsObject(Boolean defaultValue) {
        Context context = getContext();
        if (context == null) return defaultValue;
        boolean original = defaultValue != null ? defaultValue : true;
        return !StockfishSettings.isAdsRemoved(context) ? original : Boolean.FALSE;
    }

    public static int getPremiumStatus(int defaultValue) {
        Context context = getContext();
        if (context == null) return defaultValue;
        if (StockfishSettings.isPremiumEnabled(context)) {
            return 3; // DIAMOND
        }
        return defaultValue;
    }

    public static Object getPremiumStatusObject(Object defaultValue) {
        Context context = getContext();
        boolean enabled = (context == null) || StockfishSettings.isPremiumEnabled(context);
        if (enabled) {
            try {
                Class<?> premiumStatusClass = Class.forName("com.chess.entities.PremiumStatus");
                return premiumStatusClass.getField("DIAMOND").get(null);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get PremiumStatus.DIAMOND: " + t.getMessage());
            }
        }
        return defaultValue;
    }

    public static Object getDiamondStatus() {
        return getPremiumStatusObject(null);
    }

    public static boolean getAnalysisPermission(boolean defaultValue, String permissionName) {
        Context context = getContext();
        if (context == null) return defaultValue;
        if (StockfishSettings.isPremiumEnabled(context)) {
            return true;
        }
        return defaultValue;
    }

    public static boolean isBotPlayable(boolean original) {
        return true;
    }

    public static Boolean isBotPlayableObject(Boolean original) {
        return Boolean.TRUE;
    }

    public static boolean isBotEnabled(boolean original) {
        return true;
    }

    public static Boolean isBotPremium(Boolean original) {
        return Boolean.FALSE;
    }

    public static boolean isBotLocked(Object bot) {
        return false;
    }

    public static boolean isLiveMatch(Activity activity) {
        if (isDeveloperMode) return false;
        if (activity == null) return false;
        String name = activity.getClass().getName();
        String lower = name.toLowerCase();
        
        if (lower.contains("computer") || lower.contains("bot") || lower.contains("practice") ||
            lower.contains("analysis") || lower.contains("review") || lower.contains("local") ||
            lower.contains("solo") || lower.contains("tutorial") || lower.contains("puzzle")) {
            return false;
        }
        
        if (lower.contains("playactivity") || lower.contains("gameactivity") || lower.contains("live")) {
            isReviewMode = false;
            return true;
        }
        
        if (lower.contains(".play.")) {
            isReviewMode = false;
            return true;
        }
        return false;
    }

    public static Object getLocalAnalysisFlow(
        Object repository,
        Object gameIdAndType,
        String pgn,
        Object userSide,
        Object coach,
        java.util.Set<?> allowedSources,
        Object analysisDepth,
        Object analysisEngine
    ) {
        Log.d(TAG, "getLocalAnalysisFlow called with pgn: " + (pgn != null ? (pgn.substring(0, Math.min(pgn.length(), 30)) + "...") : "null"));
        return LocalAnalysisFlow.createFlow(pgn, analysisDepth);
    }
}

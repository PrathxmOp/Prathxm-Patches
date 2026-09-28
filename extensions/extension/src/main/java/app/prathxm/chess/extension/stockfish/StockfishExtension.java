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

    /** Resumed activity, tracked by the lifecycle callbacks (cheap, no hidden-API reflection). */
    private static volatile WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    public static Activity getCurrentActivity() {
        Activity tracked = resumedActivity.get();
        if (tracked != null && !tracked.isFinishing()) return tracked;
        return findResumedActivityReflectively();
    }

    /** Fallback before the callbacks run: ActivityThread.mActivities (hidden API, slow). */
    private static Activity findResumedActivityReflectively() {
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
            OverlayManager.hideEngineInfo();
            return;
        }

        ArrowInjector.clearEngineArrows(stateImplObject);
        lastArrowSignature = null;

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
                        ArrowInjector.setMoveArrows(finalStateImpl, finalAppArrows);
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

    /** Signature (position + moves) of the engine arrows currently on the board. */
    private static volatile String lastArrowSignature = null;

    /** Position (FEN key) of the most recently scheduled live analysis. */
    private static volatile String lastScheduledKey = null;

    private static void scheduleAnalysis(String fen) {
        scheduleAnalysis(fen, false);
    }

    /** @param force restart even if this position is already being analysed (settings changed). */
    private static void scheduleAnalysis(String fen, boolean force) {
        // The board callback fires several times for the same position (move animation,
        // arrow updates, re-renders). Restarting an identical search each time just burns CPU.
        String posKey = StockfishBridge.positionKey(fen);
        Future<?> running = currentJob;
        if (!force && posKey != null && posKey.equals(lastScheduledKey) && running != null && !running.isDone()) {
            return;
        }
        lastScheduledKey = posKey;
        Future<?> prev = currentJob;
        if (prev != null && !prev.isDone()) {
            prev.cancel(true);
            StockfishBridge.stopSearch();
        }

        final String jobKey = posKey;
        currentJob = executor.submit(() -> {
            try {
                Context context = getContext();
                if (context == null) return;

                int depth = StockfishSettings.getDepth(context);
                int multiPV = StockfishSettings.getMultiPV(context);

                Log.d(TAG, "Analysing FEN at depth " + depth + " with MultiPV=" + multiPV + "…");
                // Stream intermediate depths to the board so deep searches feel instant.
                StockfishProcess.AnalysisResult result = StockfishBridge.analyze(fen, depth, multiPV,
                        partial -> {
                            // Keep intermediate evaluations for the move classifier even if the
                            // search is cancelled later (see MoveClassifier.recordResult).
                            MoveClassifier.recordResult(fen, partial);
                            if (isStale(jobKey)) return;
                            displayLiveResult(context, fen, partial, false);
                        });

                MoveClassifier.recordResult(fen, result);

                // The user moved on while we were searching: never paint an outdated result,
                // but still rate the move that led here with the deepest result reached, so
                // the toast is not lost when the opponent replies quickly.
                if (isStale(jobKey)) {
                    if (MoveClassifier.isUsableForRating(result)) {
                        MoveClassifier.classifyMoveIfPossible(context, fen, result);
                    }
                    return;
                }

                if (result.moves.isEmpty()) {
                    // Checkmate / stalemate: still rate the move that produced it.
                    if (result.terminal) MoveClassifier.classifyMoveIfPossible(context, fen, result);
                    Log.d(TAG, "Engine returned no best moves.");
                    return;
                }

                MoveClassifier.classifyMoveIfPossible(context, fen, result);

                Log.i(TAG, "Best moves: " + result.moves + ", Score: " + result.score + ", depth " + result.depth);
                displayLiveResult(context, fen, result, true);

            } catch (Throwable t) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.e(TAG, "Analysis error: " + t.getMessage());
                }
            }
        });
    }

    /** True if the running job was cancelled or a newer position has been scheduled. */
    private static boolean isStale(String jobKey) {
        if (Thread.currentThread().isInterrupted()) return true;
        String latest = lastScheduledKey;
        return jobKey != null && !jobKey.equals(latest);
    }

    /**
     * Paints an analysis result (arrows, eval bar, WDL bar, mate banner). Intermediate results
     * ({@code isFinal == false}) only update arrows and bars; the mate banner waits for the
     * final search so it does not flicker.
     */
    private static void displayLiveResult(Context context, String fen,
                                          StockfishProcess.AnalysisResult result, boolean isFinal) {
        if (result == null || result.moves.isEmpty()) return;
        if (!StockfishSettings.isEngineEnabled(context)) return;

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
            // Re-injecting identical arrows restarts their animation (visible flicker while
            // the search deepens), so only push arrows when they actually changed.
            String sig = fen + '|' + result.moves
                    + (StockfishSettings.isThreatArrowsEnabled(context) ? "|" + result.ponder : "");
            if (!sig.equals(lastArrowSignature)) {
                lastArrowSignature = sig;
                ArrowInjector.injectEngineArrows(context, getStateImpl(), result.moves, result.ponder);
            }
        } else if (isFinal) {
            lastArrowSignature = null;
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

        if (!disableOverlays && StockfishSettings.isEngineInfoEnabled(context)) {
            OverlayManager.updateEngineInfo(result.depth, result.score, result.hasMate, result.mateIn);
        } else {
            OverlayManager.hideEngineInfo();
        }

        if (!isFinal) return;
        if (!disableOverlays && result.hasMate && StockfishSettings.isMateAnnouncementEnabled(context)) {
            OverlayManager.showMateAnnouncement(result.mateIn);
        } else {
            OverlayManager.hideMateAnnouncement();
        }
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
                resumedActivity = new WeakReference<>(activity);
                GestureInterceptor.registerGestureInterceptor(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (resumedActivity.get() == activity) resumedActivity = new WeakReference<>(null);
            }

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
            lastArrowSignature = null;
            OverlayManager.hideEvalBar();
            OverlayManager.hideWdlBar();
            OverlayManager.hideMateAnnouncement();
            OverlayManager.hideEngineInfo();
        } else {
            triggerAnalysisForCurrentState();
        }
    }

    public static void triggerAnalysisForCurrentState() {
        // Settings may have changed (depth, lines, overlays): always repaint and restart the
        // search even if the same position is already being analysed.
        lastArrowSignature = null;
        Object state = getStateImpl();
        if (state != null) {
            try {
                Method getPosition = state.getClass().getMethod("getPosition");
                Object positionObject = getPosition.invoke(state);
                if (positionObject != null) {
                    String fen = extractFen(positionObject);
                    if (fen != null) {
                        scheduleAnalysis(fen, true);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "triggerAnalysisForCurrentState failed: " + t.getMessage());
            }
        }
    }

    /** Application context, cached after the first successful lookup (it never changes). */
    private static volatile Context appContext;

    /**
     * The Application. Called many times per move and from hot getters (ads, premium), so the
     * reflective ActivityThread lookup runs only until it first succeeds.
     */
    public static Context getContext() {
        Context ctx = appContext;
        if (ctx == null) {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
                ctx = (Context) currentApplicationMethod.invoke(null);
                if (ctx != null) appContext = ctx;
            } catch (Throwable t) {
                Log.e(TAG, "getContext failed: " + t.getMessage());
                return null;
            }
        }
        if (ctx != null && !engineReady) {
            ensureEngineReady();
        }
        return ctx;
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
                invokeMethod.setAccessible(true);
                Object side = invokeMethod.invoke(sideToPlaySelfEffects);
                if (side == null) return null;
                return sideToWhite(side);
            }
        } catch (Throwable t) {
            Log.e(TAG, "isUserWhite failed: " + t.getMessage(), t);
        }
        return null;
    }

    /**
     * Colour the user plays from the board's {@code Side} value (WHITE, BLACK, BOTH, NONE).
     *
     * <p>The accessor for the side's colour is obfuscated ({@code c()} in 4.10.17), but the enum
     * constant names are not, so they are checked first; the Color-returning accessor is found
     * by return type as a fallback.
     *
     * @return TRUE for white, FALSE for black, null if the user plays both sides or neither
     */
    static Boolean sideToWhite(Object side) {
        if (side == null) return null;
        String name = side instanceof Enum ? ((Enum<?>) side).name() : String.valueOf(side);
        if ("WHITE".equalsIgnoreCase(name)) return Boolean.TRUE;
        if ("BLACK".equalsIgnoreCase(name)) return Boolean.FALSE;
        if ("BOTH".equalsIgnoreCase(name) || "NONE".equalsIgnoreCase(name)) return null;
        try {
            Class<?> colorClass = Class.forName("com.chess.entities.Color");
            for (Method m : side.getClass().getMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType() == colorClass) {
                    m.setAccessible(true);
                    Object color = m.invoke(side);
                    if (color == null) return null;
                    String c = color instanceof Enum ? ((Enum<?>) color).name() : color.toString();
                    if ("WHITE".equalsIgnoreCase(c)) return Boolean.TRUE;
                    if ("BLACK".equalsIgnoreCase(c)) return Boolean.FALSE;
                    return null;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "sideToWhite failed: " + t.getMessage());
        }
        return null;
    }

    /** Cached {@code variants.d.o()}: the position's full FEN (lazy property "fen" in 4.10.17). */
    private static volatile Method fullFenMethod;
    private static volatile boolean fullFenResolved;

    /**
     * FEN of an app position. Uses the app's own full FEN (with the real half-move clock and
     * move number, so Stockfish sees the 50-move rule), and falls back to assembling it from
     * FenUtilsKt with "0 1" counters.
     */
    public static String extractFen(Object position) {
        if (position == null) return null;
        String full = fullFen(position);
        if (full != null) return full;
        return assembleFen(position);
    }

    static String fullFen(Object position) {
        try {
            if (!fullFenResolved) {
                Method found = null;
                try {
                    Method o = Class.forName("com.chess.chessboard.variants.d").getMethod("o");
                    if (o.getReturnType() == String.class) found = o;
                } catch (Throwable ignored) {}
                fullFenMethod = found;
                fullFenResolved = true;
            }
            Method m = fullFenMethod;
            if (m == null || !m.getDeclaringClass().isInstance(position)) return null;
            Object r = m.invoke(position);
            return r instanceof String ? sanitizeFen((String) r) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns the FEN if it has 6 well-formed fields, otherwise null. */
    static String sanitizeFen(String fen) {
        if (fen == null) return null;
        String[] p = fen.trim().split("\\s+");
        if (p.length != 6 || p[0].split("/", -1).length != 8) return null;
        if (!(p[1].equals("w") || p[1].equals("b"))) return null;
        try {
            if (Integer.parseInt(p[4]) < 0 || Integer.parseInt(p[5]) < 1) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return p[0] + ' ' + p[1] + ' ' + p[2] + ' ' + p[3] + ' ' + p[4] + ' ' + p[5];
    }

    private static String assembleFen(Object position) {
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

    /**
     * Screens where a game against another human is in progress (or being watched live) in
     * Chess.com 4.10.17. Engine overlays are never shown on these (fair play).
     */
    private static final java.util.Set<String> ONLINE_GAME_ACTIVITIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.chess.realchess.ui.game.RealGameActivity",                  // live game
            "com.chess.realchess.ui.wait.WaitGameActivity",                  // live seek
            "com.chess.realchess.ui.wait.LiveGameSeekV6Activity",            // live seek
            "com.chess.features.daily.DailyGameActivity",                    // daily (correspondence) game
            "com.chess.waitgame.daily.DailyGameSeekActivity",                // daily seek
            "com.chess.features.connectedboards.ConnectedBoardGameActivity", // online game on an e-board
            "com.chess.features.puzzles.battle.PuzzlesBattleGameActivity",   // puzzle battle vs a human
            "com.chess.chesstv.ChessTvActivity",                             // watching live games
            "com.chess.features.more.watch.WatchActivity"                    // watching live games
    ));

    /** Screens that host a board but never an online game (bots, coach, analysis, archives...). */
    private static final java.util.Set<String> OFFLINE_BOARD_ACTIVITIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.chess.features.versusbots.game.BotGameActivityV2",
            "com.chess.features.versusbots.archive.ArchivedBotGameActivityV2",
            "com.chess.features.guidedcoachgame.GuidedCoachGameActivity",
            "com.chess.features.train.TrainGameActivity",
            "com.chess.practice.play.PracticePlayGameActivity",
            "com.chess.endgames.practice.EndgamePracticeGameActivity",
            "com.chess.endgames.challenge.EndgameChallengeGameActivity",
            "com.chess.passandplay.PassAndPlayActivity",
            "com.chess.features.live.archive.ArchivedLiveGameActivity",      // finished live games
            "com.chess.diagrams.game.DiagramGameActivity",
            "com.chess.features.explorer.GameExplorerActivity",
            "com.chess.gamereview.v2.GameReviewActivity",
            "com.chess.features.analysis.standalone.StandaloneAnalysisActivity",
            "com.chess.features.analysis.standalonev2.StandaloneAnalysisActivityV2",
            "com.chess.features.analysis.selfengineless.AnalysisSelfEnginelessActivity"
    ));

    /**
     * Fair-play gate: true if {@code activity} is an online game against a human (or a live
     * game being watched), where engine overlays must stay off.
     *
     * <p>Exact 4.10.17 class names are checked first. The old substring heuristic misfired on
     * real screens: it blocked coach, train, pass-and-play, endgame and finished-game screens
     * (any name containing "GameActivity"), and let Chess TV / Watch through. For screens in
     * neither list only online-only packages count as live.
     */
    public static boolean isLiveMatch(Activity activity) {
        if (isDeveloperMode) return false;
        if (activity == null) return false;
        boolean live = isOnlineGameActivity(activity.getClass().getName());
        if (live) isReviewMode = false;
        return live;
    }

    static boolean isOnlineGameActivity(String name) {
        if (name == null) return false;
        if (ONLINE_GAME_ACTIVITIES.contains(name)) return true;
        if (OFFLINE_BOARD_ACTIVITIES.contains(name)) return false;
        return name.startsWith("com.chess.realchess.")
                || name.startsWith("com.chess.features.daily.")
                || name.startsWith("com.chess.waitgame.")
                || name.startsWith("com.chess.chesstv.")
                || name.startsWith("com.chess.features.connectedboards.");
    }

    /**
     * Game Review entry point: the repository receives a ComputerAnalysisConfiguration whose
     * PGN is replayed through the local engine.
     *
     * @param flowClass the app's (obfuscated) coroutine Flow interface, supplied by the patch
     */
    public static Object getLocalAnalysisFlowForConfig(Class<?> flowClass, Object config, Object analysisDepth) {
        String pgn = null;
        try {
            if (config != null) {
                Object v = config.getClass().getMethod("getPgn").invoke(config);
                if (v instanceof String) pgn = (String) v;
            }
        } catch (Throwable t) {
            Log.e(TAG, "getLocalAnalysisFlowForConfig: could not read PGN", t);
        }
        Log.d(TAG, "getLocalAnalysisFlow pgn: " + (pgn != null ? (pgn.substring(0, Math.min(pgn.length(), 30)) + "...") : "null"));
        return LocalAnalysisFlow.createFlow(flowClass, pgn, analysisDepth);
    }

    public static Object getFullGameAnalysisPermissions() {
        try {
            Class<?> permClass = Class.forName("com.chess.entities.GameAnalysisPermissions");
            Class<?> quotaTypeClass = Class.forName("com.chess.entities.GameAnalysisPermissions$QuotaType");
            java.lang.reflect.Constructor<?> ctor = permClass.getConstructor(
                boolean.class, boolean.class, boolean.class, boolean.class, quotaTypeClass
            );
            return ctor.newInstance(true, true, true, true, null);
        } catch (Throwable t) {
            Log.e(TAG, "getFullGameAnalysisPermissions failed: " + t.getMessage());
            return null;
        }
    }

    public static Object getPlayedMove(Object positionObj) {
        if (positionObj == null) return null;
        try {
            Class<?> pmClass = positionObj.getClass().getClassLoader().loadClass(positionObj.getClass().getName() + "$PlayedMove");
            for (Field f : positionObj.getClass().getDeclaredFields()) {
                if (f.getType().equals(pmClass)) {
                    f.setAccessible(true);
                    return f.get(positionObj);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get playedMove via reflection", t);
        }
        return null;
    }

    public static Object getSuggestedMove(Object positionObj) {
        if (positionObj == null) return null;
        try {
            Class<?> smClass = positionObj.getClass().getClassLoader().loadClass(positionObj.getClass().getName() + "$SuggestedMove");
            for (Field f : positionObj.getClass().getDeclaredFields()) {
                if (f.getType().equals(smClass)) {
                    f.setAccessible(true);
                    return f.get(positionObj);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get suggestedMove via reflection", t);
        }
        return null;
    }

    public static String getSuggestedMoveString(Object suggestedMoveObj) {
        if (suggestedMoveObj == null) return null;
        try {
            for (Field f : suggestedMoveObj.getClass().getDeclaredFields()) {
                if (f.getType().equals(String.class)) {
                    f.setAccessible(true);
                    return (String) f.get(suggestedMoveObj);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get suggestedMove move string", t);
        }
        return null;
    }

    public static Object getPositionFromPositionAndMove(Object pmObj) {
        if (pmObj == null) return null;
        try {
            Class<?> dClass = Class.forName("com.chess.chessboard.variants.d");
            for (Field f : pmObj.getClass().getDeclaredFields()) {
                if (dClass.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(pmObj);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get position from PositionAndMove", t);
        }
        return null;
    }

    public static Object convertMove(Object position, String moveStr) {
        try {
            Class<?> converterClass = Class.forName("com.chess.chessboard.compengine.MoveConverterKt");
            Class<?> dClass = Class.forName("com.chess.chessboard.variants.d");
            Method convertMethod = converterClass.getMethod("d", dClass, String.class, boolean.class);
            return convertMethod.invoke(null, position, moveStr, true);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to convert move", t);
        }
        return null;
    }

    public static boolean shouldUseDummyMove(Object positionObj, Object positionAndMoveObj) {
        if (!isReviewMode) return true;
        if (positionObj == null) return true;
        try {
            Object playedMove = getPlayedMove(positionObj);
            if (playedMove == null) return true;

            Object suggestedMove = getSuggestedMove(positionObj);
            if (suggestedMove != null) {
                String moveStr = getSuggestedMoveString(suggestedMove);
                if (moveStr != null) {
                    Object position = getPositionFromPositionAndMove(positionAndMoveObj);
                    if (position != null) {
                        Object convertedMove = convertMove(position, moveStr);
                        if (convertedMove == null) {
                            Log.d(TAG, "shouldUseDummyMove: Suggested move conversion failed for " + moveStr + ". Bypassing to prevent crash.");
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "shouldUseDummyMove exception, falling back to dummy move", t);
            return true;
        }
        return false;
    }

    /**
     * Builds the neutral review item that replaces an un-renderable one: the played move marked
     * as a book move with a 0.00 score and no continuation. Constructed reflectively from the
     * result type's constructor (MoveInfo is api.l with 8 params in 4.10.17), filling
     * parameters by type.
     *
     * @param resultClass     the review item pair type (api.d) supplied by the patch
     * @param positionAndMove the history entry (chessboard.history.i) for this ply
     */
    public static Object buildDummyMoveResult(Class<?> resultClass, Object positionAndMove) {
        try {
            java.lang.reflect.Constructor<?> pairCtor = AppTypes.primaryCtor(resultClass);
            if (pairCtor == null || pairCtor.getParameterTypes().length == 0) return null;
            Class<?> moveInfoClass = pairCtor.getParameterTypes()[0];
            java.lang.reflect.Constructor<?> infoCtor = AppTypes.primaryCtor(moveInfoClass);
            if (infoCtor == null) return null;

            Class<?> historyClass = Class.forName("com.chess.chessboard.history.i");
            Class<?> classificationClass = Class.forName("com.chess.compengine.AnalysisMoveClassification");
            Class<?> scoreClass = Class.forName("com.chess.entities.Score");
            Class<?> colorClass = Class.forName("com.chess.entities.Color");

            Object position = historyClass.getMethod("e").invoke(positionAndMove);
            Object side = position.getClass().getMethod("getSideToMove").invoke(position);
            Object companion = scoreClass.getField("Companion").get(null);
            Object score = companion.getClass()
                    .getMethod("from", float.class, Integer.class, colorClass)
                    .invoke(companion, 0f, null, side);
            Object book = null;
            for (Object c : classificationClass.getEnumConstants()) {
                if ("BOOK".equals(((Enum<?>) c).name())) { book = c; break; }
            }

            Class<?>[] p = infoCtor.getParameterTypes();
            Object[] args = new Object[p.length];
            boolean historyUsed = false;
            for (int i = 0; i < p.length; i++) {
                if (p[i] == historyClass && !historyUsed) { args[i] = positionAndMove; historyUsed = true; }
                else if (p[i] == classificationClass) args[i] = book;
                else if (p[i] == scoreClass) args[i] = score;
                else args[i] = AppTypes.defaultFor(p[i]);
            }
            Object info = infoCtor.newInstance(args);

            Object[] pairArgs = new Object[pairCtor.getParameterTypes().length];
            pairArgs[0] = info;
            for (int i = 1; i < pairArgs.length; i++) pairArgs[i] = AppTypes.defaultFor(pairCtor.getParameterTypes()[i]);
            return pairCtor.newInstance(pairArgs);
        } catch (Throwable t) {
            Log.e(TAG, "buildDummyMoveResult failed", t);
            return null;
        }
    }
}

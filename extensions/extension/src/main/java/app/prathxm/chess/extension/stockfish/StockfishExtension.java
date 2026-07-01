package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * StockfishExtension – the runtime logic injected into Chess.com.
 *
 * This class handles:
 *   - Auto-initialization of Stockfish.
 *   - Background side-aware analysis (MultiPV + depth).
 *   - Multiple rank-based opacity arrow injections.
 *   - Stealth Settings UI & Gesture detection (long press -> Settings, double tap -> toggle arrows).
 *   - Persistent and robust arrow-merging to prevent puzzle/online board state updates from wiping our overlay.
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public class StockfishExtension {

    private static final String TAG = "StockfishExt";

    // ── Active Boards Invalidation ───────────────────────────────────────────

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

    static Activity getCurrentActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesMap = activitiesField.get(activityThread);
            if (activitiesMap instanceof java.util.Map) {
                for (Object activityRecord : ((java.util.Map<?, ?>) activitiesMap).values()) {
                    Class<?> recordClass = activityRecord.getClass();
                    java.lang.reflect.Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        java.lang.reflect.Field activityField = recordClass.getDeclaredField("activity");
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
                for (java.lang.reflect.Field field : optionalPainterClass.getDeclaredFields()) {
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

    // ── State ─────────────────────────────────────────────────────────────────

    /** Single-thread pool – one analysis job at a time, newest cancels older. */
    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stockfish-analysis");
                t.setDaemon(true);
                return t;
            });

    private static volatile Future<?> currentJob = null;

    /** The last CBViewModelStateImpl we saw (via onArrowsChanged). */
    private static final AtomicReference<WeakReference<Object>> stateImplRef =
            new AtomicReference<>(new WeakReference<>(null));

    private static volatile boolean engineReady = false;
    private static volatile boolean lifecycleCallbacksRegistered = false;
    private static volatile boolean isInitializing = false;
    static volatile boolean isReviewMode = false;
    static volatile boolean isDeveloperMode = false;

    /** Remembers the last engine-calculated HintArrows so we can merge/preserve them. */
    private static final List<Object> lastEngineArrows = new ArrayList<>();

    private static final List<String> fenHistory = new ArrayList<>();
    private static final java.util.Map<String, Float> fenToEvalMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, List<String>> fenToBestMovesMap = new java.util.concurrent.ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> isInjecting = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    // ── Initialisation (lazy, needs Context) ─────────────────────────────────

    /** Called on startup or the first onBoardChanged invocation to start the process. */
    public static void ensureEngineReady() {
        if (engineReady || isInitializing) return;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            final android.content.Context ctx = (android.content.Context) currentApplicationMethod.invoke(null);
            if (ctx == null) {
                return;
            }

            android.app.Application app = (android.app.Application) ctx.getApplicationContext();
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

    // ── Hook entry points (called from patched Smali) ─────────────────────────

    /**
     * Hook 1: fired at the start of CBViewModelStateImpl.m() every time the position is updated.
     *
     * @param stateImplObject The CBViewModelStateImpl instance.
     * @param positionObject  The new position object (com.chess.chessboard.variants.d).
     */
    public static void onBoardChanged(Object stateImplObject, Object positionObject) {
        if (stateImplObject == null || positionObject == null) return;
        
        stateImplRef.set(new WeakReference<>(stateImplObject));

        final Activity activity = getCurrentActivity();
        if (activity != null) {
            if (isLiveMatch(activity)) {
                isReviewMode = false;
            }
        }

        ensureGestureInterceptorRegistered();
        ensureEngineReady();
        if (!engineReady) return;

        Context ctx = getContext();
        if (ctx != null && !StockfishSettings.isEngineEnabled(ctx)) {
            Log.d(TAG, "Engine is disabled in settings.");
            clearEngineArrows(stateImplObject);
            hideEvalBar();
            hideWdlBar();
            hideMateAnnouncement();
            return;
        }

        // Cancel previous calculations and clear the stale engine ARROWS only.
        // We intentionally do NOT hide the eval bar here so it stays stable while
        // the new analysis is running (no flicker).
        clearEngineArrows(stateImplObject);




        String fen = extractFen(positionObject);
        if (fen == null) {
            Log.w(TAG, "onBoardChanged: could not extract FEN");
            return;
        }

        Log.d(TAG, "Position changed → FEN: " + fen);
        updateHistory(fen);
        scheduleAnalysis(fen);
    }

    private static void updateHistory(String fen) {
        String key = getFenKey(fen);
        if (key == null) return;
        synchronized (fenHistory) {
            int idx = fenHistory.indexOf(key);
            if (idx >= 0) {
                while (fenHistory.size() > idx + 1) {
                    fenHistory.remove(fenHistory.size() - 1);
                }
            } else {
                if (!fenHistory.isEmpty()) {
                    String lastKey = fenHistory.get(fenHistory.size() - 1);
                    String deduced = deduceUciMove(lastKey, key);
                    if (deduced == null) {
                        fenHistory.clear();
                        fenToEvalMap.clear();
                        fenToBestMovesMap.clear();
                        isReviewMode = false;
                    }
                }
                fenHistory.add(key);
            }
        }
    }

    private static String getFenKey(String fen) {
        if (fen == null) return null;
        String[] parts = fen.split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return fen;
    }

    private static String expandFenBoard(String fenBoard) {
        StringBuilder sb = new StringBuilder();
        for (char c : fenBoard.toCharArray()) {
            if (c == '/') continue;
            if (Character.isDigit(c)) {
                int emptySquares = c - '0';
                for (int i = 0; i < emptySquares; i++) {
                    sb.append('.');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String getSquareName(int index) {
        char file = (char) ('a' + (index % 8));
        int rank = 8 - (index / 8);
        return "" + file + rank;
    }

    private static String deduceUciMove(String prevFen, String currFen) {
        try {
            String[] prevParts = prevFen.split("\\s+");
            String[] currParts = currFen.split("\\s+");
            if (prevParts.length < 2 || currParts.length < 2) return null;

            String prevBoard = expandFenBoard(prevParts[0]);
            String currBoard = expandFenBoard(currParts[0]);
            if (prevBoard.length() != 64 || currBoard.length() != 64) return null;

            boolean whiteMoved = prevParts[1].equals("w");

            java.util.List<Integer> fromCandidates = new java.util.ArrayList<>();
            java.util.List<Integer> toCandidates = new java.util.ArrayList<>();

            for (int i = 0; i < 64; i++) {
                char p = prevBoard.charAt(i);
                char c = currBoard.charAt(i);
                if (p != c) {
                    if (p != '.') {
                        boolean isWhitePiece = Character.isUpperCase(p);
                        if (isWhitePiece == whiteMoved) {
                            fromCandidates.add(i);
                        }
                    }
                    if (c != '.') {
                        boolean isWhitePiece = Character.isUpperCase(c);
                        if (isWhitePiece == whiteMoved) {
                            toCandidates.add(i);
                        }
                    }
                }
            }

            if (fromCandidates.size() == 1 && toCandidates.size() == 1) {
                return getSquareName(fromCandidates.get(0)) + getSquareName(toCandidates.get(0));
            }

            if (fromCandidates.size() >= 1 && toCandidates.size() >= 1) {
                char kingChar = whiteMoved ? 'K' : 'k';
                int kingFrom = -1;
                int kingTo = -1;
                for (int f : fromCandidates) {
                    if (prevBoard.charAt(f) == kingChar) {
                        kingFrom = f;
                        break;
                    }
                }
                for (int t : toCandidates) {
                    if (currBoard.charAt(t) == kingChar) {
                        kingTo = t;
                        break;
                    }
                }
                if (kingFrom != -1 && kingTo != -1) {
                    return getSquareName(kingFrom) + getSquareName(kingTo);
                }

                if (toCandidates.size() == 1) {
                    int toIdx = toCandidates.get(0);
                    char movedPiece = currBoard.charAt(toIdx);
                    for (int f : fromCandidates) {
                        char prevPiece = prevBoard.charAt(f);
                        if (Character.toLowerCase(prevPiece) == Character.toLowerCase(movedPiece) ||
                            (Character.toLowerCase(prevPiece) == 'p' && (movedPiece == 'Q' || movedPiece == 'q' || movedPiece == 'R' || movedPiece == 'r' || movedPiece == 'B' || movedPiece == 'b' || movedPiece == 'N' || movedPiece == 'n'))) {
                            return getSquareName(f) + getSquareName(toIdx);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static void classifyMoveIfPossible(String currentFen, StockfishProcess.AnalysisResult currentResult) {
        Context context = getContext();
        if (context == null) return;
        
        Activity activity = getCurrentActivity();
        if (activity != null && isLiveMatch(activity) && !isReviewMode) {
            return;
        }

        if (!StockfishSettings.isMoveClassificationEnabled(context)) return;

        try {
            String currentKey = getFenKey(currentFen);
            if (currentKey == null) return;

            String prevKey = null;
            synchronized (fenHistory) {
                int idx = fenHistory.indexOf(currentKey);
                if (idx >= 1) {
                    prevKey = fenHistory.get(idx - 1);
                }
            }

            if (prevKey == null) return;

            Float prevEvalVal = fenToEvalMap.get(prevKey);
            List<String> prevBestMoves = fenToBestMovesMap.get(prevKey);
            if (prevEvalVal == null || prevBestMoves == null || prevBestMoves.isEmpty()) return;

            float prevEval = prevEvalVal;
            float currentEval = currentResult.score;

            boolean whiteMoved = prevKey.endsWith(" w");
            float delta = whiteMoved ? (currentEval - prevEval) : (prevEval - currentEval);

            String uciMove = deduceUciMove(prevKey, currentKey);
            
            String classification = "Good Move";
            String emoji = "👍";
            boolean isBlunderOrMistake = false;

            if (uciMove != null && !prevBestMoves.isEmpty() && uciMove.equals(prevBestMoves.get(0))) {
                if (delta > 0.4f) {
                    classification = "Brilliant";
                    emoji = "💡";
                } else {
                    classification = "Best Move";
                    emoji = "🎯";
                }
            } else if (uciMove != null && prevBestMoves.contains(uciMove)) {
                classification = "Excellent";
                emoji = "✨";
            } else {
                if (delta < -3.0f) {
                    classification = "Blunder";
                    emoji = "💀";
                    isBlunderOrMistake = true;
                } else if (delta < -1.5f) {
                    classification = "Mistake";
                    emoji = "❌";
                    isBlunderOrMistake = true;
                } else if (delta < -0.5f) {
                    classification = "Inaccuracy";
                    emoji = "⚠️";
                } else if (delta < -0.1f) {
                    classification = "Good Move";
                    emoji = "👍";
                } else {
                    classification = "Great Move";
                    emoji = "✅";
                }
            }

            final String toastText = emoji + " " + classification + (uciMove != null ? " (" + uciMove + ")" : "") + String.format(" [Delta: %.1f]", delta);
            final boolean triggerVibrate = isBlunderOrMistake;

            if (activity != null) {
                activity.runOnUiThread(() -> {
                    android.widget.Toast.makeText(activity, toastText, android.widget.Toast.LENGTH_SHORT).show();
                    
                    if (triggerVibrate && StockfishSettings.isBlunderAlertsEnabled(activity)) {
                        android.os.Vibrator vibrator = (android.os.Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                vibrator.vibrate(150);
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in classifyMoveIfPossible: " + t.getMessage());
        }
    }

    /**
     * Hook 2: fired at the start of CBViewModelStateImpl.a2() every time the
     * move-arrows list is updated (by the app itself or by us).
     *
     * @param stateImplObject  The CBViewModelStateImpl instance.
     * @param arrows           The new list of HintArrow objects.
     */
    public static void onArrowsChanged(Object stateImplObject, List<?> arrows) {
        if (stateImplObject == null) return;
        stateImplRef.set(new WeakReference<>(stateImplObject));

        ensureGestureInterceptorRegistered();

        // Avoid infinite recursion if the call originates from our own injection
        if (isInjecting.get()) {
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
                        java.lang.reflect.Method getPositionMethod = stateImplObject.getClass().getMethod("getPosition");
                        Object positionObject = getPositionMethod.invoke(stateImplObject);
                        if (positionObject != null) {
                            java.lang.reflect.Method getSideToMove = positionObject.getClass().getMethod("getSideToMove");
                            Object sideToMove = getSideToMove.invoke(positionObject);
                            if (sideToMove != null) {
                                java.lang.reflect.Method isWhiteMethod = sideToMove.getClass().getMethod("isWhite");
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
                    if (arrow != null && !isEngineArrow(arrow)) {
                        finalAppArrows.add(arrow);
                    }
                }
            }

            if (showArrows) {
                synchronized (lastEngineArrows) {
                    finalAppArrows.addAll(lastEngineArrows);
                }
            }

            final Object finalStateImpl = stateImplObject;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        isInjecting.set(true);
                        java.lang.reflect.Method a2 = finalStateImpl.getClass().getMethod("a2", List.class);
                        a2.invoke(finalStateImpl, finalAppArrows);
                        invalidateAllBoards();
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to inject merged arrows in onArrowsChanged: " + t.getMessage());
                    } finally {
                        isInjecting.set(false);
                    }
                }
            });
        }
    }

    // ── Analysis scheduling ───────────────────────────────────────────────────

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

                String key = getFenKey(fen);
                if (key != null) {
                    fenToEvalMap.put(key, result.score);
                    fenToBestMovesMap.put(key, result.moves);
                }

                classifyMoveIfPossible(fen, result);

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
                    injectEngineArrows(result.moves, result.ponder);
                } else {
                    clearEngineArrows(getStateImpl());
                }


                if (!disableOverlays && StockfishSettings.isEvalBarEnabled(context)) {
                    updateEvalBar(result.score, result.hasMate, result.mateIn);
                } else {
                    hideEvalBar();
                }

                if (!disableOverlays && StockfishSettings.isWdlEnabled(context)) {
                    updateWdlBar(result.wdlWin, result.wdlDraw, result.wdlLoss);
                } else {
                    hideWdlBar();
                }

                if (!disableOverlays && result.hasMate && StockfishSettings.isMateAnnouncementEnabled(context)) {
                    showMateAnnouncement(result.mateIn);
                } else {
                    hideMateAnnouncement();
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

    // ── Arrow injection ───────────────────────────────────────────────────────

    /**
     * Injects HintArrows for the engine's best moves into CBViewModelStateImpl.
     */
    private static void injectEngineArrows(List<String> uciMoves, String ponderMove) {
        Object stateImpl = getStateImpl();
        if (stateImpl == null) return;

        Context context = getContext();
        if (context == null) return;

        try {
            Class<?> uClass = Class.forName("com.chess.chessboard.u");
            Object uInstance = uClass.getField("a").get(null); // u.a = INSTANCE
            Method cMethod = uClass.getMethod("c", String.class);

            Class<?> hintArrowClass = Class.forName("com.chess.chessboard.vm.movesinput.k0");
            Class<?> squareClass = Class.forName("com.chess.chessboard.t");

            java.lang.reflect.Constructor<?> ctor = hintArrowClass.getDeclaredConstructor(
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
                    // Threat arrow has distinct Crimson Red color (0xFFD50000) and high opacity
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
                java.lang.reflect.Method getMoveArrowsMethod = stateImpl.getClass().getMethod("k4");
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
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        isInjecting.set(true);
                        java.lang.reflect.Method a2 = finalStateImpl.getClass().getMethod("a2", List.class);
                        a2.invoke(finalStateImpl, finalMerged);
                        invalidateAllBoards();
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

    private static void clearEngineArrows(Object stateImpl) {
        // NOTE: Do NOT hide the eval bar here — it is independent of engine arrows
        //       and must stay stable while new analysis loads.
        if (stateImpl == null) return;

        try {
            List<?> currentArrows = null;
            try {
                java.lang.reflect.Method getMoveArrowsMethod = stateImpl.getClass().getMethod("k4");
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
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        isInjecting.set(true);
                        java.lang.reflect.Method a2 = finalStateImpl.getClass().getMethod("a2", List.class);
                        a2.invoke(finalStateImpl, finalCleanArrows);
                        invalidateAllBoards();
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

    private static boolean isEngineArrow(Object arrow) {
        if (arrow == null) return false;
        try {
            for (java.lang.reflect.Field field : arrow.getClass().getDeclaredFields()) {
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
                registerGestureInterceptor(activity);
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

    private static void ensureGestureInterceptorRegistered() {
        try {
            Activity activity = getCurrentActivity();
            if (activity != null) {
                registerGestureInterceptor(activity);
            }
        } catch (Throwable t) {
            Log.e(TAG, "ensureGestureInterceptorRegistered failed: " + t.getMessage());
        }
    }

    private static void registerGestureInterceptor(Activity activity) {
        try {
            Window window = activity.getWindow();
            Window.Callback originalCallback = window.getCallback();
            if (originalCallback == null) return;

            if (Proxy.isProxyClass(originalCallback.getClass())) {
                try {
                    InvocationHandler handler = Proxy.getInvocationHandler(originalCallback);
                    if (handler instanceof GestureHandler) {
                        return; // Already wrapped
                    }
                } catch (Exception ignored) {}
            }

            GestureDetector gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    if (isNearLogo(activity, e)) {
                        showSettingsMenu(activity);
                    }
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (isNearLogo(activity, e)) {
                        toggleEverything(activity);
                        return true;
                    }
                    return false;
                }
            });

            Window.Callback proxyCallback = (Window.Callback) Proxy.newProxyInstance(
                Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class},
                new GestureHandler(originalCallback, gestureDetector)
            );
            window.setCallback(proxyCallback);
        } catch (Throwable t) {
            Log.e(TAG, "registerGestureInterceptor failed: " + t.getMessage());
        }
    }

    private static class GestureHandler implements InvocationHandler {
        private final Window.Callback delegate;
        private final GestureDetector gestureDetector;

        public GestureHandler(Window.Callback delegate, GestureDetector gestureDetector) {
            this.delegate = delegate;
            this.gestureDetector = gestureDetector;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("dispatchTouchEvent".equals(method.getName()) && args != null && args.length > 0) {
                MotionEvent event = (MotionEvent) args[0];
                gestureDetector.onTouchEvent(event);
            }
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static boolean isNearLogo(Activity activity, MotionEvent e) {
        float density = activity.getResources().getDisplayMetrics().density;
        float maxLogoY = 100f * density; // top 100dp
        float y = e.getY();
        return y >= 0 && y <= maxLogoY;
    }

    private static void toggleEverything(Activity activity) {
        boolean enabled = !StockfishSettings.isEngineEnabled(activity);
        StockfishSettings.setEngineEnabled(activity, enabled);
        
        if (!enabled) {
            Future<?> prev = currentJob;
            if (prev != null && !prev.isDone()) {
                prev.cancel(true);
            }
            StockfishBridge.stopSearch();
            
            clearEngineArrows(getStateImpl());
            hideEvalBar();
            hideWdlBar();
            hideMateAnnouncement();
        } else {
            Object state = getStateImpl();
            if (state != null) {
                try {
                    java.lang.reflect.Method getPosition = state.getClass().getMethod("getPosition");
                    Object positionObject = getPosition.invoke(state);
                    if (positionObject != null) {
                        String fen = extractFen(positionObject);
                        if (fen != null) {
                            scheduleAnalysis(fen);
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error re-triggering analysis: " + t.getMessage());
                }
            }

        }
    }

    private static void showSettingsMenu(Activity activity) {
        final android.app.Dialog dialog = new android.app.Dialog(activity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        float density = activity.getResources().getDisplayMetrics().density;

        android.graphics.drawable.GradientDrawable dialogBg = new android.graphics.drawable.GradientDrawable();
        dialogBg.setColor(0xFF262421); // Beautiful Chess.com board-dark background
        dialogBg.setCornerRadius(16 * density);
        dialog.getWindow().setBackgroundDrawable(dialogBg);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);

        android.widget.LinearLayout rootLayout = new android.widget.LinearLayout(activity);
        rootLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int rootPadding = (int) (20 * density);
        rootLayout.setPadding(rootPadding, rootPadding, rootPadding, rootPadding);
        scrollView.addView(rootLayout);

        // Header Title
        android.widget.TextView titleTv = new android.widget.TextView(activity);
        titleTv.setText("Engine Settings");
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setTextSize(20);
        titleTv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleTv.setGravity(android.view.Gravity.CENTER);
        rootLayout.addView(titleTv);

        addDialogSpacer(rootLayout, 16, density);



        // 1. MAIN SETTINGS SECTION
        addSectionHeader(rootLayout, "Main Settings", density, activity);

        android.widget.CheckBox enabledCb = addStyledCheckbox(rootLayout, "Enable Stockfish Engine", StockfishSettings.isEngineEnabled(activity), density, activity);

        int currentDepth = StockfishSettings.getDepth(activity);
        android.widget.TextView depthLabel = addStyledLabel(rootLayout, "Analysis Depth: " + currentDepth, density, activity);
        android.widget.SeekBar depthSeekBar = addStyledSeekBar(rootLayout, depthLabel, "Analysis Depth", currentDepth - 1, 19, 1, density, activity);

        android.widget.CheckBox arrowsCb = addStyledCheckbox(rootLayout, "Show Best Move Arrows", StockfishSettings.isArrowsVisible(activity), density, activity);
        android.widget.CheckBox evalBarCb = addStyledCheckbox(rootLayout, "Show Evaluation Bar", StockfishSettings.isEvalBarEnabled(activity), density, activity);
        android.widget.CheckBox classifCb = addStyledCheckbox(rootLayout, "Show Move Classification", StockfishSettings.isMoveClassificationEnabled(activity), density, activity);
        android.widget.CheckBox adsCb = addStyledCheckbox(rootLayout, "Remove Ads Globally", StockfishSettings.isAdsRemoved(activity), density, activity);

        addDialogSpacer(rootLayout, 16, density);

        // 2. ADVANCED SETTINGS TOGGLE
        android.widget.TextView advancedToggleBtn = new android.widget.TextView(activity);
        advancedToggleBtn.setText("⚙️ Show Advanced Settings");
        advancedToggleBtn.setTextColor(0xFF81B64C); // Chess.com Green
        advancedToggleBtn.setTextSize(14);
        advancedToggleBtn.setGravity(android.view.Gravity.CENTER);
        advancedToggleBtn.setPadding(0, (int)(8*density), 0, (int)(8*density));
        advancedToggleBtn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        
        android.widget.LinearLayout advancedLayout = new android.widget.LinearLayout(activity);
        advancedLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        advancedLayout.setVisibility(android.view.View.GONE);
        
        // Add click listener before adding to layout
        advancedToggleBtn.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                boolean isVisible = advancedLayout.getVisibility() == android.view.View.VISIBLE;
                advancedLayout.setVisibility(isVisible ? android.view.View.GONE : android.view.View.VISIBLE);
                advancedToggleBtn.setText(isVisible ? "⚙️ Show Advanced Settings" : "⚙️ Hide Advanced Settings");
            }
        });
        
        rootLayout.addView(advancedToggleBtn);
        rootLayout.addView(advancedLayout);

        addDialogSpacer(advancedLayout, 8, density);
        
        // Advanced toggles
        int currentPV = StockfishSettings.getMultiPV(activity);
        android.widget.TextView pvLabel = addStyledLabel(advancedLayout, "MultiPV (Best moves): " + currentPV, density, activity);
        android.widget.SeekBar pvSeekBar = addStyledSeekBar(advancedLayout, pvLabel, "MultiPV (Best moves to show)", currentPV - 1, 4, 1, density, activity);

        android.widget.CheckBox sideCb = addStyledCheckbox(advancedLayout, "Show Arrows Only on My Turn", StockfishSettings.isMySideOnly(activity), density, activity);
        
        android.widget.CheckBox eloCb = addStyledCheckbox(advancedLayout, "Limit Engine Elo Strength", StockfishSettings.isLimitStrength(activity), density, activity);
        int currentElo = StockfishSettings.getElo(activity);
        android.widget.TextView eloLabel = addStyledLabel(advancedLayout, "Engine Elo: " + currentElo, density, activity);
        android.widget.SeekBar eloSeekBar = addStyledSeekBar(advancedLayout, eloLabel, "Engine Elo", currentElo - 1350, 1500, 1350, density, activity);
        
        eloLabel.setEnabled(eloCb.isChecked());
        eloSeekBar.setEnabled(eloCb.isChecked());
        eloCb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                eloLabel.setEnabled(isChecked);
                eloSeekBar.setEnabled(isChecked);
            }
        });

        android.widget.CheckBox wdlCb = addStyledCheckbox(advancedLayout, "Show Win/Draw/Loss Bar", StockfishSettings.isWdlEnabled(activity), density, activity);
        android.widget.CheckBox threatCb = addStyledCheckbox(advancedLayout, "Show Threat Arrows", StockfishSettings.isThreatArrowsEnabled(activity), density, activity);
        android.widget.CheckBox blunderCb = addStyledCheckbox(advancedLayout, "Vibrate on Blunders", StockfishSettings.isBlunderAlertsEnabled(activity), density, activity);
        android.widget.CheckBox mateCb = addStyledCheckbox(advancedLayout, "Show Mate Announcement", StockfishSettings.isMateAnnouncementEnabled(activity), density, activity);

        addDialogSpacer(rootLayout, 16, density);

        // 3. ABOUT & CREDITS
        android.widget.LinearLayout creditsCard = new android.widget.LinearLayout(activity);
        creditsCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        creditsCard.setPadding(0, (int) (16 * density), 0, (int) (8 * density));

        android.widget.TextView devTv = new android.widget.TextView(activity);
        devTv.setText("Developed by PrathxmOp ✨");
        devTv.setTextColor(0xFF81B64C); // Chess.com Green accent
        devTv.setTextSize(14);
        devTv.setGravity(android.view.Gravity.CENTER);
        devTv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        devTv.setOnLongClickListener(new android.view.View.OnLongClickListener() {
            @Override
            public boolean onLongClick(android.view.View v) {
                isDeveloperMode = !isDeveloperMode;
                android.widget.Toast.makeText(activity, "Developer Mode: " + (isDeveloperMode ? "ON" : "OFF"), android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        creditsCard.addView(devTv);



        addDialogSpacer(creditsCard, 2, density);

        android.widget.TextView engineTv = new android.widget.TextView(activity);
        engineTv.setText("Engine: Stockfish 16.1 NNUE");
        engineTv.setTextColor(0xFF8B8985);
        engineTv.setTextSize(11);
        engineTv.setGravity(android.view.Gravity.CENTER);
        creditsCard.addView(engineTv);

        addDialogSpacer(creditsCard, 2, density);

        String versionText = "v" + app.prathxm.chess.extension.BuildConfig.PATCH_VERSION;

        android.widget.TextView patchTv = new android.widget.TextView(activity);
        patchTv.setText("Patch Version: " + versionText + " 🧩");
        patchTv.setTextColor(0xFF8B8985);
        patchTv.setTextSize(11);
        patchTv.setGravity(android.view.Gravity.CENTER);
        creditsCard.addView(patchTv);

        rootLayout.addView(creditsCard);

        addDialogSpacer(rootLayout, 16, density);

        // Buttons Layout
        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(activity);
        buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.END);

        android.widget.TextView cancelBtn = new android.widget.TextView(activity);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(0xFFB0B0B0);
        cancelBtn.setTextSize(16);
        cancelBtn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        cancelBtn.setGravity(android.view.Gravity.CENTER);
        int btnPaddingH = (int) (16 * density);
        int btnPaddingV = (int) (10 * density);
        cancelBtn.setPadding(btnPaddingH, btnPaddingV, btnPaddingH, btnPaddingV);
        cancelBtn.setFocusable(true);
        cancelBtn.setClickable(true);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        android.widget.TextView saveBtn = new android.widget.TextView(activity);
        saveBtn.setText("Save");
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setTextSize(16);
        saveBtn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        saveBtn.setGravity(android.view.Gravity.CENTER);
        saveBtn.setPadding(btnPaddingH, btnPaddingV, btnPaddingH, btnPaddingV);
        
        android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
        saveBg.setColor(0xFF81B64C); // Chess.com Green accent
        saveBg.setCornerRadius(8 * density);
        saveBtn.setBackground(saveBg);
        saveBtn.setFocusable(true);
        saveBtn.setClickable(true);
        
        saveBtn.setOnClickListener(v -> {
            StockfishSettings.setEngineEnabled(activity, enabledCb.isChecked());
            StockfishSettings.setDepth(activity, Math.max(1, depthSeekBar.getProgress() + 1));
            StockfishSettings.setMultiPV(activity, Math.max(1, pvSeekBar.getProgress() + 1));
            StockfishSettings.setMySideOnly(activity, sideCb.isChecked());
            StockfishSettings.setLimitStrength(activity, eloCb.isChecked());
            StockfishSettings.setElo(activity, 1350 + eloSeekBar.getProgress());
            StockfishSettings.setAdsRemoved(activity, adsCb.isChecked());
            StockfishSettings.setPremiumEnabled(activity, true);
            StockfishSettings.setArrowsVisible(activity, arrowsCb.isChecked());
            StockfishSettings.setEvalBarEnabled(activity, evalBarCb.isChecked());
            StockfishSettings.setWdlEnabled(activity, wdlCb.isChecked());
            StockfishSettings.setThreatArrowsEnabled(activity, threatCb.isChecked());
            StockfishSettings.setMoveClassificationEnabled(activity, classifCb.isChecked());
            StockfishSettings.setBlunderAlertsEnabled(activity, blunderCb.isChecked());
            StockfishSettings.setMateAnnouncementEnabled(activity, mateCb.isChecked());
            if (!mateCb.isChecked()) hideMateAnnouncement();

            android.widget.Toast.makeText(activity, "Settings Saved", android.widget.Toast.LENGTH_SHORT).show();
            
            if (!enabledCb.isChecked() || !arrowsCb.isChecked()) {
                clearEngineArrows(getStateImpl());
            } else {
                triggerAnalysisForCurrentState();
            }
            if (!evalBarCb.isChecked()) {
                hideEvalBar();
            }

            dialog.dismiss();
        });

        buttonLayout.addView(cancelBtn);
        View btnSpacer = new View(activity);
        btnSpacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams((int) (12 * density), 1));
        buttonLayout.addView(btnSpacer);
        buttonLayout.addView(saveBtn);

        rootLayout.addView(buttonLayout);

        dialog.setContentView(scrollView);
        dialog.show();

        // Layout parameters must be set after show() for dialog window
        int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f);
        dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void addSectionHeader(android.widget.LinearLayout layout, String title, float density, Activity activity) {
        android.widget.TextView header = new android.widget.TextView(activity);
        header.setText(title.toUpperCase());
        header.setTextColor(0xFF81B64C); // Chess.com Green accent
        header.setTextSize(12);
        header.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        header.setPadding(0, (int) (12 * density), 0, (int) (4 * density));
        layout.addView(header);
    }

    private static android.widget.CheckBox addStyledCheckbox(android.widget.LinearLayout layout, String labelText, boolean checked, float density, Activity activity) {
        android.widget.CheckBox cb = new android.widget.CheckBox(activity);
        cb.setText(labelText);
        cb.setTextColor(0xFFE3E3E3);
        cb.setTextSize(15);
        cb.setChecked(checked);
        cb.setPadding((int) (8 * density), (int) (8 * density), 0, (int) (8 * density));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF81B64C));
        }
        layout.addView(cb);
        return cb;
    }

    private static android.widget.TextView addStyledLabel(android.widget.LinearLayout layout, String text, float density, Activity activity) {
        android.widget.TextView label = new android.widget.TextView(activity);
        label.setText(text);
        label.setTextColor(0xFFE3E3E3);
        label.setTextSize(14);
        label.setPadding(0, (int) (8 * density), 0, 0);
        layout.addView(label);
        return label;
    }

    private static android.widget.SeekBar addStyledSeekBar(android.widget.LinearLayout layout, final android.widget.TextView labelTv, final String labelPrefix, int progress, int max, final int minVal, float density, Activity activity) {
        android.widget.SeekBar seekBar = new android.widget.SeekBar(activity);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF81B64C));
            seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF81B64C));
        }
        seekBar.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (12 * density));
        
        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar sb, int prog, boolean fromUser) {
                int val = Math.max(minVal, prog + minVal);
                labelTv.setText(labelPrefix + ": " + val);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
        
        layout.addView(seekBar);
        return seekBar;
    }

    private static void addDialogSpacer(android.widget.LinearLayout layout, int dpHeight, float density) {
        View spacer = new View(layout.getContext());
        spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (dpHeight * density)
        ));
        layout.addView(spacer);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static android.content.Context getContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            android.content.Context ctx = (android.content.Context) currentApplicationMethod.invoke(null);
            if (ctx != null && !engineReady) {
                ensureEngineReady();
            }
            return ctx;
        } catch (Throwable t) {
            Log.e(TAG, "getContext failed: " + t.getMessage());
        }
        return null;
    }

    private static Boolean isUserWhite(Object stateImplObject) {
        try {
            java.lang.reflect.Field field = null;
            try {
                field = stateImplObject.getClass().getDeclaredField("sideToPlaySelfEffects");
            } catch (NoSuchFieldException e) {
                // Find by type kotlin.jvm.functions.Function0
                for (java.lang.reflect.Field f : stateImplObject.getClass().getDeclaredFields()) {
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
                
                java.lang.reflect.Method invokeMethod = sideToPlaySelfEffects.getClass().getMethod("invoke");
                Object side = invokeMethod.invoke(sideToPlaySelfEffects);
                if (side == null) return null;
                
                java.lang.reflect.Method getColorMethod = null;
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

    static String extractFen(Object position) {
        try {
            Class<?> posExtKt = Class.forName(
                "com.chess.chessboard.variants.standard.bitboard.FenUtilsKt");
            
            String board = null;
            String castling = null;
            String enPassant = null;
            
            for (java.lang.reflect.Method m : posExtKt.getMethods()) {
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
            
            java.lang.reflect.Method getSideToMove = position.getClass().getMethod("getSideToMove");
            Object sideToMove = getSideToMove.invoke(position);
            java.lang.reflect.Method isWhiteMethod = sideToMove.getClass().getMethod("isWhite");
            boolean isWhite = (boolean) isWhiteMethod.invoke(sideToMove);
            String turn = isWhite ? "w" : "b";
            
            return board + " " + turn + " " + castling + " " + enPassant + " 0 1";
        } catch (Throwable t) {
            Log.e(TAG, "extractFen failed: " + t.getMessage(), t);
        }
        return null;
    }

    private static Object getStateImpl() {
        WeakReference<Object> ref = stateImplRef.get();
        return ref != null ? ref.get() : null;
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
        if (context == null) return defaultValue;
        if (StockfishSettings.isPremiumEnabled(context)) {
            try {
                Class<?> premiumStatusClass = Class.forName("com.chess.entities.PremiumStatus");
                return premiumStatusClass.getField("DIAMOND").get(null);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get PremiumStatus.DIAMOND via reflection: " + t.getMessage(), t);
            }
        }
        return defaultValue;
    }

    public static boolean getAnalysisPermission(boolean defaultValue, String permissionName) {
        Context context = getContext();
        if (context == null) return defaultValue;
        if (StockfishSettings.isPremiumEnabled(context)) {
            return true;
        }
        return defaultValue;
    }




    private static void updateEvalBar(final float score, final boolean hasMate, final int mateIn) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    android.view.ViewGroup decorView = (android.view.ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return; // board not visible yet, skip silently

                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardX = loc[0];
                    int boardY = loc[1];
                    int boardW = boardView.getWidth();
                    int boardH = boardView.getHeight();
                    if (boardW <= 0 || boardH <= 0) return;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int barWidth = (int) (12 * density);

                    // Reuse existing view — never remove and re-add (prevents flash)
                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    EvalBarView evalBarView;
                    if (evalBar instanceof EvalBarView) {
                        evalBarView = (EvalBarView) evalBar;
                    } else {
                        if (evalBar != null) decorView.removeView(evalBar);
                        evalBarView = new EvalBarView(decorView.getContext());
                        evalBarView.setTag("stockfish_eval_bar");
                        decorView.addView(evalBarView);
                    }
                    evalBarView.setVisibility(View.VISIBLE);
                    evalBarView.update(boardX, boardY, barWidth, boardH,
                                       score, hasMate, mateIn, isBoardFlipped(getStateImpl()));
                } catch (Throwable t) {
                    Log.e(TAG, "updateEvalBar failed: " + t.getMessage());
                }
            }
        });
    }

    private static void updateWdlBar(final int wdlWin, final int wdlDraw, final int wdlLoss) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    android.view.ViewGroup decorView = (android.view.ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return;

                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardX = loc[0];
                    int boardY = loc[1];
                    int boardW = boardView.getWidth();
                    int boardH = boardView.getHeight();
                    if (boardW <= 0 || boardH <= 0) return;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int barHeight = (int) (14 * density);
                    // Position horizontally aligned with the board, just above it (avoids captures/points below)
                    int barY = boardY - barHeight - (int)(4 * density);

                    int btnW = (int) (110 * density);
                    int barW = boardW - btnW - (int) (8 * density);

                    View wdlTag = decorView.findViewWithTag("stockfish_wdl_bar");
                    WdlBarView wdlBarView;
                    if (wdlTag instanceof WdlBarView) {
                        wdlBarView = (WdlBarView) wdlTag;
                    } else {
                        if (wdlTag != null) decorView.removeView(wdlTag);
                        wdlBarView = new WdlBarView(decorView.getContext());
                        wdlBarView.setTag("stockfish_wdl_bar");
                        decorView.addView(wdlBarView);
                    }
                    wdlBarView.setVisibility(View.VISIBLE);
                    wdlBarView.update(boardX, barY, barW, barHeight, wdlWin, wdlDraw, wdlLoss);
                } catch (Throwable t) {
                    Log.e(TAG, "updateWdlBar failed: " + t.getMessage());
                }
            }
        });
    }

    private static void hideWdlBar() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("stockfish_wdl_bar");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideWdlBar failed: " + t.getMessage());
                }
            }
        });
    }

    private static void showMateAnnouncement(final int mateIn) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    android.view.ViewGroup decorView = (android.view.ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return;

                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardX = loc[0];
                    int boardY = loc[1];
                    int boardW = boardView.getWidth();
                    if (boardW <= 0) return;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;

                    View existing = decorView.findViewWithTag("stockfish_mate_banner");
                    android.widget.TextView banner;
                    if (existing instanceof android.widget.TextView) {
                        banner = (android.widget.TextView) existing;
                    } else {
                        if (existing != null) decorView.removeView(existing);

                        banner = new android.widget.TextView(decorView.getContext());
                        banner.setTag("stockfish_mate_banner");

                        // Premium Chess.com-styled pill background
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setColor(0xEE1B1B1B);
                        bg.setCornerRadius(24 * density);
                        bg.setStroke((int)(1.5f * density), 0xFF81B64C); // green border
                        banner.setBackground(bg);

                        banner.setTextColor(0xFFFFFFFF);
                        banner.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                        banner.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
                        banner.setGravity(android.view.Gravity.CENTER);

                        int padH = (int)(16 * density);
                        int padV = (int)(8 * density);
                        banner.setPadding(padH, padV, padH, padV);

                        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                        );
                        banner.setLayoutParams(lp);
                        decorView.addView(banner);
                    }

                    // Mate label
                    String sign = mateIn > 0 ? "♟ Mate in " : "☠ Opponent Mates in ";
                    banner.setText(sign + Math.abs(mateIn) + "!");

                    // Position: horizontally centred on the board, just inside the top edge
                    banner.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(boardW, android.view.View.MeasureSpec.AT_MOST),
                            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                    );
                    int bw = banner.getMeasuredWidth();
                    int bh = banner.getMeasuredHeight();
                    int centreX = boardX + (boardW - bw) / 2;
                    int offset = 0;
                    if (StockfishSettings.isWdlEnabled(decorView.getContext())) {
                        int barHeight = (int) (14 * density);
                        offset = barHeight + (int)(4 * density);
                    }
                    int topY = Math.max(0, boardY - bh - (int)(8 * density) - offset);

                    banner.setTranslationX(centreX);
                    banner.setTranslationY(topY);
                    banner.setVisibility(View.VISIBLE);
                    banner.bringToFront();

                } catch (Throwable t) {
                    Log.e(TAG, "showMateAnnouncement failed: " + t.getMessage());
                }
            }
        });
    }

    private static void hideMateAnnouncement() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("stockfish_mate_banner");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideMateAnnouncement failed: " + t.getMessage());
                }
            }
        });
    }

    private static View findChessBoardView(View view) {
        if (view == null) return null;
        if (view.getClass().getName().equals("com.chess.chessboard.view.ChessBoardView")) {
            if (view.getVisibility() == View.VISIBLE) {
                return view;
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findChessBoardView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void hideEvalBar() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    
                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    if (evalBar != null) {
                        evalBar.setVisibility(View.GONE);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "hideEvalBar failed: " + t.getMessage());
                }
            }
        });
    }

    private static boolean isBoardFlipped(Object stateImpl) {
        if (stateImpl == null) return false;
        try {
            for (java.lang.reflect.Method m : stateImpl.getClass().getMethods()) {
                if ((m.getName().equals("isFlipped") || m.getName().equals("getFlipped")) && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    return (boolean) m.invoke(stateImpl);
                }
            }
            for (java.lang.reflect.Field f : stateImpl.getClass().getDeclaredFields()) {
                if ((f.getName().equals("flipped") || f.getName().equals("isFlipped")) && f.getType() == boolean.class) {
                    f.setAccessible(true);
                    return f.getBoolean(stateImpl);
                }
            }
        } catch (Throwable ignored) {}
        
        Boolean isWhite = isUserWhite(stateImpl);
        return isWhite != null && !isWhite;
    }


    static boolean isLiveMatch(Activity activity) {
        if (isDeveloperMode) return false;
        if (activity == null) return false;
        String name = activity.getClass().getName();
        String lower = name.toLowerCase();
        
        // If it's a computer game, local practice, analysis, review, solo, or puzzle, it's NOT a live match
        if (lower.contains("computer") || lower.contains("bot") || lower.contains("practice") ||
            lower.contains("analysis") || lower.contains("review") || lower.contains("local") ||
            lower.contains("solo") || lower.contains("tutorial") || lower.contains("puzzle")) {
            return false;
        }
        
        // If it's play activity or game activity or live chess screen, it's live match
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





    private static void triggerAnalysisForCurrentState() {
        Object state = getStateImpl();
        if (state != null) {
            try {
                java.lang.reflect.Method getPosition = state.getClass().getMethod("getPosition");
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

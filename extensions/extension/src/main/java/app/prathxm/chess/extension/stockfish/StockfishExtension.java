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

    private static Activity getCurrentActivity() {
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

    /** Remembers the last engine-calculated HintArrows so we can merge/preserve them. */
    private static final List<Object> lastEngineArrows = new ArrayList<>();

    /** Prevents infinite recursion when we invoke a2() internally. */
    private static final ThreadLocal<Boolean> isInjecting = ThreadLocal.withInitial(() -> false);

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

        ensureGestureInterceptorRegistered();
        ensureEngineReady();
        if (!engineReady) return;

        Context ctx = getContext();
        if (ctx != null && !StockfishSettings.isEngineEnabled(ctx)) {
            Log.d(TAG, "Engine is disabled in settings.");
            clearEngineArrows(stateImplObject);
            return;
        }

        // Cancel previous calculations and clear the stale engine arrows immediately.
        clearEngineArrows(stateImplObject);

        // Check side-enforcement
        if (ctx != null && StockfishSettings.isMySideOnly(ctx)) {
            Boolean userWhite = isUserWhite(stateImplObject);
            if (userWhite != null) {
                try {
                    java.lang.reflect.Method getSideToMove = positionObject.getClass().getMethod("getSideToMove");
                    Object sideToMove = getSideToMove.invoke(positionObject);
                    if (sideToMove != null) {
                        java.lang.reflect.Method isWhiteMethod = sideToMove.getClass().getMethod("isWhite");
                        boolean isWhiteMove = (boolean) isWhiteMethod.invoke(sideToMove);
                        if (isWhiteMove != userWhite) {
                            Log.d(TAG, "Not user's turn (user: " + (userWhite ? "W" : "B") + ", turn: " + (isWhiteMove ? "W" : "B") + "). Skipping analysis.");
                            return;
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error checking side-aware turn: " + t.getMessage(), t);
                }
            }
        }

        String fen = extractFen(positionObject);
        if (fen == null) {
            Log.w(TAG, "onBoardChanged: could not extract FEN");
            return;
        }

        Log.d(TAG, "Position changed → FEN: " + fen);
        scheduleAnalysis(fen);
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
                java.util.List<String> bestMoves = StockfishBridge.bestMoves(fen, depth, multiPV);

                if (bestMoves.isEmpty()) {
                    Log.d(TAG, "Engine returned no best moves.");
                    return;
                }

                Log.i(TAG, "Best moves: " + bestMoves);
                if (StockfishSettings.isArrowsVisible(context)) {
                    injectEngineArrows(bestMoves);
                }

            } catch (Throwable t) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.e(TAG, "Analysis error: " + t.getMessage());
                }
            }
        });
    }

    // ── Arrow injection ───────────────────────────────────────────────────────

    /**
     * Injects HintArrows for the engine's best moves into CBViewModelStateImpl.
     */
    private static void injectEngineArrows(List<String> uciMoves) {
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
                        if (c == 0xFF00C853 || c == 0xFF2196F3 || c == 0xFFFF9800 || c == 0xFF9C27B0 || c == 0xFFE53935) {
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
            public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {}

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
                        toggleArrows(activity);
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

    private static void toggleArrows(Activity activity) {
        boolean visible = !StockfishSettings.isArrowsVisible(activity);
        StockfishSettings.setArrowsVisible(activity, visible);
        android.widget.Toast.makeText(activity, 
            "Engine arrows: " + (visible ? "SHOWN" : "HIDDEN"), 
            android.widget.Toast.LENGTH_SHORT).show();
        
        if (!visible) {
            clearEngineArrows(getStateImpl());
        }
    }

    private static void showSettingsMenu(Activity activity) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Stockfish Engine Settings");

        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(activity);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        scrollView.addView(layout);

        // 1. Engine Enabled
        android.widget.CheckBox enabledCb = new android.widget.CheckBox(activity);
        enabledCb.setText("Enable Stockfish Engine");
        enabledCb.setChecked(StockfishSettings.isEngineEnabled(activity));
        layout.addView(enabledCb);

        // Spacer
        addSpacer(activity, layout);

        // 2. Depth Slider
        final android.widget.TextView depthLabel = new android.widget.TextView(activity);
        int currentDepth = StockfishSettings.getDepth(activity);
        depthLabel.setText("Analysis Depth: " + currentDepth);
        layout.addView(depthLabel);

        android.widget.SeekBar depthSeekBar = new android.widget.SeekBar(activity);
        depthSeekBar.setMax(20); // 1 to 20
        depthSeekBar.setProgress(currentDepth);
        depthSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(1, progress);
                depthLabel.setText("Analysis Depth: " + val);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        layout.addView(depthSeekBar);

        // Spacer
        addSpacer(activity, layout);

        // 3. MultiPV Slider
        final android.widget.TextView pvLabel = new android.widget.TextView(activity);
        int currentPV = StockfishSettings.getMultiPV(activity);
        pvLabel.setText("MultiPV (Best moves to show): " + currentPV);
        layout.addView(pvLabel);

        android.widget.SeekBar pvSeekBar = new android.widget.SeekBar(activity);
        pvSeekBar.setMax(5); // 1 to 5
        pvSeekBar.setProgress(currentPV);
        pvSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(1, progress);
                pvLabel.setText("MultiPV (Best moves to show): " + val);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        layout.addView(pvSeekBar);

        // Spacer
        addSpacer(activity, layout);

        // 4. Side-enforcement
        android.widget.CheckBox sideCb = new android.widget.CheckBox(activity);
        sideCb.setText("Show Arrows Only on My Turn");
        sideCb.setChecked(StockfishSettings.isMySideOnly(activity));
        layout.addView(sideCb);

        // Spacer
        addSpacer(activity, layout);

        // 5. Limit ELO Strength
        android.widget.CheckBox eloCb = new android.widget.CheckBox(activity);
        eloCb.setText("Limit Engine Elo Strength");
        eloCb.setChecked(StockfishSettings.isLimitStrength(activity));
        layout.addView(eloCb);

        // 6. ELO Value Slider
        final android.widget.TextView eloLabel = new android.widget.TextView(activity);
        int currentElo = StockfishSettings.getElo(activity);
        eloLabel.setText("Engine Elo: " + currentElo);
        layout.addView(eloLabel);

        android.widget.SeekBar eloSeekBar = new android.widget.SeekBar(activity);
        int progressElo = Math.max(0, currentElo - 1350);
        eloSeekBar.setMax(1500); // 1350 + 1500 = 2850
        eloSeekBar.setProgress(progressElo);
        eloSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = 1350 + progress;
                eloLabel.setText("Engine Elo: " + val);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        layout.addView(eloSeekBar);

        // Disable/enable elo seekbar based on checkbox
        eloLabel.setEnabled(eloCb.isChecked());
        eloSeekBar.setEnabled(eloCb.isChecked());
        eloCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            eloLabel.setEnabled(isChecked);
            eloSeekBar.setEnabled(isChecked);
        });

        // Spacer
        addSpacer(activity, layout);

        // 7. Remove Ads
        android.widget.CheckBox adsCb = new android.widget.CheckBox(activity);
        adsCb.setText("Remove Ads Globally");
        adsCb.setChecked(StockfishSettings.isAdsRemoved(activity));
        layout.addView(adsCb);

        // Spacer
        addSpacer(activity, layout);

        // 8. Enable Premium
        android.widget.CheckBox premiumCb = new android.widget.CheckBox(activity);
        premiumCb.setText("Enable Premium (Diamond Status)");
        premiumCb.setChecked(StockfishSettings.isPremiumEnabled(activity));
        layout.addView(premiumCb);

        builder.setView(scrollView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            StockfishSettings.setEngineEnabled(activity, enabledCb.isChecked());
            StockfishSettings.setDepth(activity, Math.max(1, depthSeekBar.getProgress()));
            StockfishSettings.setMultiPV(activity, Math.max(1, pvSeekBar.getProgress()));
            StockfishSettings.setMySideOnly(activity, sideCb.isChecked());
            StockfishSettings.setLimitStrength(activity, eloCb.isChecked());
            StockfishSettings.setElo(activity, 1350 + eloSeekBar.getProgress());
            StockfishSettings.setAdsRemoved(activity, adsCb.isChecked());
            StockfishSettings.setPremiumEnabled(activity, premiumCb.isChecked());

            android.widget.Toast.makeText(activity, "Settings Saved", android.widget.Toast.LENGTH_SHORT).show();
            
            if (!enabledCb.isChecked()) {
                clearEngineArrows(getStateImpl());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void addSpacer(Activity activity, android.widget.LinearLayout layout) {
        View spacer = new View(activity);
        int height = (int) (12 * activity.getResources().getDisplayMetrics().density);
        spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, height));
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

    private static String extractFen(Object position) {
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
}

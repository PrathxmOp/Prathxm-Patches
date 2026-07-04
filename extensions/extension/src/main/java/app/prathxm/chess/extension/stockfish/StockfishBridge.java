package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.util.Log;

/**
 * StockfishBridge – singleton façade over StockfishProcess.
 *
 * Replaces the JNI-based version.  The Stockfish Android release is a
 * standalone executable (not a .so), so we manage it as a subprocess
 * and speak UCI over its stdin/stdout.
 *
 * Call order:
 *   1. StockfishBridge.init(context)   – once, on first board load
 *   2. StockfishBridge.bestMove(fen, depth) – on every position change
 *   3. StockfishBridge.quit()          – on cleanup (optional)
 */
@SuppressWarnings("unused")
public class StockfishBridge {

    private static final String TAG = "StockfishBridge";

    private static final StockfishProcess engine = new StockfishProcess();
    private static volatile boolean initialised = false;

    /** Initialise the engine.  Safe to call multiple times. */
    public static synchronized boolean init(Context context) {
        if (initialised) return true;
        initialised = engine.start(context);
        if (!initialised) {
            Log.e(TAG, "Engine failed to start.");
        }
        return initialised;
    }

    private static Context getApplicationContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            return (Context) currentApplicationMethod.invoke(null);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get Application Context: " + t.getMessage());
        }
        return null;
    }

    /**
     * Analyse the given FEN and return the best moves in UCI format ("e2e4").
     *
     * @param fen      Full FEN string
     * @param depth    Search depth (1–20)
     * @param multiPV  Number of principal variations to search
     * @return         List of UCI move strings, e.g. ["e2e4", "e1g1"], or empty list on failure
     */
    public static java.util.List<String> bestMoves(String fen, int depth, int multiPV) {
        return analyze(fen, depth, multiPV).moves;
    }

    /** Analyse the position and return both best moves and evaluation. */
    public static synchronized StockfishProcess.AnalysisResult analyze(String fen, int depth, int multiPV) {
        Context ctx = getApplicationContext();
        if (ctx == null) {
            Log.e(TAG, "Cannot run analyze without context");
            return new StockfishProcess.AnalysisResult(new java.util.ArrayList<>(), 0.0f, false, 0, 0, 0, 0, null);
        }

        if (!initialised || !engine.isReady()) {
            Log.w(TAG, "Engine not ready or died. Attempting restart...");
            initialised = false;
            initialised = engine.start(ctx);
            if (!initialised) {
                return new StockfishProcess.AnalysisResult(new java.util.ArrayList<>(), 0.0f, false, 0, 0, 0, 0, null);
            }
        }
        return engine.analyze(ctx, fen, depth, multiPV);
    }

    /** Legacy single move analyzer */
    public static String bestMove(String fen, int depth) {
        java.util.List<String> moves = bestMoves(fen, depth, 1);
        return moves.isEmpty() ? null : moves.get(0);
    }

    /** Interrupt any ongoing search (call before starting a new one). */
    public static void stopSearch() {
        if (initialised) engine.stopSearch();
    }

    /** Shut the engine down. */
    public static void quit() {
        if (initialised) {
            engine.stop();
            initialised = false;
        }
    }
}

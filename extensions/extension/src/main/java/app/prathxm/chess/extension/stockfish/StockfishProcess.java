package app.prathxm.chess.extension.stockfish;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StockfishProcess – manages the Stockfish executable as a subprocess,
 * communicating via the UCI protocol over stdin/stdout.
 *
 * The binary is packaged as lib/&lt;abi&gt;/libstockfish.so so Android extracts it
 * into the (executable) native library directory.
 *
 * Performance notes:
 *  - Threads defaults to every available CPU core and Hash is sized from the
 *    device's physical RAM, so the engine searches as deep as the hardware allows.
 *  - The UCI output stream is parsed without regex and without per-line logging;
 *    Stockfish prints thousands of "info" lines per search and logging each one
 *    cost a lot of CPU (and heat) on its own.
 *  - UCI options are only re-sent when they actually change.
 *  - A search that runs past its deadline is explicitly stopped instead of being left
 *    running in the background.
 */
@SuppressWarnings("unused")
public class StockfishProcess {

    private static final String TAG = "StockfishProcess";

    /** Loading the ~110 MB NNUE network can take a few seconds on slow phones. */
    private static final int READY_TIMEOUT_MS = 30_000;
    /** Extra grace time on top of any movetime cap before we force a "stop". */
    private static final int BESTMOVE_GRACE_MS = 10_000;
    /** Hard ceiling for a depth-only search before we force a "stop". */
    private static final int DEFAULT_SEARCH_TIMEOUT_MS = 60_000;

    /** Scores are clamped to +/-MATE_SCORE (pawns) to keep mate evaluations ordered. */
    public static final float MATE_SCORE = 99.0f;

    private Process process;
    private PrintWriter stdin;
    private BufferedReader stdout;

    private volatile boolean ready = false;

    // Cached option state so we only send setoption when something changes.
    private int curThreads = -1;
    private int curHash = -1;
    private int curMultiPV = -1;
    private Boolean curLimitStrength = null;
    private int curElo = -1;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public boolean start(Context context) {
        try {
            File engineBin = extractBinary(context);
            if (engineBin == null) return false;

            ProcessBuilder pb = new ProcessBuilder(engineBin.getAbsolutePath());
            pb.redirectErrorStream(true);
            process = pb.start();

            stdin  = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()), 1 << 16);

            resetOptionCache();

            send("uci");
            if (!waitForLine("uciok", READY_TIMEOUT_MS)) {
                Log.e(TAG, "Engine did not respond with 'uciok'");
                stop();
                return false;
            }

            // Threads first, then Hash (Stockfish recommends this order).
            applyThreads(StockfishSettings.getThreads(context));
            applyHash(computeHashMb(context));
            send("setoption name UCI_ShowWDL value true");

            send("isready");
            if (!waitForLine("readyok", READY_TIMEOUT_MS)) {
                Log.e(TAG, "Engine did not respond with 'readyok'");
                stop();
                return false;
            }

            ready = true;
            Log.i(TAG, "Stockfish ready on " + android.os.Build.CPU_ABI
                    + " (threads=" + curThreads + ", hash=" + curHash + "MB)");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start engine: " + e.getMessage());
            return false;
        }
    }

    public boolean isReady() {
        if (!ready || process == null) return false;
        try {
            process.exitValue();
            ready = false;
            return false; // has exited
        } catch (IllegalThreadStateException e) {
            return true; // still running
        }
    }

    // ── Results ───────────────────────────────────────────────────────────────

    public static class AnalysisResult {
        /** First move of each principal variation, best first (MultiPV order). */
        public final List<String> moves;
        /** Score of the best line in pawns from WHITE's point of view (mates mapped to +/-(99-n)). */
        public final float score;
        public final boolean hasMate;
        /** Mate distance from WHITE's point of view (positive = white mates). */
        public final int mateIn;
        /** Win/Draw/Loss per mille from WHITE's point of view. */
        public final int wdlWin;
        public final int wdlDraw;
        public final int wdlLoss;
        public final String ponder;
        /** Full principal variation of the best line (starts with moves.get(0)). */
        public final List<String> pv;
        /** Score (white POV, pawns) of every MultiPV line, aligned with {@link #moves}. */
        public final float[] lineScores;
        /** Depth actually reached by the search. */
        public final int depth;
        /** True if the position has no legal moves (checkmate or stalemate). */
        public final boolean terminal;

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder) {
            this(moves, score, hasMate, mateIn, wdlWin, wdlDraw, wdlLoss, ponder,
                    moves != null && !moves.isEmpty() ? Collections.singletonList(moves.get(0)) : new ArrayList<>(),
                    new float[]{score}, 0, false);
        }

        public AnalysisResult(List<String> moves, float score, boolean hasMate, int mateIn,
                              int wdlWin, int wdlDraw, int wdlLoss, String ponder,
                              List<String> pv, float[] lineScores, int depth, boolean terminal) {
            this.moves = moves != null ? moves : new ArrayList<>();
            this.score = score;
            this.hasMate = hasMate;
            this.mateIn = mateIn;
            this.wdlWin = wdlWin;
            this.wdlDraw = wdlDraw;
            this.wdlLoss = wdlLoss;
            this.ponder = ponder;
            this.pv = pv != null ? pv : new ArrayList<>();
            this.lineScores = lineScores != null ? lineScores : new float[0];
            this.depth = depth;
            this.terminal = terminal;
        }

        public static AnalysisResult empty() {
            return new AnalysisResult(new ArrayList<>(), 0f, false, 0, 0, 0, 0, null,
                    new ArrayList<>(), new float[0], 0, false);
        }

        /** A usable result has either a best move or is a genuine terminal position. */
        public boolean isValid() {
            return !moves.isEmpty() || terminal;
        }
    }

    /**
     * Receives intermediate results of a running search (live analysis only), so the
     * arrows / eval bar can follow the search as it deepens instead of waiting for the
     * final depth. Called on the thread that runs the search.
     */
    public interface ProgressListener {
        void onProgress(AnalysisResult partial);
    }

    /** Intermediate results are only published from this depth on (earlier ones are noise). */
    private static final int PROGRESS_MIN_DEPTH = 10;
    /** Minimum time between two intermediate results. */
    private static final long PROGRESS_INTERVAL_MS = 300;

    // ── Analysis API ──────────────────────────────────────────────────────────

    public List<String> bestMoves(Context context, String fen, int depth, int multiPV) {
        return analyze(context, fen, depth, multiPV).moves;
    }

    /** Analyse a FEN position (live analysis). */
    public AnalysisResult analyze(Context context, String fen, int depth, int multiPV) {
        return analyze(context, fen, null, depth, multiPV, 0, true);
    }

    /**
     * Analyse a position.
     *
     * @param fen          Base FEN (for review this is the game's starting FEN).
     * @param uciMoves     Optional move list played from {@code fen}. Passing the game history
     *                     lets Stockfish see repetitions and the 50-move counter, which matters a
     *                     lot for evaluating endgames correctly.
     * @param depth        Target depth.
     * @param multiPV      Number of lines.
     * @param movetimeMs   Optional wall-clock cap (0 = depth only).
     * @param honorLimit   If false, UCI_LimitStrength is always disabled (used by game review so
     *                     that the review is never weakened by the play-strength setting).
     */
    public AnalysisResult analyze(Context context, String fen, List<String> uciMoves, int depth,
                                  int multiPV, int movetimeMs, boolean honorLimit) {
        return analyze(context, fen, uciMoves, depth, multiPV, movetimeMs, honorLimit, null);
    }

    /** As above, optionally publishing intermediate results to {@code progress}. */
    public AnalysisResult analyze(Context context, String fen, List<String> uciMoves, int depth,
                                  int multiPV, int movetimeMs, boolean honorLimit,
                                  ProgressListener progress) {
        if (!isReady() || fen == null) return AnalysisResult.empty();
        multiPV = Math.max(1, multiPV);
        depth = Math.max(1, depth);

        final boolean whiteToMove = isWhiteToMove(fen, uciMoves);

        try {
            drainReady();

            applyThreads(StockfishSettings.getThreads(context));
            boolean limit = honorLimit && StockfishSettings.isLimitStrength(context);
            if (curLimitStrength == null || curLimitStrength != limit) {
                send("setoption name UCI_LimitStrength value " + limit);
                curLimitStrength = limit;
            }
            if (limit) {
                int elo = Math.max(1320, Math.min(3190, StockfishSettings.getElo(context)));
                if (elo != curElo) {
                    send("setoption name UCI_Elo value " + elo);
                    curElo = elo;
                }
            }
            if (multiPV != curMultiPV) {
                send("setoption name MultiPV value " + multiPV);
                curMultiPV = multiPV;
            }

            StringBuilder pos = new StringBuilder(fen.length() + 8 + (uciMoves != null ? uciMoves.size() * 6 : 0));
            pos.append("position fen ").append(fen);
            if (uciMoves != null && !uciMoves.isEmpty()) {
                pos.append(" moves");
                for (String m : uciMoves) pos.append(' ').append(m);
            }
            send(pos.toString());
            send(movetimeMs > 0 ? ("go depth " + depth + " movetime " + movetimeMs) : ("go depth " + depth));

            return readSearchOutput(multiPV, whiteToMove,
                    movetimeMs > 0 ? movetimeMs + BESTMOVE_GRACE_MS : DEFAULT_SEARCH_TIMEOUT_MS,
                    depth, progress);
        } catch (IOException e) {
            Log.e(TAG, "analyze error: " + e.getMessage());
            ready = false;
            return AnalysisResult.empty();
        }
    }

    /** Clears the hash; call before reviewing a new game. */
    public void newGame() {
        if (!isReady()) return;
        try {
            send("ucinewgame");
            send("isready");
            waitForLine("readyok", READY_TIMEOUT_MS);
        } catch (IOException e) {
            ready = false;
        }
    }

    public void stopSearch() {
        send("stop");
    }

    public void stop() {
        ready = false;
        try { send("quit"); } catch (Exception ignored) {}
        try { if (process != null) process.destroy(); } catch (Exception ignored) {}
    }

    // ── Output parsing ────────────────────────────────────────────────────────

    private AnalysisResult readSearchOutput(int multiPV, boolean whiteToMove, long timeoutMs,
                                            int targetDepth, ProgressListener progress) throws IOException {
        String[] firstMoves = new String[multiPV];
        float[] scores = new float[multiPV];
        boolean[] haveExact = new boolean[multiPV];
        boolean[] haveAny = new boolean[multiPV];
        List<String> bestPv = null;

        boolean hasMate = false;
        int mateIn = 0;
        int wdlW = 0, wdlD = 0, wdlL = 0;
        int reachedDepth = 0;
        boolean terminal = false;
        String bestmove = null;
        String ponder = null;

        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean stopSent = false;
        int reportedDepth = 0;
        long lastReport = 0;

        String line;
        while (true) {
            if (!stopSent && System.currentTimeMillis() > deadline) {
                // Never leave a search running in the background: it wastes CPU and
                // pollutes the next search's output.
                send("stop");
                stopSent = true;
                deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
            } else if (stopSent && System.currentTimeMillis() > deadline) {
                Log.e(TAG, "Engine unresponsive after stop; restarting.");
                stop();
                break;
            }

            line = stdout.readLine();
            if (line == null) {
                // Process died (e.g. Stockfish 19 exits on an invalid FEN / illegal move).
                Log.e(TAG, "Engine output closed unexpectedly");
                ready = false;
                break;
            }

            if (line.startsWith("bestmove")) {
                String[] parts = line.split(" ");
                if (parts.length > 1 && !"(none)".equals(parts[1])) bestmove = parts[1];
                if (parts.length > 3 && "ponder".equals(parts[2])) ponder = parts[3];
                if (bestmove == null) terminal = true;
                break;
            }

            if (!line.startsWith("info ")) continue;
            if (line.startsWith("info string")) {
                if (line.contains("CRITICAL")) Log.e(TAG, line);
                continue;
            }
            if (line.indexOf(" score ") < 0) continue;

            String[] t = line.split(" ");
            int mpv = 1;
            int depth = 0;
            boolean bound = false;
            boolean isMate = false;
            int scoreVal = 0;
            boolean haveScore = false;
            int w = -1, d = -1, l = -1;
            int pvStart = -1;

            for (int i = 1; i < t.length; i++) {
                String k = t[i];
                switch (k) {
                    case "depth":
                        if (i + 1 < t.length) depth = parseIntSafe(t[++i], 0);
                        break;
                    case "multipv":
                        if (i + 1 < t.length) mpv = parseIntSafe(t[++i], 1);
                        break;
                    case "score":
                        if (i + 2 < t.length) {
                            isMate = "mate".equals(t[i + 1]);
                            scoreVal = parseIntSafe(t[i + 2], 0);
                            haveScore = true;
                            i += 2;
                        }
                        break;
                    case "lowerbound":
                    case "upperbound":
                        bound = true;
                        break;
                    case "wdl":
                        if (i + 3 < t.length) {
                            w = parseIntSafe(t[i + 1], -1);
                            d = parseIntSafe(t[i + 2], -1);
                            l = parseIntSafe(t[i + 3], -1);
                            i += 3;
                        }
                        break;
                    case "pv":
                        pvStart = i + 1;
                        i = t.length; // pv is always last
                        break;
                    default:
                        break;
                }
            }

            if (!haveScore || mpv < 1 || mpv > multiPV) continue;
            int idx = mpv - 1;

            // Fail-high/fail-low lines only carry a bound, not a real evaluation.
            if (bound && haveExact[idx]) continue;

            if (depth == 0 && pvStart < 0) {
                // "info depth 0 score mate 0" / "score cp 0" -> checkmate / stalemate
                terminal = true;
            }

            float whiteScore;
            int whiteMate = 0;
            if (isMate) {
                whiteMate = whiteToMove ? scoreVal : -scoreVal;
                if (scoreVal == 0) {
                    // Side to move is checkmated.
                    whiteScore = whiteToMove ? -MATE_SCORE : MATE_SCORE;
                    whiteMate = 0;
                } else {
                    whiteScore = whiteMate > 0 ? (MATE_SCORE - whiteMate) : (-MATE_SCORE - whiteMate);
                }
            } else {
                float pawns = scoreVal / 100.0f;
                whiteScore = whiteToMove ? pawns : -pawns;
            }

            scores[idx] = whiteScore;
            haveAny[idx] = true;
            if (!bound) haveExact[idx] = true;

            if (pvStart > 0 && pvStart < t.length) {
                firstMoves[idx] = t[pvStart];
            }

            if (idx == 0) {
                if (depth > reachedDepth) reachedDepth = depth;
                hasMate = isMate;
                mateIn = (isMate && scoreVal != 0) ? whiteMate : 0;
                if (w >= 0 && d >= 0 && l >= 0) {
                    if (whiteToMove) { wdlW = w; wdlD = d; wdlL = l; }
                    else { wdlW = l; wdlD = d; wdlL = w; }
                }
                if (pvStart > 0 && pvStart < t.length) {
                    ArrayList<String> pv = new ArrayList<>(t.length - pvStart);
                    for (int j = pvStart; j < t.length; j++) pv.add(t[j]);
                    bestPv = pv;
                }
            }

            // Publish a snapshot once the best line of a new depth is exact. The other lines
            // may still be from the previous depth, which is fine for a live preview.
            if (progress != null && idx == 0 && !bound && !stopSent
                    && depth >= PROGRESS_MIN_DEPTH && depth > reportedDepth && depth < targetDepth
                    && firstMoves[0] != null) {
                long now = System.currentTimeMillis();
                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                    reportedDepth = depth;
                    lastReport = now;
                    try {
                        progress.onProgress(buildResult(multiPV, firstMoves, scores, haveAny, hasMate,
                                mateIn, wdlW, wdlD, wdlL,
                                bestPv != null && bestPv.size() > 1 ? bestPv.get(1) : null,
                                bestPv, depth, false, null));
                    } catch (Throwable ignored) {
                        // A failing listener must never break the search.
                    }
                }
            }
        }

        return buildResult(multiPV, firstMoves, scores, haveAny, hasMate, mateIn, wdlW, wdlD, wdlL,
                ponder, bestPv, reachedDepth, terminal, bestmove);
    }

    private static AnalysisResult buildResult(int multiPV, String[] firstMovesIn, float[] scoresIn,
                                              boolean[] haveAny, boolean hasMate, int mateIn,
                                              int wdlW, int wdlD, int wdlL, String ponder,
                                              List<String> bestPv, int reachedDepth,
                                              boolean terminal, String bestmove) {
        // Work on copies: intermediate snapshots are taken while the search keeps writing.
        String[] firstMoves = firstMovesIn.clone();
        float[] scores = scoresIn.clone();

        List<String> moves = new ArrayList<>(multiPV);
        List<Float> lineScoreList = new ArrayList<>(multiPV);
        if (bestmove != null) {
            firstMoves[0] = bestmove;
            if (bestPv == null || bestPv.isEmpty() || !bestmove.equals(bestPv.get(0))) {
                ArrayList<String> pv = new ArrayList<>(2);
                pv.add(bestmove);
                if (ponder != null) pv.add(ponder);
                bestPv = pv;
            }
        }
        for (int i = 0; i < multiPV; i++) {
            if (firstMoves[i] == null) continue;
            if (moves.contains(firstMoves[i])) continue;
            moves.add(firstMoves[i]);
            lineScoreList.add(haveAny[i] ? scores[i] : scores[0]);
        }
        float[] lineScores = new float[lineScoreList.size()];
        for (int i = 0; i < lineScores.length; i++) lineScores[i] = lineScoreList.get(i);

        boolean isTerminal = terminal && moves.isEmpty();
        if (isTerminal && !hasMate) {
            scores[0] = 0f; // stalemate
        }

        return new AnalysisResult(moves, scores[0], hasMate, mateIn, wdlW, wdlD, wdlL, ponder,
                bestPv != null ? bestPv : new ArrayList<>(), lineScores, reachedDepth, isTerminal);
    }

    // ── Options ───────────────────────────────────────────────────────────────

    private void resetOptionCache() {
        curThreads = -1;
        curHash = -1;
        curMultiPV = -1;
        curLimitStrength = null;
        curElo = -1;
    }

    private void applyThreads(int threads) {
        threads = Math.max(1, threads);
        if (threads != curThreads) {
            send("setoption name Threads value " + threads);
            curThreads = threads;
        }
    }

    private void applyHash(int mb) {
        if (mb != curHash) {
            send("setoption name Hash value " + mb);
            curHash = mb;
        }
    }

    /**
     * Size the transposition table from physical RAM. A bigger hash means the engine
     * re-searches far fewer positions, i.e. deeper results for the same CPU time (and heat).
     */
    static int computeHashMb(Context context) {
        long totalMb = 0;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                totalMb = mi.totalMem / (1024L * 1024L);
            }
        } catch (Throwable ignored) {}
        // A deep MultiPV review search visits tens of millions of nodes per position; at the
        // old sizes the table was overwritten constantly. These sizes stay well below what the
        // low-memory killer tolerates for a foreground app's child process.
        if (totalMb >= 11_000) return 768;
        if (totalMb >= 7_000) return 512;
        if (totalMb >= 5_000) return 256;
        if (totalMb >= 3_000) return 128;
        return 32;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean isWhiteToMove(String fen, List<String> moves) {
        boolean white = true;
        int sp = fen.indexOf(' ');
        if (sp >= 0 && sp + 1 < fen.length()) white = fen.charAt(sp + 1) != 'b';
        if (moves != null && (moves.size() & 1) == 1) white = !white;
        return white;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void send(String cmd) {
        if (stdin != null) stdin.println(cmd);
    }

    private boolean waitForLine(String token, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while (System.currentTimeMillis() < deadline) {
            line = stdout.readLine();
            if (line == null) break;
            if (line.startsWith(token)) return true;
        }
        return false;
    }

    private void drainReady() throws IOException {
        while (stdout.ready()) {
            if (stdout.readLine() == null) break;
        }
    }

    private File extractBinary(Context context) {
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        File engineBin = new File(nativeLibDir, "libstockfish.so");

        if (!engineBin.exists()) {
            Log.e(TAG, "Stockfish binary not found at: " + engineBin.getAbsolutePath());
            File dir = new File(nativeLibDir);
            if (dir.exists()) {
                Log.e(TAG, "Native lib dir contents: " + java.util.Arrays.toString(dir.list()));
            }
            return null;
        }
        if (!engineBin.canExecute()) {
            Log.e(TAG, "Stockfish binary is not executable: " + engineBin.getAbsolutePath());
            return null;
        }
        return engineBin;
    }
}

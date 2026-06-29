package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * StockfishProcess – manages the Stockfish executable as a subprocess,
 * communicating via the UCI protocol over stdin/stdout.
 *
 * Binary layout inside the patched APK assets:
 *   assets/stockfish/arm64-v8a/stockfish
 *   assets/stockfish/armeabi-v7a/stockfish
 *
 * On first run the binary is copied to the app's private data dir,
 * made executable, and then launched.
 */
@SuppressWarnings("unused")
public class StockfishProcess {

    private static final String TAG = "StockfishProcess";

    // UCI timeout for "readyok" and "bestmove" responses (ms)
    private static final int READY_TIMEOUT_MS = 5_000;
    private static final int BESTMOVE_TIMEOUT_MS = 15_000;

    private Process process;
    private PrintWriter stdin;
    private BufferedReader stdout;

    private volatile boolean ready = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Extract the binary for the running ABI from APK assets, make it
     * executable, and start the process.
     *
     * @param context  Any context (application context preferred).
     * @return true if the engine started and responded to "uci".
     */
    public boolean start(Context context) {
        try {
            File engineBin = extractBinary(context);
            if (engineBin == null) return false;

            ProcessBuilder pb = new ProcessBuilder(engineBin.getAbsolutePath());
            pb.redirectErrorStream(true);
            process = pb.start();

            OutputStream os = process.getOutputStream();
            stdin  = new PrintWriter(os, true /* autoFlush */);
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Initialise UCI and wait for "uciok"
            send("uci");
            if (!waitForLine("uciok", READY_TIMEOUT_MS)) {
                Log.e(TAG, "Engine did not respond with 'uciok'");
                stop();
                return false;
            }

            // Configure engine options (e.g. hash table, threads)
            send("setoption name Hash value 32");
            send("setoption name Threads value 2");

            // Confirm readiness
            send("isready");
            if (!waitForLine("readyok", READY_TIMEOUT_MS)) {
                Log.e(TAG, "Engine did not respond with 'readyok'");
                stop();
                return false;
            }

            ready = true;
            Log.i(TAG, "Stockfish ready on " + Build.CPU_ABI);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start engine: " + e.getMessage());
            return false;
        }
    }

    public boolean isReady() {
        return ready && process != null && process.isAlive();
    }

    /** @return the list of best moves in UCI format ("e2e4") for the given FEN, up to multiPV. */
    public java.util.List<String> bestMoves(Context context, String fen, int depth, int multiPV) {
        java.util.List<String> moves = new java.util.ArrayList<>();
        if (!isReady()) return moves;
        try {
            // Drain any leftover output
            drainReady();

            // Configure strength limits
            if (StockfishSettings.isLimitStrength(context)) {
                send("setoption name UCI_LimitStrength value true");
                send("setoption name UCI_Elo value " + StockfishSettings.getElo(context));
            } else {
                send("setoption name UCI_LimitStrength value false");
            }

            // Set MultiPV option
            send("setoption name MultiPV value " + multiPV);
            send("position fen " + fen);
            send("go depth " + depth);

            String[] pvMoves = new String[multiPV];
            long deadline = System.currentTimeMillis() + BESTMOVE_TIMEOUT_MS;
            String line;
            while (System.currentTimeMillis() < deadline) {
                line = stdout.readLine();
                if (line == null) break;
                Log.d(TAG, "< " + line);

                if (line.startsWith("info ") && line.contains(" pv ")) {
                    // Try to parse multipv index
                    int mpvIdx = 1; // Default to 1 if not specified
                    if (line.contains(" multipv ")) {
                        String[] parts = line.split("\\s+");
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (parts[i].equals("multipv")) {
                                try {
                                    mpvIdx = Integer.parseInt(parts[i+1]);
                                } catch (NumberFormatException ignored) {}
                                break;
                            }
                        }
                    }

                    // Extract the first move after "pv"
                    String pvMove = null;
                    String[] parts = line.split("\\s+");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].equals("pv")) {
                            pvMove = parts[i+1];
                            break;
                        }
                    }

                    if (pvMove != null && mpvIdx >= 1 && mpvIdx <= multiPV) {
                        pvMoves[mpvIdx - 1] = pvMove;
                    }
                }

                if (line.startsWith("bestmove ")) {
                    String[] parts = line.split("\\s+");
                    String best = parts.length > 1 ? parts[1] : null;
                    if (best != null) {
                        pvMoves[0] = best; // Ensure bestmove is always multipv 1
                    }
                    break;
                }
            }

            for (String m : pvMoves) {
                if (m != null) {
                    moves.add(m);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "bestMoves error: " + e.getMessage());
        }
        return moves;
    }

    /** Cancel any ongoing search immediately. */
    public void stopSearch() {
        send("stop");
    }

    /** Shut the engine down. */
    public void stop() {
        ready = false;
        try { send("quit"); } catch (Exception ignored) {}
        try { if (process != null) process.destroy(); } catch (Exception ignored) {}
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void send(String cmd) {
        Log.d(TAG, "> " + cmd);
        if (stdin != null) stdin.println(cmd);
    }

    /** Block until a line containing {@code token} is seen or timeout elapses. */
    private boolean waitForLine(String token, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while (System.currentTimeMillis() < deadline) {
            line = stdout.readLine();
            if (line == null) break;
            Log.d(TAG, "< " + line);
            if (line.contains(token)) return true;
        }
        return false;
    }

    /** Non‑blocking drain of any buffered output lines. */
    private void drainReady() throws IOException {
        while (stdout.ready()) {
            String line = stdout.readLine();
            if (line != null) Log.d(TAG, "(drain) < " + line);
        }
    }

    /**
     * Find the stockfish binary in the app's native library directory.
     * The binary is packaged as lib/&lt;abi&gt;/libstockfish.so in the APK,
     * and Android automatically extracts it to an executable directory.
     */
    private File extractBinary(Context context) {
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        File engineBin = new File(nativeLibDir, "libstockfish.so");

        if (!engineBin.exists()) {
            Log.e(TAG, "Stockfish binary not found at: " + engineBin.getAbsolutePath());
            // Debug: list native lib dir contents
            File dir = new File(nativeLibDir);
            if (dir.exists()) {
                String[] files = dir.list();
                Log.e(TAG, "Native lib dir contents: " + java.util.Arrays.toString(files));
            }
            return null;
        }

        if (!engineBin.canExecute()) {
            Log.e(TAG, "Stockfish binary is not executable: " + engineBin.getAbsolutePath());
            return null;
        }

        Log.i(TAG, "Found stockfish binary at: " + engineBin.getAbsolutePath());
        return engineBin;
    }

    // Inline import reference — android.os.Build is always available
    private static final class Build {
        static final String CPU_ABI = android.os.Build.CPU_ABI;
    }
}

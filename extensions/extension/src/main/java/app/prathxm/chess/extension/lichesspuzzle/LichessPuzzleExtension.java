/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 *
 * Derived from / ported from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LichessPuzzleExtension {
    private static final String TAG = "LichessPuzzle";
    private static final String DAILY_URL = "https://lichess.org/api/puzzle/daily";
    private static final int HEARTS = 999999;
    private static volatile Puzzle lastPuzzle;

    private LichessPuzzleExtension() {
    }

    public static void launchLichessActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            android.content.Context ctx = (android.content.Context) currentApplicationMethod.invoke(null);
            if (ctx != null) {
                android.content.Intent intent = new android.content.Intent(ctx, LichessPuzzleJourneyActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } else {
                Log.e(TAG, "Application context is null");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch LichessPuzzleJourneyActivity: " + t.getMessage(), t);
        }
    }

    public static Object getDailyPuzzle(String date, Object continuation) {
        Log.d(TAG, "getDailyPuzzle() called with date: " + date);
        try {
            Puzzle puzzle = fetchDailyPuzzle(date);
            lastPuzzle = puzzle;
            Object res = response(puzzle);
            Log.d(TAG, "getDailyPuzzle() successfully created GetDailyPuzzleResponse: " + res);
            return res;
        } catch (Throwable t) {
            Log.e(TAG, "getDailyPuzzle() failed: " + t.getMessage(), t);
            try {
                Puzzle puzzle = fallbackPuzzle(date);
                lastPuzzle = puzzle;
                return response(puzzle);
            } catch (Throwable fallbackError) {
                // If even the fallback formatting instance fails, return safe stub
                return null;
            }
        }
    }

    public static Object submitDailyPuzzleAction(long dailyPuzzleId, Object action, Object hintState) {
        Log.d(TAG, "submitDailyPuzzleAction() called with id: " + dailyPuzzleId);
        try {
            Puzzle puzzle = lastPuzzle != null ? lastPuzzle : fallbackPuzzle(today());
            Object res = newInstance(
                "chesscom.puzzles.v2.SubmitDailyPuzzleActionResponse",
                new Class<?>[]{
                    cls("chesscom.puzzles.v2.DailyPuzzleAttemptState"),
                    cls("chesscom.puzzles.v2.DailyPuzzleUserStats"),
                    cls("okio.ByteString")
                },
                attempt(puzzle),
                stats(),
                emptyByteString()
            );
            Log.d(TAG, "submitDailyPuzzleAction() successfully created response: " + res);
            return res;
        } catch (Throwable t) {
            Log.e(TAG, "submitDailyPuzzleAction() failed: " + t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    private static Puzzle fetchDailyPuzzle(String date) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(DAILY_URL).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append("\n");
            }
        }

        JSONObject root = new JSONObject(body.toString());
        JSONObject puzzleJson = root.getJSONObject("puzzle");
        JSONObject gameJson = root.getJSONObject("game");
        JSONArray themes = puzzleJson.optJSONArray("themes");

        String id = puzzleJson.optString("id", "lichess");
        int rating = puzzleJson.optInt("rating", 0);
        String theme = themes != null && themes.length() > 0 ? themes.optString(0, "tactics") : "tactics";
        String title = "Lichess " + theme + (rating > 0 ? " " + rating : "");
        
        String fen = puzzleJson.optString("fen", "");
        JSONArray solutionArray = puzzleJson.optJSONArray("solution");

        // Construct a proper puzzle PGN using the FEN and the solution array
        StringBuilder pgnBuilder = new StringBuilder();
        pgnBuilder.append("[Event \"Lichess Daily Puzzle\"]\n");
        pgnBuilder.append("[Site \"https://lichess.org/training/").append(id).append("\"]\n");
        pgnBuilder.append("[Date \"").append(normalizeDate(date).replace("-", ".")).append("\"]\n");
        
        if (!fen.isEmpty()) {
            pgnBuilder.append("[FEN \"").append(fen).append("\"]\n");
            pgnBuilder.append("[SetUp \"1\"]\n");
        }
        pgnBuilder.append("\n");

        if (fen.isEmpty() || solutionArray == null || solutionArray.length() == 0) {
            throw new IllegalStateException("Puzzle contains no position or solution");
        }
        java.util.List<String> solution = new java.util.ArrayList<>();
        for (int i = 0; i < solutionArray.length(); i++) solution.add(solutionArray.optString(i));
        String movetext = uciToPgnMovetext(fen, solution);
        if (movetext == null) throw new IllegalStateException("Unreadable solution " + solution);
        pgnBuilder.append(movetext).append(" *");

        String pgn = pgnBuilder.toString().trim();
        
        Log.d(TAG, "fetchDailyPuzzle() downloaded successfully: title=" + title + ", rating=" + rating + ", pgn=" + pgn);
        return new Puzzle(idToInt(id), title, normalizeDate(date), pgn);
    }

    /**
     * Lichess sends the solution in UCI ("a3a2 a1a2 f5a5"). The app's PGN parser rejects bare
     * UCI, so every real daily puzzle used to fail and the fallback stub was shown instead.
     * Pieces are written with their from-square ("1... Qa3xa2 2. Ka1xa2 Rf5a5") so no
     * disambiguation is needed; pawns use plain SAN ("exf6", "a8=Q").
     *
     * @return the movetext, or null if a move does not fit the position
     */
    static String uciToPgnMovetext(String fen, java.util.List<String> uciMoves) {
        String[] f = fen.trim().split("\\s+");
        if (f.length < 2) return null;
        char[] b = app.prathxm.chess.extension.stockfish.BoardUtil.parseBoard(fen);
        boolean white = "w".equals(f[1]);
        int moveNo = 1;
        if (f.length >= 6) {
            try { moveNo = Math.max(1, Integer.parseInt(f[5])); } catch (NumberFormatException ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String uci : uciMoves) {
            int from = app.prathxm.chess.extension.stockfish.BoardUtil.square(uci, 0);
            int to = app.prathxm.chess.extension.stockfish.BoardUtil.square(uci, 2);
            if (from < 0 || to < 0) return null;
            char piece = b[from];
            if (piece == '.' || Character.isUpperCase(piece) != white) return null;
            char lower = Character.toLowerCase(piece);
            boolean capture = b[to] != '.' || (lower == 'p' && (from % 8) != (to % 8));
            if (white) sb.append(first ? "" : " ").append(moveNo).append(". ");
            else if (first) sb.append(moveNo).append("... ");
            else sb.append(' ');
            String fromName = uci.substring(0, 2), toName = uci.substring(2, 4);
            if (lower == 'k' && Math.abs((from % 8) - (to % 8)) == 2) {
                sb.append((to % 8) > (from % 8) ? "O-O" : "O-O-O");
            } else {
                if (lower == 'p') {
                    // Pawns in plain SAN ("exf6", "a8=Q"): the parser rejects "e5xf6".
                    if (capture) sb.append(fromName.charAt(0)).append('x');
                    sb.append(toName);
                    if (uci.length() >= 5) sb.append('=').append(Character.toUpperCase(uci.charAt(4)));
                } else {
                    sb.append(Character.toUpperCase(piece)).append(fromName).append(capture ? "x" : "").append(toName);
                }
            }
            app.prathxm.chess.extension.stockfish.BoardUtil.apply(b, uci);
            if (!white) moveNo++;
            white = !white;
            first = false;
        }
        return sb.toString();
    }

    private static Object response(Puzzle puzzle) throws Exception {
        return newInstance(
            "chesscom.puzzles.v2.GetDailyPuzzleResponse",
            new Class<?>[]{
                cls("chesscom.puzzles.v2.DailyPuzzle"),
                cls("chesscom.puzzles.v2.DailyPuzzleAttemptState"),
                cls("chesscom.puzzles.v2.DailyPuzzleUserStats"),
                Integer.class,
                cls("okio.ByteString")
            },
            dailyPuzzle(puzzle),
            attempt(puzzle),
            stats(),
            HEARTS,
            emptyByteString()
        );
    }

    private static Object dailyPuzzle(Puzzle puzzle) throws Exception {
        return newInstance(
            "chesscom.puzzles.v2.DailyPuzzle",
            new Class<?>[]{
                long.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                cls("chesscom.puzzles.v2.DailyPuzzleAuthorDetails"),
                cls("chesscom.puzzles.v2.DailyPuzzleVideoDetails"),
                boolean.class,
                int.class,
                cls("okio.ByteString")
            },
            (long) puzzle.id,
            puzzle.title,
            puzzle.date,
            puzzle.pgn,
            0,
            0,
            null,
            null,
            true,
            HEARTS,
            emptyByteString()
        );
    }

    private static Object attempt(Puzzle puzzle) throws Exception {
        return newInstance(
            "chesscom.puzzles.v2.DailyPuzzleAttemptState",
            new Class<?>[]{
                long.class,
                String.class,
                int.class,
                cls("java.time.Instant"),
                int.class,
                String.class,
                cls("chesscom.puzzles.v2.DailyPuzzleHintState"),
                Boolean.class,
                cls("okio.ByteString")
            },
            (long) puzzle.id,
            puzzle.date,
            HEARTS,
            null,
            0,
            "Lichess puzzle loaded locally.",
            null,
            Boolean.FALSE,
            emptyByteString()
        );
    }

    private static Object stats() throws Exception {
        return newInstance(
            "chesscom.puzzles.v2.DailyPuzzleUserStats",
            new Class<?>[]{int.class, int.class, int.class, cls("okio.ByteString")},
            0,
            100,
            0,
            emptyByteString()
        );
    }

    private static Object emptyByteString() throws Exception {
        return cls("okio.ByteString").getField("d").get(null);
    }

    private static Object newInstance(String className, Class<?>[] types, Object... args) throws Exception {
        Constructor<?> constructor = cls(className).getConstructor(types);
        return constructor.newInstance(args);
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    private static Puzzle fallbackPuzzle(String date) {
        String pgn = "[Event \"Lichess Puzzle\"]\n" +
            "[Site \"https://lichess.org\"]\n" +
            "[Date \"" + normalizeDate(date).replace("-", ".") + "\"]\n" +
            "[White \"White\"]\n" +
            "[Black \"Black\"]\n" +
            "[Result \"*\"]\n" +
            "[FEN \"6k1/5ppp/8/8/8/8/5PPP/6K1 w - - 0 1\"]\n" +
            "[SetUp \"1\"]\n\n" +
            "1. Kf2 Kf8 2. Kf3 Ke7 *";
        return new Puzzle(764424, "Lichess fallback", normalizeDate(date), pgn);
    }

    private static String normalizeDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return today();
        }
        return date;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static int idToInt(String id) {
        int hash = id == null ? 0 : id.toLowerCase(Locale.US).hashCode();
        return hash == Integer.MIN_VALUE ? 1 : Math.abs(hash);
    }

    private static final class Puzzle {
        final int id;
        final String title;
        final String date;
        final String pgn;

        Puzzle(int id, String title, String date, String pgn) {
            this.id = id == 0 ? 1 : id;
            this.title = title;
            this.date = date;
            this.pgn = pgn;
        }
    }
}
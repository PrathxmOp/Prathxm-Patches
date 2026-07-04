/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
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

    public static Object submitDailyPuzzleAction(int dailyPuzzleId, Object action, Object hintState, Object continuation) {
        Log.d(TAG, "submitDailyPuzzleAction() called with id: " + dailyPuzzleId);
        try {
            Puzzle puzzle = lastPuzzle != null ? lastPuzzle : fallbackPuzzle(today());
            Object res = newInstance(
                "chesscom.puzzles.v2alpha.SubmitDailyPuzzleActionResponse",
                new Class<?>[]{
                    cls("chesscom.puzzles.v2alpha.DailyPuzzleAttemptState"),
                    cls("chesscom.puzzles.v2alpha.DailyPuzzleUserStats"),
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

        // Append the correct solution moves to the PGN body
        if (solutionArray != null) {
            for (int i = 0; i < solutionArray.length(); i++) {
                pgnBuilder.append(solutionArray.optString(i)).append(" ");
            }
        }
        pgnBuilder.append("*");
        
        String pgn = pgnBuilder.toString().trim();
        
        if (solutionArray == null || solutionArray.length() == 0) {
            throw new IllegalStateException("Puzzle contains no solution");
        }
        
        Log.d(TAG, "fetchDailyPuzzle() downloaded successfully: title=" + title + ", rating=" + rating + ", pgn=" + pgn);
        return new Puzzle(idToInt(id), title, normalizeDate(date), pgn);
    }

    private static Object response(Puzzle puzzle) throws Exception {
        return newInstance(
            "chesscom.puzzles.v2alpha.GetDailyPuzzleResponse",
            new Class<?>[]{
                cls("chesscom.puzzles.v2alpha.DailyPuzzle"),
                cls("chesscom.puzzles.v2alpha.DailyPuzzleAttemptState"),
                cls("chesscom.puzzles.v2alpha.DailyPuzzleUserStats"),
                int.class,
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
            "chesscom.puzzles.v2alpha.DailyPuzzle",
            new Class<?>[]{
                int.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                cls("chesscom.puzzles.v2alpha.DailyPuzzleAuthorDetails"),
                cls("chesscom.puzzles.v2alpha.DailyPuzzleVideoDetails"),
                boolean.class,
                int.class,
                cls("okio.ByteString")
            },
            puzzle.id,
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
            "chesscom.puzzles.v2alpha.DailyPuzzleAttemptState",
            new Class<?>[]{
                int.class,
                String.class,
                int.class,
                cls("java.time.Instant"),
                int.class,
                String.class,
                cls("chesscom.puzzles.v2alpha.DailyPuzzleHintState"),
                cls("okio.ByteString")
            },
            puzzle.id,
            puzzle.date,
            HEARTS,
            null,
            0,
            "Lichess puzzle loaded locally.",
            null,
            emptyByteString()
        );
    }

    private static Object stats() throws Exception {
        return newInstance(
            "chesscom.puzzles.v2alpha.DailyPuzzleUserStats",
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
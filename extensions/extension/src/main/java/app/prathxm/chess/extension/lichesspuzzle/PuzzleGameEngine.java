/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PuzzleGameEngine {
    private static final String TAG = "PuzzleGameEngine";

    public interface PuzzleListener {
        void onPuzzleLoaded(String fen, boolean playerIsWhite, int rating, String theme, int levelIndex, boolean isOnline);
        void onMoveResult(boolean correct, boolean solved, String expectedUci, boolean isCapture);
        void onOpponentMove(String uci);
        void onStateReset(String fen, boolean playerIsWhite, boolean flipped, boolean isInteractable, char playerColor, java.util.List<String> movesToApply);
        void onError(String msg);
    }

    public static class PuzzleHistoryItem {
        public final String fen;
        public final String[] moves;
        public final int rating;
        public final String theme;
        public final int levelIndex;
        public final boolean isOnline;

        public PuzzleHistoryItem(String fen, String[] moves, int rating, String theme, int levelIndex, boolean isOnline) {
            this.fen = fen;
            this.moves = moves;
            this.rating = rating;
            this.theme = theme;
            this.levelIndex = levelIndex;
            this.isOnline = isOnline;
        }
    }

    private final Context context;
    private final LichessPuzzleDatabaseHelper dbHelper;
    private final PuzzleListener listener;
    private final Handler mainHandler;

    // Game state
    private String startFen;
    private String currentPuzzleId;
    private String[] solutionMoves;
    private int currentMoveIdx;
    private int maxMoveIdxReached;
    private boolean isPlayerTurn;
    private boolean isFinished;
    private boolean isAnalysisMode;
    private boolean playerIsWhite;
    private boolean isOnlinePuzzle;
    private int rating;
    private String theme;
    private int levelIndex;

    private final List<PuzzleHistoryItem> puzzleHistory = new ArrayList<>();
    private int historyIndex = -1;
    private int hintClickCount = 0;

    public PuzzleGameEngine(Context context, LichessPuzzleDatabaseHelper dbHelper, PuzzleListener listener) {
        this.context = context.getApplicationContext();
        this.dbHelper = dbHelper;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public void loadOnlinePuzzle(String puzzleId) {
        new Thread(() -> {
            try {
                if (!isNetworkAvailable()) {
                    mainHandler.post(this::loadOfflineRandom);
                    return;
                }

                String urlStr = (puzzleId == null) 
                        ? "https://lichess.org/api/puzzle/daily" 
                        : "https://lichess.org/api/puzzle/" + puzzleId;
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    connection.disconnect();
                    parseAndInit(response.toString());
                } else {
                    connection.disconnect();
                    mainHandler.post(this::loadOfflineRandom);
                }
            } catch (Exception e) {
                Log.e(TAG, "Network puzzle load failed, falling back to offline", e);
                mainHandler.post(this::loadOfflineRandom);
            }
        }).start();
    }

    private void parseAndInit(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONObject puzzleObj = root.getJSONObject("puzzle");

            currentPuzzleId = puzzleObj.optString("id", null);
            startFen = puzzleObj.getString("fen");
            JSONArray solutionArr = puzzleObj.getJSONArray("solution");
            
            solutionMoves = new String[solutionArr.length()];
            for (int i = 0; i < solutionArr.length(); i++) {
                solutionMoves[i] = solutionArr.getString(i);
            }

            rating = puzzleObj.getInt("rating");
            theme = puzzleObj.optJSONArray("themes") != null ? 
                    puzzleObj.getJSONArray("themes").optString(0, "tactics") : "tactics";
            levelIndex = -1;
            isOnlinePuzzle = true;

            PuzzleHistoryItem item = new PuzzleHistoryItem(startFen, solutionMoves, rating, theme, levelIndex, true);
            addToHistory(item);

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onPuzzleLoaded(startFen, true, rating, theme, levelIndex, true);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse online puzzle", e);
            mainHandler.post(this::loadOfflineRandom);
        }
    }

    public void loadOfflineRandom() {
        if (dbHelper.getPuzzlesCount() == 0) {
            notifyError("No offline puzzles available.");
            return;
        }
        JSONObject puzzleObj = dbHelper.getRandomUnsolvedPuzzle(null);
        if (puzzleObj == null) {
            notifyError("All offline puzzles solved! Download more.");
            return;
        }
        initOfflinePuzzle(puzzleObj);
    }

    public void loadOfflineTheme(String theme) {
        if (dbHelper.getPuzzlesCount() == 0) {
            notifyError("No offline puzzles available.");
            return;
        }
        JSONObject puzzleObj = dbHelper.getRandomUnsolvedPuzzle(theme);
        if (puzzleObj == null) {
            puzzleObj = dbHelper.getRandomUnsolvedPuzzle(null);
        }
        if (puzzleObj != null) {
            initOfflinePuzzle(puzzleObj);
        } else {
            notifyError("No puzzles available. Please connect to internet.");
        }
    }

    public void loadOfflineLevel(int levelIdx) {
        if (dbHelper.getPuzzlesCount() == 0) {
            notifyError("No offline puzzles available.");
            return;
        }
        int idx = levelIdx - 1;
        JSONObject puzzleObj = dbHelper.getSpecificPuzzleByOffset(idx);
        if (puzzleObj == null) {
            notifyError("Invalid level select: " + levelIdx);
            return;
        }
        initOfflinePuzzle(puzzleObj, levelIdx);
    }

    public void initOfflinePuzzle(JSONObject puzzleObj) {
        initOfflinePuzzle(puzzleObj, -1);
    }

    public void initOfflinePuzzle(JSONObject puzzleObj, int levelIdx) {
        try {
            currentPuzzleId = puzzleObj.optString("id", null);
            startFen = puzzleObj.getString("fen");
            String movesStr = puzzleObj.getString("moves");
            solutionMoves = movesStr.split(" ");
            rating = puzzleObj.getInt("rating");
            theme = puzzleObj.optString("theme", "tactics");

            levelIndex = levelIdx;
            isOnlinePuzzle = false;

            PuzzleHistoryItem item = new PuzzleHistoryItem(startFen, solutionMoves, rating, theme, levelIndex, false);
            addToHistory(item);

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onPuzzleLoaded(startFen, false, rating, theme, levelIndex, false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to load offline puzzle data", e);
            notifyError("Failed to load offline puzzle.");
        }
    }

    private void addToHistory(PuzzleHistoryItem item) {
        while (puzzleHistory.size() > historyIndex + 1) {
            puzzleHistory.remove(puzzleHistory.size() - 1);
        }
        puzzleHistory.add(item);
        historyIndex = puzzleHistory.size() - 1;
    }

    public void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            loadHistoryItem(puzzleHistory.get(historyIndex));
        } else {
            if (levelIndex > 1) {
                loadOfflineLevel(levelIndex - 1);
            } else {
                notifyError("First Puzzle: No previous puzzles to load.");
            }
        }
    }

    public void navigateForward() {
        if (historyIndex < puzzleHistory.size() - 1) {
            historyIndex++;
            loadHistoryItem(puzzleHistory.get(historyIndex));
        } else {
            if (levelIndex != -1) {
                // If it is level journey mode, next level load should be controlled by activity (checks unlocked state)
                if (listener != null) {
                    listener.onError("Next level is handled by the journey progression.");
                }
            } else {
                if (dbHelper.getPuzzlesCount() > 0) {
                    if (theme != null && !"tactics".equals(theme)) {
                        loadOfflineTheme(theme);
                    } else {
                        loadOfflineRandom();
                    }
                } else {
                    loadOnlinePuzzle(null);
                }
            }
        }
    }

    public void loadHistoryItem(PuzzleHistoryItem item) {
        this.startFen = item.fen;
        this.solutionMoves = item.moves;
        this.rating = item.rating;
        this.theme = item.theme;
        this.levelIndex = item.levelIndex;
        this.isOnlinePuzzle = item.isOnline;
        this.hintClickCount = 0;

        mainHandler.post(() -> {
            if (listener != null) {
                listener.onPuzzleLoaded(startFen, isOnlinePuzzle, rating, theme, levelIndex, isOnlinePuzzle);
            }
        });
    }

    public void resetPuzzleState(boolean isDailyCompleted) {
        if (startFen == null || solutionMoves == null || solutionMoves.length == 0) return;

        isAnalysisMode = false;
        currentMoveIdx = 0;
        isFinished = false;
        hintClickCount = 0;

        if (isOnlinePuzzle) {
            boolean playerIsWhiteInFen = startFen.contains(" w ");
            playerIsWhite = playerIsWhiteInFen;
            currentMoveIdx = 0;
            maxMoveIdxReached = 0;
            isPlayerTurn = true;

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onStateReset(startFen, playerIsWhite, !playerIsWhite, true, playerIsWhite ? 'w' : 'b', new java.util.ArrayList<>());
                }
            });
        } else {
            boolean opponentIsWhite = startFen.contains(" w ");
            playerIsWhite = !opponentIsWhite;
            currentMoveIdx = 1;
            maxMoveIdxReached = 1;
            isPlayerTurn = true;

            mainHandler.post(() -> {
                if (listener != null) {
                    java.util.List<String> moves = new java.util.ArrayList<>();
                    moves.add(solutionMoves[0]);
                    listener.onStateReset(startFen, playerIsWhite, !playerIsWhite, true, playerIsWhite ? 'w' : 'b', moves);
                }
            });
        }
    }

    public void submitMove(String from, String to, boolean isCapture) {
        if (isAnalysisMode) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMoveResult(true, false, null, isCapture);
                }
            });
            return;
        }

        if (!isPlayerTurn || isFinished) return;

        String playerMove = from + to;
        String expectedMove = solutionMoves[currentMoveIdx];

        if (playerMove.equalsIgnoreCase(expectedMove.substring(0, 4))) {
            currentMoveIdx++;
            if (currentMoveIdx > maxMoveIdxReached) {
                maxMoveIdxReached = currentMoveIdx;
            }
            hintClickCount = 0;

            if (currentMoveIdx >= solutionMoves.length) {
                isFinished = true;
                isPlayerTurn = false;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onMoveResult(true, true, expectedMove, isCapture);
                    }
                });
            } else {
                isPlayerTurn = false;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onMoveResult(true, false, expectedMove, isCapture);
                    }
                });

                // Opponent reply
                mainHandler.postDelayed(() -> {
                    if (isFinished) return;
                    String opponentMove = solutionMoves[currentMoveIdx];
                    currentMoveIdx++;
                    if (currentMoveIdx > maxMoveIdxReached) {
                        maxMoveIdxReached = currentMoveIdx;
                    }
                    hintClickCount = 0;
                    isPlayerTurn = true;

                    if (listener != null) {
                        listener.onOpponentMove(opponentMove);
                    }
                }, 1000);
            }
        } else {
            // Incorrect move
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMoveResult(false, false, expectedMove, isCapture);
                }
            });

            isPlayerTurn = false;
            mainHandler.postDelayed(() -> {
                if (isFinished) return;
                // reset state
                isPlayerTurn = true;
                hintClickCount = 0;
                if (listener != null) {
                    java.util.List<String> moves = new java.util.ArrayList<>();
                    if (!isOnlinePuzzle) {
                        moves.add(solutionMoves[0]);
                    }
                    listener.onStateReset(startFen, playerIsWhite, !playerIsWhite, true, playerIsWhite ? 'w' : 'b', moves);
                }
            }, 1000);
        }
    }

    public void undoLastMove() {
        int limit = isOnlinePuzzle ? 0 : 1;
        if (solutionMoves == null || currentMoveIdx <= limit) return;

        currentMoveIdx--;
        if (isOnlinePuzzle) {
            isPlayerTurn = (currentMoveIdx % 2 == 0);
        } else {
            isPlayerTurn = (currentMoveIdx % 2 != 0);
        }
        isFinished = false;
        hintClickCount = 0;

        final int currentIdx = currentMoveIdx;
        final boolean turn = isPlayerTurn;
        mainHandler.post(() -> {
            if (listener != null) {
                java.util.List<String> moves = new java.util.ArrayList<>();
                for (int i = 0; i < currentIdx; i++) {
                    moves.add(solutionMoves[i]);
                }
                listener.onStateReset(startFen, playerIsWhite, !playerIsWhite, turn, playerIsWhite ? 'w' : 'b', moves);
            }
        });
    }

    public void redoNextMove() {
        if (solutionMoves == null || currentMoveIdx >= maxMoveIdxReached) return;

        String nextMove = solutionMoves[currentMoveIdx];
        currentMoveIdx++;
        if (isOnlinePuzzle) {
            isPlayerTurn = (currentMoveIdx % 2 == 0);
        } else {
            isPlayerTurn = (currentMoveIdx % 2 != 0);
        }

        if (currentMoveIdx >= solutionMoves.length) {
            isFinished = true;
            isPlayerTurn = false;
        }

        final int currentIdx = currentMoveIdx;
        final boolean turn = isPlayerTurn;
        mainHandler.post(() -> {
            if (listener != null) {
                java.util.List<String> moves = new java.util.ArrayList<>();
                for (int i = 0; i < currentIdx; i++) {
                    moves.add(solutionMoves[i]);
                }
                listener.onStateReset(startFen, playerIsWhite, !playerIsWhite, turn, playerIsWhite ? 'w' : 'b', moves);

                if (currentIdx >= solutionMoves.length) {
                    listener.onMoveResult(true, true, nextMove, false);
                }
            }
        });
    }

    private void soundMovePlayed(String move) {
        // no-op, handled by caller / listener callbacks
    }

    private void notifyError(String msg) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(msg);
            }
        });
    }

    // Getters / Setters
    public String getStartFen() { return startFen; }
    public String getCurrentPuzzleId() { return currentPuzzleId; }
    public String[] getSolutionMoves() { return solutionMoves; }
    public int getCurrentMoveIdx() { return currentMoveIdx; }
    public int getMaxMoveIdxReached() { return maxMoveIdxReached; }
    public boolean isPlayerTurn() { return isPlayerTurn; }
    public boolean isFinished() { return isFinished; }
    public boolean isAnalysisMode() { return isAnalysisMode; }
    public void setAnalysisMode(boolean mode) { this.isAnalysisMode = mode; }
    public boolean isPlayerIsWhite() { return playerIsWhite; }
    public boolean isOnlinePuzzle() { return isOnlinePuzzle; }
    public int getRating() { return rating; }
    public String getTheme() { return theme; }
    public int getLevelIndex() { return levelIndex; }
    public List<PuzzleHistoryItem> getPuzzleHistory() { return puzzleHistory; }
    public int getHistoryIndex() { return historyIndex; }
    public int getHintClickCount() { return hintClickCount; }
    public void setHintClickCount(int count) { this.hintClickCount = count; }
    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
    }
}

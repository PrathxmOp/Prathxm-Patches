/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.content.Context;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoveClassifier {
    private static final String TAG = "MoveClassifier";

    private static final List<String> fenHistory = new ArrayList<>();
    private static final Map<String, Float> fenToEvalMap = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> fenToBestMovesMap = new ConcurrentHashMap<>();

    public static void clearHistory() {
        synchronized (fenHistory) {
            fenHistory.clear();
            fenToEvalMap.clear();
            fenToBestMovesMap.clear();
        }
    }

    public static List<String> getFenHistory() {
        return fenHistory;
    }

    public static Map<String, Float> getFenToEvalMap() {
        return fenToEvalMap;
    }

    public static Map<String, List<String>> getFenToBestMovesMap() {
        return fenToBestMovesMap;
    }

    public static void updateHistory(String fen) {
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
                        StockfishExtension.isReviewMode = false;
                    }
                }
                fenHistory.add(key);
            }
        }
    }

    public static String getFenKey(String fen) {
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

    public static String deduceUciMove(String prevFen, String currFen) {
        try {
            String[] prevParts = prevFen.split("\\s+");
            String[] currParts = currFen.split("\\s+");
            if (prevParts.length < 2 || currParts.length < 2) return null;

            String prevBoard = expandFenBoard(prevParts[0]);
            String currBoard = expandFenBoard(currParts[0]);
            if (prevBoard.length() != 64 || currBoard.length() != 64) return null;

            boolean whiteMoved = prevParts[1].equals("w");

            List<Integer> fromCandidates = new ArrayList<>();
            List<Integer> toCandidates = new ArrayList<>();

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
    public static void classifyMoveIfPossible(Context context, String currentFen, StockfishProcess.AnalysisResult currentResult) {
        if (context == null) return;
        
        Activity activity = StockfishExtension.getCurrentActivity();
        if (activity != null && StockfishExtension.isLiveMatch(activity) && !StockfishExtension.isReviewMode) {
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
                    Toast.makeText(activity, toastText, Toast.LENGTH_SHORT).show();
                    
                    if (triggerVibrate && StockfishSettings.isBlunderAlertsEnabled(activity)) {
                        Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= 26) {
                                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
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
}

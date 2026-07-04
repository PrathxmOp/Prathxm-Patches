/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class PuzzleRushManager {
    public interface RushListener {
        void onLoadPuzzle(int targetRating);
        void onUIUpdate(int score, int strikes, int opponentScore, int timeLeft);
        void onOpponentSolved(int opponentScore);
        void onGameOver(String title, String message, int score);
    }

    private final Activity activity;
    private final RushListener listener;
    private final Handler mainHandler;

    private volatile boolean isBattleMode;
    private volatile int score;
    private volatile int strikes;
    private volatile int timeLeft;
    private volatile int opponentScore;
    private int opponentSolveCountdown;
    private volatile boolean isFinished;
    private Timer timer;

    public PuzzleRushManager(Activity activity, RushListener listener) {
        this.activity = activity;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void startRun(boolean isBattle) {
        stopTimer();
        this.isBattleMode = isBattle;
        this.score = 0;
        this.strikes = 0;
        this.timeLeft = 180;
        this.opponentScore = 0;
        this.opponentSolveCountdown = 15 + (int) (Math.random() * 10);
        this.isFinished = false;

        notifyUIUpdate();
        loadNextPuzzle();
        startTimer();
    }

    public void stopRun() {
        stopTimer();
        isFinished = true;
    }

    public void handleCorrectSolve() {
        if (isFinished) return;
        score++;
        notifyUIUpdate();
        loadNextPuzzle();
    }

    public void handleIncorrectSolve() {
        if (isFinished) return;
        strikes++;
        notifyUIUpdate();

        if (strikes >= 3) {
            endRun();
        } else {
            loadNextPuzzle();
        }
    }

    private void loadNextPuzzle() {
        if (isFinished) return;
        int targetRating = 600 + score * 45;
        if (targetRating > 2800) targetRating = 2800;

        if (listener != null) {
            listener.onLoadPuzzle(targetRating);
        }
    }

    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isFinished) {
                    cancel();
                    return;
                }

                timeLeft--;

                if (isBattleMode) {
                    opponentSolveCountdown--;
                    if (opponentSolveCountdown <= 0) {
                        opponentScore++;
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onOpponentSolved(opponentScore);
                            }
                        });
                        opponentSolveCountdown = 12 + (int) (Math.random() * 12);
                    }
                }

                mainHandler.post(() -> {
                    if (timeLeft <= 0) {
                        timeLeft = 0;
                        endRun();
                    } else {
                        notifyUIUpdate();
                    }
                });
            }
        }, 1000, 1000);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void endRun() {
        stopTimer();
        isFinished = true;
        
        mainHandler.post(() -> {
            if (isBattleMode) {
                String title;
                String message;
                if (score > opponentScore) {
                    title = "🏆 Victory!";
                    message = "You defeated your opponent " + score + " to " + opponentScore + "!";
                } else if (score < opponentScore) {
                    title = "💀 Defeat!";
                    message = "Opponent won the battle " + opponentScore + " to " + score + ".";
                } else {
                    title = "🤝 Draw!";
                    message = "A close match! Both scored " + score + ".";
                }
                if (listener != null) {
                    listener.onGameOver(title, message, score);
                }
            } else {
                if (listener != null) {
                    listener.onGameOver("Puzzle Rush Complete!", "You solved " + score + " puzzles!", score);
                }
            }
        });
    }

    private void notifyUIUpdate() {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onUIUpdate(score, strikes, opponentScore, timeLeft);
            }
        });
    }

    // Getters
    public int getScore() { return score; }
    public int getStrikes() { return strikes; }
    public int getTimeLeft() { return timeLeft; }
    public int getOpponentScore() { return opponentScore; }
    public boolean isFinished() { return isFinished; }
}

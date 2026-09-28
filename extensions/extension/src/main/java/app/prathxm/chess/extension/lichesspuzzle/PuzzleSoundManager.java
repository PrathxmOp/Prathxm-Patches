/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 *
 * Derived from / ported from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

/**
 * Puzzle sounds, played from the Chess.com app's own assets.
 *
 * <p>Each sound has a list of candidate asset paths; the first one that exists in the installed
 * app is used. Chess.com 4.10.17 has no {@code sounds/puzzles/correct.mp3},
 * {@code sounds/puzzles/incorrect.mp3} or {@code sounds/puzzles/puzzle-path/puzzle-solved.mp3}
 * (the paths the old code used), so the correct / wrong / solved sounds never played.
 *
 * <p>Sounds are addressed by type, not by matching a path: the old
 * {@code path.contains("correct.mp3")} check was also true for "incorrect.mp3".
 */
public class PuzzleSoundManager {
    private static final String TAG = "PuzzleSoundManager";

    private static final String[] CORRECT = {
            "sounds/game-actions/correct.mp3", "sounds/puzzle-correct.mp3", "sounds/puzzles/correct.mp3"};
    private static final String[] INCORRECT = {
            "sounds/game-actions/incorrect.mp3", "sounds/puzzle-incorrect.mp3", "sounds/puzzles/incorrect.mp3"};
    private static final String[] SOLVED = {
            "sounds/puzzles/puzzle-path/puzzle-path-correct.mp3", "sounds/puzzle-path-correct.mp3",
            "sounds/puzzles/puzzle-path/puzzle-solved.mp3", "sounds/game-actions/correct.mp3"};
    private static final String[] MOVE_SELF = {"sounds/game-actions/move-self.mp3", "sounds/move-self.mp3"};
    private static final String[] MOVE_OPPONENT = {
            "sounds/game-actions/move-opponent.mp3", "sounds/move-opponent.mp3", "sounds/game-actions/move-self.mp3"};
    private static final String[] CAPTURE = {"sounds/game-actions/capture.mp3", "sounds/capture.mp3"};

    private SoundPool soundPool;
    private int soundCorrectId = -1;
    private int soundIncorrectId = -1;
    private int soundSolvedId = -1;
    private int soundMoveSelfId = -1;
    private int soundMoveOpponentId = -1;
    private int soundCaptureId = -1;
    private final Context context;

    public PuzzleSoundManager(Context context) {
        this.context = context.getApplicationContext();
        initSoundPool();
    }

    private void initSoundPool() {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(attrs)
                    .build();

            AssetManager am = context.getAssets();
            soundCorrectId = load(am, CORRECT);
            soundIncorrectId = load(am, INCORRECT);
            soundSolvedId = load(am, SOLVED);
            soundMoveSelfId = load(am, MOVE_SELF);
            soundMoveOpponentId = load(am, MOVE_OPPONENT);
            soundCaptureId = load(am, CAPTURE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SoundPool", e);
        }
    }

    /** Loads the first candidate asset that exists; -1 if none does. */
    private int load(AssetManager am, String[] candidates) {
        for (String path : candidates) {
            try (AssetFileDescriptor afd = am.openFd(path)) {
                return soundPool.load(afd, 1);
            } catch (Exception ignored) {
                // try the next candidate
            }
        }
        Log.w(TAG, "No sound asset found for " + candidates[0]);
        return -1;
    }

    private void play(int soundId) {
        if (soundPool != null && soundId > 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public void playCorrect() {
        play(soundCorrectId);
    }

    public void playIncorrect() {
        play(soundIncorrectId);
    }

    public void playSolved() {
        play(soundSolvedId);
    }

    /** The user's own move. */
    public void playMoveSound(boolean isCapture) {
        play(isCapture ? soundCaptureId : soundMoveSelfId);
    }

    /** The puzzle's reply move (Chess.com uses a different sound for the opponent). */
    public void playOpponentMoveSound(boolean isCapture) {
        play(isCapture ? soundCaptureId : soundMoveOpponentId);
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}

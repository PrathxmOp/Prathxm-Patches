/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

public class PuzzleSoundManager {
    private static final String TAG = "PuzzleSoundManager";

    private SoundPool soundPool;
    private int soundCorrectId = -1;
    private int soundIncorrectId = -1;
    private int soundSolvedId = -1;
    private int soundMoveSelfId = -1;
    private int soundCaptureId = -1;
    private final Context context;

    public PuzzleSoundManager(Context context) {
        this.context = context.getApplicationContext();
        initSoundPool();
    }

    private void initSoundPool() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(5)
                        .setAudioAttributes(attrs)
                        .build();
            } else {
                soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
            }

            AssetManager am = context.getAssets();
            try {
                soundCorrectId = soundPool.load(am.openFd("sounds/puzzles/correct.mp3"), 1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sound: correct", e);
            }
            try {
                soundIncorrectId = soundPool.load(am.openFd("sounds/puzzles/incorrect.mp3"), 1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sound: incorrect", e);
            }
            try {
                soundSolvedId = soundPool.load(am.openFd("sounds/puzzles/puzzle-path/puzzle-solved.mp3"), 1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sound: solved", e);
            }
            try {
                soundMoveSelfId = soundPool.load(am.openFd("sounds/game-actions/move-self.mp3"), 1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sound: move-self", e);
            }
            try {
                soundCaptureId = soundPool.load(am.openFd("sounds/game-actions/capture.mp3"), 1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sound: capture", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SoundPool", e);
        }
    }

    public void playSound(String path) {
        if (soundPool == null) return;
        int soundId = -1;
        if (path.contains("correct.mp3")) {
            soundId = soundCorrectId;
        } else if (path.contains("incorrect.mp3")) {
            soundId = soundIncorrectId;
        } else if (path.contains("puzzle-solved.mp3")) {
            soundId = soundSolvedId;
        } else if (path.contains("move-self.mp3")) {
            soundId = soundMoveSelfId;
        } else if (path.contains("capture.mp3")) {
            soundId = soundCaptureId;
        }

        if (soundId != -1) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else {
            // Fallback
            try {
                AssetFileDescriptor afd = context.getAssets().openFd(path);
                MediaPlayer player = new MediaPlayer();
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.prepare();
                player.start();
                player.setOnCompletionListener(MediaPlayer::release);
            } catch (Exception e) {
                Log.e(TAG, "Failed to play sound (fallback): " + path, e);
            }
        }
    }

    public void playMoveSound(boolean isCapture) {
        if (isCapture) {
            playSound("sounds/game-actions/capture.mp3");
        } else {
            playSound("sounds/game-actions/move-self.mp3");
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}

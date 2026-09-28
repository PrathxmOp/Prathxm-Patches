/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class OverlayManager {
    private static final String TAG = "OverlayManager";

    public static void updateEvalBar(final float score, final boolean hasMate, final int mateIn, final Object stateImpl) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return;

                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardX = loc[0];
                    int boardY = loc[1];
                    int boardW = boardView.getWidth();
                    int boardH = boardView.getHeight();
                    if (boardW <= 0 || boardH <= 0) return;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int barWidth = (int) (12 * density);

                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    EvalBarView evalBarView;
                    if (evalBar instanceof EvalBarView) {
                        evalBarView = (EvalBarView) evalBar;
                    } else {
                        if (evalBar != null) decorView.removeView(evalBar);
                        evalBarView = new EvalBarView(decorView.getContext());
                        evalBarView.setTag("stockfish_eval_bar");
                        decorView.addView(evalBarView);
                    }
                    evalBarView.setVisibility(View.VISIBLE);
                    evalBarView.update(boardX, boardY, barWidth, boardH,
                                       score, hasMate, mateIn, isBoardFlipped(stateImpl));
                } catch (Throwable t) {
                    Log.e(TAG, "updateEvalBar failed: " + t.getMessage());
                }
            }
        });
    }

    public static void hideEvalBar() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    
                    View evalBar = decorView.findViewWithTag("stockfish_eval_bar");
                    if (evalBar != null) {
                        evalBar.setVisibility(View.GONE);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "hideEvalBar failed: " + t.getMessage());
                }
            }
        });
    }

    public static void updateWdlBar(final int wdlWin, final int wdlDraw, final int wdlLoss) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return;

                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardX = loc[0];
                    int boardY = loc[1];
                    int boardW = boardView.getWidth();
                    int boardH = boardView.getHeight();
                    if (boardW <= 0 || boardH <= 0) return;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int barHeight = (int) (14 * density);
                    int barY = boardY - barHeight - (int)(4 * density);

                    int btnW = (int) (INFO_SLOT_DP * density);
                    int barW = boardW - btnW - (int) (8 * density);

                    View wdlTag = decorView.findViewWithTag("stockfish_wdl_bar");
                    WdlBarView wdlBarView;
                    if (wdlTag instanceof WdlBarView) {
                        wdlBarView = (WdlBarView) wdlTag;
                    } else {
                        if (wdlTag != null) decorView.removeView(wdlTag);
                        wdlBarView = new WdlBarView(decorView.getContext());
                        wdlBarView.setTag("stockfish_wdl_bar");
                        decorView.addView(wdlBarView);
                    }
                    wdlBarView.setVisibility(View.VISIBLE);
                    wdlBarView.update(boardX, barY, barW, barHeight, wdlWin, wdlDraw, wdlLoss);
                } catch (Throwable t) {
                    Log.e(TAG, "updateWdlBar failed: " + t.getMessage());
                }
            }
        });
    }

    public static void hideWdlBar() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("stockfish_wdl_bar");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideWdlBar failed: " + t.getMessage());
                }
            }
        });
    }

    /** Width reserved to the right of the W/D/L bar (also used by the engine info line). */
    private static final int INFO_SLOT_DP = 110;

    /**
     * Shows "depth · score" in the slot above the board's top-right corner (next to the W/D/L
     * bar), e.g. "d22 · +0.35" or "d18 · M3".
     */
    public static void updateEngineInfo(final int depth, final float score, final boolean hasMate, final int mateIn) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || activity.getWindow() == null) return;
                    ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return;
                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardW = boardView.getWidth();
                    if (boardW <= 0) return;
                    float density = decorView.getContext().getResources().getDisplayMetrics().density;
                    int h = (int) (14 * density);
                    int w = (int) (INFO_SLOT_DP * density);
                    int y = loc[1] - h - (int) (4 * density);
                    if (y < 0) return;

                    View existing = decorView.findViewWithTag("stockfish_engine_info");
                    TextView info;
                    if (existing instanceof TextView) {
                        info = (TextView) existing;
                    } else {
                        if (existing != null) decorView.removeView(existing);
                        info = new TextView(decorView.getContext());
                        info.setTag("stockfish_engine_info");
                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(0xCC1B1A18);
                        bg.setCornerRadius(h / 3f);
                        info.setBackground(bg);
                        info.setTextColor(0xFFE3E3E3);
                        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
                        info.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
                        info.setGravity(Gravity.CENTER);
                        info.setIncludeFontPadding(false);
                        info.setSingleLine(true);
                        info.setLayoutParams(new FrameLayout.LayoutParams(w, h));
                        decorView.addView(info);
                    }
                    ViewGroup.LayoutParams lp = info.getLayoutParams();
                    if (lp.width != w || lp.height != h) {
                        lp.width = w;
                        lp.height = h;
                        info.setLayoutParams(lp);
                    }
                    info.setText(formatEngineInfo(depth, score, hasMate, mateIn));
                    info.setTranslationX(loc[0] + boardW - w);
                    info.setTranslationY(y);
                    info.setVisibility(View.VISIBLE);
                    info.bringToFront();
                } catch (Throwable t) {
                    Log.e(TAG, "updateEngineInfo failed: " + t.getMessage());
                }
            }
        });
    }

    /** "d22 · +0.35", "d18 · M3" / "d18 · -M2" (white's point of view). */
    static String formatEngineInfo(int depth, float score, boolean hasMate, int mateIn) {
        String eval;
        if (hasMate && mateIn != 0) {
            eval = (mateIn > 0 ? "M" : "-M") + Math.abs(mateIn);
        } else {
            eval = String.format(java.util.Locale.US, "%+.2f", score);
        }
        return (depth > 0 ? "d" + depth + " \u00B7 " : "") + eval;
    }

    public static void hideEngineInfo() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null || activity.getWindow() == null) return;
                    View v = activity.getWindow().getDecorView().findViewWithTag("stockfish_engine_info");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideEngineInfo failed: " + t.getMessage());
                }
            }
        });
    }

    public static void showMateAnnouncement(final int mateIn) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    ViewGroup decorView = (ViewGroup) window.getDecorView();
                    if (decorView == null) return;

                    View boardView = findChessBoardView(decorView);
                    if (boardView == null) return;

                    int[] loc = new int[2];
                    boardView.getLocationInWindow(loc);
                    int boardX = loc[0];
                    int boardY = loc[1];
                    int boardW = boardView.getWidth();
                    if (boardW <= 0) return;

                    float density = decorView.getContext().getResources().getDisplayMetrics().density;

                    View existing = decorView.findViewWithTag("stockfish_mate_banner");
                    TextView banner;
                    if (existing instanceof TextView) {
                        banner = (TextView) existing;
                    } else {
                        if (existing != null) decorView.removeView(existing);

                        banner = new TextView(decorView.getContext());
                        banner.setTag("stockfish_mate_banner");

                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(0xEE1B1B1B);
                        bg.setCornerRadius(24 * density);
                        bg.setStroke((int)(1.5f * density), 0xFF81B64C); // green border
                        banner.setBackground(bg);

                        banner.setTextColor(Color.WHITE);
                        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        banner.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
                        banner.setGravity(Gravity.CENTER);

                        int padH = (int)(16 * density);
                        int padV = (int)(8 * density);
                        banner.setPadding(padH, padV, padH, padV);

                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                        );
                        banner.setLayoutParams(lp);
                        decorView.addView(banner);
                    }

                    String sign = mateIn > 0 ? "♟ Mate in " : "☠ Opponent Mates in ";
                    banner.setText(sign + Math.abs(mateIn) + "!");

                    banner.measure(
                            View.MeasureSpec.makeMeasureSpec(boardW, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                    int bw = banner.getMeasuredWidth();
                    int bh = banner.getMeasuredHeight();
                    int centreX = boardX + (boardW - bw) / 2;
                    int offset = 0;
                    if (StockfishSettings.isWdlEnabled(decorView.getContext())
                            || StockfishSettings.isEngineInfoEnabled(decorView.getContext())) {
                        int barHeight = (int) (14 * density);
                        offset = barHeight + (int)(4 * density);
                    }
                    int topY = Math.max(0, boardY - bh - (int)(8 * density) - offset);

                    banner.setTranslationX(centreX);
                    banner.setTranslationY(topY);
                    banner.setVisibility(View.VISIBLE);
                    banner.bringToFront();

                } catch (Throwable t) {
                    Log.e(TAG, "showMateAnnouncement failed: " + t.getMessage());
                }
            }
        });
    }

    public static void hideMateAnnouncement() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Activity activity = StockfishExtension.getCurrentActivity();
                    if (activity == null) return;
                    Window window = activity.getWindow();
                    if (window == null) return;
                    View decorView = window.getDecorView();
                    if (decorView == null) return;
                    View v = decorView.findViewWithTag("stockfish_mate_banner");
                    if (v != null) v.setVisibility(View.GONE);
                } catch (Throwable t) {
                    Log.e(TAG, "hideMateAnnouncement failed: " + t.getMessage());
                }
            }
        });
    }

    public static View findChessBoardView(View view) {
        if (view == null) return null;
        if (view.getClass().getName().equals("com.chess.chessboard.view.ChessBoardView")) {
            if (view.getVisibility() == View.VISIBLE) {
                return view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findChessBoardView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public static boolean isBoardFlipped(Object stateImpl) {
        if (stateImpl == null) return false;
        try {
            for (Method m : stateImpl.getClass().getMethods()) {
                // 4.10.17: CBViewModelStateImpl.getFlipBoard() (also follows manual flips)
                String n = m.getName();
                if ((n.equals("getFlipBoard") || n.equals("isFlipped") || n.equals("getFlipped"))
                        && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    return (boolean) m.invoke(stateImpl);
                }
            }
            for (Field f : stateImpl.getClass().getDeclaredFields()) {
                if ((f.getName().equals("flipped") || f.getName().equals("isFlipped")) && f.getType() == boolean.class) {
                    f.setAccessible(true);
                    return f.getBoolean(stateImpl);
                }
            }
        } catch (Throwable ignored) {}
        
        Boolean isWhite = StockfishExtension.isUserWhite(stateImpl);
        return isWhite != null && !isWhite;
    }
}

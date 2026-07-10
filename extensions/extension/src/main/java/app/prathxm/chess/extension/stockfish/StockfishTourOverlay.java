/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class StockfishTourOverlay extends Dialog {

    public static void showTourIfNeeded(final Activity activity) {
        if (activity == null) return;
        
        // Only show the tour on the main HomeActivity
        String name = activity.getClass().getName();
        if (!name.contains("HomeActivity")) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!StockfishSettings.isTourShown(activity)) {
                    StockfishTourOverlay dialog = new StockfishTourOverlay(activity);
                    dialog.show();
                }
            }
        });
    }

    public StockfishTourOverlay(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        
        float density = getContext().getResources().getDisplayMetrics().density;
        
        // 1. Root Layout (Fullscreen FrameLayout with dark overlay)
        FrameLayout root = new FrameLayout(getContext());
        root.setBackgroundColor(0xCC12110F); // 80% opaque dark brown/grey matching Chess.com style
        
        // 2. Highlight Area (Top 100dp)
        View highlightView = new View(getContext());
        FrameLayout.LayoutParams highlightParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (100f * density)
        );
        highlightParams.gravity = Gravity.TOP;
        highlightView.setLayoutParams(highlightParams);
        
        // Create dashed green background
        GradientDrawable dashedBorder = new GradientDrawable();
        dashedBorder.setColor(0x1A81B64C); // 10% opaque green
        dashedBorder.setStroke((int) (2 * density), 0xFF81B64C, 10 * density, 5 * density); // dashed green stroke
        highlightView.setBackground(dashedBorder);
        root.addView(highlightView);
        
        // Add a text indicating "Logo Area" inside the highlightView or just below it
        TextView labelLogo = new TextView(getContext());
        labelLogo.setText("GESTURE LOGO AREA (TOP 100dp)");
        labelLogo.setTextColor(0xFF81B64C);
        labelLogo.setTextSize(11);
        labelLogo.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        labelLogo.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.gravity = Gravity.TOP;
        labelParams.topMargin = (int) (105f * density); // Just below highlight box
        labelLogo.setLayoutParams(labelParams);
        root.addView(labelLogo);

        // 3. Arrow or pointing indicator (simple text/arrow pointing up)
        TextView pointer = new TextView(getContext());
        pointer.setText("▲\n│");
        pointer.setTextColor(0xFF81B64C);
        pointer.setTextSize(18);
        pointer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pointerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pointerParams.gravity = Gravity.TOP;
        pointerParams.topMargin = (int) (125f * density);
        pointer.setLayoutParams(pointerParams);
        root.addView(pointer);
        
        // 4. Instructions Card
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding((int) (24 * density), (int) (24 * density), (int) (24 * density), (int) (24 * density));
        
        // Card background (Rounded rectangle, dark grey/brown)
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF262421); // Chess.com sidebar color
        cardBg.setCornerRadius(16 * density);
        cardBg.setStroke((int) (1 * density), 0xFF3E3C39);
        card.setBackground(cardBg);
        
        // Card layout params
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            (int) (320 * density), // fixed width 320dp
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = Gravity.CENTER_HORIZONTAL;
        cardParams.topMargin = (int) (175f * density);
        card.setLayoutParams(cardParams);
        
        // Title: Local Stockfish
        TextView titleTv = new TextView(getContext());
        titleTv.setText("Local Stockfish Gestures");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(20);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setGravity(Gravity.CENTER);
        card.addView(titleTv);
        
        // Subtitle
        TextView subtitleTv = new TextView(getContext());
        subtitleTv.setText("Since the engine runs locally offline, use these quick gestures on the Chess.com logo at the top of the screen:");
        subtitleTv.setTextColor(0xFFB0AEA9);
        subtitleTv.setTextSize(13);
        subtitleTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = (int) (12 * density);
        subParams.bottomMargin = (int) (20 * density);
        subtitleTv.setLayoutParams(subParams);
        card.addView(subtitleTv);
        
        // Feature 1: Tap & Hold
        LinearLayout row1 = createFeatureRow(
            "👆", 
            "Tap & Hold Logo", 
            "Opens the Stockfish settings menu to configure engine depth, MultiPV, evaluation bars, and ELO limits.",
            density
        );
        card.addView(row1);
        
        // Divider
        View divider = new View(getContext());
        divider.setBackgroundColor(0xFF3E3C39);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density)
        );
        divParams.topMargin = (int) (12 * density);
        divParams.bottomMargin = (int) (12 * density);
        divider.setLayoutParams(divParams);
        card.addView(divider);
        
        // Feature 2: Double-Tap
        LinearLayout row2 = createFeatureRow(
            "⚡", 
            "Double-Tap Logo", 
            "Instantly toggles Panic Mode (hides or shows the evaluation bar, threat arrows, and WDL indicators).",
            density
        );
        card.addView(row2);
        
        // Button: Got it!
        Button btn = new Button(getContext());
        btn.setText("Got it!");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setAllCaps(false);
        
        // Button background (Chess.com green rounded button)
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF81B64C);
        btnBg.setCornerRadius(8 * density);
        btn.setBackground(btnBg);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (46 * density)
        );
        btnParams.topMargin = (int) (24 * density);
        btn.setLayoutParams(btnParams);
        
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StockfishSettings.setTourShown(getContext(), true);
                dismiss();
            }
        });
        card.addView(btn);
        
        root.addView(card);
        setContentView(root);
        
        // Fade in animation
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(400).start();
    }
    
    private LinearLayout createFeatureRow(String emoji, String title, String desc, float density) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        
        // Emoji icon
        TextView icon = new TextView(getContext());
        icon.setText(emoji);
        icon.setTextSize(22);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconParams.rightMargin = (int) (12 * density);
        icon.setLayoutParams(iconParams);
        row.addView(icon);
        
        // Content container
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        
        // Title
        TextView titleTv = new TextView(getContext());
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(14);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(titleTv);
        
        // Description
        TextView descTv = new TextView(getContext());
        descTv.setText(desc);
        descTv.setTextColor(0xFF8B8985);
        descTv.setTextSize(12);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = (int) (2 * density);
        descTv.setLayoutParams(descParams);
        content.addView(descTv);
        
        row.addView(content);
        return row;
    }
}

/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import app.prathxm.chess.extension.BuildConfig;

public class StockfishSettingsDialog {

    public static void showSettingsMenu(Activity activity) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = activity.getResources().getDisplayMetrics().density;

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(0xFF262421); // Beautiful Chess.com board-dark background
        dialogBg.setCornerRadius(16 * density);
        dialog.getWindow().setBackgroundDrawable(dialogBg);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        int rootPadding = (int) (20 * density);
        rootLayout.setPadding(rootPadding, rootPadding, rootPadding, rootPadding);
        scrollView.addView(rootLayout);

        // Header Title
        TextView titleTv = new TextView(activity);
        titleTv.setText("Engine Settings");
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setTextSize(20);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setGravity(Gravity.CENTER);
        rootLayout.addView(titleTv);

        addDialogSpacer(rootLayout, 16, density);

        // 1. ENGINE
        addSectionHeader(rootLayout, "Engine", density, activity);

        CheckBox enabledCb = addStyledCheckbox(rootLayout, "Enable Stockfish", StockfishSettings.isEngineEnabled(activity), density, activity);
        addHint(rootLayout, "Double-tap the top bar to switch it off or on instantly (panic mode).", density, activity);

        int currentDepth = StockfishSettings.getDepth(activity);
        TextView depthLabel = addStyledLabel(rootLayout, "Analysis depth: " + currentDepth, density, activity);
        SeekBar depthSeekBar = addStyledSeekBar(rootLayout, depthLabel, "Analysis depth", currentDepth - 1, StockfishSettings.MAX_DEPTH - 1, 1, density, activity);
        addHint(rootLayout, "Higher is stronger but slower. 18–22 suits most phones. Arrows update while the engine thinks.", density, activity);

        // 2. ON THE BOARD
        addSectionHeader(rootLayout, "On the board", density, activity);
        CheckBox arrowsCb = addStyledCheckbox(rootLayout, "Best-move arrows", StockfishSettings.isArrowsVisible(activity), density, activity);
        CheckBox evalBarCb = addStyledCheckbox(rootLayout, "Evaluation bar", StockfishSettings.isEvalBarEnabled(activity), density, activity);
        CheckBox classifCb = addStyledCheckbox(rootLayout, "Rate each move (Best, Blunder…)", StockfishSettings.isMoveClassificationEnabled(activity), density, activity);

        addDialogSpacer(rootLayout, 12, density);

        // ADVANCED TOGGLE
        TextView advancedToggleBtn = new TextView(activity);
        advancedToggleBtn.setText("Show advanced settings ▾");
        advancedToggleBtn.setTextColor(0xFF81B64C); // Chess.com Green
        advancedToggleBtn.setTextSize(14);
        advancedToggleBtn.setGravity(Gravity.CENTER);
        advancedToggleBtn.setPadding(0, (int)(8*density), 0, (int)(8*density));
        advancedToggleBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        LinearLayout advancedLayout = new LinearLayout(activity);
        advancedLayout.setOrientation(LinearLayout.VERTICAL);
        advancedLayout.setVisibility(View.GONE);

        advancedToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isVisible = advancedLayout.getVisibility() == View.VISIBLE;
                advancedLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                advancedToggleBtn.setText(isVisible ? "Show advanced settings ▾" : "Hide advanced settings ▴");
            }
        });

        rootLayout.addView(advancedToggleBtn);
        rootLayout.addView(advancedLayout);

        // Arrows & overlays
        addSectionHeader(advancedLayout, "Arrows & overlays", density, activity);
        int currentPV = StockfishSettings.getMultiPV(activity);
        TextView pvLabel = addStyledLabel(advancedLayout, "Number of arrows: " + currentPV, density, activity);
        SeekBar pvSeekBar = addStyledSeekBar(advancedLayout, pvLabel, "Number of arrows", currentPV - 1, 4, 1, density, activity);
        addHint(advancedLayout, "Green is the best move, then blue, orange and purple. More arrows make each search a little slower.", density, activity);
        CheckBox sideCb = addStyledCheckbox(advancedLayout, "Arrows only on my turn", StockfishSettings.isMySideOnly(activity), density, activity);
        CheckBox threatCb = addStyledCheckbox(advancedLayout, "Threat arrow (opponent's best reply)", StockfishSettings.isThreatArrowsEnabled(activity), density, activity);
        CheckBox wdlCb = addStyledCheckbox(advancedLayout, "Win / Draw / Loss bar", StockfishSettings.isWdlEnabled(activity), density, activity);
        CheckBox infoCb = addStyledCheckbox(advancedLayout, "Depth & score above the board", StockfishSettings.isEngineInfoEnabled(activity), density, activity);
        CheckBox mateCb = addStyledCheckbox(advancedLayout, "Announce forced mates", StockfishSettings.isMateAnnouncementEnabled(activity), density, activity);
        CheckBox blunderCb = addStyledCheckbox(advancedLayout, "Vibrate on mistakes & blunders", StockfishSettings.isBlunderAlertsEnabled(activity), density, activity);
        addHint(advancedLayout, "Needs \"Rate each move\" to be on.", density, activity);

        // Performance
        addSectionHeader(advancedLayout, "Performance", density, activity);
        final int cpuCount = StockfishSettings.getCpuCount();
        int currentThreads = StockfishSettings.getThreads(activity);
        TextView threadsLabel = addStyledLabel(advancedLayout, "CPU threads: " + currentThreads + " of " + cpuCount, density, activity);
        SeekBar threadsSeekBar = addStyledSeekBar(advancedLayout, threadsLabel, "CPU threads", currentThreads - 1, Math.max(0, cpuCount - 1), 1, density, activity);
        final String threadsSuffix = " of " + cpuCount;
        threadsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                threadsLabel.setText("CPU threads: " + (prog + 1) + threadsSuffix);
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        addHint(advancedLayout, "All cores gives the fastest analysis. Lower it if your phone gets hot.", density, activity);

        // Game Review
        addSectionHeader(advancedLayout, "Game Review", density, activity);
        int currentBoost = StockfishSettings.getReviewDepthBoost(activity);
        TextView boostLabel = addStyledLabel(advancedLayout, "Extra review depth: +" + currentBoost, density, activity);
        SeekBar boostSeekBar = addStyledSeekBar(advancedLayout, boostLabel, "Extra review depth", currentBoost, 10, 0, density, activity);
        boostSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                boostLabel.setText("Extra review depth: +" + prog);
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        addHint(advancedLayout, "Added on top of the Fast / Standard / Deep / Maximum preset you choose in Game Review. Each +1 is more accurate and takes longer.", density, activity);

        // Engine strength
        addSectionHeader(advancedLayout, "Engine strength", density, activity);
        CheckBox eloCb = addStyledCheckbox(advancedLayout, "Limit strength to an Elo", StockfishSettings.isLimitStrength(activity), density, activity);
        int currentElo = Math.max(1320, Math.min(3190, StockfishSettings.getElo(activity)));
        TextView eloLabel = addStyledLabel(advancedLayout, "Engine Elo: " + currentElo, density, activity);
        SeekBar eloSeekBar = addStyledSeekBar(advancedLayout, eloLabel, "Engine Elo", currentElo - 1320, 3190 - 1320, 1320, density, activity);
        addHint(advancedLayout, "Only changes the live arrows. Game Review always runs at full strength.", density, activity);

        eloLabel.setEnabled(eloCb.isChecked());
        eloSeekBar.setEnabled(eloCb.isChecked());
        eloLabel.setAlpha(eloCb.isChecked() ? 1f : 0.4f);
        eloSeekBar.setAlpha(eloCb.isChecked() ? 1f : 0.4f);
        eloCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                eloLabel.setEnabled(isChecked);
                eloSeekBar.setEnabled(isChecked);
                eloLabel.setAlpha(isChecked ? 1f : 0.4f);
                eloSeekBar.setAlpha(isChecked ? 1f : 0.4f);
            }
        });

        // Reset
        addDialogSpacer(advancedLayout, 8, density);
        TextView resetBtn = new TextView(activity);
        resetBtn.setText("Reset engine settings to defaults");
        resetBtn.setTextColor(0xFFE15554);
        resetBtn.setTextSize(13);
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
        resetBtn.setOnClickListener(v -> new android.app.AlertDialog.Builder(activity)
                .setTitle("Reset engine settings?")
                .setMessage("Depth, arrows, overlays, threads, review depth and strength go back to their defaults.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    StockfishSettings.resetToDefaults(activity);
                    Object st = StockfishExtension.getStateImpl();
                    ArrowInjector.clearEngineArrows(st);
                    OverlayManager.hideEvalBar();
                    OverlayManager.hideWdlBar();
                    OverlayManager.hideMateAnnouncement();
                    OverlayManager.hideEngineInfo();
                    StockfishExtension.triggerAnalysisForCurrentState();
                    Toast.makeText(activity, "Engine settings reset", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .show());
        advancedLayout.addView(resetBtn);

        addDialogSpacer(rootLayout, 16, density);

        // 3. ABOUT & CREDITS
        LinearLayout creditsCard = new LinearLayout(activity);
        creditsCard.setOrientation(LinearLayout.VERTICAL);
        creditsCard.setPadding(0, (int) (16 * density), 0, (int) (8 * density));

        TextView devTv = new TextView(activity);
        devTv.setText("Prathxm Patches");
        devTv.setTextColor(0xFF81B64C); // Chess.com Green accent
        devTv.setTextSize(14);
        devTv.setGravity(Gravity.CENTER);
        devTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        devTv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                StockfishExtension.isDeveloperMode = !StockfishExtension.isDeveloperMode;
                Toast.makeText(activity, "Developer Mode: " + (StockfishExtension.isDeveloperMode ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        creditsCard.addView(devTv);

        addDialogSpacer(creditsCard, 2, density);

        TextView engineTv = new TextView(activity);
        engineTv.setText("Engine: Stockfish 19 NNUE · offline");
        engineTv.setTextColor(0xFF8B8985);
        engineTv.setTextSize(11);
        engineTv.setGravity(Gravity.CENTER);
        creditsCard.addView(engineTv);

        addDialogSpacer(creditsCard, 2, density);

        String versionText = "v" + BuildConfig.PATCH_VERSION;

        TextView patchTv = new TextView(activity);
        patchTv.setText("Patches " + versionText);
        patchTv.setTextColor(0xFF8B8985);
        patchTv.setTextSize(11);
        patchTv.setGravity(Gravity.CENTER);
        creditsCard.addView(patchTv);

        rootLayout.addView(creditsCard);

        addDialogSpacer(rootLayout, 16, density);

        // Buttons Layout
        LinearLayout buttonLayout = new LinearLayout(activity);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END);

        TextView cancelBtn = new TextView(activity);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(0xFFB0B0B0);
        cancelBtn.setTextSize(16);
        cancelBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cancelBtn.setGravity(Gravity.CENTER);
        int btnPaddingH = (int) (16 * density);
        int btnPaddingV = (int) (10 * density);
        cancelBtn.setPadding(btnPaddingH, btnPaddingV, btnPaddingH, btnPaddingV);
        cancelBtn.setFocusable(true);
        cancelBtn.setClickable(true);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        TextView saveBtn = new TextView(activity);
        saveBtn.setText("Save");
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setTextSize(16);
        saveBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(btnPaddingH, btnPaddingV, btnPaddingH, btnPaddingV);
        
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(0xFF81B64C); // Chess.com Green accent
        saveBg.setCornerRadius(8 * density);
        saveBtn.setBackground(saveBg);
        saveBtn.setFocusable(true);
        saveBtn.setClickable(true);
        
        saveBtn.setOnClickListener(v -> {
            StockfishSettings.setEngineEnabled(activity, enabledCb.isChecked());
            StockfishSettings.setDepth(activity, Math.max(1, depthSeekBar.getProgress() + 1));
            StockfishSettings.setMultiPV(activity, Math.max(1, pvSeekBar.getProgress() + 1));
            int threads = Math.max(1, threadsSeekBar.getProgress() + 1);
            // Store 0 (= auto, all cores) when the maximum is selected.
            StockfishSettings.setThreads(activity, threads >= cpuCount ? 0 : threads);
            StockfishSettings.setReviewDepthBoost(activity, boostSeekBar.getProgress());
            StockfishSettings.setMySideOnly(activity, sideCb.isChecked());
            StockfishSettings.setLimitStrength(activity, eloCb.isChecked());
            StockfishSettings.setElo(activity, 1320 + eloSeekBar.getProgress());
            StockfishSettings.setPremiumEnabled(activity, true);
            StockfishSettings.setArrowsVisible(activity, arrowsCb.isChecked());
            StockfishSettings.setEvalBarEnabled(activity, evalBarCb.isChecked());
            StockfishSettings.setWdlEnabled(activity, wdlCb.isChecked());
            StockfishSettings.setEngineInfoEnabled(activity, infoCb.isChecked());
            StockfishSettings.setThreatArrowsEnabled(activity, threatCb.isChecked());
            StockfishSettings.setMoveClassificationEnabled(activity, classifCb.isChecked());
            StockfishSettings.setBlunderAlertsEnabled(activity, blunderCb.isChecked());
            StockfishSettings.setMateAnnouncementEnabled(activity, mateCb.isChecked());
            if (!mateCb.isChecked()) OverlayManager.hideMateAnnouncement();

            Toast.makeText(activity, "Settings saved", Toast.LENGTH_SHORT).show();

            // Apply immediately: hide whatever was switched off, then re-run the current
            // position so everything that is on shows up without having to make a move.
            Object state = StockfishExtension.getStateImpl();
            if (!enabledCb.isChecked() || !arrowsCb.isChecked()) {
                ArrowInjector.clearEngineArrows(state);
            }
            if (!enabledCb.isChecked() || !evalBarCb.isChecked()) OverlayManager.hideEvalBar();
            if (!enabledCb.isChecked() || !wdlCb.isChecked()) OverlayManager.hideWdlBar();
            if (!enabledCb.isChecked() || !infoCb.isChecked()) OverlayManager.hideEngineInfo();
            if (!enabledCb.isChecked()) OverlayManager.hideMateAnnouncement();
            if (enabledCb.isChecked()) {
                StockfishExtension.triggerAnalysisForCurrentState();
            }

            dialog.dismiss();
        });

        buttonLayout.addView(cancelBtn);
        View btnSpacer = new View(activity);
        btnSpacer.setLayoutParams(new LinearLayout.LayoutParams((int) (12 * density), 1));
        buttonLayout.addView(btnSpacer);
        buttonLayout.addView(saveBtn);

        rootLayout.addView(buttonLayout);

        dialog.setContentView(scrollView);
        dialog.show();

        int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f);
        dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void addSectionHeader(LinearLayout layout, String title, float density, Activity activity) {
        TextView header = new TextView(activity);
        header.setText(title.toUpperCase());
        header.setTextColor(0xFF81B64C); // Chess.com Green accent
        header.setTextSize(12);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.setPadding(0, (int) (12 * density), 0, (int) (4 * density));
        layout.addView(header);
    }

    private static CheckBox addStyledCheckbox(LinearLayout layout, String labelText, boolean checked, float density, Activity activity) {
        CheckBox cb = new CheckBox(activity);
        cb.setText(labelText);
        cb.setTextColor(0xFFE3E3E3);
        cb.setTextSize(15);
        cb.setChecked(checked);
        cb.setPadding((int) (8 * density), (int) (8 * density), 0, (int) (8 * density));
        if (Build.VERSION.SDK_INT >= 21) {
            cb.setButtonTintList(ColorStateList.valueOf(0xFF81B64C));
        }
        layout.addView(cb);
        return cb;
    }

    private static TextView addStyledLabel(LinearLayout layout, String text, float density, Activity activity) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(0xFFE3E3E3);
        label.setTextSize(14);
        label.setPadding(0, (int) (8 * density), 0, 0);
        layout.addView(label);
        return label;
    }

    private static SeekBar addStyledSeekBar(LinearLayout layout, final TextView labelTv, final String labelPrefix, int progress, int max, final int minVal, float density, Activity activity) {
        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        if (Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(ColorStateList.valueOf(0xFF81B64C));
            seekBar.setThumbTintList(ColorStateList.valueOf(0xFF81B64C));
        }
        seekBar.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (12 * density));
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                int val = Math.max(minVal, prog + minVal);
                labelTv.setText(labelPrefix + ": " + val);
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        
        layout.addView(seekBar);
        return seekBar;
    }

    private static TextView addHint(LinearLayout layout, String text, float density, Activity activity) {
        TextView hint = new TextView(activity);
        hint.setText(text);
        hint.setTextColor(0xFF8B8985);
        hint.setTextSize(12);
        hint.setPadding((int) (8 * density), 0, (int) (8 * density), (int) (6 * density));
        layout.addView(hint);
        return hint;
    }

    private static void addDialogSpacer(LinearLayout layout, int dpHeight, float density) {
        View spacer = new View(layout.getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (dpHeight * density)
        ));
        layout.addView(spacer);
    }
}

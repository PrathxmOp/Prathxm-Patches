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

        // 1. MAIN SETTINGS SECTION
        addSectionHeader(rootLayout, "Main Settings", density, activity);

        CheckBox enabledCb = addStyledCheckbox(rootLayout, "Enable Stockfish Engine", StockfishSettings.isEngineEnabled(activity), density, activity);

        int currentDepth = StockfishSettings.getDepth(activity);
        TextView depthLabel = addStyledLabel(rootLayout, "Analysis Depth: " + currentDepth, density, activity);
        SeekBar depthSeekBar = addStyledSeekBar(rootLayout, depthLabel, "Analysis Depth", currentDepth - 1, 19, 1, density, activity);

        CheckBox arrowsCb = addStyledCheckbox(rootLayout, "Show Best Move Arrows", StockfishSettings.isArrowsVisible(activity), density, activity);
        CheckBox evalBarCb = addStyledCheckbox(rootLayout, "Show Evaluation Bar", StockfishSettings.isEvalBarEnabled(activity), density, activity);
        CheckBox classifCb = addStyledCheckbox(rootLayout, "Show Move Classification", StockfishSettings.isMoveClassificationEnabled(activity), density, activity);
        CheckBox adsCb = addStyledCheckbox(rootLayout, "Remove Ads Globally", StockfishSettings.isAdsRemoved(activity), density, activity);

        addDialogSpacer(rootLayout, 16, density);

        // 2. ADVANCED SETTINGS TOGGLE
        TextView advancedToggleBtn = new TextView(activity);
        advancedToggleBtn.setText("⚙️ Show Advanced Settings");
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
                advancedToggleBtn.setText(isVisible ? "⚙️ Show Advanced Settings" : "⚙️ Hide Advanced Settings");
            }
        });
        
        rootLayout.addView(advancedToggleBtn);
        rootLayout.addView(advancedLayout);

        addDialogSpacer(advancedLayout, 8, density);
        
        // Advanced toggles
        int currentPV = StockfishSettings.getMultiPV(activity);
        TextView pvLabel = addStyledLabel(advancedLayout, "MultiPV (Best moves): " + currentPV, density, activity);
        SeekBar pvSeekBar = addStyledSeekBar(advancedLayout, pvLabel, "MultiPV (Best moves to show)", currentPV - 1, 4, 1, density, activity);

        CheckBox sideCb = addStyledCheckbox(advancedLayout, "Show Arrows Only on My Turn", StockfishSettings.isMySideOnly(activity), density, activity);
        
        CheckBox eloCb = addStyledCheckbox(advancedLayout, "Limit Engine Elo Strength", StockfishSettings.isLimitStrength(activity), density, activity);
        int currentElo = StockfishSettings.getElo(activity);
        TextView eloLabel = addStyledLabel(advancedLayout, "Engine Elo: " + currentElo, density, activity);
        SeekBar eloSeekBar = addStyledSeekBar(advancedLayout, eloLabel, "Engine Elo", currentElo - 1350, 1500, 1350, density, activity);
        
        eloLabel.setEnabled(eloCb.isChecked());
        eloSeekBar.setEnabled(eloCb.isChecked());
        eloCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                eloLabel.setEnabled(isChecked);
                eloSeekBar.setEnabled(isChecked);
            }
        });

        CheckBox wdlCb = addStyledCheckbox(advancedLayout, "Show Win/Draw/Loss Bar", StockfishSettings.isWdlEnabled(activity), density, activity);
        CheckBox threatCb = addStyledCheckbox(advancedLayout, "Show Threat Arrows", StockfishSettings.isThreatArrowsEnabled(activity), density, activity);
        CheckBox blunderCb = addStyledCheckbox(advancedLayout, "Vibrate on Blunders", StockfishSettings.isBlunderAlertsEnabled(activity), density, activity);
        CheckBox mateCb = addStyledCheckbox(advancedLayout, "Show Mate Announcement", StockfishSettings.isMateAnnouncementEnabled(activity), density, activity);

        addDialogSpacer(rootLayout, 16, density);

        // 3. ABOUT & CREDITS
        LinearLayout creditsCard = new LinearLayout(activity);
        creditsCard.setOrientation(LinearLayout.VERTICAL);
        creditsCard.setPadding(0, (int) (16 * density), 0, (int) (8 * density));

        TextView devTv = new TextView(activity);
        devTv.setText("Developed by PrathxmOp ✨");
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
        engineTv.setText("Engine: Stockfish 16.1 NNUE");
        engineTv.setTextColor(0xFF8B8985);
        engineTv.setTextSize(11);
        engineTv.setGravity(Gravity.CENTER);
        creditsCard.addView(engineTv);

        addDialogSpacer(creditsCard, 2, density);

        String versionText = "v" + BuildConfig.PATCH_VERSION;

        TextView patchTv = new TextView(activity);
        patchTv.setText("Patch Version: " + versionText + " 🧩");
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
            StockfishSettings.setMySideOnly(activity, sideCb.isChecked());
            StockfishSettings.setLimitStrength(activity, eloCb.isChecked());
            StockfishSettings.setElo(activity, 1350 + eloSeekBar.getProgress());
            StockfishSettings.setAdsRemoved(activity, adsCb.isChecked());
            StockfishSettings.setPremiumEnabled(activity, true);
            StockfishSettings.setArrowsVisible(activity, arrowsCb.isChecked());
            StockfishSettings.setEvalBarEnabled(activity, evalBarCb.isChecked());
            StockfishSettings.setWdlEnabled(activity, wdlCb.isChecked());
            StockfishSettings.setThreatArrowsEnabled(activity, threatCb.isChecked());
            StockfishSettings.setMoveClassificationEnabled(activity, classifCb.isChecked());
            StockfishSettings.setBlunderAlertsEnabled(activity, blunderCb.isChecked());
            StockfishSettings.setMateAnnouncementEnabled(activity, mateCb.isChecked());
            if (!mateCb.isChecked()) OverlayManager.hideMateAnnouncement();

            Toast.makeText(activity, "Settings Saved", Toast.LENGTH_SHORT).show();
            
            if (!enabledCb.isChecked() || !arrowsCb.isChecked()) {
                ArrowInjector.clearEngineArrows(StockfishExtension.getStateImpl());
            } else {
                StockfishExtension.triggerAnalysisForCurrentState();
            }
            if (!evalBarCb.isChecked()) {
                OverlayManager.hideEvalBar();
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

    private static void addDialogSpacer(LinearLayout layout, int dpHeight, float density) {
        View spacer = new View(layout.getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (dpHeight * density)
        ));
        layout.addView(spacer);
    }
}

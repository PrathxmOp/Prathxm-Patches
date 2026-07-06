/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.crash;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import app.prathxm.chess.extension.BuildConfig;

public class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve error log
        String errorLog = getIntent().getStringExtra("error_log");
        if (errorLog == null) {
            errorLog = "No crash log found.";
        }

        // Build diagnostic info
        String appVersion = "Unknown";
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        String patchVersion = BuildConfig.PATCH_VERSION;
        String deviceModel = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;
        String sdkVersion = String.valueOf(Build.VERSION.SDK_INT);

        final String diagnosticInfo = "--- DIAGNOSTIC INFO ---\n" +
                "App Version: " + appVersion + "\n" +
                "Patch Version: " + patchVersion + "\n" +
                "Device: " + deviceModel + "\n" +
                "Android Version: " + androidVersion + " (SDK " + sdkVersion + ")\n" +
                "---------------------\n\n" +
                errorLog;

        // Set up root layout with sleek dark mode background
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212")); // Sleek dark mode
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        // Header Title (Warning icon + text)
        TextView headerIcon = new TextView(this);
        headerIcon.setText("⚠️");
        headerIcon.setTextSize(48);
        headerIcon.setGravity(Gravity.CENTER);
        root.addView(headerIcon);

        TextView title = new TextView(this);
        title.setText("Chess.com Crashed Unexpectedly");
        title.setTextColor(Color.parseColor("#E74C3C")); // Elegant red warning color
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dpToPx(8), 0, dpToPx(16));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("A crash occurred in one of the active patches or app threads. Below is the error log to help developers debug the issue.");
        subtitle.setTextColor(Color.parseColor("#B3B3B3")); // Muted grey
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dpToPx(24));
        root.addView(subtitle);

        // Code-block container for the crash log
        LinearLayout logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(Color.parseColor("#1A1A1A")); // Card dark background
        logBg.setCornerRadius(dpToPx(12));
        logBg.setStroke(dpToPx(1.5f), Color.parseColor("#2E2E2E")); // Thin grey border
        logContainer.setBackground(logBg);
        logContainer.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        logLp.bottomMargin = dpToPx(24);
        logContainer.setLayoutParams(logLp);

        // Horizontal and vertical scrolling for long stack traces
        ScrollView logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView logView = new TextView(this);
        logView.setText(diagnosticInfo);
        logView.setTextColor(Color.parseColor("#3498DB")); // Code-style blue color
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setHorizontallyScrolling(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        
        hScroll.addView(logView);
        logScroll.addView(hScroll);
        logContainer.addView(logScroll);
        root.addView(logContainer);

        // Actions panel for buttons
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Copy button
        Button btnCopy = new Button(this);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dpToPx(48), 1.0f);
        copyParams.rightMargin = dpToPx(8);
        btnCopy.setLayoutParams(copyParams);
        btnCopy.setText("📋 Copy Logs");
        btnCopy.setTextColor(Color.WHITE);
        btnCopy.setTextSize(14);
        btnCopy.setTypeface(Typeface.DEFAULT_BOLD);
        
        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setColor(Color.parseColor("#34495E")); // Sleek blue-grey
        copyBg.setCornerRadius(dpToPx(8));
        btnCopy.setBackground(copyBg);
        
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Chess App Crash Log", diagnosticInfo));
                Toast.makeText(this, "Crash log copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });
        buttonsLayout.addView(btnCopy);

        // Report issue button
        Button btnReport = new Button(this);
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(0, dpToPx(48), 1.2f);
        reportParams.leftMargin = dpToPx(8);
        btnReport.setLayoutParams(reportParams);
        btnReport.setText("🐙 Report Issue");
        btnReport.setTextColor(Color.WHITE);
        btnReport.setTextSize(14);
        btnReport.setTypeface(Typeface.DEFAULT_BOLD);
        
        GradientDrawable reportBg = new GradientDrawable();
        reportBg.setColor(Color.parseColor("#27AE60")); // Premium green button
        reportBg.setCornerRadius(dpToPx(8));
        btnReport.setBackground(reportBg);

        final String finalAppVersion = appVersion;
        btnReport.setOnClickListener(v -> {
            // First copy logs to clipboard
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Chess App Crash Log", diagnosticInfo));
            }
            
            // Generate GitHub Issue URL with parameters
            String githubUrl = "https://github.com/PrathxmOp/Prathxm-Patches/issues/new";
            String titleParam = "[Crash Report] " + finalAppVersion + " Unexpected Crash";
            String bodyParam = "### Describe the bug\nA crash occurred unexpectedly.\n\n" +
                    "### Stack Trace\n" +
                    "*(Logs copied to clipboard. Paste them here)*\n\n" +
                    "```\n" +
                    diagnosticInfo + "\n" +
                    "```";
            
            try {
                githubUrl += "?title=" + URLEncoder.encode(titleParam, "UTF-8") +
                        "&body=" + URLEncoder.encode(bodyParam, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {}
            
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        buttonsLayout.addView(btnReport);

        root.addView(buttonsLayout);
        setContentView(root);
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}

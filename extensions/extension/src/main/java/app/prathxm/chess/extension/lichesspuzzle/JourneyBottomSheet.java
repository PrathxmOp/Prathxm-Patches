/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class JourneyBottomSheet {

    public interface Listener {
        void onThemeSelected(String mode, String themeKey);
        void onSolveJourneyClicked();
    }

    private final Activity activity;
    private final FrameLayout parentContainer;
    private final LichessPuzzleDatabaseHelper dbHelper;
    private final Typeface fontBold;
    private final Typeface fontRegular;
    private final Listener listener;

    private View dimOverlay;
    private LinearLayout bottomSheetLayout;
    private LinearLayout themeScrollContent;
    private final Map<String, TextView> themeCountViews = new HashMap<>();

    private final int COLOR_GREEN = Color.parseColor("#81B64C");
    private final int COLOR_TEXT_PRIMARY = Color.WHITE;
    private final int COLOR_TEXT_SECONDARY = Color.parseColor("#A0A0A5");

    public JourneyBottomSheet(Activity activity, FrameLayout parentContainer, LichessPuzzleDatabaseHelper dbHelper,
                              Typeface fontBold, Typeface fontRegular, Listener listener) {
        this.activity = activity;
        this.parentContainer = parentContainer;
        this.dbHelper = dbHelper;
        this.fontBold = fontBold;
        this.fontRegular = fontRegular;
        this.listener = listener;

        setupViews();
    }

    private void setupViews() {
        int margin16 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, activity.getResources().getDisplayMetrics());

        // Dimming Overlay
        dimOverlay = new View(activity);
        dimOverlay.setBackgroundColor(Color.parseColor("#90000000"));
        dimOverlay.setVisibility(View.GONE);
        dimOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        dimOverlay.setOnClickListener(v -> dismiss());
        parentContainer.addView(dimOverlay);

        // Bottom Sheet Layout
        bottomSheetLayout = new LinearLayout(activity);
        bottomSheetLayout.setOrientation(LinearLayout.VERTICAL);
        bottomSheetLayout.setVisibility(View.GONE);
        
        float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics());
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#22211F"));
        sheetBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bottomSheetLayout.setBackground(sheetBg);
        bottomSheetLayout.setPadding(margin16, margin16, margin16, 0);
        
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int sheetHeight = (int) (dm.heightPixels * 0.75);

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                sheetHeight,
                Gravity.BOTTOM
        );
        bottomSheetLayout.setLayoutParams(sheetParams);

        // Header Title
        TextView sheetTitle = new TextView(activity);
        sheetTitle.setText("Puzzle Themes & Practice");
        sheetTitle.setTextColor(Color.WHITE);
        sheetTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) sheetTitle.setTypeface(fontBold);
        LinearLayout.LayoutParams sheetTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sheetTitleLp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics());
        sheetTitle.setLayoutParams(sheetTitleLp);
        bottomSheetLayout.addView(sheetTitle);

        // Scrollable Themes Area
        ScrollView sheetScrollView = new ScrollScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        sheetScrollView.setLayoutParams(scrollLp);
        
        themeScrollContent = new LinearLayout(activity);
        themeScrollContent.setOrientation(LinearLayout.VERTICAL);
        themeScrollContent.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        populateThemeList();

        sheetScrollView.addView(themeScrollContent);
        bottomSheetLayout.addView(sheetScrollView);

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics())
        );
        spacer.setLayoutParams(spacerLp);
        bottomSheetLayout.addView(spacer);
        
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomRowLp.bottomMargin = margin16;
        bottomRow.setLayoutParams(bottomRowLp);
        
        Button closeBtn = new Button(activity);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) closeBtn.setTypeface(fontBold);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#2D2B29"));
        closeBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, activity.getResources().getDisplayMetrics()));
        closeBtn.setBackground(closeBg);
        int closeSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52, activity.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeSize, closeSize);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> dismiss());
        bottomRow.addView(closeBtn);
        
        Button innerSolveBtn = new Button(activity);
        innerSolveBtn.setText("Solve Journey Puzzles");
        innerSolveBtn.setTextColor(Color.WHITE);
        innerSolveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (fontBold != null) innerSolveBtn.setTypeface(fontBold);
        innerSolveBtn.setAllCaps(false);
        GradientDrawable innerSolveBg = new GradientDrawable();
        innerSolveBg.setColor(COLOR_GREEN);
        innerSolveBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, activity.getResources().getDisplayMetrics()));
        innerSolveBtn.setBackground(innerSolveBg);
        LinearLayout.LayoutParams innerSolveLp = new LinearLayout.LayoutParams(
                0, closeSize, 1.0f
        );
        innerSolveLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics());
        innerSolveBtn.setLayoutParams(innerSolveLp);
        innerSolveBtn.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onSolveJourneyClicked();
            }
        });
        bottomRow.addView(innerSolveBtn);
        bottomSheetLayout.addView(bottomRow);
        
        parentContainer.addView(bottomSheetLayout);
    }

    public void show() {
        if (bottomSheetLayout.getVisibility() == View.VISIBLE) return;
        
        populateThemeList();
        
        dimOverlay.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(250);
        fadeIn.setFillAfter(true);
        dimOverlay.startAnimation(fadeIn);
        
        bottomSheetLayout.setVisibility(View.VISIBLE);
        TranslateAnimation slideUp = new TranslateAnimation(
                0, 0,
                bottomSheetLayout.getHeight() > 0 ? bottomSheetLayout.getHeight() : 1000, 0
        );
        slideUp.setDuration(250);
        slideUp.setInterpolator(new DecelerateInterpolator());
        bottomSheetLayout.startAnimation(slideUp);
    }

    public void dismiss() {
        if (bottomSheetLayout.getVisibility() != View.VISIBLE) return;
        
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(200);
        fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override public void onAnimationStart(android.view.animation.Animation animation) {}
            @Override public void onAnimationRepeat(android.view.animation.Animation animation) {}
            @Override public void onAnimationEnd(android.view.animation.Animation animation) {
                dimOverlay.setVisibility(View.GONE);
            }
        });
        dimOverlay.startAnimation(fadeOut);
        
        TranslateAnimation slideDown = new TranslateAnimation(
                0, 0,
                0, bottomSheetLayout.getHeight()
        );
        slideDown.setDuration(200);
        slideDown.setInterpolator(new AccelerateInterpolator());
        slideDown.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override public void onAnimationStart(android.view.animation.Animation animation) {}
            @Override public void onAnimationRepeat(android.view.animation.Animation animation) {}
            @Override public void onAnimationEnd(android.view.animation.Animation animation) {
                bottomSheetLayout.setVisibility(View.GONE);
            }
        });
        bottomSheetLayout.startAnimation(slideDown);
    }

    private void addThemeCategoryHeader(LinearLayout parent, String title) {
        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(Color.parseColor("#81B64C"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontBold != null) header.setTypeface(fontBold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, activity.getResources().getDisplayMetrics());
        lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics());
        header.setLayoutParams(lp);
        parent.addView(header);
    }

    private void populateThemeList() {
        if (themeScrollContent == null) return;
        themeScrollContent.removeAllViews();
        themeCountViews.clear();

        // Category: Recommended
        addThemeCategoryHeader(themeScrollContent, "Recommended");
        addThemeRow(themeScrollContent, "Daily Puzzle", "📅", "Complete today's official daily puzzle.", "", "daily", null);
        addThemeRow(themeScrollContent, "Healthy mix", "🎯", "A mix of everything. You don't know what to expect, so be ready for anything! Just like in real games.", "", "theme", "healthyMix");
        addThemeRow(themeScrollContent, "Puzzle Rush", "⚡", "Solve as many puzzles as you can in 3 minutes! 3 strikes and you are out.", "", "rush", null);
        addThemeRow(themeScrollContent, "Puzzle Battle", "⚔️", "Race against a computer opponent to solve more puzzles in 3 minutes!", "", "battle", null);

        // Category: Phases
        addThemeCategoryHeader(themeScrollContent, "Phases");
        addThemeRow(themeScrollContent, "Opening", "♟️", "A tactic during the first phase of the game.", "", "theme", "opening");
        addThemeRow(themeScrollContent, "Middlegame", "⚔️", "A tactic during the second phase of the game.", "", "theme", "middlegame");
        addThemeRow(themeScrollContent, "Endgame", "👑", "A tactic during the last phase of the game.", "", "theme", "endgame");

        // Category: Endgame Types
        addThemeCategoryHeader(themeScrollContent, "Endgame Types");
        addThemeRow(themeScrollContent, "Rook endgame", "♜", "An endgame with only rooks and pawns.", "", "theme", "rookEndgame");
        addThemeRow(themeScrollContent, "Bishop endgame", "♝", "An endgame with only bishops and pawns.", "", "theme", "bishopEndgame");
        addThemeRow(themeScrollContent, "Pawn endgame", "♙", "An endgame with only pawns.", "", "theme", "pawnEndgame");
        addThemeRow(themeScrollContent, "Knight endgame", "♞", "An endgame with only knights and pawns.", "", "theme", "knightEndgame");
        addThemeRow(themeScrollContent, "Queen endgame", "♛", "An endgame with only queens and pawns.", "", "theme", "queenEndgame");
        addThemeRow(themeScrollContent, "Queen and Rook", "🏰", "An endgame with only queens, rooks, and pawns.", "", "theme", "queenRookEndgame");

        loadThemeCountsAsync();
    }

    private void loadThemeCountsAsync() {
        if (dbHelper == null || themeCountViews.isEmpty()) return;
        new Thread(() -> {
            final Map<String, String> results = new HashMap<>();
            android.content.SharedPreferences.Editor editor = activity.getSharedPreferences("lichess_puzzle_prefs", Context.MODE_PRIVATE).edit();
            for (String themeKey : themeCountViews.keySet()) {
                int localCount = dbHelper.getThemeCount(themeKey);
                String formatted = java.text.NumberFormat.getInstance().format(localCount);
                results.put(themeKey, formatted);
                editor.putString("cached_count_" + themeKey, formatted);
            }
            editor.apply();
            activity.runOnUiThread(() -> {
                for (Map.Entry<String, String> entry : results.entrySet()) {
                    TextView tv = themeCountViews.get(entry.getKey());
                    if (tv != null) {
                        tv.setText(entry.getValue());
                    }
                }
            });
        }).start();
    }

    private void addThemeRow(LinearLayout parent, String name, String emoji, String description, String count, String mode, String themeKey) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics())
        );

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics());
        row.setLayoutParams(rowLp);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#2D2B29"));
        rowBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, activity.getResources().getDisplayMetrics()));
        row.setBackground(rowBg);
        row.setClickable(true);
        row.setFocusable(true);

        TextView emojiView = new TextView(activity);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emojiLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics());
        emojiView.setLayoutParams(emojiLp);
        row.addView(emojiView);

        LinearLayout textContainer = new LinearLayout(activity);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        textContainer.setLayoutParams(textLp);

        TextView titleView = new TextView(activity);
        titleView.setText(name);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (fontBold != null) titleView.setTypeface(fontBold);
        textContainer.addView(titleView);

        if (description != null && !description.isEmpty()) {
            TextView descView = new TextView(activity);
            descView.setText(description);
            descView.setTextColor(COLOR_TEXT_SECONDARY);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            if (fontRegular != null) descView.setTypeface(fontRegular);
            descView.setSingleLine(false);
            textContainer.addView(descView);
        }
        row.addView(textContainer);

        String displayCount = count;
        if (themeKey != null) {
            String cacheKey = "cached_count_" + themeKey;
            displayCount = activity.getSharedPreferences("lichess_puzzle_prefs", Context.MODE_PRIVATE).getString(cacheKey, "...");
        }

        TextView countView = null;
        if (displayCount != null && !displayCount.isEmpty()) {
            countView = new TextView(activity);
            countView.setText(displayCount);
            countView.setTextColor(Color.parseColor("#B1B0AE"));
            countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            if (fontRegular != null) countView.setTypeface(fontRegular);
            countView.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, activity.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, activity.getResources().getDisplayMetrics())
            );
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#1C1A18"));
            badgeBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, activity.getResources().getDisplayMetrics()));
            countView.setBackground(badgeBg);

            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            countLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics());
            countView.setLayoutParams(countLp);
            row.addView(countView);
        }

        if (themeKey != null && countView != null) {
            themeCountViews.put(themeKey, countView);
        }

        row.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onThemeSelected(mode, themeKey);
            }
        });
        parent.addView(row);
    }

    private static class ScrollScrollView extends ScrollView {
        public ScrollScrollView(Context context) {
            super(context);
        }
    }
}

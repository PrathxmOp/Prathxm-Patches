package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.RelativeLayout;

import java.io.File;
import java.util.Locale;

public class LichessPuzzleJourneyActivity extends Activity implements PuzzleJourneyMapView.OnLevelClickListener {

    private static final String TAG = "LichessPuzzleJourney";

    private FrameLayout mainContainer;
    private ScrollView mapScrollView;
    private PuzzleJourneyMapView mapInterface;
    
    // Streak Progress UI
    private TextView streakTextView;
    private ProgressBar streakProgressBar;
    private TextView streakMilestoneBadge;
    
    private Button mainSolveButton;
    private View solveAndListRow;
    
    // Header & Subtitles
    private TextView titleText;
    private TextView subtitleText;
    private TextView topBadge;

    // Coach views
    private android.widget.ImageView coachAvatar;

    // Database / Loading
    private PuzzleDownloadManager downloadManager;
    private boolean isDatabaseReady = false;
    private LichessPuzzleDatabaseHelper dbHelper;
    private JourneyBottomSheet journeyBottomSheet;

    // Theme Colors
    private final int COLOR_BG = Color.parseColor("#121214");
    private final int COLOR_CARD = Color.parseColor("#1E1E24");
    private final int COLOR_GREEN = Color.parseColor("#81B64C");
    private final int COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private android.graphics.Typeface fontBold;
    private android.graphics.Typeface fontRegular;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupJourneyUI();

        dbHelper = new LichessPuzzleDatabaseHelper(this);
        downloadManager = new PuzzleDownloadManager(
                this,
                mainContainer,
                mapScrollView,
                dbHelper,
                new PuzzleDownloadManager.PuzzleDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        isDatabaseReady = true;
                        refreshProgress();
                    }

                    @Override
                    public void onDownloadFailed(String error) {
                        // Handled in download manager UI
                    }

                    @Override
                    public void onMigrationComplete() {
                        isDatabaseReady = true;
                        refreshProgress();
                    }
                }
        );
        if (fontBold != null || fontRegular != null) {
            downloadManager.setTypefaces(fontBold, fontRegular);
        }

        journeyBottomSheet = new JourneyBottomSheet(
                this,
                mainContainer,
                dbHelper,
                fontBold,
                fontRegular,
                new JourneyBottomSheet.Listener() {
                    @Override
                    public void onThemeSelected(String mode, String themeKey) {
                        Intent intent = new Intent(LichessPuzzleJourneyActivity.this, StandaloneLichessActivity.class);
                        intent.putExtra("puzzle_mode", mode);
                        if (themeKey != null) {
                            intent.putExtra("puzzle_theme", themeKey);
                        }
                        startActivity(intent);
                    }

                    @Override
                    public void onSolveJourneyClicked() {
                        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                                .getInt("unlocked_level", 1);
                        onLevelClick(unlockedLevel);
                    }
                }
        );

        File oldFile = new File(getFilesDir(), "puzzles.json");
        if (oldFile.exists()) {
            oldFile.delete();
        }

        File localFile = new File(getFilesDir(), "puzzles.json.gz");
        if (dbHelper.getPuzzlesCount() > 0) {
            if (localFile.exists()) {
                localFile.delete();
            }
            isDatabaseReady = true;
            refreshProgress();
        } else if (localFile.exists() && localFile.length() > 0) {
            downloadManager.migrateJsonToDbAsync(localFile);
        } else {
            downloadManager.startDownloadFlow(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isDatabaseReady) {
            refreshProgress();
        } else {
            updateStreakProgress();
        }
    }

    private void setupJourneyUI() {
        try {
            fontBold = android.graphics.Typeface.createFromAsset(getAssets(), "composeResources/com.chess.designsystem.fonts.generated.resources/font/chess_sans_bold.ttf");
            fontRegular = android.graphics.Typeface.createFromAsset(getAssets(), "composeResources/com.chess.designsystem.fonts.generated.resources/font/custom_regular.ttf");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom Chess.com fonts", e);
        }

        mainContainer = new FrameLayout(this);
        mainContainer.setFitsSystemWindows(true);
        try {
            android.graphics.Bitmap bgBitmap = android.graphics.BitmapFactory.decodeStream(getAssets().open("images/puzzle_bg.png"));
            if (bgBitmap != null) {
                mainContainer.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bgBitmap));
            } else {
                mainContainer.setBackgroundColor(COLOR_BG);
            }
        } catch (Exception e) {
            mainContainer.setBackgroundColor(COLOR_BG);
        }
        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Header Panel
        RelativeLayout header = new RelativeLayout(this);
        header.setBackgroundColor(Color.parseColor("#E6272522"));
        int headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                headerHeight
        ));

        subtitleText = new TextView(this);

        LinearLayout headerTitleLayout = new LinearLayout(this);
        headerTitleLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerTitleLayout.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        headerTitleLayout.setLayoutParams(titleParams);

        topBadge = new TextView(this);
        topBadge.setText("0");
        topBadge.setTextColor(Color.WHITE);
        topBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        if (fontBold != null) {
            topBadge.setTypeface(fontBold);
        } else {
            topBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        topBadge.setGravity(Gravity.CENTER);
        int topBadgeSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams topBadgeParams = new LinearLayout.LayoutParams(topBadgeSize, topBadgeSize);
        topBadgeParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        topBadge.setLayoutParams(topBadgeParams);
        headerTitleLayout.addView(topBadge);

        titleText = new TextView(this);
        titleText.setText("Puzzles");
        titleText.setTextColor(COLOR_TEXT_PRIMARY);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) {
            titleText.setTypeface(fontBold);
        } else {
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        headerTitleLayout.addView(titleText);
        header.addView(headerTitleLayout);

        // DB Download Button on the right
        int dbBtnResId = getDrawableResId("glyph_utility_download");
        if (dbBtnResId == 0) dbBtnResId = getDrawableResId("glyph_board_download");
        if (dbBtnResId == 0) dbBtnResId = getDrawableResId("glyph_utility_refresh");
        if (dbBtnResId == 0) dbBtnResId = getDrawableResId("glyph_board_cogwheel");
        
        View dbBtn;
        if (dbBtnResId != 0) {
            android.widget.ImageView imgBtn = new android.widget.ImageView(this);
            imgBtn.setImageResource(dbBtnResId);
            imgBtn.setColorFilter(Color.WHITE);
            dbBtn = imgBtn;
        } else {
            TextView txtBtn = new TextView(this);
            txtBtn.setText("📥");
            txtBtn.setTextColor(Color.WHITE);
            txtBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            txtBtn.setGravity(Gravity.CENTER);
            dbBtn = txtBtn;
        }

        RelativeLayout.LayoutParams dbBtnParams = new RelativeLayout.LayoutParams(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics())
        );
        dbBtnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        dbBtnParams.addRule(RelativeLayout.CENTER_VERTICAL);
        dbBtnParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        dbBtn.setLayoutParams(dbBtnParams);
        dbBtn.setPadding(12, 12, 12, 12);
        dbBtn.setClickable(true);
        dbBtn.setFocusable(true);
        
        TypedValue outVal = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true);
        dbBtn.setBackgroundResource(outVal.resourceId);
        
        dbBtn.setOnClickListener(v -> {
            String[] options = {
                "Lightweight Mode (20,000 puzzles)",
                "Standard Mode (50,000 puzzles)",
                "Full Database (100,000 puzzles)",
                "Mega Database (500,000 puzzles)",
                "All Puzzles (6M+ puzzles)"
            };
            int[] limits = {20000, 50000, 100000, 500000, 10000000};
            
            new android.app.AlertDialog.Builder(LichessPuzzleJourneyActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Database Size")
                .setItems(options, (dialog, which) -> {
                    int limit = limits[which];
                    Intent intent = new Intent(LichessPuzzleJourneyActivity.this, StandaloneLichessActivity.class);
                    intent.putExtra("force_download", true);
                    intent.putExtra("download_limit", limit);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            dbBtn.setTooltipText("Change/Download Database");
        }
        dbBtn.setOnLongClickListener(v -> {
            android.widget.Toast.makeText(LichessPuzzleJourneyActivity.this, "Change/Download Database", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });

        header.addView(dbBtn);
        root.addView(header);

        // --- Coach Avatar & Speech Bubble ---
        LinearLayout coachLayout = new LinearLayout(this);
        coachLayout.setOrientation(LinearLayout.HORIZONTAL);
        coachLayout.setGravity(Gravity.BOTTOM);
        coachLayout.setPadding(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics())
        );
        coachLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        coachAvatar = new android.widget.ImageView(this);
        coachAvatar.setScaleType(android.widget.ImageView.ScaleType.FIT_END);
        updateCoachAvatar();
        
        int avatarWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        int avatarHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarWidth, avatarHeight);
        avatarParams.gravity = Gravity.BOTTOM;
        coachAvatar.setLayoutParams(avatarParams);
        coachLayout.addView(coachAvatar);

        LinearLayout speechBubbleContainer = new LinearLayout(this);
        speechBubbleContainer.setOrientation(LinearLayout.HORIZONTAL);
        speechBubbleContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        containerParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        speechBubbleContainer.setLayoutParams(containerParams);

        View speechBubbleTail = new SpeechBubbleTail(this);
        int tailWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        int tailHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams tailParams = new LinearLayout.LayoutParams(tailWidth, tailHeight);
        tailParams.rightMargin = -1;
        speechBubbleTail.setLayoutParams(tailParams);
        speechBubbleContainer.addView(speechBubbleTail);

        LinearLayout speechBubbleBody = new LinearLayout(this);
        speechBubbleBody.setOrientation(LinearLayout.VERTICAL);
        speechBubbleBody.setPadding(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics())
        );
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.WHITE);
        bubbleBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        speechBubbleBody.setBackground(bubbleBg);
        speechBubbleBody.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView coachMsgText = new TextView(this);
        coachMsgText.setText("Feel like speeding things up? Score for solving quickly in Puzzle Rush, where your third mistake is your last!");
        coachMsgText.setTextColor(Color.parseColor("#484644"));
        coachMsgText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontRegular != null) coachMsgText.setTypeface(fontRegular);
        speechBubbleBody.addView(coachMsgText);

        speechBubbleContainer.addView(speechBubbleBody);
        coachLayout.addView(speechBubbleContainer);
        root.addView(coachLayout);

        // Scrollable map
        mapScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        mapScrollView.setLayoutParams(scrollParams);

        mapInterface = new PuzzleJourneyMapView(this);
        mapInterface.setOnLevelClickListener(this);
        mapScrollView.addView(mapInterface);
        root.addView(mapScrollView);

        // Stats & Streak Progress Row
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        statsRow.setBackgroundColor(Color.TRANSPARENT);
        statsRow.setPadding(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            0,
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics())
        );
        statsRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        streakTextView = new TextView(this);
        streakTextView.setText("0");
        streakTextView.setTextColor(COLOR_TEXT_PRIMARY);
        streakTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        if (fontBold != null) {
            streakTextView.setTypeface(fontBold);
        } else {
            streakTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        streakTextView.setLayoutParams(textLp);
        statsRow.addView(streakTextView);

        // Progress Bar
        streakProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        streakProgressBar.setMax(10);
        streakProgressBar.setProgress(0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            streakProgressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5B00")));
            streakProgressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#403E3B")));
        }
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                1.0f
        );
        progressParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        streakProgressBar.setLayoutParams(progressParams);
        statsRow.addView(streakProgressBar);

        // Milestone Badge
        streakMilestoneBadge = new TextView(this);
        streakMilestoneBadge.setText("0");
        streakMilestoneBadge.setTextColor(Color.WHITE);
        streakMilestoneBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        if (fontBold != null) {
            streakMilestoneBadge.setTypeface(fontBold);
        } else {
            streakMilestoneBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        streakMilestoneBadge.setGravity(Gravity.CENTER);
        int badgeSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        streakMilestoneBadge.setLayoutParams(badgeParams);
        statsRow.addView(streakMilestoneBadge);

        root.addView(statsRow);

        // Solve and List buttons
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin16 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        rowParams.setMargins(margin16, margin16, margin16, margin16);
        row.setLayoutParams(rowParams);
        solveAndListRow = row;

        // List menu button
        LinearLayout listBtn = new LinearLayout(this);
        listBtn.setGravity(Gravity.CENTER);
        int listBtnSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams listBtnParams = new LinearLayout.LayoutParams(listBtnSize, listBtnSize);
        listBtnParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        listBtn.setLayoutParams(listBtnParams);
        
        GradientDrawable listBg = new GradientDrawable();
        listBg.setColor(Color.parseColor("#312E2B"));
        listBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        listBtn.setBackground(listBg);
        listBtn.setClickable(true);
        listBtn.setFocusable(true);
        
        TextView listIcon = new TextView(this);
        listIcon.setText("☰");
        listIcon.setTextColor(COLOR_TEXT_PRIMARY);
        listIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        listBtn.addView(listIcon);
        
        listBtn.setOnClickListener(v -> {
            if (journeyBottomSheet != null) {
                journeyBottomSheet.show();
            }
        });
        row.addView(listBtn);

        // Solve Puzzles button
        mainSolveButton = new Button(this);
        mainSolveButton.setText("Solve Puzzles");
        mainSolveButton.setTextColor(Color.WHITE);
        mainSolveButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (fontBold != null) mainSolveButton.setTypeface(fontBold);
        mainSolveButton.setAllCaps(false);
        GradientDrawable solveBg = new GradientDrawable();
        solveBg.setColor(COLOR_GREEN);
        solveBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        mainSolveButton.setBackground(solveBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52, getResources().getDisplayMetrics()),
                1.0f
        );
        mainSolveButton.setLayoutParams(btnParams);
        mainSolveButton.setOnClickListener(v -> {
            int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .getInt("unlocked_level", 1);
            onLevelClick(unlockedLevel);
        });
        row.addView(mainSolveButton);
        root.addView(row);

        mainContainer.addView(root);
        setContentView(mainContainer);
    }

    private void updateStreakProgress() {
        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .getInt("unlocked_level", 1);
        int totalPuzzlesSolved = Math.max(0, unlockedLevel - 1);
        int targetMilestone = totalPuzzlesSolved <= 0 ? 10 : ((totalPuzzlesSolved / 10) + 1) * 10;

        runOnUiThread(() -> {
            if (streakTextView != null) {
                streakTextView.setText(String.valueOf(totalPuzzlesSolved));
            }
            if (topBadge != null) {
                topBadge.setText(String.valueOf(totalPuzzlesSolved));
                int woodBadgeResId = getDrawableResId("puzzle_tier_wood_pawn");
                if (woodBadgeResId != 0) {
                    topBadge.setBackgroundResource(woodBadgeResId);
                }
            }
            if (streakProgressBar != null && streakMilestoneBadge != null) {
                streakProgressBar.setMax(10);
                streakProgressBar.setProgress(totalPuzzlesSolved % 10);
                streakMilestoneBadge.setText(String.valueOf(targetMilestone));
                
                int woodBadgeResId = getDrawableResId("puzzle_tier_wood_pawn");
                if (targetMilestone <= 10 && woodBadgeResId != 0) {
                    streakMilestoneBadge.setBackgroundResource(woodBadgeResId);
                } else {
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setShape(GradientDrawable.OVAL);
                    int badgeColor;
                    int strokeColor;
                    if (targetMilestone <= 10) {
                        badgeColor = Color.parseColor("#8B5A2B");
                        strokeColor = Color.parseColor("#A0522D");
                    } else if (targetMilestone <= 20) {
                        badgeColor = Color.parseColor("#C0C0C0");
                        strokeColor = Color.parseColor("#A9A9A9");
                    } else if (targetMilestone <= 30) {
                        badgeColor = Color.parseColor("#FFD700");
                        strokeColor = Color.parseColor("#DAA520");
                    } else {
                        badgeColor = Color.parseColor("#2C3E50");
                        strokeColor = Color.parseColor("#34495E");
                    }
                    badgeBg.setColor(badgeColor);
                    badgeBg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), strokeColor);
                    streakMilestoneBadge.setBackground(badgeBg);
                }
            }
        });
    }

    private int getDrawableResId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private void refreshProgress() {
        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .getInt("unlocked_level", 1);
        
        mapInterface.setProgress(unlockedLevel);
        titleText.setText(String.format(Locale.US, "%d Puzzles", unlockedLevel - 1));
        subtitleText.setText(getTierName(unlockedLevel) + " • Complete Level to Advance");
        updateStreakProgress();

        mapScrollView.post(() -> {
            float spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());
            int relativeIdx = (unlockedLevel - 1) % 20;
            float height = mapInterface.getHeight();
            float padding = spacing * 1.5f;
            float targetY = height - padding - (relativeIdx * spacing);

            int scrollTarget = (int) targetY - (mapScrollView.getHeight() / 2);
            mapScrollView.scrollTo(0, Math.max(0, scrollTarget));
        });
    }

    private String getTierName(int level) {
        if (level <= 20) return "Wood Tier (Levels 1-20)";
        if (level <= 50) return "Stone Tier (Levels 21-50)";
        if (level <= 100) return "Bronze Tier (Levels 51-100)";
        if (level <= 200) return "Silver Tier (Levels 101-200)";
        if (level <= 500) return "Gold Tier (Levels 201-500)";
        if (level <= 1000) return "Crystal Tier (Levels 501-1000)";
        return "Legend Tier (Levels 1001+)";
    }

    @Override
    public void onLevelClick(int levelIndex) {
        Intent intent = new Intent(this, StandaloneLichessActivity.class);
        intent.putExtra("level_index", levelIndex);
        startActivity(intent);
    }

    @Override
    public void onPageChange(int pageOffset) {
        int newPage = mapInterface.getCurrentPage() + pageOffset;
        if (newPage >= 0) {
            int maxPossiblePage = 10000 / 20;
            if (newPage <= maxPossiblePage) {
                mapInterface.setCurrentPage(newPage);
                mapScrollView.post(() -> mapScrollView.scrollTo(0, mapInterface.getHeight()));
            }
        }
    }

    private void updateCoachAvatar() {
        if (coachAvatar == null) return;
        
        int avatarResId = getResources().getIdentifier("color_coach_danny", "drawable", getPackageName());
        if (avatarResId == 0) {
            avatarResId = getResources().getIdentifier("color_coach_david", "drawable", getPackageName());
        }
        if (avatarResId == 0) {
            avatarResId = getResources().getIdentifier("color_coach_hikaru", "drawable", getPackageName());
        }
        if (avatarResId == 0) {
            avatarResId = getResources().getIdentifier("coach_david", "drawable", getPackageName());
        }
        if (avatarResId == 0) {
            avatarResId = getResources().getIdentifier("coach", "drawable", getPackageName());
        }
        if (avatarResId != 0) {
            coachAvatar.setImageResource(avatarResId);
        }
    }

    private static class SpeechBubbleTail extends View {
        private final android.graphics.Paint paint;
        private final android.graphics.Path path;

        public SpeechBubbleTail(android.content.Context context) {
            super(context);
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            path = new android.graphics.Path();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            path.reset();
            path.moveTo(getWidth(), getHeight() * 0.3f);
            path.lineTo(getWidth(), getHeight() * 0.7f);
            path.lineTo(0, getHeight() * 0.5f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}

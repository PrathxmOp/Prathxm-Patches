package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
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
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class StandaloneLichessActivity extends Activity implements LichessBoardView.BoardListener, PuzzleGameEngine.PuzzleListener, PuzzleRushManager.RushListener {

    private static final String TAG = "StandaloneLichess";

    private FrameLayout mainContainer;
    private LinearLayout gameLayout;
    private LichessBoardView chessboard;
    private TextView headerTitle;
    private TextView titleView;
    private TextView statusBanner;
    private TextView ratingView;
    private TextView streakTextView;
    private LinearLayout strikesContainer;
    private android.widget.ImageView sideColorIndicator;
    private ProgressBar progressBar;
    private ProgressBar streakProgressBar;
    private TextView streakMilestoneBadge;
    private int currentStreak = 0;
    private android.widget.ImageView coachAvatar;
    private android.graphics.Typeface fontBold;
    private android.graphics.Typeface fontRegular;

    // Managers and Engine
    private PuzzleSoundManager soundManager;
    private PuzzleDownloadManager downloadManager;
    private LichessPuzzleDatabaseHelper dbHelper;
    private PuzzleGameEngine gameEngine;
    private PuzzleRushManager rushManager;

    // Puzzle modes
    private String activeMode = "daily";
    private String selectedTheme = null;
    private android.widget.ImageView flameIcon;
    private LinearLayout actionsLayout;
    private View speechBubbleTail;

    // Timer State for normal mode
    private TextView timerView;
    private int elapsedSeconds = 0;
    private Timer normalTimer;
    private boolean resetTimerOnNextStateReset = false;

    // Theme Colors (Premium Dark Theme - matching Chess.com style)
    private final int COLOR_BG = Color.parseColor("#302e2b");
    private final int COLOR_CARD = Color.parseColor("#272522");
    private final int COLOR_GREEN = Color.parseColor("#81B64C");
    private final int COLOR_RED = Color.parseColor("#FA412F");
    private final int COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private final int COLOR_TEXT_SECONDARY = Color.parseColor("#B1B0AE");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Resolve mode and theme first
        int levelIndex = getIntent().getIntExtra("level_index", -1);
        if (levelIndex != -1) {
            activeMode = "journey";
        } else {
            activeMode = getIntent().getStringExtra("puzzle_mode");
            if (activeMode == null) {
                activeMode = "daily";
            }
        }
        if ("theme".equals(activeMode)) {
            selectedTheme = getIntent().getStringExtra("puzzle_theme");
        }

        String prefKey = "puzzle_streak";
        if ("theme".equals(activeMode) && selectedTheme != null) {
            prefKey = "theme_streak_" + selectedTheme;
        } else if ("journey".equals(activeMode)) {
            prefKey = "journey_streak";
        }
        currentStreak = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE).getInt(prefKey, 0);

        soundManager = new PuzzleSoundManager(this);
        dbHelper = LichessPuzzleDatabaseHelper.getInstance(this);
        gameEngine = new PuzzleGameEngine(this, dbHelper, this);
        rushManager = new PuzzleRushManager(this, this);
        setupPremiumUI();

        downloadManager = new PuzzleDownloadManager(
                this,
                mainContainer,
                gameLayout,
                dbHelper,
                new PuzzleDownloadManager.PuzzleDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        if (getIntent().getBooleanExtra("force_download", false)) {
                            finish();
                        } else {
                            loadOfflinePuzzlesAsync();
                        }
                    }

                    @Override
                    public void onDownloadFailed(String error) {
                        // Handled inside download manager UI
                    }

                    @Override
                    public void onMigrationComplete() {
                        loadOfflinePuzzlesAsync();
                    }
                }
        );
        if (fontBold != null || fontRegular != null) {
            downloadManager.setTypefaces(fontBold, fontRegular);
        }

        File oldFile = new File(getFilesDir(), "puzzles.json");
        if (oldFile.exists()) {
            oldFile.delete();
        }

        File localFile = new File(getFilesDir(), "puzzles.json.gz");
        boolean forceDownload = getIntent().getBooleanExtra("force_download", false);
        if (forceDownload) {
            if (getIntent().hasExtra("download_limit")) {
                int limit = getIntent().getIntExtra("download_limit", 50000);
                downloadManager.setSelectedDownloadLimit(limit);
                if (downloadManager.isNetworkAvailable()) {
                    downloadManager.startDownloadPuzzles();
                } else {
                    downloadManager.showOfflineWarningOverlay();
                }
            } else {
                downloadManager.startDownloadFlow(true);
            }
        } else if (dbHelper.getPuzzlesCount() > 0) {
            if (localFile.exists()) {
                localFile.delete();
            }
            loadOfflinePuzzlesAsync();
        } else if (localFile.exists() && localFile.length() > 0) {
            downloadManager.migrateJsonToDbAsync(localFile);
        } else {
            downloadManager.startDownloadFlow(false);
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        rushManager.stopRun();
        if (gameEngine != null) {
            gameEngine.destroy();
        }
        if (soundManager != null) {
            soundManager.release();
            soundManager = null;
        }
        super.onDestroy();
    }

    private void setupPremiumUI() {
        try {
            fontBold = android.graphics.Typeface.createFromAsset(getAssets(), "composeResources/com.chess.designsystem.fonts.generated.resources/font/chess_sans_bold.ttf");
            fontRegular = android.graphics.Typeface.createFromAsset(getAssets(), "composeResources/com.chess.designsystem.fonts.generated.resources/font/custom_regular.ttf");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom Chess.com fonts", e);
        }

        // Main container FrameLayout
        mainContainer = new FrameLayout(this);
        mainContainer.setFitsSystemWindows(true);
        mainContainer.setBackgroundColor(COLOR_BG);
        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Game Layout (Vertical)
        gameLayout = new LinearLayout(this);
        gameLayout.setOrientation(LinearLayout.VERTICAL);
        gameLayout.setBackgroundColor(COLOR_BG);
        gameLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Header / Title Bar
        RelativeLayout header = new RelativeLayout(this);
        header.setBackgroundColor(COLOR_CARD);
        int headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                headerHeight
        ));

        // Back Chevron Button
        android.widget.ImageView backButton = new android.widget.ImageView(this);
        backButton.setId(1001);
        int chevronLeftResId = getDrawableResId("glyph_arrow_chevron_left");
        if (chevronLeftResId != 0) {
            backButton.setImageResource(chevronLeftResId);
        }
        backButton.setColorFilter(Color.WHITE);
        RelativeLayout.LayoutParams backParams = new RelativeLayout.LayoutParams(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics())
        );
        backParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        backParams.addRule(RelativeLayout.CENTER_VERTICAL);
        backParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        backButton.setLayoutParams(backParams);
        backButton.setPadding(12, 12, 12, 12);
        backButton.setClickable(true);
        backButton.setFocusable(true);
        TypedValue outVal = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true);
        backButton.setBackgroundResource(outVal.resourceId);
        backButton.setOnClickListener(v -> finish());
        header.addView(backButton);

        // Settings Cog Button
        android.widget.ImageView settingsBtn = new android.widget.ImageView(this);
        settingsBtn.setId(1002);
        int cogResId = getDrawableResId("glyph_board_cogwheel");
        if (cogResId == 0) {
            cogResId = getDrawableResId("glyph_utility_cogwheel");
        }
        if (cogResId != 0) {
            settingsBtn.setImageResource(cogResId);
        }
        settingsBtn.setColorFilter(Color.WHITE);
        RelativeLayout.LayoutParams settingsParams = new RelativeLayout.LayoutParams(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics())
        );
        settingsParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        settingsParams.addRule(RelativeLayout.CENTER_VERTICAL);
        settingsParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        settingsBtn.setLayoutParams(settingsParams);
        settingsBtn.setPadding(12, 12, 12, 12);
        settingsBtn.setClickable(true);
        settingsBtn.setBackgroundResource(outVal.resourceId);
        settingsBtn.setOnClickListener(v -> showThemeSettingsDialog());

        // Center Title Block
        LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.HORIZONTAL);
        titleContainer.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
        titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        titleParams.addRule(RelativeLayout.RIGHT_OF, 1001);
        titleParams.addRule(RelativeLayout.LEFT_OF, 1002);
        titleParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        titleParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        titleContainer.setLayoutParams(titleParams);

        // Wooden board badge
        android.widget.ImageView woodBadge = new android.widget.ImageView(this);
        int woodBadgeResId = getDrawableResId("puzzle_tier_wood_pawn");
        if (woodBadgeResId != 0) {
            woodBadge.setImageResource(woodBadgeResId);
        }
        int badgeIconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeIconSize, badgeIconSize);
        badgeLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        woodBadge.setLayoutParams(badgeLp);
        titleContainer.addView(woodBadge);

        headerTitle = new TextView(this);
        headerTitle.setText("Lichess Puzzles");
        headerTitle.setTextColor(COLOR_TEXT_PRIMARY);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (fontBold != null) {
            headerTitle.setTypeface(fontBold);
        } else {
            headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textLp.weight = 1.0f;
        headerTitle.setLayoutParams(textLp);
        titleContainer.addView(headerTitle);

        header.addView(titleContainer);
        header.addView(settingsBtn);

        gameLayout.addView(header);

        // Content Area (Scrollable)
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1.0f
        ));
        
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.setPadding(0, 0, 0, 0);

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
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110, getResources().getDisplayMetrics())
        ));

        // Coach Avatar
        coachAvatar = new android.widget.ImageView(this);
        coachAvatar.setScaleType(android.widget.ImageView.ScaleType.FIT_END);
        updateCoachAvatar();
        
        int avatarWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        int avatarHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarWidth, avatarHeight);
        avatarParams.gravity = Gravity.BOTTOM;
        coachAvatar.setLayoutParams(avatarParams);
        coachLayout.addView(coachAvatar);

        // Speech Bubble Layout
        LinearLayout speechBubbleContainer = new LinearLayout(this);
        speechBubbleContainer.setOrientation(LinearLayout.HORIZONTAL);
        speechBubbleContainer.setGravity(Gravity.CENTER_VERTICAL);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        containerParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        speechBubbleContainer.setLayoutParams(containerParams);

        // Speech Bubble Tail
        speechBubbleTail = new SpeechBubbleTail(this);
        int tailWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        int tailHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams tailParams = new LinearLayout.LayoutParams(tailWidth, tailHeight);
        tailParams.rightMargin = -1;
        speechBubbleTail.setLayoutParams(tailParams);
        speechBubbleContainer.addView(speechBubbleTail);

        // Speech Bubble Body
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

        // Speech Bubble Title / Status Row
        LinearLayout sideRow = new LinearLayout(this);
        sideRow.setOrientation(LinearLayout.HORIZONTAL);
        sideRow.setGravity(Gravity.CENTER_VERTICAL);
        
        sideColorIndicator = new android.widget.ImageView(this);
        int indSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams indParams = new LinearLayout.LayoutParams(indSize, indSize);
        indParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        sideColorIndicator.setLayoutParams(indParams);
        updateSideColorIndicator(true);
        sideRow.addView(sideColorIndicator);

        statusBanner = new TextView(this);
        statusBanner.setText("White to Move");
        statusBanner.setTextColor(Color.parseColor("#1a1a1a"));
        statusBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (fontBold != null) {
            statusBanner.setTypeface(fontBold);
        } else {
            statusBanner.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        sideRow.addView(statusBanner);
        speechBubbleBody.addView(sideRow);

        // Subtitle instructions
        titleView = new TextView(this);
        titleView.setText("Find the best sequence of moves.");
        titleView.setTextColor(Color.parseColor("#484644"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleView.setMinLines(2);
        if (fontRegular != null) {
            titleView.setTypeface(fontRegular);
        }
        titleView.setPadding(0, 4, 0, 0);
        speechBubbleBody.addView(titleView);

        speechBubbleContainer.addView(speechBubbleBody);
        coachLayout.addView(speechBubbleContainer);
        contentLayout.addView(coachLayout);

        // Chess Board Container
        FrameLayout boardContainer = new FrameLayout(this);
        boardContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Custom Board View
        chessboard = new LichessBoardView(this);
        chessboard.setBoardListener(this);
        boardContainer.addView(chessboard);
        contentLayout.addView(boardContainer);
        loadBoardTheme();

        // Loading indicator
        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        ((LinearLayout.LayoutParams) progressBar.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        ((LinearLayout.LayoutParams) progressBar.getLayoutParams()).setMargins(0, 16, 0, 0);
        contentLayout.addView(progressBar);

        scrollView.addView(contentLayout);
        gameLayout.addView(scrollView);

        // --- Bottom Panel ---
        LinearLayout bottomArea = new LinearLayout(this);
        bottomArea.setOrientation(LinearLayout.VERTICAL);
        bottomArea.setBackgroundColor(COLOR_CARD);
        bottomArea.setPadding(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics())
        );
        bottomArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Stats Row
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        statsRow.setPadding(0, 0, 0, 12);
        statsRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Left Column: Rating & Score (takes up remaining space on left)
        LinearLayout ratingStreakContainer = new LinearLayout(this);
        ratingStreakContainer.setOrientation(LinearLayout.HORIZONTAL);
        ratingStreakContainer.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        ratingStreakContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        ratingView = new TextView(this);
        ratingView.setText("--");
        ratingView.setTextColor(Color.WHITE);
        ratingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        if (fontBold != null) {
            ratingView.setTypeface(fontBold);
        } else {
            ratingView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        ratingStreakContainer.addView(ratingView);
        statsRow.addView(ratingStreakContainer);

        // Center Column: Middle Container (handles strikes or streak counts)
        LinearLayout middleContainer = new LinearLayout(this);
        middleContainer.setOrientation(LinearLayout.HORIZONTAL);
        middleContainer.setGravity(Gravity.CENTER);
        middleContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Flame icon
        flameIcon = new android.widget.ImageView(this);
        int flameResId = getDrawableResId("ic_col_flame_24");
        if (flameResId != 0) {
            flameIcon.setImageResource(flameResId);
        }
        int flameSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams flameLp = new LinearLayout.LayoutParams(flameSize, flameSize);
        flameLp.leftMargin = 0;
        flameLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        flameIcon.setLayoutParams(flameLp);
        middleContainer.addView(flameIcon);

        streakTextView = new TextView(this);
        streakTextView.setText(String.valueOf(currentStreak));
        streakTextView.setTextColor(Color.parseColor("#E55B1E"));
        streakTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) {
            streakTextView.setTypeface(fontBold);
        } else {
            streakTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        middleContainer.addView(streakTextView);

        strikesContainer = new LinearLayout(this);
        strikesContainer.setOrientation(LinearLayout.HORIZONTAL);
        strikesContainer.setGravity(Gravity.CENTER_VERTICAL);
        strikesContainer.setVisibility(View.GONE);
        middleContainer.addView(strikesContainer);

        statsRow.addView(middleContainer);

        // Right Column: Timer Container (takes up remaining space on right, aligning content right)
        LinearLayout timerContainer = new LinearLayout(this);
        timerContainer.setOrientation(LinearLayout.HORIZONTAL);
        timerContainer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        timerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        android.widget.ImageView clockIcon = new android.widget.ImageView(this);
        int clockResId = getDrawableResId("glyph_board_simple_badge_clock");
        if (clockResId == 0) {
            clockResId = getDrawableResId("glyph_board_badge_clock");
        }
        if (clockResId != 0) {
            clockIcon.setImageResource(clockResId);
            clockIcon.setColorFilter(Color.parseColor("#9C9A98"));
        }
        int clockSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(clockSize, clockSize);
        clockLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        clockIcon.setLayoutParams(clockLp);
        timerContainer.addView(clockIcon);

        timerView = new TextView(this);
        timerView.setText("00:00");
        timerView.setTextColor(Color.parseColor("#9C9A98"));
        timerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (fontRegular != null) {
            timerView.setTypeface(fontRegular);
        }
        timerContainer.addView(timerView);
        statsRow.addView(timerContainer);

        bottomArea.addView(statsRow);

        // Progress Container
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.HORIZONTAL);
        progressContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams containerParamsBottom = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParamsBottom.setMargins(0, 4, 0, 24);
        progressContainer.setLayoutParams(containerParamsBottom);

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
        progressParams.setMargins(0, 0, 16, 0);
        streakProgressBar.setLayoutParams(progressParams);
        progressContainer.addView(streakProgressBar);

        // Milestone Badge
        streakMilestoneBadge = new TextView(this);
        streakMilestoneBadge.setText("10");
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

        if (woodBadgeResId != 0) {
            streakMilestoneBadge.setBackgroundResource(woodBadgeResId);
        } else {
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.OVAL);
            badgeBg.setColor(Color.parseColor("#8B5A2B"));
            badgeBg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), Color.parseColor("#A0522D"));
            streakMilestoneBadge.setBackground(badgeBg);
        }

        progressContainer.addView(streakMilestoneBadge);
        bottomArea.addView(progressContainer);

        // Action Buttons
        actionsLayout = new LinearLayout(this);
        actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionsLayout.setGravity(Gravity.CENTER_VERTICAL);
        actionsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        updateBottomActionPanel();

        bottomArea.addView(actionsLayout);
        gameLayout.addView(bottomArea);

        mainContainer.addView(gameLayout);
        setContentView(mainContainer);
    }

    private int getDrawableResId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private void updateSideColorIndicator(boolean isWhite) {
        if (sideColorIndicator == null) return;
        GradientDrawable indicatorBg = new GradientDrawable();
        indicatorBg.setShape(GradientDrawable.RECTANGLE);
        indicatorBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));
        if (isWhite) {
            indicatorBg.setColor(Color.WHITE);
            indicatorBg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics()), Color.parseColor("#808080"));
        } else {
            indicatorBg.setColor(Color.BLACK);
            indicatorBg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics()), Color.parseColor("#404040"));
        }
        sideColorIndicator.setImageDrawable(indicatorBg);
    }

    private LinearLayout createActionItem(String drawableName, String labelText, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        item.setBackgroundResource(outValue.resourceId);
        item.setOnClickListener(listener);
        item.setPadding(0, 12, 0, 12);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        int resId = getDrawableResId(drawableName);
        if (resId != 0) {
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setImageResource(resId);
            icon.setColorFilter(Color.parseColor("#9C9A98"));
            int iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            icon.setLayoutParams(lp);
            item.addView(icon);
        } else {
            TextView icon = new TextView(this);
            String fallback = "💡";
            if (labelText.equals("Back")) fallback = "◀";
            else if (labelText.equals("Next") || labelText.equals("Forward")) fallback = "▶";
            else if (labelText.equals("Restart")) fallback = "🔄";
            else if (labelText.equals("Analysis")) fallback = "🔍";
            icon.setText(fallback);
            icon.setTextColor(Color.WHITE);
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            if (fontBold != null) icon.setTypeface(fontBold);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon);
        }

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.parseColor("#9C9A98"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (fontRegular != null) label.setTypeface(fontRegular);
        label.setPadding(0, 4, 0, 0);
        label.setGravity(Gravity.CENTER);
        item.addView(label);

        return item;
    }

    private void updateBottomActionPanel() {
        runOnUiThread(() -> {
            if (actionsLayout == null) return;
            actionsLayout.removeAllViews();

            if (!gameEngine.isFinished()) {
                // Hint
                actionsLayout.addView(createActionItem("glyph_device_bulb_glow", "Hint", v -> {
                    String[] solutionMoves = gameEngine.getSolutionMoves();
                    int currentMoveIdx = gameEngine.getCurrentMoveIdx();
                    if (solutionMoves != null && currentMoveIdx < solutionMoves.length && gameEngine.isPlayerTurn() && !gameEngine.isFinished()) {
                        String expectedMove = solutionMoves[currentMoveIdx];
                        String fromSquare = expectedMove.substring(0, 2);
                        String toSquare = expectedMove.substring(2, 4);
                        if (gameEngine.getHintClickCount() == 0) {
                            chessboard.showHint(fromSquare);
                            chessboard.clearArrow();
                            gameEngine.setHintClickCount(1);
                            updateSpeechBubble("💡 Hint (1/2)", "Find where to move the highlighted piece.", Color.parseColor("#E67E22"));
                        } else {
                            chessboard.showArrow(fromSquare, toSquare);
                            gameEngine.setHintClickCount(0);
                            updateSpeechBubble("💡 Hint (2/2)", "Move the piece along the arrow.", Color.parseColor("#E67E22"));
                        }
                    }
                }));

                // Back
                actionsLayout.addView(createActionItem("glyph_arrow_chevron_left", "Back", v -> {
                    int limit = gameEngine.isOnlinePuzzle() ? 0 : 1;
                    if (gameEngine.getSolutionMoves() != null && gameEngine.getCurrentMoveIdx() > limit) {
                        gameEngine.undoLastMove();
                    }
                }));

                // Forward
                actionsLayout.addView(createActionItem("glyph_arrow_chevron_right", "Forward", v -> {
                    if (gameEngine.getSolutionMoves() != null && gameEngine.getCurrentMoveIdx() < gameEngine.getMaxMoveIdxReached()) {
                        gameEngine.redoNextMove();
                    }
                }));
            } else {
                boolean isDailyCompleted = isDailyCompleted();

                if (gameEngine.isAnalysisMode()) {
                    // Reset
                    actionsLayout.addView(createActionItem("glyph_arrow_spin_redo", "Reset", v -> {
                        if (isDailyCompleted) {
                            updateSpeechBubble("🎉 Completed!", "Today's daily puzzle is complete!", COLOR_GREEN);
                            return;
                        }
                        resetTimerOnNextStateReset = true;
                        gameEngine.resetPuzzleState(isDailyCompleted);
                    }));

                    actionsLayout.addView(createGreenNextButton());
                } else {
                    // Restart
                    if (!isDailyCompleted) {
                        actionsLayout.addView(createActionItem("glyph_arrow_spin_redo", "Restart", v -> {
                            resetTimerOnNextStateReset = true;
                            gameEngine.resetPuzzleState(isDailyCompleted);
                        }));
                    }

                    // Analysis
                    actionsLayout.addView(createActionItem("glyph_board_analysis", "Analysis", v -> {
                        gameEngine.setAnalysisMode(true);
                        chessboard.setInteractable(true);
                        chessboard.setPlayerColor('a');
                        updateSpeechBubble("🔍 Analysis Mode", "Explore moves freely on the board.", Color.parseColor("#2980B9"));
                        updateBottomActionPanel();
                    }));

                    actionsLayout.addView(createGreenNextButton());
                }
            }
        });
    }

    private View createGreenNextButton() {
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.VERTICAL);
        btnLayout.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()), 2.0f
        );
        params.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        btnLayout.setLayoutParams(params);

        boolean isDailyCompleted = isDailyCompleted();

        GradientDrawable bg = new GradientDrawable();
        if (isDailyCompleted) {
            bg.setColor(Color.parseColor("#5F5E5C"));
        } else {
            bg.setColor(Color.parseColor("#81B64C"));
        }
        bg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        btnLayout.setBackground(bg);

        TextView text = new TextView(this);
        if (isDailyCompleted) {
            text.setText("Daily Completed");
        } else {
            text.setText("Next");
        }
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (fontBold != null) {
            text.setTypeface(fontBold);
        } else {
            text.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        text.setGravity(Gravity.CENTER);
        btnLayout.addView(text);

        if (isDailyCompleted) {
            btnLayout.setClickable(false);
            btnLayout.setFocusable(false);
        } else {
            btnLayout.setClickable(true);
            btnLayout.setFocusable(true);
            btnLayout.setOnClickListener(v -> {
                loadNextPuzzle();
            });
        }

        return btnLayout;
    }

    private android.graphics.drawable.Drawable createStatusDrawable(boolean isCorrect, int color) {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float cx = bounds.centerX();
                float cy = bounds.centerY();
                float radius = Math.min(bounds.width(), bounds.height()) / 2.0f;

                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(color);
                canvas.drawCircle(cx, cy, radius, paint);

                paint.setColor(Color.WHITE);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(radius * 0.2f);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);

                if (isCorrect) {
                    float startX = cx - radius * 0.4f;
                    float startY = cy + radius * 0.1f;
                    float midX = cx - radius * 0.1f;
                    float midY = cy + radius * 0.4f;
                    float endX = cx + radius * 0.4f;
                    float endY = cy - radius * 0.3f;
                    canvas.drawLine(startX, startY, midX, midY, paint);
                    canvas.drawLine(midX, midY, endX, endY, paint);
                } else {
                    float offset = radius * 0.35f;
                    canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, paint);
                    canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, paint);
                }
            }

            @Override
            public void setAlpha(int alpha) {}

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    private void updateSpeechBubble(String title, String subtitle, int titleColor) {
        runOnUiThread(() -> {
            if (statusBanner == null || titleView == null || sideColorIndicator == null) return;
            
            boolean isWhiteToMove = title.contains("⬜") || title.toLowerCase().contains("white");
            boolean isBlackToMove = title.contains("⬛") || title.toLowerCase().contains("black");
            boolean isSuccess = title.contains("Correct") || title.contains("Success") || title.contains("Solved");
            boolean isWrong = title.contains("Wrong") || title.contains("Incorrect") || title.contains("Error") || title.contains("Failed");
            
            sideColorIndicator.setVisibility(View.VISIBLE);
            
            if (isWhiteToMove || isBlackToMove) {
                updateSideColorIndicator(isWhiteToMove);
                statusBanner.setText(isWhiteToMove ? "White to Move" : "Black to Move");
                statusBanner.setTextColor(Color.parseColor("#1a1a1a"));
                titleView.setVisibility(View.GONE);
            } else if (isSuccess) {
                sideColorIndicator.setImageDrawable(createStatusDrawable(true, COLOR_GREEN));
                String cleanTitle = title.replace("✅", "").replace("🎉", "").trim();
                statusBanner.setText(cleanTitle);
                statusBanner.setTextColor(COLOR_GREEN);
                titleView.setVisibility(View.VISIBLE);
                titleView.setText(subtitle);
            } else if (isWrong) {
                sideColorIndicator.setImageDrawable(createStatusDrawable(false, COLOR_RED));
                String cleanTitle = title.replace("❌", "").trim();
                statusBanner.setText(cleanTitle);
                statusBanner.setTextColor(COLOR_RED);
                titleView.setVisibility(View.VISIBLE);
                titleView.setText(subtitle);
            } else {
                sideColorIndicator.setVisibility(View.GONE);
                statusBanner.setText(title);
                statusBanner.setTextColor(titleColor);
                if (subtitle == null || subtitle.trim().isEmpty()) {
                    titleView.setVisibility(View.GONE);
                } else {
                    titleView.setVisibility(View.VISIBLE);
                    titleView.setText(subtitle);
                }
            }
        });
    }

    private void loadBoardTheme() {
        android.content.SharedPreferences prefs = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE);
        String lightStr = prefs.getString("board_theme_light", "#EEEED2");
        String darkStr = prefs.getString("board_theme_dark", "#769656");
        try {
            int lightColor = Color.parseColor(lightStr);
            int darkColor = Color.parseColor(darkStr);
            if (chessboard != null) {
                chessboard.setBoardThemeColors(lightColor, darkColor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load board theme", e);
        }
    }

    private void showThemeSettingsDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        
        float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#262421"));
        bg.setCornerRadius(r);
        bg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), Color.parseColor("#3D3A37"));
        root.setBackground(bg);
        
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Board Theme");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) title.setTypeface(fontBold);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()));
        root.addView(title);

        String[] themes = {"Green (Default)", "Brown", "Blue", "Icy", "Glass"};
        String[] lightColors = {"#EEEED2", "#F0D9B5", "#EFEFEF", "#E2E4E6", "#ECECD7"};
        String[] darkColors = {"#769656", "#B58863", "#7296B6", "#97A3AF", "#567676"};

        android.content.SharedPreferences prefs = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE);
        String savedLight = prefs.getString("board_theme_light", "#EEEED2");

        for (int i = 0; i < themes.length; i++) {
            final int which = i;
            boolean isSelected = lightColors[i].equalsIgnoreCase(savedLight);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            
            int rowPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            row.setPadding(rowPadding, rowPadding, rowPadding, rowPadding);
            
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            row.setLayoutParams(rowParams);
            
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.parseColor("#312E2B"));
            rowBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
            
            if (isSelected) {
                rowBg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()), Color.parseColor("#81B64C"));
            }
            row.setBackground(rowBg);

            LinearLayout preview = new LinearLayout(this);
            preview.setOrientation(LinearLayout.VERTICAL);
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(size, size);
            previewParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            preview.setLayoutParams(previewParams);

            LinearLayout topHalf = new LinearLayout(this);
            topHalf.setOrientation(LinearLayout.HORIZONTAL);
            topHalf.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            View s1 = new View(this);
            s1.setBackgroundColor(Color.parseColor(lightColors[i]));
            s1.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            View s2 = new View(this);
            s2.setBackgroundColor(Color.parseColor(darkColors[i]));
            s2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            topHalf.addView(s1);
            topHalf.addView(s2);

            LinearLayout bottomHalf = new LinearLayout(this);
            bottomHalf.setOrientation(LinearLayout.HORIZONTAL);
            bottomHalf.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            View s3 = new View(this);
            s3.setBackgroundColor(Color.parseColor(darkColors[i]));
            s3.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            View s4 = new View(this);
            s4.setBackgroundColor(Color.parseColor(lightColors[i]));
            s4.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            bottomHalf.addView(s3);
            bottomHalf.addView(s4);

            preview.addView(topHalf);
            preview.addView(bottomHalf);
            
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
            preview.setBackground(previewBg);
            preview.setClipToOutline(true);

            row.addView(preview);

            TextView text = new TextView(this);
            text.setText(themes[i]);
            text.setTextColor(Color.WHITE);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            if (fontBold != null) text.setTypeface(fontBold);
            row.addView(text);

            if (isSelected) {
                View spacer = new View(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));
                
                TextView checkMark = new TextView(this);
                checkMark.setText("✔");
                checkMark.setTextColor(Color.parseColor("#81B64C"));
                checkMark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                if (fontBold != null) checkMark.setTypeface(fontBold);
                row.addView(checkMark);
            }

            row.setOnClickListener(v -> {
                getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("board_theme_light", lightColors[which])
                    .putString("board_theme_dark", darkColors[which])
                    .apply();
                loadBoardTheme();
                dialog.dismiss();
            });

            root.addView(row);
        }

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setAttributes(params);
        }
        dialog.show();
    }

    private void startTimer() {
        startTimer(false);
    }

    private void startTimer(boolean resume) {
        stopTimer();
        if (!resume) {
            elapsedSeconds = 0;
            timerView.setText("00:00");
        } else {
            int mins = elapsedSeconds / 60;
            int secs = elapsedSeconds % 60;
            timerView.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
        }
        normalTimer = new Timer();
        normalTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                elapsedSeconds++;
                runOnUiThread(() -> {
                    int mins = elapsedSeconds / 60;
                    int secs = elapsedSeconds % 60;
                    timerView.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
                });
            }
        }, 1000, 1000);
    }

    private void stopTimer() {
        if (normalTimer != null) {
            normalTimer.cancel();
            normalTimer = null;
        }
    }

    private void adjustUIForActiveMode() {
        runOnUiThread(() -> {
            if (activeMode.equals("rush")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Rush");
                if (ratingView != null) ratingView.setText("Score: 0");
                if (streakTextView != null) streakTextView.setVisibility(View.GONE);
                if (strikesContainer != null) {
                    strikesContainer.setVisibility(View.VISIBLE);
                    updateStrikesUI(0);
                }
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.GONE);
                if (flameIcon != null) flameIcon.setVisibility(View.GONE);
                if (streakProgressBar != null) {
                    streakProgressBar.setVisibility(View.VISIBLE);
                    streakProgressBar.setMax(180);
                    streakProgressBar.setProgress(180);
                }
            } else if (activeMode.equals("battle")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Battle");
                if (ratingView != null) ratingView.setText("You: 0  vs  Opponent: 0");
                if (streakTextView != null) streakTextView.setVisibility(View.GONE);
                if (strikesContainer != null) {
                    strikesContainer.setVisibility(View.VISIBLE);
                    updateStrikesUI(0);
                }
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.GONE);
                if (flameIcon != null) flameIcon.setVisibility(View.GONE);
                if (streakProgressBar != null) {
                    streakProgressBar.setVisibility(View.VISIBLE);
                    streakProgressBar.setMax(180);
                    streakProgressBar.setProgress(180);
                }
            } else if (activeMode.equals("theme")) {
                String themeLabel = "Theme Puzzles";
                if (selectedTheme != null) {
                    if (selectedTheme.equals("healthyMix")) {
                        themeLabel = "Theme: Healthy Mix";
                    } else if (selectedTheme.equals("opening")) {
                        themeLabel = "Theme: Opening Tactics";
                    } else if (selectedTheme.equals("middlegame")) {
                        themeLabel = "Theme: Middlegame Tactics";
                    } else if (selectedTheme.equals("endgame")) {
                        themeLabel = "Theme: Endgame Tactics";
                    } else if (selectedTheme.equals("rookEndgame")) {
                        themeLabel = "Theme: Rook Endgames";
                    } else if (selectedTheme.equals("bishopEndgame")) {
                        themeLabel = "Theme: Bishop Endgames";
                    } else if (selectedTheme.equals("pawnEndgame")) {
                        themeLabel = "Theme: Pawn Endgames";
                    } else if (selectedTheme.equals("knightEndgame")) {
                        themeLabel = "Theme: Knight Endgames";
                    } else if (selectedTheme.equals("queenEndgame")) {
                        themeLabel = "Theme: Queen Endgames";
                    } else if (selectedTheme.equals("queenRookEndgame")) {
                        themeLabel = "Theme: Queen & Rook";
                    } else {
                        themeLabel = "Theme: " + selectedTheme.substring(0, 1).toUpperCase() + selectedTheme.substring(1);
                    }
                }
                if (headerTitle != null) headerTitle.setText(themeLabel);
                if (streakTextView != null) {
                    streakTextView.setVisibility(View.VISIBLE);
                    streakTextView.setText(String.valueOf(currentStreak));
                }
                if (strikesContainer != null) strikesContainer.setVisibility(View.GONE);
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            } else if (activeMode.equals("custom")) {
                if (headerTitle != null) headerTitle.setText("Custom Puzzles");
                if (streakTextView != null) {
                    streakTextView.setVisibility(View.VISIBLE);
                    streakTextView.setText(String.valueOf(currentStreak));
                }
                if (strikesContainer != null) strikesContainer.setVisibility(View.GONE);
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            } else if (activeMode.equals("journey")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Journey");
                if (streakTextView != null) {
                    streakTextView.setVisibility(View.VISIBLE);
                    streakTextView.setText(String.valueOf(currentStreak));
                }
                if (strikesContainer != null) strikesContainer.setVisibility(View.GONE);
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            } else {
                if (headerTitle != null) headerTitle.setText("Lichess Puzzles");
                if (streakTextView != null) {
                    streakTextView.setVisibility(View.VISIBLE);
                    streakTextView.setText(String.valueOf(currentStreak));
                }
                if (strikesContainer != null) strikesContainer.setVisibility(View.GONE);
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showGameOverDialog(String title, String message, int score) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        
        float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#272522"));
        bg.setCornerRadius(r);
        bg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), Color.parseColor("#3D3A37"));
        layout.setBackground(bg);
        
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(pad, pad, pad, pad);
        
        TextView trophyView = new TextView(this);
        trophyView.setText(title.contains("Defeat") ? "💀" : "🏆");
        trophyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        trophyView.setGravity(Gravity.CENTER);
        layout.addView(trophyView);
        
        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleText.setPadding(0, 12, 0, 8);
        if (fontBold != null) {
            titleText.setTypeface(fontBold);
        } else {
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        titleText.setGravity(Gravity.CENTER);
        layout.addView(titleText);
        
        TextView msgText = new TextView(this);
        msgText.setText(message);
        msgText.setTextColor(Color.parseColor("#C3C2C1"));
        msgText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        msgText.setPadding(0, 0, 0, 24);
        if (fontRegular != null) {
            msgText.setTypeface(fontRegular);
        }
        msgText.setGravity(Gravity.CENTER);
        layout.addView(msgText);
        
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        
        TextView playAgainBtn = new TextView(this);
        playAgainBtn.setText("Play Again");
        playAgainBtn.setTextColor(Color.WHITE);
        playAgainBtn.setGravity(Gravity.CENTER);
        playAgainBtn.setPadding(32, 16, 32, 16);
        GradientDrawable greenBg = new GradientDrawable();
        greenBg.setColor(Color.parseColor("#81B64C"));
        greenBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        playAgainBtn.setBackground(greenBg);
        if (fontBold != null) {
            playAgainBtn.setTypeface(fontBold);
        }
        playAgainBtn.setClickable(true);
        
        TextView closeBtn = new TextView(this);
        closeBtn.setText("Close");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(32, 16, 32, 16);
        GradientDrawable greyBg = new GradientDrawable();
        greyBg.setColor(Color.parseColor("#484644"));
        greyBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        closeBtn.setBackground(greyBg);
        if (fontBold != null) {
            closeBtn.setTypeface(fontBold);
        }
        closeBtn.setClickable(true);
        
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        btnLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        playAgainBtn.setLayoutParams(btnLp);
        
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        closeBtn.setLayoutParams(closeLp);
        
        btnRow.addView(playAgainBtn);
        btnRow.addView(closeBtn);
        layout.addView(btnRow);
        
        dialog.setContentView(layout);
        
        playAgainBtn.setOnClickListener(v -> {
            dialog.dismiss();
            rushManager.startRun(activeMode.equals("battle"));
        });
        
        closeBtn.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        
        dialog.show();
    }

    private void loadOfflinePuzzlesAsync() {
        progressBar.setVisibility(View.VISIBLE);
        updateSpeechBubble("⬜ Initializing...", "Loading offline database...", Color.BLACK);
        new Thread(() -> {
            try {
                // Ensure db is loaded
                int count = dbHelper.getPuzzlesCount();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    int levelIndex = getIntent().getIntExtra("level_index", -1);
                    if (levelIndex != -1) {
                        activeMode = "journey";
                    } else {
                        activeMode = getIntent().getStringExtra("puzzle_mode");
                        if (activeMode == null) {
                            activeMode = "daily";
                        }
                    }
                    adjustUIForActiveMode();
                    
                    if (activeMode.equals("journey")) {
                        gameEngine.loadOfflineLevel(levelIndex);
                    } else if (activeMode.equals("rush") || activeMode.equals("battle")) {
                        rushManager.startRun(activeMode.equals("battle"));
                    } else if (activeMode.equals("custom")) {
                        gameEngine.loadOfflineRandom();
                    } else if (activeMode.equals("theme")) {
                        selectedTheme = getIntent().getStringExtra("puzzle_theme");
                        gameEngine.loadOfflineTheme(selectedTheme);
                    } else {
                        gameEngine.loadOnlinePuzzle(null);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load offline database", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showError("Failed to load database: " + e.getMessage());
                });
            }
        }).start();
    }

    private boolean isDailyCompleted() {
        if (activeMode.equals("daily") && gameEngine.getCurrentPuzzleId() != null) {
            String completedId = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .getString("completed_daily_puzzle_id", "");
            return gameEngine.getCurrentPuzzleId().equals(completedId);
        }
        return false;
    }

    private void saveStreak() {
        String prefKey = "puzzle_streak";
        if ("theme".equals(activeMode) && selectedTheme != null) {
            prefKey = "theme_streak_" + selectedTheme;
        } else if ("journey".equals(activeMode)) {
            prefKey = "journey_streak";
        }
        getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .edit()
                .putInt(prefKey, currentStreak)
                .apply();
    }

    private void saveLevelProgress() {
        int levelIndex = getIntent().getIntExtra("level_index", -1);
        if (levelIndex != -1) {
            int currentUnlocked = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .getInt("unlocked_level", 1);
            if (levelIndex == currentUnlocked) {
                getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .edit()
                        .putInt("unlocked_level", currentUnlocked + 1)
                        .apply();
            }
        }
    }

    private void saveDailyProgress() {
        if (activeMode.equals("daily") && gameEngine.getCurrentPuzzleId() != null) {
            getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("completed_daily_puzzle_id", gameEngine.getCurrentPuzzleId())
                    .apply();
            updateSpeechBubble("🎉 Completed!", "Today's daily puzzle is complete!", COLOR_GREEN);
            updateBottomActionPanel();
        }
    }

    private void loadNextPuzzle() {
        if (activeMode.equals("rush") || activeMode.equals("battle")) {
            if (rushManager.isFinished()) return;
            return;
        }

        if (gameEngine.getHistoryIndex() < gameEngine.getPuzzleHistory().size() - 1) {
            gameEngine.navigateForward();
        } else {
            int levelIndex = getIntent().getIntExtra("level_index", -1);
            if (levelIndex != -1) {
                int nextLevel = levelIndex + 1;
                int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .getInt("unlocked_level", 1);
                int totalSize = dbHelper.getPuzzlesCount();
                if (nextLevel <= unlockedLevel && nextLevel <= totalSize) {
                    getIntent().putExtra("level_index", nextLevel);
                    gameEngine.loadOfflineLevel(nextLevel);
                } else if (nextLevel > unlockedLevel) {
                    updateSpeechBubble("Locked 🔒", "Solve the current puzzle to unlock the next level!", COLOR_RED);
                } else {
                    updateSpeechBubble("Completed!", "You have completed all levels!", COLOR_GREEN);
                }
            } else {
                gameEngine.navigateForward();
            }
        }
    }

    // --- PuzzleGameEngine.PuzzleListener Callbacks ---
    
    @Override
    public void onPuzzleLoaded(String fen, boolean playerIsWhite, int rating, String theme, int levelIndex, boolean isOnline) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            updatePuzzleHeaderAndStats(levelIndex, rating, theme);
            resetTimerOnNextStateReset = true;
            gameEngine.resetPuzzleState(isDailyCompleted());
        });
    }

    @Override
    public void onMoveResult(boolean correct, boolean solved, String expectedUci, boolean isCapture) {
        runOnUiThread(() -> {
            if (correct) {
                if (expectedUci != null) {
                    String from = expectedUci.substring(0, 2);
                    String to = expectedUci.substring(2);
                    chessboard.makeMove(from, to);
                }
                soundManager.playMoveSound(isCapture);
                if (solved) {
                    updateSpeechBubble("🎉 Success!", "Puzzle Solved!", COLOR_GREEN);
                    soundManager.playSound("sounds/puzzles/puzzle-path/puzzle-solved.mp3");

                    if (activeMode.equals("rush") || activeMode.equals("battle")) {
                        rushManager.handleCorrectSolve();
                    } else {
                        updateBottomActionPanel();
                        currentStreak++;
                        saveStreak();
                        if (gameEngine.getCurrentPuzzleId() != null) {
                            final String puzzleId = gameEngine.getCurrentPuzzleId();
                            new Thread(() -> dbHelper.markAsSolved(puzzleId)).start();
                        }
                        updatePuzzleHeaderAndStats(gameEngine.getLevelIndex(), gameEngine.getRating(), gameEngine.getTheme());
                        saveLevelProgress();
                        saveDailyProgress();
                    }
                } else {
                    updateSpeechBubble("✅ Correct!", "Opponent is moving...", COLOR_GREEN);
                }
            } else {
                chessboard.clearArrow();
                updateSpeechBubble("❌ Wrong move!", "Try a different sequence of moves.", COLOR_RED);
                soundManager.playSound("sounds/puzzles/incorrect.mp3");

                if (activeMode.equals("rush") || activeMode.equals("battle")) {
                    rushManager.handleIncorrectSolve();
                } else {
                    chessboard.setInteractable(false);
                    chessboard.postDelayed(() -> {
                        resetTimerOnNextStateReset = true;
                        gameEngine.resetPuzzleState(isDailyCompleted());
                    }, 1000);
                }
            }
        });
    }

    @Override
    public void onOpponentMove(String uci) {
        runOnUiThread(() -> {
            if (gameEngine.isFinished()) return;
            String oppFrom = uci.substring(0, 2);
            String oppTo = uci.substring(2, 4);
            char oppTarget = chessboard.getPieceAt(oppTo);
            soundManager.playMoveSound(oppTarget != ' ');
            chessboard.makeMove(oppFrom, oppTo);

            chessboard.setInteractable(true);
            String sideToMoveText = gameEngine.isPlayerIsWhite() ? "⬜ White to Move" : "⬛ Black to Move";
            updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
        });
    }

    @Override
    public void onStateReset(String fen, boolean playerIsWhite, boolean flipped, boolean isInteractable, char playerColor, java.util.List<String> movesToApply) {
        runOnUiThread(() -> {
            chessboard.setFEN(fen);
            chessboard.setFlipped(flipped);
            chessboard.setInteractable(isInteractable);
            chessboard.setPlayerColor(playerColor);

            if (movesToApply != null && !movesToApply.isEmpty()) {
                for (int i = 0; i < movesToApply.size(); i++) {
                    String move = movesToApply.get(i);
                    String from = move.substring(0, 2);
                    String to = move.substring(2, 4);
                    char target = chessboard.getPieceAt(to);
                    chessboard.makeMove(from, to, false);
                    if (i == movesToApply.size() - 1) {
                        soundManager.playMoveSound(target != ' ');
                    }
                }
            }
            
            if (!activeMode.equals("rush") && !activeMode.equals("battle")) {
                if (resetTimerOnNextStateReset) {
                    startTimer(false);
                    resetTimerOnNextStateReset = false;
                } else {
                    if (normalTimer == null) {
                        startTimer(true);
                    }
                }
            }

            String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
            updateSpeechBubble(sideToMoveText, "Find the best sequence of moves.", Color.BLACK);
            updateBottomActionPanel();
        });
    }

    @Override
    public void onError(String msg) {
        runOnUiThread(() -> showError(msg));
    }

    // --- PuzzleRushManager.RushListener Callbacks ---

    @Override
    public void onLoadPuzzle(int targetRating) {
        JSONObject puzzleObj = dbHelper.getRushPuzzle(targetRating);
        if (puzzleObj != null) {
            gameEngine.initOfflinePuzzle(puzzleObj);
        } else {
            showError("No suitable puzzle found.");
        }
    }

    @Override
    public void onUIUpdate(int score, int strikes, int opponentScore, int timeLeft) {
        runOnUiThread(() -> {
            if (activeMode.equals("rush")) {
                if (ratingView != null) ratingView.setText("Score: " + score);
            } else if (activeMode.equals("battle")) {
                if (ratingView != null) ratingView.setText("You: " + score + "  vs  Opponent: " + opponentScore);
            }

            if (strikesContainer != null) {
                updateStrikesUI(strikes);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                    if (i < strikes) {
                        sb.append("❌ ");
                    } else {
                        sb.append("⬜ ");
                    }
                }
                if (streakTextView != null) {
                    streakTextView.setText(sb.toString().trim());
                }
            }

            int mins = timeLeft / 60;
            int secs = timeLeft % 60;
            timerView.setText(String.format(Locale.US, "%02d:%02d", mins, secs));

            if (streakProgressBar != null) {
                streakProgressBar.setProgress(timeLeft);
            }
        });
    }

    private void updateStrikesUI(int strikes) {
        if (strikesContainer == null) return;
        strikesContainer.removeAllViews();
        for (int i = 0; i < 3; i++) {
            TextView strikeView = new TextView(this);
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
            strikeView.setLayoutParams(lp);
            strikeView.setGravity(Gravity.CENTER);
            
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            if (i < strikes) {
                shape.setColor(Color.parseColor("#E74C3C")); // Red filled circle
                strikeView.setText("✕");
                strikeView.setTextColor(Color.WHITE);
                strikeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                if (fontBold != null) strikeView.setTypeface(fontBold);
            } else {
                shape.setColor(Color.parseColor("#312E2B")); // Empty dark background
                shape.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics()), Color.parseColor("#454341")); // Thin border
                strikeView.setText("");
            }
            strikeView.setBackground(shape);
            strikesContainer.addView(strikeView);
        }
    }

    @Override
    public void onOpponentSolved(int opponentScore) {
        runOnUiThread(() -> {
            if (ratingView != null) {
                ratingView.setText("You: " + rushManager.getScore() + "  vs  Opponent: " + opponentScore);
            }
            updateSpeechBubble("⚔️ Battle Status", "Opponent solved a puzzle! (" + opponentScore + ")", Color.parseColor("#E74C3C"));
        });
    }

    @Override
    public void onGameOver(String title, String message, int score) {
        runOnUiThread(() -> {
            stopTimer();
            chessboard.setInteractable(false);
            showGameOverDialog(title, message, score);
        });
    }

    // --- LichessBoardView.BoardListener ---
    
    @Override
    public void onMove(String fromSquare, String toSquare) {
        char targetPiece = chessboard.getPieceAt(toSquare);
        boolean isCapture = targetPiece != ' ';

        if (gameEngine.isAnalysisMode()) {
            chessboard.makeMove(fromSquare, toSquare);
            soundManager.playMoveSound(isCapture);
            return;
        }

        String playerMove = fromSquare + toSquare;
        String[] solutionMoves = gameEngine.getSolutionMoves();
        int currentMoveIdx = gameEngine.getCurrentMoveIdx();
        if (solutionMoves != null && currentMoveIdx < solutionMoves.length) {
            String expectedMove = solutionMoves[currentMoveIdx];
            if (!playerMove.equalsIgnoreCase(expectedMove.substring(0, 4))) {
                chessboard.makeMove(fromSquare, toSquare);
            }
        }

        gameEngine.submitMove(fromSquare, toSquare, isCapture);
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
            coachAvatar.setVisibility(View.VISIBLE);
            if (speechBubbleTail != null) {
                speechBubbleTail.setVisibility(View.VISIBLE);
            }
        } else {
            coachAvatar.setVisibility(View.GONE);
            if (speechBubbleTail != null) {
                speechBubbleTail.setVisibility(View.GONE);
            }
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            updateSpeechBubble("❌ Error", message, COLOR_RED);
            ratingView.setText("Connection Error");
        });
    }

    private void updatePuzzleHeaderAndStats(int levelIdx, int rating, String theme) {
        runOnUiThread(() -> {
            if (activeMode.equals("rush") || activeMode.equals("battle")) {
                return;
            }

            int referenceCount;
            if (activeMode.equals("theme")) {
                referenceCount = currentStreak;
            } else {
                referenceCount = levelIdx;
                if (referenceCount == -1) {
                    int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                            .getInt("unlocked_level", 1);
                    referenceCount = Math.max(0, unlockedLevel - 1);
                }
            }

            if (headerTitle != null) {
                if (activeMode.equals("theme")) {
                    String themeLabel = "Theme Puzzles";
                    if (selectedTheme != null) {
                        if (selectedTheme.equals("healthyMix")) {
                            themeLabel = "Theme: Healthy Mix";
                        } else if (selectedTheme.equals("opening")) {
                            themeLabel = "Theme: Opening Tactics";
                        } else if (selectedTheme.equals("middlegame")) {
                            themeLabel = "Theme: Middlegame Tactics";
                        } else if (selectedTheme.equals("endgame")) {
                            themeLabel = "Theme: Endgame Tactics";
                        } else if (selectedTheme.equals("rookEndgame")) {
                            themeLabel = "Theme: Rook Endgames";
                        } else if (selectedTheme.equals("bishopEndgame")) {
                            themeLabel = "Theme: Bishop Endgames";
                        } else if (selectedTheme.equals("pawnEndgame")) {
                            themeLabel = "Theme: Pawn Endgames";
                        } else if (selectedTheme.equals("knightEndgame")) {
                            themeLabel = "Theme: Knight Endgames";
                        } else if (selectedTheme.equals("queenEndgame")) {
                            themeLabel = "Theme: Queen Endgames";
                        } else if (selectedTheme.equals("queenRookEndgame")) {
                            themeLabel = "Theme: Queen & Rook";
                        } else {
                            themeLabel = "Theme: " + selectedTheme.substring(0, 1).toUpperCase() + selectedTheme.substring(1);
                        }
                    }
                    headerTitle.setText(themeLabel);
                } else if (levelIdx != -1) {
                    headerTitle.setText(levelIdx + " Puzzles");
                } else {
                    if (activeMode.equals("custom")) {
                        headerTitle.setText("Custom Puzzles");
                    } else {
                        headerTitle.setText("Lichess Puzzles");
                    }
                }
            }
            if (ratingView != null) {
                ratingView.setText(String.valueOf(rating));
            }
            if (streakTextView != null) {
                streakTextView.setText(String.valueOf(referenceCount));
            }
            if (streakProgressBar != null && streakMilestoneBadge != null) {
                int targetMilestone = referenceCount <= 0 ? 10 : ((referenceCount / 10) + 1) * 10;
                streakProgressBar.setMax(10);
                streakProgressBar.setProgress(referenceCount % 10);
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

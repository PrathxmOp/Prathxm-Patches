package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class StandaloneLichessActivity extends Activity implements LichessBoardView.BoardListener {

    private static final String TAG = "StandaloneLichess";

    private FrameLayout mainContainer;
    private LinearLayout gameLayout;
    private LichessBoardView chessboard;
    private TextView headerTitle;
    private TextView titleView;
    private TextView statusBanner;
    private TextView ratingView;
    private TextView streakTextView;
    private android.widget.ImageView sideColorIndicator;
    private ProgressBar progressBar;
    private ProgressBar streakProgressBar;
    private TextView streakMilestoneBadge;
    private int currentStreak = 0;
    private int maxMoveIdxReached = 1;
    private android.widget.ImageView coachAvatar;
    private android.graphics.Typeface fontBold;
    private android.graphics.Typeface fontRegular;

    // Low latency sound caching fields
    private android.media.SoundPool soundPool;
    private int soundCorrectId = -1;
    private int soundIncorrectId = -1;
    private int soundSolvedId = -1;
    private int soundMoveSelfId = -1;
    private int soundCaptureId = -1;

    // Puzzle modes
    private String activeMode = "daily";
    private int rushScore = 0;
    private int rushStrikes = 0;
    private int rushTimeLeft = 180;
    private int opponentScore = 0;
    private int opponentSolveCountdown = 20;
    private android.widget.ImageView flameIcon;
    
    // Offline Database
    private LinearLayout downloadOverlay;
    private TextView downloadStatusText;
    private ProgressBar downloadProgressBar;
    private final List<JSONObject> offlinePuzzles = new ArrayList<>();
    
    // Game State
    private String startFen;
    private String currentPuzzleId = null;
    private String[] solutionMoves;
    private int currentMoveIdx = 0;
    private boolean isPlayerTurn = false;
    private boolean isFinished = false;
    private boolean isAnalysisMode = false;
    private boolean playerIsWhite = true;
    private LinearLayout actionsLayout;
    private View speechBubbleTail;

    // Timer State
    private TextView timerView;
    private int elapsedSeconds = 0;
    private Timer timer;

    // Theme Colors (Premium Dark Theme - matching Chess.com style)
    private final int COLOR_BG = Color.parseColor("#302e2b");
    private final int COLOR_CARD = Color.parseColor("#272522");
    private final int COLOR_GREEN = Color.parseColor("#81B64C");
    private final int COLOR_RED = Color.parseColor("#FA412F");
    private final int COLOR_ORANGE = Color.parseColor("#FF5B00");
    private final int COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private final int COLOR_TEXT_SECONDARY = Color.parseColor("#B1B0AE");

    // History tracking helper
    private static class PuzzleHistoryItem {
        final String fen;
        final String[] moves;
        final int rating;
        final String theme;
        final int levelIndex;

        PuzzleHistoryItem(String fen, String[] moves, int rating, String theme, int levelIndex) {
            this.fen = fen;
            this.moves = moves;
            this.rating = rating;
            this.theme = theme;
            this.levelIndex = levelIndex;
        }
    }

    private final List<PuzzleHistoryItem> puzzleHistory = new ArrayList<>();
    private int historyIndex = -1;
    private int hintClickCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentStreak = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE).getInt("puzzle_streak", 0);
        setupPremiumUI();
        initSoundPool();

        // Migrate/delete old uncompressed puzzles.json
        File oldFile = new File(getFilesDir(), "puzzles.json");
        if (oldFile.exists()) {
            oldFile.delete();
        }

        File localFile = new File(getFilesDir(), "puzzles.json.gz");
        if (localFile.exists() && localFile.length() > 0) {
            loadOfflinePuzzlesAsync(localFile);
        } else {
            if (isNetworkAvailable()) {
                startDownloadPuzzles();
            } else {
                showOfflineWarningOverlay();
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDestroy();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
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

        // Center Title Block
        LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.HORIZONTAL);
        titleContainer.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
        titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
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
        if (fontBold != null) {
            headerTitle.setTypeface(fontBold);
        } else {
            headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        titleContainer.addView(headerTitle);
        header.addView(titleContainer);

        // Settings Cog Button
        android.widget.ImageView settingsBtn = new android.widget.ImageView(this);
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
        contentLayout.setPadding(0, 0, 0, 0); // Flush edges for chessboard

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

        // Speech Bubble Layout (Horizontal containing Tail and Body)
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
        tailParams.rightMargin = -1; // slight overlap
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
        
        // Side color square indicator
        sideColorIndicator = new android.widget.ImageView(this);
        int indSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams indParams = new LinearLayout.LayoutParams(indSize, indSize);
        indParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        sideColorIndicator.setLayoutParams(indParams);
        updateSideColorIndicator(playerIsWhite);
        sideRow.addView(sideColorIndicator);

        statusBanner = new TextView(this);
        statusBanner.setText(playerIsWhite ? "White to Move" : "Black to Move");
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
        titleView.setMinLines(2); // Fix layout shifts by pre-allocating space for 2 lines
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

        // --- Bottom Panel (Streak + Orange Progress Bar + Actions) ---
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

        // Rating & Streak Container
        LinearLayout ratingStreakContainer = new LinearLayout(this);
        ratingStreakContainer.setOrientation(LinearLayout.HORIZONTAL);
        ratingStreakContainer.setGravity(Gravity.CENTER_VERTICAL);
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

        // Flame icon
        flameIcon = new android.widget.ImageView(this);
        int flameResId = getDrawableResId("ic_col_flame_24");
        if (flameResId != 0) {
            flameIcon.setImageResource(flameResId);
        }
        int flameSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams flameLp = new LinearLayout.LayoutParams(flameSize, flameSize);
        flameLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        flameLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        flameIcon.setLayoutParams(flameLp);
        ratingStreakContainer.addView(flameIcon);

        streakTextView = new TextView(this);
        streakTextView.setText(String.valueOf(currentStreak));
        streakTextView.setTextColor(Color.parseColor("#E55B1E"));
        streakTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) {
            streakTextView.setTypeface(fontBold);
        } else {
            streakTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        ratingStreakContainer.addView(streakTextView);
        statsRow.addView(ratingStreakContainer);

        // Timer Container
        LinearLayout timerContainer = new LinearLayout(this);
        timerContainer.setOrientation(LinearLayout.HORIZONTAL);
        timerContainer.setGravity(Gravity.CENTER_VERTICAL);

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

        // Progress Container (ProgressBar + Target Badge on Right)
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.HORIZONTAL);
        progressContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams containerParamsBottom = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParamsBottom.setMargins(0, 4, 0, 24);
        progressContainer.setLayoutParams(containerParamsBottom);

        // Progress Bar
        streakProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        streakProgressBar.setMax(10);
        streakProgressBar.setProgress(0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            streakProgressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(COLOR_ORANGE));
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

            if (!isFinished) {
                // While solving: 3 standard buttons (Hint, Back, Forward)
                // Hint
                actionsLayout.addView(createActionItem("glyph_device_bulb_glow", "Hint", v -> {
                    if (solutionMoves != null && currentMoveIdx < solutionMoves.length && isPlayerTurn && !isFinished) {
                        String expectedMove = solutionMoves[currentMoveIdx];
                        String fromSquare = expectedMove.substring(0, 2);
                        String toSquare = expectedMove.substring(2, 4);
                        if (hintClickCount == 0) {
                            chessboard.showHint(fromSquare);
                            chessboard.clearArrow();
                            hintClickCount = 1;
                            updateSpeechBubble("💡 Hint (1/2)", "Find where to move the highlighted piece.", Color.parseColor("#E67E22"));
                        } else {
                            chessboard.showArrow(fromSquare, toSquare);
                            hintClickCount = 0;
                            updateSpeechBubble("💡 Hint (2/2)", "Move the piece along the arrow.", Color.parseColor("#E67E22"));
                        }
                    }
                }));

                // Back
                actionsLayout.addView(createActionItem("glyph_arrow_chevron_left", "Back", v -> {
                    if (solutionMoves != null && currentMoveIdx > 1) {
                        undoLastMove();
                    }
                }));

                // Forward
                actionsLayout.addView(createActionItem("glyph_arrow_chevron_right", "Forward", v -> {
                    if (solutionMoves != null && currentMoveIdx < maxMoveIdxReached) {
                        redoNextMove();
                    }
                }));
            } else {
                boolean isDailyCompleted = false;
                if (activeMode.equals("daily") && currentPuzzleId != null) {
                    String completedId = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                            .getString("completed_daily_puzzle_id", "");
                    if (currentPuzzleId.equals(completedId)) {
                        isDailyCompleted = true;
                    }
                }

                if (isAnalysisMode) {
                    // In Analysis Mode: Reset, Next (Green Button)
                    final boolean dailyComp = isDailyCompleted;
                    actionsLayout.addView(createActionItem("glyph_arrow_spin_redo", "Reset", v -> {
                        if (dailyComp) {
                            updateSpeechBubble("🎉 Completed!", "Today's daily puzzle is complete!", COLOR_GREEN);
                            return;
                        }
                        isAnalysisMode = false;
                        resetPuzzleState();
                    }));

                    actionsLayout.addView(createGreenNextButton());
                } else {
                    // Solved: Restart, Analysis, Next (Green Button)
                    // Restart
                    if (!isDailyCompleted) {
                        actionsLayout.addView(createActionItem("glyph_arrow_spin_redo", "Restart", v -> {
                            resetPuzzleState();
                        }));
                    }

                    // Analysis
                    actionsLayout.addView(createActionItem("glyph_board_analysis", "Analysis", v -> {
                        isAnalysisMode = true;
                        chessboard.setInteractable(true);
                        chessboard.setPlayerColor('a'); // 'a' for any / both sides
                        updateSpeechBubble("🔍 Analysis Mode", "Explore moves freely on the board.", Color.parseColor("#2980B9"));
                        updateBottomActionPanel();
                    }));

                    // Next (Green Button)
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

        boolean isDailyCompleted = false;
        if (activeMode.equals("daily") && currentPuzzleId != null) {
            String completedId = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .getString("completed_daily_puzzle_id", "");
            if (currentPuzzleId.equals(completedId)) {
                isDailyCompleted = true;
            }
        }

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
        
        // Round corners for dialog
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

            // 2x2 chess square preview
            LinearLayout preview = new LinearLayout(this);
            preview.setOrientation(LinearLayout.VERTICAL);
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(size, size);
            previewParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            preview.setLayoutParams(previewParams);

            // Top half
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

            // Bottom half
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
        stopTimer();
        if (activeMode.equals("rush") || activeMode.equals("battle")) {
            timerView.setText("03:00");
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    rushTimeLeft--;
                    
                    // Simulated opponent tick in Battle mode
                    if (activeMode.equals("battle") && !isFinished) {
                        opponentSolveCountdown--;
                        if (opponentSolveCountdown <= 0) {
                            opponentScore++;
                            runOnUiThread(() -> {
                                if (ratingView != null) {
                                    ratingView.setText("You: " + rushScore + "  vs  Opponent: " + opponentScore);
                                }
                                updateSpeechBubble("⚔️ Battle Status", "Opponent solved a puzzle! (" + opponentScore + ")", Color.parseColor("#E74C3C"));
                            });
                            opponentSolveCountdown = 12 + (int)(Math.random() * 12);
                        }
                    }

                    runOnUiThread(() -> {
                        if (rushTimeLeft <= 0) {
                            rushTimeLeft = 0;
                            stopTimer();
                            isFinished = true;
                            isPlayerTurn = false;
                            chessboard.setInteractable(false);
                            if (activeMode.equals("rush")) {
                                endRushRun();
                            } else {
                                endBattleRun();
                            }
                        }
                        int mins = rushTimeLeft / 60;
                        int secs = rushTimeLeft % 60;
                        timerView.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
                        if (streakProgressBar != null) {
                            streakProgressBar.setProgress(rushTimeLeft);
                        }
                    });
                }
            }, 1000, 1000);
        } else {
            elapsedSeconds = 0;
            timerView.setText("00:00");
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
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
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void adjustUIForActiveMode() {
        runOnUiThread(() -> {
            if (activeMode.equals("rush")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Rush");
                if (ratingView != null) ratingView.setText("Score: 0");
                if (streakTextView != null) streakTextView.setText("⬜ ⬜ ⬜");
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
                if (streakTextView != null) streakTextView.setText("⬜ ⬜ ⬜");
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.GONE);
                if (flameIcon != null) flameIcon.setVisibility(View.GONE);
                if (streakProgressBar != null) {
                    streakProgressBar.setVisibility(View.VISIBLE);
                    streakProgressBar.setMax(180);
                    streakProgressBar.setProgress(180);
                }
            } else if (activeMode.equals("custom")) {
                if (headerTitle != null) headerTitle.setText("Custom Puzzles");
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            } else if (activeMode.equals("journey")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Journey");
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            } else {
                if (headerTitle != null) headerTitle.setText("Lichess Puzzles");
                if (streakMilestoneBadge != null) streakMilestoneBadge.setVisibility(View.VISIBLE);
                if (flameIcon != null) flameIcon.setVisibility(View.VISIBLE);
                if (streakProgressBar != null) streakProgressBar.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadRushOrBattlePuzzle() {
        if (offlinePuzzles.isEmpty()) {
            showError("No offline puzzles available.");
            return;
        }
        int targetRating = 600 + rushScore * 45;
        if (targetRating > 2800) targetRating = 2800;

        int baseIdx = 0;
        for (int i = 0; i < offlinePuzzles.size(); i++) {
            if (offlinePuzzles.get(i).optInt("rating", 1500) >= targetRating) {
                baseIdx = i;
                break;
            }
        }
        
        int range = Math.min(15, offlinePuzzles.size() - baseIdx);
        int randOffset = (range > 0) ? (int)(Math.random() * range) : 0;
        int selectedIdx = baseIdx + randOffset;
        if (selectedIdx >= offlinePuzzles.size()) {
            selectedIdx = offlinePuzzles.size() - 1;
        }
        
        JSONObject puzzleObj = offlinePuzzles.get(selectedIdx);
        initOfflinePuzzle(puzzleObj);
    }

    private void updateStrikesUI() {
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                if (i < rushStrikes) {
                    sb.append("❌ ");
                } else {
                    sb.append("⬜ ");
                }
            }
            if (streakTextView != null) {
                streakTextView.setText(sb.toString().trim());
            }
        });
    }

    private void resetRushOrBattleRun() {
        rushScore = 0;
        rushStrikes = 0;
        rushTimeLeft = 180;
        opponentScore = 0;
        opponentSolveCountdown = 15 + (int)(Math.random() * 10);
        isFinished = false;
        
        if (activeMode.equals("rush")) {
            if (ratingView != null) ratingView.setText("Score: 0");
        } else if (activeMode.equals("battle")) {
            if (ratingView != null) ratingView.setText("You: 0  vs  Opponent: 0");
        }
        
        updateStrikesUI();
        loadRushOrBattlePuzzle();
        startTimer();
    }

    private void endRushRun() {
        runOnUiThread(() -> {
            stopTimer();
            showGameOverDialog("Puzzle Rush Complete!", "You solved " + rushScore + " puzzles!", rushScore);
        });
    }

    private void endBattleRun() {
        runOnUiThread(() -> {
            stopTimer();
            String title;
            String message;
            if (rushScore > opponentScore) {
                title = "🏆 Victory!";
                message = "You defeated your opponent " + rushScore + " to " + opponentScore + "!";
            } else if (rushScore < opponentScore) {
                title = "💀 Defeat!";
                message = "Opponent won the battle " + opponentScore + " to " + rushScore + ".";
            } else {
                title = "🤝 Draw!";
                message = "A close match! Both scored " + rushScore + ".";
            }
            showGameOverDialog(title, message, rushScore);
        });
    }

    private void showGameOverDialog(String title, String message, int score) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        
        // Custom View
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        
        // Round corners for dialog
        float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#272522"));
        bg.setCornerRadius(r);
        bg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), Color.parseColor("#3D3A37"));
        layout.setBackground(bg);
        
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(pad, pad, pad, pad);
        
        // Trophy / Skull Emoji
        TextView trophyView = new TextView(this);
        trophyView.setText(title.contains("Defeat") ? "💀" : "🏆");
        trophyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        trophyView.setGravity(Gravity.CENTER);
        layout.addView(trophyView);
        
        // Title text
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
        
        // Message text
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
        
        // Buttons Row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        
        // Play Again Button (Green)
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
        
        // Close Button (Grey)
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
            resetRushOrBattleRun();
        });
        
        closeBtn.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        
        dialog.show();
    }

    private void loadOfflinePuzzlesAsync(File file) {
        progressBar.setVisibility(View.VISIBLE);
        updateSpeechBubble("⬜ Initializing...", "Loading offline database...", Color.BLACK);
        new Thread(() -> {
            try {
                loadOfflinePuzzlesFromLocalFile(file);
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
                        loadSpecificOfflineLevel(levelIndex);
                    } else if (activeMode.equals("rush") || activeMode.equals("battle")) {
                        resetRushOrBattleRun();
                    } else if (activeMode.equals("custom")) {
                        loadRandomOfflinePuzzle();
                    } else {
                        loadPuzzle(null);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load offline database", e);
                runOnUiThread(() -> {
                    showError("Failed to parse local puzzles.");
                });
            }
        }).start();
    }

    private void loadOfflinePuzzlesFromLocalFile(File file) throws Exception {
        java.io.InputStream fileStream = new java.io.FileInputStream(file);
        java.io.InputStream gzipStream = new java.util.zip.GZIPInputStream(fileStream);
        BufferedReader br = new BufferedReader(new java.io.InputStreamReader(gzipStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();

        JSONArray arr = new JSONArray(sb.toString());
        offlinePuzzles.clear();
        for (int i = 0; i < arr.length(); i++) {
            offlinePuzzles.add(arr.getJSONObject(i));
        }
        
        // Sort by rating so that difficulty curve is smooth
        java.util.Collections.sort(offlinePuzzles, (p1, p2) -> {
            int r1 = p1.optInt("rating", 1500);
            int r2 = p2.optInt("rating", 1500);
            return Integer.compare(r1, r2);
        });
    }

    private void startDownloadPuzzles() {
        showDownloadProgress("Downloading 10,000 offline puzzles (0%)...\nPlease wait.", 0);
        new Thread(() -> {
            try {
                JSONArray allPuzzles = new JSONArray();
                int totalBatches = 10;
                int limitPerBatch = 1000;

                for (int i = 0; i < totalBatches; i++) {
                    int offset = i * limitPerBatch;
                    final int progressPercent = (i * 100) / totalBatches;
                    final int batchNum = i + 1;
                    showDownloadProgress(
                            String.format(Locale.US, "Downloading 10,000 offline puzzles...\nBatch %d/%d (%d%%)", batchNum, totalBatches, progressPercent),
                            progressPercent
                    );

                    String urlStr = String.format(Locale.US, "https://datasets-server.huggingface.co/rows?dataset=Lichess%%2Fchess-puzzles&config=default&split=train&offset=%d&limit=%d", offset, limitPerBatch);
                    URL url = new URL(urlStr);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);

                    int code = connection.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject responseJson = new JSONObject(response.toString());
                        JSONArray rows = responseJson.getJSONArray("rows");
                        for (int j = 0; j < rows.length(); j++) {
                            JSONObject rowObj = rows.getJSONObject(j).getJSONObject("row");
                            JSONObject puzzleItem = new JSONObject();
                            puzzleItem.put("id", rowObj.getString("PuzzleId"));
                            puzzleItem.put("fen", rowObj.getString("FEN"));
                            puzzleItem.put("moves", rowObj.getString("Moves"));
                            puzzleItem.put("rating", rowObj.getInt("Rating"));

                            JSONArray themesArr = rowObj.optJSONArray("Themes");
                            String theme = (themesArr != null && themesArr.length() > 0) ? themesArr.getString(0) : "tactics";
                            puzzleItem.put("theme", theme);

                            allPuzzles.put(puzzleItem);
                        }
                    } else {
                        throw new Exception("HTTP server error: " + code);
                    }
                    connection.disconnect();
                    Thread.sleep(100);
                }

                showDownloadProgress("Saving puzzles database to storage...", 100);

                File file = new File(getFilesDir(), "puzzles.json.gz");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(fos);
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(gzos, "UTF-8");
                writer.write(allPuzzles.toString());
                writer.flush();
                writer.close();

                loadOfflinePuzzlesFromLocalFile(file);

                runOnUiThread(() -> {
                    hideDownloadUI();
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
                        loadSpecificOfflineLevel(levelIndex);
                    } else if (activeMode.equals("rush") || activeMode.equals("battle")) {
                        resetRushOrBattleRun();
                    } else if (activeMode.equals("custom")) {
                        loadRandomOfflinePuzzle();
                    } else {
                        loadPuzzle(null);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                runOnUiThread(() -> showDownloadFailed("Failed to download puzzles: " + e.getMessage()));
            }
        }).start();
    }

    private void showDownloadProgress(String message, int progressVal) {
        runOnUiThread(() -> {
            gameLayout.setVisibility(View.GONE);
            
            if (downloadOverlay == null || downloadOverlay.getParent() == null) {
                if (downloadOverlay != null) {
                    mainContainer.removeView(downloadOverlay);
                }
                downloadOverlay = new LinearLayout(this);
                downloadOverlay.setOrientation(LinearLayout.VERTICAL);
                downloadOverlay.setGravity(Gravity.CENTER);
                downloadOverlay.setBackgroundColor(COLOR_BG);
                downloadOverlay.setPadding(64, 64, 64, 64);

                TextView titleText = new TextView(this);
                titleText.setText("Initializing Offline Database");
                titleText.setTextColor(COLOR_TEXT_PRIMARY);
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                titleText.setGravity(Gravity.CENTER);
                titleText.setPadding(0, 0, 0, 16);
                downloadOverlay.addView(titleText);

                downloadStatusText = new TextView(this);
                downloadStatusText.setText(message);
                downloadStatusText.setTextColor(COLOR_TEXT_SECONDARY);
                downloadStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                downloadStatusText.setGravity(Gravity.CENTER);
                downloadStatusText.setPadding(0, 0, 0, 32);
                downloadOverlay.addView(downloadStatusText);

                downloadProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                downloadProgressBar.setMax(100);
                downloadProgressBar.setProgress(progressVal);
                
                LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics())
                );
                pbParams.setMargins(0, 0, 0, 32);
                downloadProgressBar.setLayoutParams(pbParams);
                downloadOverlay.addView(downloadProgressBar);

                mainContainer.addView(downloadOverlay, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            } else {
                downloadStatusText.setText(message);
                downloadProgressBar.setProgress(progressVal);
            }
        });
    }

    private void showOfflineWarningOverlay() {
        runOnUiThread(() -> {
            gameLayout.setVisibility(View.GONE);
            
            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
            }

            downloadOverlay = new LinearLayout(this);
            downloadOverlay.setOrientation(LinearLayout.VERTICAL);
            downloadOverlay.setGravity(Gravity.CENTER);
            downloadOverlay.setBackgroundColor(COLOR_BG);
            downloadOverlay.setPadding(64, 64, 64, 64);

            TextView warningIcon = new TextView(this);
            warningIcon.setText("⚠");
            warningIcon.setTextColor(COLOR_RED);
            warningIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
            warningIcon.setGravity(Gravity.CENTER);
            downloadOverlay.addView(warningIcon);

            TextView titleText = new TextView(this);
            titleText.setText("Puzzles Not Downloaded");
            titleText.setTextColor(COLOR_TEXT_PRIMARY);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 32, 0, 16);
            downloadOverlay.addView(titleText);

            TextView descText = new TextView(this);
            descText.setText("To play fully offline, the Lichess puzzle database of 10,000 puzzles must be downloaded on first launch.\n\nPlease connect to the internet and click Retry.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            descText.setGravity(Gravity.CENTER);
            descText.setPadding(0, 0, 0, 48);
            downloadOverlay.addView(descText);

            Button retryButton = new Button(this);
            retryButton.setText("Retry Download");
            retryButton.setTextColor(Color.WHITE);
            retryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            retryButton.setTypeface(null, android.graphics.Typeface.BOLD);
            retryButton.setAllCaps(false);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(COLOR_GREEN);
            btnBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
            retryButton.setBackground(btnBg);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics())
            );
            retryButton.setLayoutParams(btnParams);
            retryButton.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    startDownloadPuzzles();
                } else {
                    descText.setText("Still offline. Please check your internet connection.\n\nTo play fully offline, the Lichess puzzle database of 10,000 puzzles must be downloaded on first launch.");
                    descText.setTextColor(COLOR_RED);
                }
            });
            downloadOverlay.addView(retryButton);

            mainContainer.addView(downloadOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        });
    }

    private void showDownloadFailed(String errorMessage) {
        runOnUiThread(() -> {
            gameLayout.setVisibility(View.GONE);
            
            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
            }

            downloadOverlay = new LinearLayout(this);
            downloadOverlay.setOrientation(LinearLayout.VERTICAL);
            downloadOverlay.setGravity(Gravity.CENTER);
            downloadOverlay.setBackgroundColor(COLOR_BG);
            downloadOverlay.setPadding(64, 64, 64, 64);

            TextView failIcon = new TextView(this);
            failIcon.setText("❌");
            failIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
            failIcon.setGravity(Gravity.CENTER);
            downloadOverlay.addView(failIcon);

            TextView titleText = new TextView(this);
            titleText.setText("Download Failed");
            titleText.setTextColor(COLOR_TEXT_PRIMARY);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 32, 0, 16);
            downloadOverlay.addView(titleText);

            TextView descText = new TextView(this);
            descText.setText(errorMessage + "\n\nPlease check your internet connection and try again.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            descText.setGravity(Gravity.CENTER);
            descText.setPadding(0, 0, 0, 48);
            downloadOverlay.addView(descText);

            Button retryButton = new Button(this);
            retryButton.setText("Retry Download");
            retryButton.setTextColor(Color.WHITE);
            retryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            retryButton.setTypeface(null, android.graphics.Typeface.BOLD);
            retryButton.setAllCaps(false);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(COLOR_GREEN);
            btnBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
            retryButton.setBackground(btnBg);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics())
            );
            retryButton.setLayoutParams(btnParams);
            retryButton.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    startDownloadPuzzles();
                } else {
                    descText.setText("Still offline. Please check your internet connection and try again.");
                    descText.setTextColor(COLOR_RED);
                }
            });
            downloadOverlay.addView(retryButton);

            mainContainer.addView(downloadOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        });
    }

    private void hideDownloadUI() {
        runOnUiThread(() -> {
            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
                downloadOverlay = null;
            }
            gameLayout.setVisibility(View.VISIBLE);
        });
    }

    private void loadPuzzle(String puzzleId) {
        progressBar.setVisibility(View.VISIBLE);
        updateSpeechBubble("⬜ Connecting...", "Loading daily puzzle details...", Color.BLACK);
        ratingView.setText("Loading...");

        new Thread(() -> {
            try {
                if (!isNetworkAvailable()) {
                    runOnUiThread(this::loadRandomOfflinePuzzle);
                    return;
                }

                String urlStr = (puzzleId == null) 
                        ? "https://lichess.org/api/puzzle/daily" 
                        : "https://lichess.org/api/puzzle/" + puzzleId;
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    parseAndInit(response.toString());
                } else {
                    runOnUiThread(this::loadRandomOfflinePuzzle);
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Network puzzle load failed, falling back to offline", e);
                runOnUiThread(this::loadRandomOfflinePuzzle);
            }
        }).start();
    }

    private void addToHistory(PuzzleHistoryItem item) {
        while (puzzleHistory.size() > historyIndex + 1) {
            puzzleHistory.remove(puzzleHistory.size() - 1);
        }
        puzzleHistory.add(item);
        historyIndex = puzzleHistory.size() - 1;
    }

    private void loadHistoryItem(PuzzleHistoryItem item) {
        this.startFen = item.fen;
        this.solutionMoves = item.moves;
        this.hintClickCount = 0;

        if (item.levelIndex != -1) {
            getIntent().putExtra("level_index", item.levelIndex);
        } else {
            getIntent().removeExtra("level_index");
        }

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            updatePuzzleHeaderAndStats(item.levelIndex, item.rating, item.theme);
            resetPuzzleState();
        });
    }

    private void loadPreviousPuzzle() {
        if (historyIndex > 0) {
            historyIndex--;
            loadHistoryItem(puzzleHistory.get(historyIndex));
        } else {
            int levelIndex = getIntent().getIntExtra("level_index", -1);
            if (levelIndex > 1) {
                int prevLevel = levelIndex - 1;
                getIntent().putExtra("level_index", prevLevel);
                loadSpecificOfflineLevel(prevLevel);
            } else {
                updateSpeechBubble("First Puzzle", "No previous puzzles to load.", Color.BLACK);
            }
        }
    }

    private void loadNextPuzzle() {
        if (activeMode.equals("rush") || activeMode.equals("battle")) {
            if (isFinished) return;
            loadRushOrBattlePuzzle();
            return;
        }



        if (historyIndex < puzzleHistory.size() - 1) {
            historyIndex++;
            loadHistoryItem(puzzleHistory.get(historyIndex));
        } else {
            int levelIndex = getIntent().getIntExtra("level_index", -1);
            if (levelIndex != -1) {
                int nextLevel = levelIndex + 1;
                int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .getInt("unlocked_level", 1);
                if (nextLevel <= unlockedLevel && nextLevel <= offlinePuzzles.size()) {
                    getIntent().putExtra("level_index", nextLevel);
                    loadSpecificOfflineLevel(nextLevel);
                } else if (nextLevel > unlockedLevel) {
                    updateSpeechBubble("Locked 🔒", "Solve the current puzzle to unlock the next level!", COLOR_RED);
                } else {
                    updateSpeechBubble("Completed!", "You have completed all levels!", COLOR_GREEN);
                }
            } else {
                if (!offlinePuzzles.isEmpty()) {
                    loadRandomOfflinePuzzle();
                } else {
                    loadPuzzle(null);
                }
            }
        }
    }

    private void loadRandomOfflinePuzzle() {
        if (offlinePuzzles.isEmpty()) {
            showError("No offline puzzles available.");
            return;
        }
        int randIdx = (int) (Math.random() * offlinePuzzles.size());
        JSONObject puzzleObj = offlinePuzzles.get(randIdx);
        initOfflinePuzzle(puzzleObj);
    }

    private void loadSpecificOfflineLevel(int levelIndex) {
        if (offlinePuzzles.isEmpty()) {
            showError("No offline puzzles available.");
            return;
        }

        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .getInt("unlocked_level", 1);
        if (levelIndex > unlockedLevel) {
            levelIndex = unlockedLevel;
            getIntent().putExtra("level_index", levelIndex);
        }

        int idx = levelIndex - 1;
        if (idx < 0 || idx >= offlinePuzzles.size()) {
            showError("Invalid level select: " + levelIndex);
            return;
        }
        JSONObject puzzleObj = offlinePuzzles.get(idx);
        initOfflinePuzzle(puzzleObj);
    }

    private void initOfflinePuzzle(JSONObject puzzleObj) {
        try {
            currentPuzzleId = puzzleObj.optString("id", null);
            startFen = puzzleObj.getString("fen");
            String movesStr = puzzleObj.getString("moves");
            solutionMoves = movesStr.split(" ");
            int rating = puzzleObj.getInt("rating");
            String theme = puzzleObj.optString("theme", "tactics");

            int idx = offlinePuzzles.indexOf(puzzleObj);
            int levelIdx = (idx != -1) ? (idx + 1) : -1;

            PuzzleHistoryItem item = new PuzzleHistoryItem(startFen, solutionMoves, rating, theme, levelIdx);
            addToHistory(item);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                updatePuzzleHeaderAndStats(levelIdx, rating, theme);
                resetPuzzleState();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to load offline puzzle data", e);
            showError("Failed to load offline puzzle.");
        }
    }

    private void parseAndInit(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONObject puzzleObj = root.getJSONObject("puzzle");

            currentPuzzleId = puzzleObj.optString("id", null);
            startFen = puzzleObj.getString("fen");
            JSONArray solutionArr = puzzleObj.getJSONArray("solution");
            
            solutionMoves = new String[solutionArr.length()];
            for (int i = 0; i < solutionArr.length(); i++) {
                solutionMoves[i] = solutionArr.getString(i);
            }

            final int rating = puzzleObj.getInt("rating");
            final String themes = puzzleObj.optJSONArray("themes") != null ? 
                    puzzleObj.getJSONArray("themes").optString(0, "tactics") : "tactics";

            PuzzleHistoryItem item = new PuzzleHistoryItem(startFen, solutionMoves, rating, themes, -1);
            addToHistory(item);

            boolean isDailyCompleted = false;
            if (activeMode.equals("daily") && currentPuzzleId != null) {
                String completedId = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .getString("completed_daily_puzzle_id", "");
                if (currentPuzzleId.equals(completedId)) {
                    isDailyCompleted = true;
                }
            }

            final boolean finalIsDailyCompleted = isDailyCompleted;
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                updatePuzzleHeaderAndStats(-1, rating, themes);
                if (finalIsDailyCompleted) {
                    isFinished = true;
                    isPlayerTurn = false;
                    chessboard.setFEN(startFen);
                    chessboard.setInteractable(false);
                    if (solutionMoves != null && solutionMoves.length > 0) {
                        String firstMove = solutionMoves[0];
                        chessboard.makeMove(firstMove.substring(0, 2), firstMove.substring(2));
                    }
                    updateSpeechBubble("🎉 Completed!", "Today's daily puzzle is complete!", COLOR_GREEN);
                    updateBottomActionPanel();
                } else {
                    resetPuzzleState();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse online puzzle", e);
            runOnUiThread(this::loadRandomOfflinePuzzle);
        }
    }

    private void resetPuzzleState() {
        if (startFen == null || solutionMoves == null || solutionMoves.length == 0) return;

        isAnalysisMode = false;
        updateCoachAvatar();
        chessboard.setFEN(startFen);
        currentMoveIdx = 0;
        isFinished = false;
        hintClickCount = 0;

        // Auto-play the first move (which is the opponent's setup move)
        String firstMove = solutionMoves[0];
        String from = firstMove.substring(0, 2);
        String to = firstMove.substring(2);
        
        // Auto-orient board based on player turn
        boolean opponentIsWhite = startFen.contains(" w ");
        playerIsWhite = !opponentIsWhite;
        chessboard.setFlipped(!opponentIsWhite);

        chessboard.makeMove(from, to);
        currentMoveIdx = 1;
        maxMoveIdxReached = 1;
        isPlayerTurn = true;
        chessboard.setInteractable(true);
        chessboard.setPlayerColor(playerIsWhite ? 'w' : 'b');
        
        startTimer();

        String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
        updateSpeechBubble(sideToMoveText, "Find the best sequence of moves.", Color.BLACK);
        updateBottomActionPanel();
    }

    private void undoLastMove() {
        if (solutionMoves == null || currentMoveIdx < 2) {
            return;
        }

        currentMoveIdx -= 2;
        isPlayerTurn = true;
        isFinished = false;
        hintClickCount = 0;

        chessboard.setFEN(startFen);
        for (int i = 0; i < currentMoveIdx; i++) {
            String move = solutionMoves[i];
            chessboard.makeMove(move.substring(0, 2), move.substring(2));
        }

        String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
        updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
    }

    private void redoNextMove() {
        if (solutionMoves == null || currentMoveIdx >= maxMoveIdxReached) {
            return;
        }

        // Replay the player's move
        String playerMove = solutionMoves[currentMoveIdx];
        String fromP = playerMove.substring(0, 2);
        String toP = playerMove.substring(2, 4);
        boolean isCaptureP = chessboard.getPieceAt(toP) != ' ';
        chessboard.makeMove(fromP, toP);
        playMoveSound(isCaptureP);
        currentMoveIdx++;
        playSound("sounds/puzzles/correct.mp3");

        // If the puzzle is now finished
        if (currentMoveIdx >= solutionMoves.length) {
            isFinished = true;
            isPlayerTurn = false;
            chessboard.setInteractable(false);
            stopTimer();
            updateSpeechBubble("🎉 Success!", "Puzzle Solved!", COLOR_GREEN);
            playSound("sounds/puzzles/puzzle-path/puzzle-solved.mp3");
            updateBottomActionPanel();
            return;
        }

        // Replay the opponent's reply (if it was already reached/played before)
        if (currentMoveIdx < maxMoveIdxReached) {
            String opponentMove = solutionMoves[currentMoveIdx];
            String fromO = opponentMove.substring(0, 2);
            String toO = opponentMove.substring(2, 4);
            boolean isCaptureO = chessboard.getPieceAt(toO) != ' ';
            chessboard.makeMove(fromO, toO);
            playMoveSound(isCaptureO);
            currentMoveIdx++;
        }

        // Update turn indicator
        String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
        updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
    }

    @Override
    public void onMove(String fromSquare, String toSquare) {
        // Check if move is a capture first
        char targetPiece = chessboard.getPieceAt(toSquare);
        boolean isCapture = targetPiece != ' ';

        if (isAnalysisMode) {
            chessboard.makeMove(fromSquare, toSquare);
            playMoveSound(isCapture);
            return;
        }

        if (!isPlayerTurn || isFinished) return;

        String playerMove = fromSquare + toSquare;
        String expectedMove = solutionMoves[currentMoveIdx];

        if (playerMove.equalsIgnoreCase(expectedMove.substring(0, 4))) {
            // Play move/capture sound
            playMoveSound(isCapture);
            
            chessboard.makeMove(fromSquare, expectedMove.substring(2));
            currentMoveIdx++;
            if (currentMoveIdx > maxMoveIdxReached) {
                maxMoveIdxReached = currentMoveIdx;
            }
            hintClickCount = 0;

            if (currentMoveIdx >= solutionMoves.length) {
                isFinished = true;
                isPlayerTurn = false;
                chessboard.setInteractable(false);
                stopTimer();
                updateSpeechBubble("🎉 Success!", "Puzzle Solved!", COLOR_GREEN);
                playSound("sounds/puzzles/puzzle-path/puzzle-solved.mp3");

                if (activeMode.equals("rush") || activeMode.equals("battle")) {
                    rushScore++;
                    if (activeMode.equals("rush")) {
                        if (ratingView != null) ratingView.setText("Score: " + rushScore);
                    } else {
                        if (ratingView != null) ratingView.setText("You: " + rushScore + "  vs  Opponent: " + opponentScore);
                    }
                    chessboard.postDelayed(() -> {
                        loadNextPuzzle();
                    }, 1500);
                } else {
                    updateBottomActionPanel();

                    // Update streak
                    currentStreak++;
                    getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                            .edit()
                            .putInt("puzzle_streak", currentStreak)
                            .apply();

                    PuzzleHistoryItem currentItem = null;
                    if (historyIndex >= 0 && historyIndex < puzzleHistory.size()) {
                        currentItem = puzzleHistory.get(historyIndex);
                    }
                    int levelIdx = (currentItem != null) ? currentItem.levelIndex : -1;
                    int rating = (currentItem != null) ? currentItem.rating : 1500;
                    String theme = (currentItem != null) ? currentItem.theme : "tactics";
                    updatePuzzleHeaderAndStats(levelIdx, rating, theme);

                    // Save level progress
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

                    // Save daily puzzle completion
                    if (activeMode.equals("daily") && currentPuzzleId != null) {
                        getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("completed_daily_puzzle_id", currentPuzzleId)
                                .apply();
                        updateSpeechBubble("🎉 Success!", "Today's daily puzzle is complete!", COLOR_GREEN);
                        updateBottomActionPanel();
                    }
                }
            } else {
                isPlayerTurn = false;
                chessboard.setInteractable(false);
                updateSpeechBubble("✅ Correct!", "Opponent is moving...", COLOR_GREEN);
                
                chessboard.postDelayed(() -> {
                    if (isFinished) return;
                    String opponentMove = solutionMoves[currentMoveIdx];
                    // Check opponent capture
                    String oppFrom = opponentMove.substring(0, 2);
                    String oppTo = opponentMove.substring(2, 4);
                    char oppTarget = chessboard.getPieceAt(oppTo);
                    playMoveSound(oppTarget != ' ');
                    chessboard.makeMove(oppFrom, oppTo);
                    currentMoveIdx++;
                    if (currentMoveIdx > maxMoveIdxReached) {
                        maxMoveIdxReached = currentMoveIdx;
                    }
                    hintClickCount = 0;
                    isPlayerTurn = true;
                    chessboard.setInteractable(true);
                    String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
                    updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
                }, 1000);
            }
        } else {
            chessboard.clearArrow();
            updateSpeechBubble("❌ Wrong move!", "Try a different sequence of moves.", COLOR_RED);
            playSound("sounds/puzzles/incorrect.mp3");

            if (activeMode.equals("rush") || activeMode.equals("battle")) {
                rushStrikes++;
                updateStrikesUI();

                // Highlight correct move
                if (solutionMoves != null && currentMoveIdx < solutionMoves.length) {
                    String correctMove = solutionMoves[currentMoveIdx];
                    chessboard.showArrow(correctMove.substring(0, 2), correctMove.substring(2, 4));
                }

                if (rushStrikes >= 3) {
                    isFinished = true;
                    isPlayerTurn = false;
                    chessboard.setInteractable(false);
                    stopTimer();
                    if (activeMode.equals("rush")) {
                        endRushRun();
                    } else {
                        endBattleRun();
                    }
                } else {
                    isPlayerTurn = false;
                    chessboard.setInteractable(false);
                    chessboard.postDelayed(() -> {
                        loadNextPuzzle();
                    }, 1500);
                }
            } else {
                // For level/daily puzzles: lock board temporarily, and reset to last correct state after 1s
                isPlayerTurn = false;
                chessboard.setInteractable(false);
                chessboard.postDelayed(() -> {
                    if (isFinished) return;
                    chessboard.setFEN(startFen);
                    for (int i = 0; i < currentMoveIdx; i++) {
                        String move = solutionMoves[i];
                        chessboard.makeMove(move.substring(0, 2), move.substring(2));
                    }
                    isPlayerTurn = true;
                    chessboard.setInteractable(true);
                    String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
                    updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
                }, 1000);

                // Do not reset streak on incorrect moves
                PuzzleHistoryItem currentItem = null;
                if (historyIndex >= 0 && historyIndex < puzzleHistory.size()) {
                    currentItem = puzzleHistory.get(historyIndex);
                }
                int levelIdx = (currentItem != null) ? currentItem.levelIndex : -1;
                int rating = (currentItem != null) ? currentItem.rating : 1500;
                String theme = (currentItem != null) ? currentItem.theme : "tactics";
                updatePuzzleHeaderAndStats(levelIdx, rating, theme);
            }
        }
    }

    private void initSoundPool() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                soundPool = new android.media.SoundPool.Builder()
                        .setMaxStreams(5)
                        .setAudioAttributes(attrs)
                        .build();
            } else {
                soundPool = new android.media.SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0);
            }

            android.content.res.AssetManager am = getAssets();
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

    private void playSound(String path) {
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
                android.content.res.AssetFileDescriptor afd = getAssets().openFd(path);
                android.media.MediaPlayer player = new android.media.MediaPlayer();
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.prepare();
                player.start();
                player.setOnCompletionListener(android.media.MediaPlayer::release);
            } catch (Exception e) {
                Log.e(TAG, "Failed to play sound (fallback): " + path, e);
            }
        }
    }

    private void playMoveSound(boolean isCapture) {
        if (isCapture) {
            playSound("sounds/game-actions/capture.mp3");
        } else {
            playSound("sounds/game-actions/move-self.mp3");
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
            if (activeMode.equals("rush")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Rush");
                if (ratingView != null) ratingView.setText("Score: " + rushScore);
                updateStrikesUI();
                return;
            }
            if (activeMode.equals("battle")) {
                if (headerTitle != null) headerTitle.setText("Puzzle Battle");
                if (ratingView != null) ratingView.setText("You: " + rushScore + "  vs  Opponent: " + opponentScore);
                updateStrikesUI();
                return;
            }

            // Calculate target puzzle context metric (use level index or global tracker fallback)
            int referenceCount = levelIdx;
            if (referenceCount == -1) {
                int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .getInt("unlocked_level", 1);
                referenceCount = Math.max(0, unlockedLevel - 1);
            }

            if (headerTitle != null) {
                if (levelIdx != -1) {
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
                
                // Dynamic Milestone Badge Styling
                int woodBadgeResId = getDrawableResId("puzzle_tier_wood_pawn");
                if (targetMilestone <= 10 && woodBadgeResId != 0) {
                    streakMilestoneBadge.setBackgroundResource(woodBadgeResId);
                } else {
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setShape(GradientDrawable.OVAL);
                    int badgeColor;
                    int strokeColor;
                    if (targetMilestone <= 10) {
                        badgeColor = Color.parseColor("#8B5A2B"); // Bronze
                        strokeColor = Color.parseColor("#A0522D");
                    } else if (targetMilestone <= 20) {
                        badgeColor = Color.parseColor("#C0C0C0"); // Silver
                        strokeColor = Color.parseColor("#A9A9A9");
                    } else if (targetMilestone <= 30) {
                        badgeColor = Color.parseColor("#FFD700"); // Gold
                        strokeColor = Color.parseColor("#DAA520");
                    } else {
                        badgeColor = Color.parseColor("#2C3E50"); // Obsidian / Platinum
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
            // Pointing to the left: triangle coordinates (Top-right, bottom-right, middle-left)
            path.moveTo(getWidth(), getHeight() * 0.3f);
            path.lineTo(getWidth(), getHeight() * 0.7f);
            path.lineTo(0, getHeight() * 0.5f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}

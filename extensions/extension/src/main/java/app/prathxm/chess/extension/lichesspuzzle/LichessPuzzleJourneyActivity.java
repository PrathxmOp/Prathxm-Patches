package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

public class LichessPuzzleJourneyActivity extends Activity implements PuzzleJourneyMapView.OnLevelClickListener {

    private static final String TAG = "LichessPuzzleJourney";

    private FrameLayout mainContainer;
    private ScrollView mapScrollView;
    private PuzzleJourneyMapView mapInterface;
    
    // Streak Progress UI
    private TextView streakTextView;
    private ProgressBar streakProgressBar;
    private TextView streakMilestoneBadge;
    
    // Bottom navigation/overlay views
    private View dimOverlay;
    private LinearLayout bottomSheet;
    private Button mainSolveButton;
    private View solveAndListRow;
    
    // Header & Subtitles
    private TextView titleText;
    private TextView subtitleText;
    private TextView topBadge;

    // Coach views
    private android.widget.ImageView coachAvatar;

    // Database / Loading
    private LinearLayout downloadOverlay;
    private TextView downloadStatusText;
    private ProgressBar downloadProgressBar;
    private boolean isDatabaseReady = false;
    private LichessPuzzleDatabaseHelper dbHelper;

    // Theme Colors
    private final int COLOR_BG = Color.parseColor("#121214");
    private final int COLOR_CARD = Color.parseColor("#1E1E24");
    private final int COLOR_GREEN = Color.parseColor("#81B64C");
    private final int COLOR_RED = Color.parseColor("#E74C3C");
    private final int COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private final int COLOR_TEXT_SECONDARY = Color.parseColor("#A0A0A5");
    private android.graphics.Typeface fontBold;
    private android.graphics.Typeface fontRegular;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupJourneyUI();

        dbHelper = new LichessPuzzleDatabaseHelper(this);

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
            migrateJsonToDbAsync(localFile);
        } else {
            if (isNetworkAvailable()) {
                startDownloadDatabase();
            } else {
                showOfflineWarningOverlay();
            }
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

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
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

        // Header Panel (Centered)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#E6272522"));
        header.setGravity(Gravity.CENTER);
        header.setPadding(32, 24, 32, 24);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        subtitleText = new TextView(this); // Instantiated to prevent NPE but not added to layout

        // Header Title container with badge
        LinearLayout headerTitleLayout = new LinearLayout(this);
        headerTitleLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerTitleLayout.setGravity(Gravity.CENTER);
        headerTitleLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Top Badge (Puzzle piece)
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
        View speechBubbleTail = new SpeechBubbleTail(this);
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

        TextView coachMsgText = new TextView(this);
        coachMsgText.setText("Feel like speeding things up? Score for solving quickly in Puzzle Rush, where your third mistake is your last!");
        coachMsgText.setTextColor(Color.parseColor("#484644"));
        coachMsgText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontRegular != null) coachMsgText.setTypeface(fontRegular);
        speechBubbleBody.addView(coachMsgText);

        speechBubbleContainer.addView(speechBubbleBody);
        coachLayout.addView(speechBubbleContainer);
        root.addView(coachLayout);

        // Winding Map inside a ScrollView
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

        // Stats & Streak Progress Row (Moved to bottom, above buttons)
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

        // Solve and List buttons horizontal row
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

        // List menu button on the left
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
        
        listBtn.setOnClickListener(v -> showBottomSheet());
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



        // Bottom Navigation Bar is removed
        int navHeight = 0;

        mainContainer.addView(root);

        // Dimming Overlay
        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.parseColor("#90000000"));
        dimOverlay.setVisibility(View.GONE);
        dimOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        dimOverlay.setOnClickListener(v -> hideBottomSheet());
        mainContainer.addView(dimOverlay);

        // Bottom Sheet Layout
        bottomSheet = new LinearLayout(this);
        bottomSheet.setOrientation(LinearLayout.VERTICAL);
        bottomSheet.setVisibility(View.GONE);
        
        float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#22211F"));
        sheetBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bottomSheet.setBackground(sheetBg);
        bottomSheet.setPadding(margin16, margin16, margin16, 0);
        
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int sheetHeight = (int) (dm.heightPixels * 0.75);

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                sheetHeight,
                Gravity.BOTTOM
        );
        sheetParams.bottomMargin = navHeight;
        bottomSheet.setLayoutParams(sheetParams);

        // Header Title for Bottom Sheet
        TextView sheetTitle = new TextView(this);
        sheetTitle.setText("Puzzle Themes & Practice");
        sheetTitle.setTextColor(Color.WHITE);
        sheetTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) sheetTitle.setTypeface(fontBold);
        LinearLayout.LayoutParams sheetTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sheetTitleLp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        sheetTitle.setLayoutParams(sheetTitleLp);
        bottomSheet.addView(sheetTitle);

        // Scrollable area for list
        ScrollView sheetScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        sheetScrollView.setLayoutParams(scrollLp);
        
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Category: Recommended
        addThemeCategoryHeader(scrollContent, "Recommended");
        addThemeRow(scrollContent, "Daily Puzzle", "📅", "Complete today's official daily puzzle.", "", "daily", null);
        addThemeRow(scrollContent, "Healthy mix", "🎯", "A mix of everything. You don't know what to expect, so be ready for anything! Just like in real games.", "6,360,928", "theme", "healthyMix");

        // Category: Phases
        addThemeCategoryHeader(scrollContent, "Phases");
        addThemeRow(scrollContent, "Opening", "♟️", "A tactic during the first phase of the game.", "319,834", "theme", "opening");
        addThemeRow(scrollContent, "Middlegame", "⚔️", "A tactic during the second phase of the game.", "2,898,480", "theme", "middlegame");
        addThemeRow(scrollContent, "Endgame", "👑", "A tactic during the last phase of the game.", "3,142,614", "theme", "endgame");

        // Category: Endgame Types
        addThemeCategoryHeader(scrollContent, "Endgame Types");
        addThemeRow(scrollContent, "Rook endgame", "♜", "An endgame with only rooks and pawns.", "334,797", "theme", "rookEndgame");
        addThemeRow(scrollContent, "Bishop endgame", "♝", "An endgame with only bishops and pawns.", "84,922", "theme", "bishopEndgame");
        addThemeRow(scrollContent, "Pawn endgame", "♙", "An endgame with only pawns.", "227,186", "theme", "pawnEndgame");
        addThemeRow(scrollContent, "Knight endgame", "♞", "An endgame with only knights and pawns.", "51,531", "theme", "knightEndgame");
        addThemeRow(scrollContent, "Queen endgame", "♛", "An endgame with only queens and pawns.", "72,656", "theme", "queenEndgame");
        addThemeRow(scrollContent, "Queen and Rook", "🏰", "An endgame with only queens, rooks, and pawns.", "47,016", "theme", "queenRookEndgame");

        sheetScrollView.addView(scrollContent);
        bottomSheet.addView(sheetScrollView);

        View spacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics())
        );
        spacer.setLayoutParams(spacerLp);
        bottomSheet.addView(spacer);
        
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomRowLp.bottomMargin = margin16;
        bottomRow.setLayoutParams(bottomRowLp);
        
        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) closeBtn.setTypeface(fontBold);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#2D2B29"));
        closeBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));
        closeBtn.setBackground(closeBg);
        int closeSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeSize, closeSize);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> hideBottomSheet());
        bottomRow.addView(closeBtn);
        
        Button innerSolveBtn = new Button(this);
        innerSolveBtn.setText("Solve Journey Puzzles");
        innerSolveBtn.setTextColor(Color.WHITE);
        innerSolveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (fontBold != null) innerSolveBtn.setTypeface(fontBold);
        innerSolveBtn.setAllCaps(false);
        GradientDrawable innerSolveBg = new GradientDrawable();
        innerSolveBg.setColor(COLOR_GREEN);
        innerSolveBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));
        innerSolveBtn.setBackground(innerSolveBg);
        LinearLayout.LayoutParams innerSolveLp = new LinearLayout.LayoutParams(
                0, closeSize, 1.0f
                );
        innerSolveLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        innerSolveBtn.setLayoutParams(innerSolveLp);
        innerSolveBtn.setOnClickListener(v -> {
            hideBottomSheet();
            int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                    .getInt("unlocked_level", 1);
            onLevelClick(unlockedLevel);
        });
        bottomRow.addView(innerSolveBtn);
        bottomSheet.addView(bottomRow);
        
        mainContainer.addView(bottomSheet);

        setContentView(mainContainer);
        updateStreakProgress();
    }

    private void showBottomSheet() {
        if (bottomSheet.getVisibility() == View.VISIBLE) return;
        
        if (solveAndListRow != null) {
            solveAndListRow.setVisibility(View.GONE);
        }
        
        dimOverlay.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(250);
        fadeIn.setFillAfter(true);
        dimOverlay.startAnimation(fadeIn);
        
        bottomSheet.setVisibility(View.VISIBLE);
        android.view.animation.TranslateAnimation slideUp = new android.view.animation.TranslateAnimation(
                0, 0,
                bottomSheet.getHeight() > 0 ? bottomSheet.getHeight() : 1000, 0
        );
        slideUp.setDuration(250);
        slideUp.setInterpolator(new android.view.animation.DecelerateInterpolator());
        bottomSheet.startAnimation(slideUp);
    }
    
    private void hideBottomSheet() {
        if (bottomSheet.getVisibility() != View.VISIBLE) return;
        
        if (solveAndListRow != null) {
            solveAndListRow.setVisibility(View.VISIBLE);
        }
        
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
        
        android.view.animation.TranslateAnimation slideDown = new android.view.animation.TranslateAnimation(
                0, 0,
                0, bottomSheet.getHeight()
        );
        slideDown.setDuration(200);
        slideDown.setInterpolator(new android.view.animation.AccelerateInterpolator());
        slideDown.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override public void onAnimationStart(android.view.animation.Animation animation) {}
            @Override public void onAnimationRepeat(android.view.animation.Animation animation) {}
            @Override public void onAnimationEnd(android.view.animation.Animation animation) {
                bottomSheet.setVisibility(View.GONE);
            }
        });
        bottomSheet.startAnimation(slideDown);
    }

    private void updateStreakProgress() {
        // 1. Fetch current puzzle tracking indices (unlocked level index)
        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .getInt("unlocked_level", 1);
        
        // Total solved puzzles is equivalent to the unlockedLevel index minus 1
        int totalPuzzlesSolved = Math.max(0, unlockedLevel - 1);
        
        // 2. Compute dynamic milestone target based on tens ceiling bounds
        int targetMilestone = totalPuzzlesSolved <= 0 ? 10 : ((totalPuzzlesSolved / 10) + 1) * 10;

        runOnUiThread(() -> {
            // Update large count display on left
            if (streakTextView != null) {
                streakTextView.setText(String.valueOf(totalPuzzlesSolved));
            }
            
            // Update small count profile badge icon
            if (topBadge != null) {
                topBadge.setText(String.valueOf(totalPuzzlesSolved));
                int woodBadgeResId = getDrawableResId("puzzle_tier_wood_pawn");
                if (woodBadgeResId != 0) {
                    topBadge.setBackgroundResource(woodBadgeResId);
                }
            }
            
            // Synchronize the Progress Bar and Milestone Text badge
            if (streakProgressBar != null && streakMilestoneBadge != null) {
                streakProgressBar.setMax(10);
                
                // Percentage indicator runs from 0 to 9 in current tens tier loop
                streakProgressBar.setProgress(totalPuzzlesSolved % 10);
                streakMilestoneBadge.setText(String.valueOf(targetMilestone));
                
                // Style badge background based on level metrics
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

    private int getDrawableResId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private void refreshProgress() {
        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .getInt("unlocked_level", 1);
        
        mapInterface.setProgress(unlockedLevel);
        
        // Update Title & Subtitle based on progress
        titleText.setText(String.format(Locale.US, "%d Puzzles", unlockedLevel - 1));
        subtitleText.setText(getTierName(unlockedLevel) + " • Complete Level to Advance");
        updateStreakProgress();

        // Focus scrolling near the active level (with safety check after layout finishes)
        mapScrollView.post(() -> {
            float spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());
            int relativeIdx = (unlockedLevel - 1) % 20;
            // Target Y in the bottom-up coordinates
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
            int maxPossiblePage = 10000 / 20; // 500 pages total
            if (newPage <= maxPossiblePage) {
                mapInterface.setCurrentPage(newPage);
                // Scroll ScrollView to bottom on change so they start looking bottom-up
                mapScrollView.post(() -> mapScrollView.scrollTo(0, mapInterface.getHeight()));
            }
        }
    }

    private void startDownloadDatabase() {
        showDownloadProgress("Downloading offline puzzles (0%)...\nPlease wait.", 0);
        new Thread(() -> {
            try {
                int startOffset = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .getInt("download_offset", 0);
                
                int totalBatches = 200; // 20,000 puzzles
                int limitPerBatch = 100;
                List<JSONObject> batchList = new ArrayList<>();

                for (int i = 0; i < totalBatches; i++) {
                    int offset = startOffset + (i * limitPerBatch);
                    final int progressPercent = (i * 100) / totalBatches;
                    final int batchNum = i + 1;
                    
                    if (i % 10 == 0 || i == totalBatches - 1) {
                        showDownloadProgress(
                                String.format(Locale.US, "Downloading 20,000 offline puzzles...\nBatch %d/%d (%d%%)", batchNum, totalBatches, progressPercent),
                                progressPercent
                        );
                    }

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

                            batchList.add(puzzleItem);
                        }
                        
                        if (batchList.size() >= 500) {
                            dbHelper.insertPuzzles(batchList);
                            batchList.clear();
                        }
                        
                        getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                                .edit()
                                .putInt("download_offset", offset + limitPerBatch)
                                .apply();
                    } else if (code == 429) {
                        Log.w(TAG, "Rate limited! Sleeping for 5 seconds...");
                        Thread.sleep(5000);
                        i--;
                        continue;
                    } else {
                        throw new Exception("HTTP server error: " + code);
                    }
                    connection.disconnect();
                    Thread.sleep(50);
                }

                if (!batchList.isEmpty()) {
                    dbHelper.insertPuzzles(batchList);
                    batchList.clear();
                }

                getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                        .edit()
                        .putInt("download_offset", startOffset + (totalBatches * limitPerBatch))
                        .apply();

                showDownloadProgress("Saving puzzles database to storage...", 100);

                runOnUiThread(() -> {
                    hideDownloadUI();
                    isDatabaseReady = true;
                    refreshProgress();
                });

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                runOnUiThread(() -> {
                    hideDownloadUI();
                    showDownloadFailed("Failed to download database: " + e.getMessage());
                });
            }
        }).start();
    }

    private void migrateJsonToDbAsync(File file) {
        showDownloadProgress("Migrating offline database...\nPlease wait.", 0);
        new Thread(() -> {
            try {
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
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getJSONObject(i));
                }
                dbHelper.insertPuzzles(list);
                file.delete(); // Delete local file after migration

                runOnUiThread(() -> {
                    hideDownloadUI();
                    isDatabaseReady = true;
                    refreshProgress();
                });
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
                runOnUiThread(() -> {
                    hideDownloadUI();
                    showDownloadFailed("Failed to migrate database.");
                });
            }
        }).start();
    }

    private void showDownloadProgress(String message, int progressVal) {
        runOnUiThread(() -> {
            mapScrollView.setVisibility(View.GONE);
            
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
                if (fontBold != null) {
                    titleText.setTypeface(fontBold);
                } else {
                    titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                titleText.setGravity(Gravity.CENTER);
                titleText.setPadding(0, 0, 0, 16);
                downloadOverlay.addView(titleText);

                downloadStatusText = new TextView(this);
                downloadStatusText.setText(message);
                downloadStatusText.setTextColor(COLOR_TEXT_SECONDARY);
                downloadStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                if (fontRegular != null) downloadStatusText.setTypeface(fontRegular);
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
            mapScrollView.setVisibility(View.GONE);
            
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
            if (fontBold != null) {
                titleText.setTypeface(fontBold);
            } else {
                titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 32, 0, 16);
            downloadOverlay.addView(titleText);

            TextView descText = new TextView(this);
            descText.setText("To play fully offline, the Lichess puzzle database of 10,000 puzzles must be downloaded on first launch.\n\nPlease connect to the internet and click Retry.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            if (fontRegular != null) descText.setTypeface(fontRegular);
            descText.setGravity(Gravity.CENTER);
            descText.setPadding(0, 0, 0, 48);
            downloadOverlay.addView(descText);

            Button retryButton = new Button(this);
            retryButton.setText("Retry Download");
            retryButton.setTextColor(Color.WHITE);
            retryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            if (fontBold != null) {
                retryButton.setTypeface(fontBold);
            } else {
                retryButton.setTypeface(null, android.graphics.Typeface.BOLD);
            }
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
                    startDownloadDatabase();
                } else {
                    Toast.makeText(this, "Still offline. Please check your internet connection.", Toast.LENGTH_SHORT).show();
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
            mapScrollView.setVisibility(View.GONE);
            
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
            if (fontBold != null) {
                titleText.setTypeface(fontBold);
            } else {
                titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 32, 0, 16);
            downloadOverlay.addView(titleText);

            TextView descText = new TextView(this);
            descText.setText(errorMessage + "\n\nPlease check your internet connection and try again.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            if (fontRegular != null) descText.setTypeface(fontRegular);
            descText.setGravity(Gravity.CENTER);
            descText.setPadding(0, 0, 0, 48);
            downloadOverlay.addView(descText);

            Button retryButton = new Button(this);
            retryButton.setText("Retry Download");
            retryButton.setTextColor(Color.WHITE);
            retryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            if (fontBold != null) {
                retryButton.setTypeface(fontBold);
            } else {
                retryButton.setTypeface(null, android.graphics.Typeface.BOLD);
            }
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
                    startDownloadDatabase();
                } else {
                    Toast.makeText(this, "Still offline. Please check your internet connection.", Toast.LENGTH_SHORT).show();
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
            mapScrollView.setVisibility(View.VISIBLE);
        });
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

    private void addThemeCategoryHeader(LinearLayout parent, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.parseColor("#81B64C")); // Chess.com green accent
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontBold != null) header.setTypeface(fontBold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        header.setLayoutParams(lp);
        parent.addView(header);
    }

    private void addThemeRow(LinearLayout parent, String name, String emoji, String description, String count, String mode, String themeKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()),
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics())
        );

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        row.setLayoutParams(rowLp);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#2D2B29"));
        rowBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));
        row.setBackground(rowBg);
        row.setClickable(true);
        row.setFocusable(true);

        // Emoji
        TextView emojiView = new TextView(this);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emojiLp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        emojiView.setLayoutParams(emojiLp);
        row.addView(emojiView);

        // Text container
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        textContainer.setLayoutParams(textLp);

        TextView titleView = new TextView(this);
        titleView.setText(name);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (fontBold != null) titleView.setTypeface(fontBold);
        textContainer.addView(titleView);

        if (description != null && !description.isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(description);
            descView.setTextColor(Color.parseColor("#A0A0A5"));
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            if (fontRegular != null) descView.setTypeface(fontRegular);
            descView.setSingleLine(false);
            textContainer.addView(descView);
        }
        row.addView(textContainer);

        // Count Badge
        if (count != null && !count.isEmpty()) {
            TextView countView = new TextView(this);
            countView.setText(count);
            countView.setTextColor(Color.parseColor("#B1B0AE"));
            countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            if (fontRegular != null) countView.setTypeface(fontRegular);
            countView.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics())
            );
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#1C1A18"));
            badgeBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics()));
            countView.setBackground(badgeBg);

            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            countLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            countView.setLayoutParams(countLp);
            row.addView(countView);
        }

        row.setOnClickListener(v -> {
            hideBottomSheet();
            Intent intent = new Intent(LichessPuzzleJourneyActivity.this, StandaloneLichessActivity.class);
            intent.putExtra("puzzle_mode", mode);
            if (themeKey != null) {
                intent.putExtra("puzzle_theme", themeKey);
            }
            startActivity(intent);
        });

        parent.addView(row);
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

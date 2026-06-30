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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private ProgressBar progressBar;
    private ProgressBar streakProgressBar;
    private TextView streakMilestoneBadge;
    private int currentStreak = 0;
    private android.widget.ImageView coachAvatar;
    private android.graphics.Typeface fontBold;
    private android.graphics.Typeface fontRegular;
    
    // Offline Database
    private LinearLayout downloadOverlay;
    private TextView downloadStatusText;
    private ProgressBar downloadProgressBar;
    private final List<JSONObject> offlinePuzzles = new ArrayList<>();
    
    // Game State
    private String startFen;
    private String[] solutionMoves;
    private int currentMoveIdx = 0;
    private boolean isPlayerTurn = false;
    private boolean isFinished = false;
    private boolean playerIsWhite = true;

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

        File localFile = new File(getFilesDir(), "puzzles.json");
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
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(COLOR_CARD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(32, 24, 32, 24);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Back Arrow
        TextView backArrow = new TextView(this);
        backArrow.setText("◀");
        backArrow.setTextColor(COLOR_TEXT_PRIMARY);
        backArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        backArrow.setPadding(16, 16, 32, 16);
        if (fontBold != null) backArrow.setTypeface(fontBold);
        backArrow.setOnClickListener(v -> finish());
        header.addView(backArrow);

        headerTitle = new TextView(this);
        headerTitle.setText("Lichess Puzzles");
        headerTitle.setTextColor(COLOR_TEXT_PRIMARY);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) {
            headerTitle.setTypeface(fontBold);
        } else {
            headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));
        header.addView(headerTitle);

        // Settings icon placeholder
        TextView settingsIcon = new TextView(this);
        settingsIcon.setText("⚙");
        settingsIcon.setTextColor(COLOR_TEXT_PRIMARY);
        settingsIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        if (fontBold != null) settingsIcon.setTypeface(fontBold);
        settingsIcon.setPadding(16, 16, 16, 16);
        header.addView(settingsIcon);

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
        contentLayout.setPadding(32, 24, 32, 24);

        // --- Coach Avatar & Speech Bubble ---
        LinearLayout coachLayout = new LinearLayout(this);
        coachLayout.setOrientation(LinearLayout.HORIZONTAL);
        coachLayout.setGravity(Gravity.CENTER_VERTICAL);
        coachLayout.setPadding(0, 16, 0, 32);
        coachLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Circular Coach Avatar
        coachAvatar = new android.widget.ImageView(this);
        updateCoachAvatar();
        
        int avatarSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        coachAvatar.setLayoutParams(avatarParams);
        coachLayout.addView(coachAvatar);

        // Speech Bubble
        LinearLayout speechBubble = new LinearLayout(this);
        speechBubble.setOrientation(LinearLayout.VERTICAL);
        speechBubble.setPadding(24, 16, 24, 16);
        
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.WHITE);
        bubbleBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        speechBubble.setBackground(bubbleBg);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.setMargins(24, 0, 0, 0);
        speechBubble.setLayoutParams(bubbleParams);

        // Speech Bubble Title / Status
        statusBanner = new TextView(this);
        statusBanner.setText("⬜ White to Move");
        statusBanner.setTextColor(Color.BLACK);
        statusBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (fontBold != null) {
            statusBanner.setTypeface(fontBold);
        } else {
            statusBanner.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        speechBubble.addView(statusBanner);

        // Subtitle instructions
        titleView = new TextView(this);
        titleView.setText("Find the best sequence of moves.");
        titleView.setTextColor(Color.parseColor("#484644"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontRegular != null) {
            titleView.setTypeface(fontRegular);
        }
        titleView.setPadding(0, 4, 0, 0);
        speechBubble.addView(titleView);

        coachLayout.addView(speechBubble);
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
        bottomArea.setPadding(32, 24, 32, 32);
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

        ratingView = new TextView(this);
        ratingView.setText("Rating: -- | Theme: tactics");
        ratingView.setTextColor(COLOR_TEXT_SECONDARY);
        ratingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontBold != null) {
            ratingView.setTypeface(fontBold);
        } else {
            ratingView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        ratingView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));
        statsRow.addView(ratingView);

        timerView = new TextView(this);
        timerView.setText("00:00");
        timerView.setTextColor(COLOR_TEXT_SECONDARY);
        timerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (fontRegular != null) {
            timerView.setTypeface(fontRegular);
        }
        statsRow.addView(timerView);

        bottomArea.addView(statsRow);

        // Progress Container (ProgressBar + Target Badge on Right)
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.HORIZONTAL);
        progressContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 4, 0, 24);
        progressContainer.setLayoutParams(containerParams);

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
        streakMilestoneBadge.setText("1");
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

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Color.parseColor("#8B5A2B")); // Wood-like color
        badgeBg.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()), Color.parseColor("#A0522D"));
        streakMilestoneBadge.setBackground(badgeBg);

        progressContainer.addView(streakMilestoneBadge);
        bottomArea.addView(progressContainer);

        // Action Buttons
        LinearLayout actionsLayout = new LinearLayout(this);
        actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionsLayout.setGravity(Gravity.CENTER_VERTICAL);
        actionsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Hint Item
        actionsLayout.addView(createActionItem("💡", "Hint", v -> {
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

        // Back Item
        actionsLayout.addView(createActionItem("◀", "Back", v -> {
            if (solutionMoves != null && currentMoveIdx > 1) {
                undoLastMove();
            } else {
                loadPreviousPuzzle();
            }
        }));

        // Next Item
        actionsLayout.addView(createActionItem("▶", "Next", v -> loadNextPuzzle()));

        bottomArea.addView(actionsLayout);
        gameLayout.addView(bottomArea);

        mainContainer.addView(gameLayout);
        setContentView(mainContainer);
    }

    private LinearLayout createActionItem(String iconText, String labelText, View.OnClickListener listener) {
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

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        if (fontBold != null) icon.setTypeface(fontBold);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(COLOR_TEXT_SECONDARY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (fontRegular != null) label.setTypeface(fontRegular);
        label.setPadding(0, 4, 0, 0);
        label.setGravity(Gravity.CENTER);
        item.addView(label);

        return item;
    }

    private void updateSpeechBubble(String title, String subtitle, int titleColor) {
        runOnUiThread(() -> {
            statusBanner.setText(title);
            statusBanner.setTextColor(titleColor);
            titleView.setText(subtitle);
        });
    }

    private void startTimer() {
        stopTimer();
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

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
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
                        loadSpecificOfflineLevel(levelIndex);
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
        BufferedReader br = new BufferedReader(new FileReader(file));
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

                File file = new File(getFilesDir(), "puzzles.json");
                FileWriter writer = new FileWriter(file);
                writer.write(allPuzzles.toString());
                writer.flush();
                writer.close();

                loadOfflinePuzzlesFromLocalFile(file);

                runOnUiThread(() -> {
                    hideDownloadUI();
                    int levelIndex = getIntent().getIntExtra("level_index", -1);
                    if (levelIndex != -1) {
                        loadSpecificOfflineLevel(levelIndex);
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
        if (historyIndex < puzzleHistory.size() - 1) {
            historyIndex++;
            loadHistoryItem(puzzleHistory.get(historyIndex));
        } else {
            int levelIndex = getIntent().getIntExtra("level_index", -1);
            if (levelIndex != -1) {
                int nextLevel = levelIndex + 1;
                if (nextLevel <= offlinePuzzles.size()) {
                    getIntent().putExtra("level_index", nextLevel);
                    loadSpecificOfflineLevel(nextLevel);
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

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                updatePuzzleHeaderAndStats(-1, rating, themes);
                resetPuzzleState();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse online puzzle", e);
            runOnUiThread(this::loadRandomOfflinePuzzle);
        }
    }

    private void resetPuzzleState() {
        if (startFen == null || solutionMoves == null || solutionMoves.length == 0) return;

        updateCoachAvatar();
        chessboard.setFEN(startFen);
        currentMoveIdx = 0;
        isFinished = false;
        hintClickCount = 0;

        // Auto-play the first move (which is the opponent's setup move)
        String firstMove = solutionMoves[0];
        String from = firstMove.substring(0, 2);
        String to = firstMove.substring(2, 4);
        
        // Auto-orient board based on player turn
        boolean opponentIsWhite = startFen.contains(" w ");
        playerIsWhite = !opponentIsWhite;
        chessboard.setFlipped(!opponentIsWhite);

        chessboard.makeMove(from, to);
        currentMoveIdx = 1;
        isPlayerTurn = true;
        
        startTimer();

        String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
        updateSpeechBubble(sideToMoveText, "Find the best sequence of moves.", Color.BLACK);
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
            chessboard.makeMove(move.substring(0, 2), move.substring(2, 4));
        }

        String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
        updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
    }

    @Override
    public void onMove(String fromSquare, String toSquare) {
        if (!isPlayerTurn || isFinished) return;

        String playerMove = fromSquare + toSquare;
        String expectedMove = solutionMoves[currentMoveIdx];

        if (playerMove.equalsIgnoreCase(expectedMove.substring(0, 4))) {
            chessboard.makeMove(fromSquare, toSquare);
            currentMoveIdx++;
            hintClickCount = 0;

            if (currentMoveIdx >= solutionMoves.length) {
                isFinished = true;
                isPlayerTurn = false;
                stopTimer();
                updateSpeechBubble("🎉 Success!", "Puzzle Solved!", COLOR_GREEN);
                playSound("sounds/puzzles/puzzle-path/puzzle-solved.mp3");

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
            } else {
                isPlayerTurn = false;
                updateSpeechBubble("✅ Correct!", "Opponent is moving...", COLOR_GREEN);
                playSound("sounds/puzzles/correct.mp3");
                
                chessboard.postDelayed(() -> {
                    if (isFinished) return;
                    String opponentMove = solutionMoves[currentMoveIdx];
                    chessboard.makeMove(opponentMove.substring(0, 2), opponentMove.substring(2, 4));
                    currentMoveIdx++;
                    hintClickCount = 0;
                    isPlayerTurn = true;
                    String sideToMoveText = playerIsWhite ? "⬜ White to Move" : "⬛ Black to Move";
                    updateSpeechBubble(sideToMoveText, "Find the next move.", Color.BLACK);
                }, 1000);
            }
        } else {
            updateSpeechBubble("❌ Wrong move!", "Try a different sequence of moves.", COLOR_RED);
            playSound("sounds/puzzles/incorrect.mp3");

            // Reset streak
            currentStreak = 0;
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
        }
    }

    private void playSound(String path) {
        try {
            android.content.res.AssetFileDescriptor afd = getAssets().openFd(path);
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.prepare();
            player.start();
            player.setOnCompletionListener(android.media.MediaPlayer::release);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play sound: " + path, e);
        }
    }

    private void updateCoachAvatar() {
        if (coachAvatar == null) return;
        try {
            String[] coaches = {"Danny.png", "Magnus.png", "Hikaru.png", "Vishy.png", "Ben.png", "Judit.png", "Anna.png", "Levy.png"};
            int randomIdx = (int) (Math.random() * coaches.length);
            java.io.InputStream ims = getAssets().open("coaches_avatars/" + coaches[randomIdx]);
            android.graphics.drawable.Drawable d = android.graphics.drawable.Drawable.createFromStream(ims, null);
            coachAvatar.setImageDrawable(d);
        } catch (Exception e) {
            int avatarResId = getResources().getIdentifier("coach_david", "drawable", getPackageName());
            if (avatarResId == 0) {
                avatarResId = getResources().getIdentifier("coach", "drawable", getPackageName());
            }
            if (avatarResId != 0) {
                coachAvatar.setImageResource(avatarResId);
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
            if (headerTitle != null) {
                if (levelIdx != -1) {
                    headerTitle.setText(levelIdx + " Puzzles");
                } else {
                    headerTitle.setText("Lichess Puzzles");
                }
            }
            if (ratingView != null) {
                ratingView.setText(rating + "   🔥 " + currentStreak);
            }
            if (streakProgressBar != null && streakMilestoneBadge != null) {
                int targetMilestone = ((currentStreak / 10) + 1) * 10;
                streakProgressBar.setMax(10);
                streakProgressBar.setProgress(currentStreak % 10);
                streakMilestoneBadge.setText(String.valueOf(targetMilestone));
            }
        });
    }
}

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

public class LichessPuzzleJourneyActivity extends Activity implements PuzzleJourneyMapView.OnLevelClickListener {

    private static final String TAG = "LichessPuzzleJourney";

    private FrameLayout mainContainer;
    private ScrollView mapScrollView;
    private PuzzleJourneyMapView mapInterface;
    
    // Header & Subtitles
    private TextView titleText;
    private TextView subtitleText;

    // Database / Loading
    private LinearLayout downloadOverlay;
    private TextView downloadStatusText;
    private ProgressBar downloadProgressBar;
    private boolean isDatabaseReady = false;

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

        File localFile = new File(getFilesDir(), "puzzles.json");
        if (localFile.exists() && localFile.length() > 0) {
            isDatabaseReady = true;
            refreshProgress();
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
        mainContainer.setBackgroundColor(COLOR_BG);
        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Header Panel
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

        // Header text container (Title + Subtitle)
        LinearLayout headerTextContainer = new LinearLayout(this);
        headerTextContainer.setOrientation(LinearLayout.VERTICAL);
        headerTextContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        titleText = new TextView(this);
        titleText.setText("Puzzle Journey");
        titleText.setTextColor(COLOR_TEXT_PRIMARY);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (fontBold != null) {
            titleText.setTypeface(fontBold);
        } else {
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        headerTextContainer.addView(titleText);

        subtitleText = new TextView(this);
        subtitleText.setText("Wood Tier");
        subtitleText.setTextColor(COLOR_TEXT_SECONDARY);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (fontRegular != null) subtitleText.setTypeface(fontRegular);
        headerTextContainer.addView(subtitleText);

        header.addView(headerTextContainer);

        // Settings Cog
        TextView settingsIcon = new TextView(this);
        settingsIcon.setText("⚙");
        settingsIcon.setTextColor(COLOR_TEXT_PRIMARY);
        settingsIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        if (fontBold != null) settingsIcon.setTypeface(fontBold);
        settingsIcon.setPadding(16, 16, 16, 16);
        header.addView(settingsIcon);

        root.addView(header);

        // Winding Map inside a ScrollView
        mapScrollView = new ScrollView(this);
        mapScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        mapInterface = new PuzzleJourneyMapView(this);
        mapInterface.setOnLevelClickListener(this);
        mapScrollView.addView(mapInterface);

        root.addView(mapScrollView);
        mainContainer.addView(root);

        setContentView(mainContainer);
    }

    private void refreshProgress() {
        int unlockedLevel = getSharedPreferences("lichess_puzzle_prefs", MODE_PRIVATE)
                .getInt("unlocked_level", 1);
        
        mapInterface.setProgress(unlockedLevel);
        
        // Update Title & Subtitle based on progress
        titleText.setText(String.format(Locale.US, "%d Puzzles", unlockedLevel - 1));
        subtitleText.setText(getTierName(unlockedLevel) + " • Complete Level to Advance");

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

                runOnUiThread(() -> {
                    hideDownloadUI();
                    isDatabaseReady = true;
                    refreshProgress();
                });

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                runOnUiThread(() -> showDownloadFailed("Failed to download database: " + e.getMessage()));
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
}

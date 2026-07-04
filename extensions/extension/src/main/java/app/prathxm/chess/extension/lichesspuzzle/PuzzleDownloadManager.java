/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class PuzzleDownloadManager {
    private static final String TAG = "PuzzleDownloadManager";

    private static final int COLOR_BG = Color.parseColor("#302e2b"); // Chess.com bg
    private static final int COLOR_CARD = Color.parseColor("#262421"); // Chess.com card bg
    private static final int COLOR_TEXT_PRIMARY = Color.WHITE;
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#B1B0AE"); // Chess.com gray text
    private static final int COLOR_GREEN = Color.parseColor("#81B64C"); // Chess.com brand green
    private static final int COLOR_RED = Color.parseColor("#FA412F");

    private final Activity activity;
    private final FrameLayout mainContainer;
    private final View contentLayout;
    private final LichessPuzzleDatabaseHelper dbHelper;
    private final PuzzleDownloadListener listener;

    private View downloadOverlay;
    private TextView downloadStatusText;
    private ProgressBar downloadProgressBar;
    private Typeface fontBold;
    private Typeface fontRegular;
    private int selectedDownloadLimit = 50000;

    public interface PuzzleDownloadListener {
        void onDownloadComplete();
        void onDownloadFailed(String error);
        void onMigrationComplete();
    }

    public PuzzleDownloadManager(Activity activity, FrameLayout mainContainer, View contentLayout,
                                 LichessPuzzleDatabaseHelper dbHelper, PuzzleDownloadListener listener) {
        this.activity = activity;
        this.mainContainer = mainContainer;
        this.contentLayout = contentLayout;
        this.dbHelper = dbHelper;
        this.listener = listener;
    }

    public void setTypefaces(Typeface fontBold, Typeface fontRegular) {
        this.fontBold = fontBold;
        this.fontRegular = fontRegular;
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public void startDownloadFlow(boolean forceDownload) {
        if (forceDownload) {
            if (isNetworkAvailable()) {
                startDownloadPuzzles();
            } else {
                showOfflineWarningOverlay();
            }
        } else {
            if (isNetworkAvailable()) {
                showDownloadSelectionOverlay();
            } else {
                showOfflineWarningOverlay();
            }
        }
    }

    public void setSelectedDownloadLimit(int limit) {
        this.selectedDownloadLimit = limit;
    }

    public int getSelectedDownloadLimit() {
        return selectedDownloadLimit;
    }

    public void startDownloadPuzzles() {
        showDownloadProgress("Connecting to server...", 0);
        new Thread(() -> {
            File tempFile = new File(activity.getCacheDir(), "temp_lichess_puzzles.gz");
            try {
                if (dbHelper != null) {
                    dbHelper.clearAllPuzzles();
                }

                String fileSuffix;
                if (selectedDownloadLimit == 20000) {
                    fileSuffix = "_20k.csv.gz";
                } else if (selectedDownloadLimit == 50000) {
                    fileSuffix = "_50k.csv.gz";
                } else if (selectedDownloadLimit == 100000) {
                    fileSuffix = "_100k.csv.gz";
                } else if (selectedDownloadLimit == 500000) {
                    fileSuffix = "_500k.csv.gz";
                } else {
                    fileSuffix = "_all.csv.gz";
                }

                String urlStr = "https://github.com/PrathxmOp/Prathxm-Patches/releases/download/database/lichess_offline_puzzles" + fileSuffix;
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP server error: " + code);
                }

                int fileLength = connection.getContentLength();
                InputStream input = connection.getInputStream();

                // Phase 1: Pure Download to Cache
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int read;
                int bytesRead = 0;
                long lastProgressTime = 0;

                while ((read = input.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    bytesRead += read;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime > 150) {
                        lastProgressTime = now;
                        final int currentBytes = bytesRead;
                        final int total = fileLength;
                        activity.runOnUiThread(() -> {
                            if (total > 0) {
                                int progressPercent = (int) (((long) currentBytes * 100) / total);
                                showDownloadProgress(
                                        String.format(Locale.US, "Downloading offline database...\n(%d%%)", progressPercent),
                                        progressPercent
                                );
                            } else {
                                showDownloadProgress(
                                        String.format(Locale.US, "Downloading offline database...\n(%.1f MB)", (float) currentBytes / (1024 * 1024)),
                                        0
                                );
                            }
                        });
                    }
                }
                fos.flush();
                fos.close();
                input.close();
                connection.disconnect();

                // Phase 2: Local Import to SQLite
                showDownloadProgress("Preparing database import...", 0);

                FileInputStream fis = new FileInputStream(tempFile);
                GZIPInputStream gzipStream = new GZIPInputStream(fis);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));

                reader.readLine(); // skip header
                List<JSONObject> batchList = new ArrayList<>();
                String line;
                int count = 0;
                int totalPuzzles = selectedDownloadLimit;
                int progressTotal = (totalPuzzles > 500000) ? 6100000 : totalPuzzles;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        JSONObject puzzleItem = new JSONObject();
                        puzzleItem.put("id", parts[0]);
                        puzzleItem.put("fen", parts[1]);
                        puzzleItem.put("moves", parts[2]);
                        puzzleItem.put("rating", Integer.parseInt(parts[3]));
                        puzzleItem.put("theme", parts[4]);
                        batchList.add(puzzleItem);
                    }

                    if (count + batchList.size() >= totalPuzzles) {
                        int remaining = totalPuzzles - count;
                        if (remaining > 0 && remaining <= batchList.size()) {
                            dbHelper.insertPuzzles(batchList.subList(0, remaining));
                            count += remaining;
                        }
                        batchList.clear();
                        break;
                    }

                    if (batchList.size() >= 1000) {
                        dbHelper.insertPuzzles(batchList);
                        count += batchList.size();
                        batchList.clear();

                        final int currentCount = count;
                        activity.runOnUiThread(() -> {
                            int importProgress = Math.min(100, (currentCount * 100) / progressTotal);
                            showDownloadProgress(
                                    String.format(Locale.US, "Importing puzzles into database...\n%d / %d (%d%%)", currentCount, progressTotal, importProgress),
                                    importProgress
                            );
                        });
                    }
                }

                if (!batchList.isEmpty()) {
                    dbHelper.insertPuzzles(batchList);
                    count += batchList.size();
                    batchList.clear();
                }

                reader.close();
                fis.close();
                tempFile.delete(); // Cleanup cache GZIP file

                showDownloadProgress("Finalizing setup...", 100);

                activity.runOnUiThread(() -> {
                    hideDownloadUI();
                    if (listener != null) {
                        listener.onDownloadComplete();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Download/Import failed", e);
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                activity.runOnUiThread(() -> {
                    hideDownloadUI();
                    showDownloadFailed("Failed to set up puzzles: " + e.getMessage());
                    if (listener != null) {
                        listener.onDownloadFailed(e.getMessage());
                    }
                });
            }
        }).start();
    }

    public void migrateJsonToDbAsync(File file) {
        showDownloadProgress("Migrating offline database...\nPlease wait.", 0);
        new Thread(() -> {
            try {
                FileInputStream fileStream = new FileInputStream(file);
                GZIPInputStream gzipStream = new GZIPInputStream(fileStream);
                BufferedReader br = new BufferedReader(new InputStreamReader(gzipStream, "UTF-8"));
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
                file.delete();

                activity.runOnUiThread(() -> {
                    hideDownloadUI();
                    if (listener != null) {
                        listener.onMigrationComplete();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
                activity.runOnUiThread(() -> {
                    hideDownloadUI();
                    showDownloadFailed("Failed to migrate database.");
                    if (listener != null) {
                        listener.onDownloadFailed("Migration failed: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    public void showDownloadSelectionOverlay() {
        activity.runOnUiThread(() -> {
            if (contentLayout != null) {
                contentLayout.setVisibility(View.GONE);
            }
            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
            }

            float density = activity.getResources().getDisplayMetrics().density;

            // Main Background Panel
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(COLOR_BG);
            root.setPadding((int)(24 * density), (int)(16 * density), (int)(24 * density), (int)(24 * density));
            downloadOverlay = root;

            // Header layout for Title and Close button
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(0, 0, 0, (int)(16 * density));

            TextView titleText = new TextView(activity);
            titleText.setText("Setup Offline Puzzles");
            titleText.setTextColor(COLOR_TEXT_PRIMARY);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleText.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            titleText.setLayoutParams(titleParams);
            header.addView(titleText);

            // Close button (if database is already present or not strictly forced)
            if (dbHelper.getPuzzlesCount() > 0) {
                TextView closeBtn = new TextView(activity);
                closeBtn.setText("✕");
                closeBtn.setTextColor(COLOR_TEXT_SECONDARY);
                closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                closeBtn.setPadding((int)(8 * density), (int)(8 * density), (int)(8 * density), (int)(8 * density));
                closeBtn.setOnClickListener(v -> hideDownloadUI());
                header.addView(closeBtn);
            }
            root.addView(header);

            TextView descText = new TextView(activity);
            descText.setText("Select a database size to download. Larger files provide more variety but require more space.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            descText.setTypeface(fontRegular);
            descText.setPadding(0, 0, 0, (int)(24 * density));
            root.addView(descText);

            // Scrollable cards list
            ScrollView scrollView = new ScrollView(activity);
            scrollView.setVerticalScrollBarEnabled(false);
            LinearLayout cardsContainer = new LinearLayout(activity);
            cardsContainer.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(cardsContainer);

            int[] limits = {20000, 50000, 100000, 500000, 10000000};
            String[] titles = {"Lightweight Database", "Standard Database", "Expanded Database", "Mega Database", "Complete Database"};
            String[] descs = {
                "20,000 puzzles (~1.0 MB download)",
                "50,000 puzzles (~2.3 MB download)",
                "100,000 puzzles (~4.5 MB download)",
                "500,000 puzzles (~22 MB download)",
                "All 6M+ puzzles (~150 MB download)"
            };

            for (int i = 0; i < limits.length; i++) {
                final int limit = limits[i];
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(16 * density));

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setCornerRadius(10 * density);

                // Highlight standard option
                if (limit == 50000) {
                    cardBg.setColor(COLOR_GREEN);
                } else {
                    cardBg.setColor(COLOR_CARD);
                    cardBg.setStroke((int)(1.5f * density), Color.parseColor("#3d3b38"));
                }
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                cardParams.setMargins(0, 0, 0, (int)(14 * density));
                card.setLayoutParams(cardParams);

                TextView cardTitle = new TextView(activity);
                cardTitle.setText(titles[i]);
                cardTitle.setTextColor(COLOR_TEXT_PRIMARY);
                cardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                cardTitle.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
                card.addView(cardTitle);

                TextView cardDesc = new TextView(activity);
                cardDesc.setText(descs[i]);
                cardDesc.setTextColor(limit == 50000 ? Color.parseColor("#EAF2F8") : COLOR_TEXT_SECONDARY);
                cardDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                cardDesc.setTypeface(fontRegular);
                cardDesc.setPadding(0, (int)(6 * density), 0, 0);
                card.addView(cardDesc);

                card.setOnClickListener(v -> {
                    selectedDownloadLimit = limit;
                    startDownloadPuzzles();
                });

                cardsContainer.addView(card);
            }
            root.addView(scrollView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
            ));

            mainContainer.addView(downloadOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        });
    }

    public void showDownloadProgress(String message, int progressVal) {
        activity.runOnUiThread(() -> {
            if (contentLayout != null) {
                contentLayout.setVisibility(View.GONE);
            }

            float density = activity.getResources().getDisplayMetrics().density;

            if (downloadOverlay == null || downloadOverlay.getParent() == null || downloadStatusText == null || downloadProgressBar == null) {
                if (downloadOverlay != null) {
                    mainContainer.removeView(downloadOverlay);
                }

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                root.setBackgroundColor(COLOR_BG);
                root.setPadding((int)(40 * density), 0, (int)(40 * density), 0);
                downloadOverlay = root;

                TextView titleText = new TextView(activity);
                titleText.setText("Setting Up Puzzles");
                titleText.setTextColor(COLOR_TEXT_PRIMARY);
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                titleText.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
                titleText.setGravity(Gravity.CENTER);
                titleText.setPadding(0, 0, 0, (int)(16 * density));
                root.addView(titleText);

                downloadStatusText = new TextView(activity);
                downloadStatusText.setText(message);
                downloadStatusText.setTextColor(COLOR_TEXT_SECONDARY);
                downloadStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                downloadStatusText.setTypeface(fontRegular);
                downloadStatusText.setGravity(Gravity.CENTER);
                downloadStatusText.setPadding(0, 0, 0, (int)(28 * density));
                root.addView(downloadStatusText);

                // Horizontal Premium Progress Bar
                downloadProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                downloadProgressBar.setMax(100);
                downloadProgressBar.setProgress(progressVal);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    downloadProgressBar.setProgressTintList(ColorStateList.valueOf(COLOR_GREEN));
                }

                LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int)(10 * density)
                );
                downloadProgressBar.setLayoutParams(pbParams);
                root.addView(downloadProgressBar);

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

    public void showOfflineWarningOverlay() {
        activity.runOnUiThread(() -> {
            if (contentLayout != null) {
                contentLayout.setVisibility(View.GONE);
            }

            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
            }

            float density = activity.getResources().getDisplayMetrics().density;

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(COLOR_BG);
            root.setPadding((int)(32 * density), 0, (int)(32 * density), 0);
            downloadOverlay = root;

            TextView warningIcon = new TextView(activity);
            warningIcon.setText("⚠");
            warningIcon.setTextColor(COLOR_RED);
            warningIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
            warningIcon.setGravity(Gravity.CENTER);
            warningIcon.setPadding(0, 0, 0, (int)(16 * density));
            root.addView(warningIcon);

            TextView titleText = new TextView(activity);
            titleText.setText("Connection Required");
            titleText.setTextColor(COLOR_TEXT_PRIMARY);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            titleText.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 0, 0, (int)(12 * density));
            root.addView(titleText);

            TextView descText = new TextView(activity);
            descText.setText("To play offline Lichess puzzles, the database must be downloaded on first startup. Please connect to the internet to continue.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            descText.setTypeface(fontRegular);
            descText.setGravity(Gravity.CENTER);
            descText.setPadding(0, 0, 0, (int)(32 * density));
            root.addView(descText);

            Button retryButton = new Button(activity);
            retryButton.setText("Retry Connection");
            retryButton.setTextColor(Color.WHITE);
            retryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            retryButton.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
            retryButton.setAllCaps(false);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(COLOR_GREEN);
            btnBg.setCornerRadius(8 * density);
            retryButton.setBackground(btnBg);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(48 * density)
            );
            retryButton.setLayoutParams(btnParams);
            retryButton.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    showDownloadSelectionOverlay();
                } else {
                    descText.setText("Still offline. Please check your network and click retry.");
                    descText.setTextColor(COLOR_RED);
                }
            });
            root.addView(retryButton);

            mainContainer.addView(downloadOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));
        });
    }

    public void showDownloadFailed(String errorMessage) {
        activity.runOnUiThread(() -> {
            if (contentLayout != null) {
                contentLayout.setVisibility(View.GONE);
            }

            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
            }

            float density = activity.getResources().getDisplayMetrics().density;

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(COLOR_BG);
            root.setPadding((int)(32 * density), 0, (int)(32 * density), 0);
            downloadOverlay = root;

            TextView failIcon = new TextView(activity);
            failIcon.setText("✕");
            failIcon.setTextColor(COLOR_RED);
            failIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
            failIcon.setGravity(Gravity.CENTER);
            failIcon.setPadding(0, 0, 0, (int)(16 * density));
            root.addView(failIcon);

            TextView titleText = new TextView(activity);
            titleText.setText("Setup Failed");
            titleText.setTextColor(COLOR_TEXT_PRIMARY);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            titleText.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 0, 0, (int)(12 * density));
            root.addView(titleText);

            TextView descText = new TextView(activity);
            descText.setText(errorMessage + "\n\nPlease ensure your connection is stable and try again.");
            descText.setTextColor(COLOR_TEXT_SECONDARY);
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            descText.setTypeface(fontRegular);
            descText.setGravity(Gravity.CENTER);
            descText.setPadding(0, 0, 0, (int)(32 * density));
            root.addView(descText);

            Button retryButton = new Button(activity);
            retryButton.setText("Retry Setup");
            retryButton.setTextColor(Color.WHITE);
            retryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            retryButton.setTypeface(fontBold != null ? fontBold : Typeface.create("sans-serif", Typeface.BOLD));
            retryButton.setAllCaps(false);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(COLOR_GREEN);
            btnBg.setCornerRadius(8 * density);
            retryButton.setBackground(btnBg);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(48 * density)
            );
            retryButton.setLayoutParams(btnParams);
            retryButton.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    showDownloadSelectionOverlay();
                } else {
                    descText.setText("Offline. Please check your connection and retry.");
                    descText.setTextColor(COLOR_RED);
                }
            });
            root.addView(retryButton);

            mainContainer.addView(downloadOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        });
    }

    public void hideDownloadUI() {
        activity.runOnUiThread(() -> {
            if (downloadOverlay != null) {
                mainContainer.removeView(downloadOverlay);
                downloadOverlay = null;
                downloadStatusText = null;
                downloadProgressBar = null;
            }
            if (contentLayout != null) {
                contentLayout.setVisibility(View.VISIBLE);
            }
        });
    }
}

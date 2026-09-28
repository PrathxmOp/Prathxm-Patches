package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.content.SharedPreferences;

public class StockfishSettings {

    private static final String PREFS_NAME = "stockfish_settings";
    
    // Preference Keys
    private static final String KEY_ENGINE_ENABLED = "engine_enabled";
    private static final String KEY_DEPTH = "analysis_depth";
    private static final String KEY_MULTIPV = "multipv_count";
    private static final String KEY_ARROWS_VISIBLE = "arrows_visible";
    private static final String KEY_MY_SIDE_ONLY = "my_side_only";
    private static final String KEY_LIMIT_STRENGTH = "limit_strength";
    private static final String KEY_ELO = "elo_rating";
    private static final String KEY_ARROW_COLOR = "arrow_color";
    private static final String KEY_ADS_REMOVED = "ads_removed";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAdsRemoved(Context context) {
        return getPrefs(context).getBoolean(KEY_ADS_REMOVED, true);
    }

    public static void setAdsRemoved(Context context, boolean removed) {
        getPrefs(context).edit().putBoolean(KEY_ADS_REMOVED, removed).apply();
    }

    public static boolean isEngineEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENGINE_ENABLED, true);
    }

    public static void setEngineEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENGINE_ENABLED, enabled).apply();
    }

    /** Max selectable live-analysis depth. */
    public static final int MAX_DEPTH = 40;

    public static int getDepth(Context context) {
        return Math.max(1, Math.min(MAX_DEPTH, getPrefs(context).getInt(KEY_DEPTH, 18)));
    }

    // ── Engine power ─────────────────────────────────────────────────────────

    private static final String KEY_THREADS = "engine_threads";

    public static int getCpuCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** Search threads; defaults to 3 (stored 0 = auto). */
    public static int getThreads(Context context) {
        int t = getPrefs(context).getInt(KEY_THREADS, 0);
        int cpus = getCpuCount();
        if (t <= 0) return Math.min(3, cpus);
        return Math.min(t, cpus);
    }

    public static void setThreads(Context context, int threads) {
        getPrefs(context).edit().putInt(KEY_THREADS, Math.max(0, threads)).apply();
    }

    private static final String KEY_REVIEW_BOOST = "review_depth_boost";

    /** Extra depth added on top of the Chess.com game review depth preset. */
    public static int getReviewDepthBoost(Context context) {
        return Math.max(0, Math.min(10, getPrefs(context).getInt(KEY_REVIEW_BOOST, 0)));
    }

    public static void setReviewDepthBoost(Context context, int boost) {
        getPrefs(context).edit().putInt(KEY_REVIEW_BOOST, Math.max(0, Math.min(10, boost))).apply();
    }

    public static void setDepth(Context context, int depth) {
        getPrefs(context).edit().putInt(KEY_DEPTH, depth).apply();
    }

    public static int getMultiPV(Context context) {
        return getPrefs(context).getInt(KEY_MULTIPV, 1);
    }

    public static void setMultiPV(Context context, int count) {
        getPrefs(context).edit().putInt(KEY_MULTIPV, count).apply();
    }

    public static boolean isArrowsVisible(Context context) {
        return getPrefs(context).getBoolean(KEY_ARROWS_VISIBLE, true);
    }

    public static void setArrowsVisible(Context context, boolean visible) {
        getPrefs(context).edit().putBoolean(KEY_ARROWS_VISIBLE, visible).apply();
    }

    public static boolean isMySideOnly(Context context) {
        return getPrefs(context).getBoolean(KEY_MY_SIDE_ONLY, true);
    }

    public static void setMySideOnly(Context context, boolean mySideOnly) {
        getPrefs(context).edit().putBoolean(KEY_MY_SIDE_ONLY, mySideOnly).apply();
    }

    public static boolean isLimitStrength(Context context) {
        return getPrefs(context).getBoolean(KEY_LIMIT_STRENGTH, false);
    }

    public static void setLimitStrength(Context context, boolean limit) {
        getPrefs(context).edit().putBoolean(KEY_LIMIT_STRENGTH, limit).apply();
    }

    public static int getElo(Context context) {
        return getPrefs(context).getInt(KEY_ELO, 2000);
    }

    public static void setElo(Context context, int elo) {
        getPrefs(context).edit().putInt(KEY_ELO, elo).apply();
    }

    private static final String KEY_PREMIUM_ENABLED = "premium_enabled";

    public static boolean isPremiumEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PREMIUM_ENABLED, true);
    }

    public static void setPremiumEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PREMIUM_ENABLED, enabled).apply();
    }

    public static int getArrowColor(Context context) {
        return getPrefs(context).getInt(KEY_ARROW_COLOR, 0xFF00C853); // Default Green
    }

    public static void setArrowColor(Context context, int color) {
        getPrefs(context).edit().putInt(KEY_ARROW_COLOR, color).apply();
    }

    private static final String KEY_SHOW_EVAL_BAR = "show_eval_bar";

    public static boolean isEvalBarEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_EVAL_BAR, false);
    }

    public static void setEvalBarEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_EVAL_BAR, enabled).apply();
    }

    private static final String KEY_WARNING_ACCEPTED = "warning_accepted";

    public static boolean isWarningAccepted(Context context) {
        return getPrefs(context).getBoolean(KEY_WARNING_ACCEPTED, false);
    }

    public static void setWarningAccepted(Context context, boolean accepted) {
        getPrefs(context).edit().putBoolean(KEY_WARNING_ACCEPTED, accepted).apply();
    }

    private static final String KEY_SHOW_WDL = "show_wdl";

    public static boolean isWdlEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_WDL, false);
    }

    public static void setWdlEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_WDL, enabled).apply();
    }

    private static final String KEY_SHOW_THREAT_ARROWS = "show_threat_arrows";

    public static boolean isThreatArrowsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_THREAT_ARROWS, false);
    }

    public static void setThreatArrowsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_THREAT_ARROWS, enabled).apply();
    }

    private static final String KEY_SHOW_MOVE_CLASSIFICATION = "show_move_classification";

    public static boolean isMoveClassificationEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_MOVE_CLASSIFICATION, false);
    }

    public static void setMoveClassificationEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MOVE_CLASSIFICATION, enabled).apply();
    }

    private static final String KEY_ENABLE_BLUNDER_ALERTS = "enable_blunder_alerts";

    public static boolean isBlunderAlertsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLE_BLUNDER_ALERTS, false);
    }

    public static void setBlunderAlertsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_BLUNDER_ALERTS, enabled).apply();
    }

    private static final String KEY_SHOW_MATE_ANNOUNCEMENT = "show_mate_announcement";

    public static boolean isMateAnnouncementEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_MATE_ANNOUNCEMENT, false);
    }

    public static void setMateAnnouncementEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MATE_ANNOUNCEMENT, enabled).apply();
    }

    private static final String KEY_SHOW_ENGINE_INFO = "show_engine_info";

    /** Small "depth · score" line above the board, next to the W/D/L bar. */
    public static boolean isEngineInfoEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_ENGINE_INFO, false);
    }

    public static void setEngineInfoEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ENGINE_INFO, enabled).apply();
    }

    /** Restores every engine setting to its default (the tour flag is kept). */
    public static void resetToDefaults(Context context) {
        boolean tour = isTourShown(context);
        boolean warning = isWarningAccepted(context);
        getPrefs(context).edit().clear()
                .putBoolean(KEY_TOUR_SHOWN, tour)
                .putBoolean(KEY_WARNING_ACCEPTED, warning)
                .apply();
    }

    private static final String KEY_TOUR_SHOWN = "stockfish_tour_shown";

    public static boolean isTourShown(Context context) {
        return getPrefs(context).getBoolean(KEY_TOUR_SHOWN, false);
    }

    public static void setTourShown(Context context, boolean shown) {
        getPrefs(context).edit().putBoolean(KEY_TOUR_SHOWN, shown).apply();
    }
}


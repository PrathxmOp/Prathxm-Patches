/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import java.util.List;

/**
 * Evaluation math shared by the live move classifier and the offline game review.
 *
 * Move quality is judged on <b>expected points</b> (win probability), not raw pawn deltas.
 * Raw deltas are misleading: going from +8 to +5 changes nothing about the result, while
 * going from +0.5 to -2.5 loses the game. Mate scores (encoded as 99 - n pawns) also made
 * every move of a mating sequence look like a +1 pawn "improvement", which is why the old
 * review kept calling ordinary endgame moves "Great" instead of "Best".
 */
public final class ReviewMath {

    private ReviewMath() {}

    // Classification names understood by com.chess.compengine.AnalysisMoveClassification
    public static final String BOOK = "book";
    public static final String BRILLIANT = "brilliant";
    public static final String GREAT = "greatFind";
    public static final String BEST = "best";
    public static final String EXCELLENT = "excellent";
    public static final String GOOD = "good";
    public static final String INACCURACY = "inaccuracy";
    public static final String MISTAKE = "mistake";
    public static final String BLUNDER = "blunder";
    public static final String MISS = "miss";
    public static final String FORCED = "forced";

    // Expected-points loss thresholds (Chess.com style)
    public static final float EXCELLENT_MAX = 0.02f;
    public static final float GOOD_MAX = 0.05f;
    public static final float INACCURACY_MAX = 0.10f;
    public static final float MISTAKE_MAX = 0.20f;

    /** Win-probability gap between best and second-best move needed for "Great". */
    public static final float GREAT_GAP = 0.15f;

    /**
     * Win probability (0..1) for WHITE from an evaluation in pawns (white POV).
     * Lichess logistic model. Mate scores are encoded as +/-(99 - n) pawns, so anything at or
     * beyond +/-MATE_THRESHOLD is a forced mate (or tablebase win) and maps to exactly 0 / 1.
     */
    public static final float MATE_THRESHOLD = 50f;

    public static float whiteWin(float score) {
        if (score >= MATE_THRESHOLD) return 1f;
        if (score <= -MATE_THRESHOLD) return 0f;
        double cp = Math.max(-1000.0, Math.min(1000.0, score * 100.0));
        return (float) (1.0 / (1.0 + Math.exp(-0.00368208 * cp)));
    }

    /** Win probability for the given side. */
    public static float win(float whiteScore, boolean forWhite) {
        float w = whiteWin(whiteScore);
        return forWhite ? w : 1f - w;
    }

    /** Lichess per-move accuracy from win probabilities (0..1) before/after, mover POV. */
    public static float moveAccuracy(float winBefore, float winAfter) {
        float diff = Math.max(0f, (winBefore - winAfter) * 100f);
        double acc = 103.1668 * Math.exp(-0.04354 * diff) - 3.1669;
        return (float) Math.max(0.0, Math.min(100.0, acc + 1.0));
    }

    /**
     * Game accuracy: average of a volatility-weighted mean and the harmonic mean of the
     * per-move accuracies (Lichess method). Each element is {accuracy, weight}.
     */
    public static float gameAccuracy(List<float[]> moves) {
        if (moves == null || moves.isEmpty()) return 100f;
        double wSum = 0, wAcc = 0, harm = 0;
        int n = 0;
        for (float[] m : moves) {
            double a = Math.max(0.0, Math.min(100.0, m[0]));
            double w = Math.max(0.5, Math.min(12.0, m[1]));
            wSum += w;
            wAcc += a * w;
            harm += 1.0 / Math.max(a, 10.0);
            n++;
        }
        double weighted = wAcc / wSum;
        double harmonic = n / harm;
        return (float) Math.min(100.0, (weighted + harmonic) / 2.0);
    }

    /**
     * Classify a played move.
     *
     * @param best        played move is the engine's best move
     * @param forced      only one legal move was available
     * @param loss        expected points lost by the played move (>= 0)
     * @param winBefore   mover's win probability before the move (best play)
     * @param winAfter    mover's win probability after the played move
     * @param secondGap   win-probability gap between best and second best line (-1 = unknown)
     * @param sacrifice   the move gives up material for good (engine line confirms)
     * @param recapture   the move recaptures on the square the opponent just captured on
     * @param oppPrevLoss expected points lost by the opponent's previous move (-1 = unknown)
     * @param missedMate  a forced mate was available and the played move lets it go
     */
    public static String classify(boolean best, boolean forced, float loss, float winBefore, float winAfter,
                                  float secondGap, boolean sacrifice, boolean recapture,
                                  float oppPrevLoss, boolean missedMate) {
        if (forced) return FORCED;

        if (best || loss < 0.005f) {
            if (sacrifice && !recapture && winAfter >= 0.50f && winBefore < 0.97f) {
                return BRILLIANT;
            }
            // "Great": the only good move, in a position that is not already decided.
            if (best && !recapture && secondGap >= GREAT_GAP
                    && winAfter >= 0.35f && winBefore <= 0.97f) {
                return GREAT;
            }
            return best ? BEST : EXCELLENT;
        }

        if (missedMate) {
            // Letting a forced mate go is a Miss unless the move is still totally winning.
            return winAfter >= 0.97f ? EXCELLENT : MISS;
        }

        if (loss < EXCELLENT_MAX) return EXCELLENT;
        if (loss < GOOD_MAX) return GOOD;

        // Failing to punish the opponent's error.
        boolean chance = oppPrevLoss >= INACCURACY_MAX && winBefore >= 0.60f;
        if (chance && loss >= INACCURACY_MAX && winAfter >= 0.20f) return MISS;

        if (loss < INACCURACY_MAX) return INACCURACY;
        if (loss < MISTAKE_MAX) return MISTAKE;
        return BLUNDER;
    }

    /**
     * Derived from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
     * Whether a move inside known opening theory may be shown as "Book".
     */
    public static boolean isBookEligible(String c) {
        return !(MISTAKE.equals(c) || BLUNDER.equals(c) || MISS.equals(c) || FORCED.equals(c));
    }

    /**
     * Derived from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
     * Report-card "performance" for an accuracy (0..100).
     */
    public static String performance(float acc) {
        if (acc >= 97) return BRILLIANT;
        if (acc >= 92) return GREAT;
        if (acc >= 85) return BEST;
        if (acc >= 75) return EXCELLENT;
        if (acc >= 62) return GOOD;
        if (acc >= 48) return INACCURACY;
        if (acc >= 35) return MISTAKE;
        return BLUNDER;
    }

    /**
     * Derived from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
     * Tactics score (0..100) for report card.
     */
    public static float tacticsScore(float overall, int[] t) {
        float s = overall + 4f * t[1] + 2f * t[2] - 2f * t[7] - 5f * t[8] - 3f * t[10];
        return Math.max(0f, Math.min(100f, s));
    }

    /**
     * Derived from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
     * One-line game summary for the report card.
     */
    public static String summary(float wAcc, float bAcc, int[] w, int[] b, String opening) {
        StringBuilder sb = new StringBuilder();
        if (opening != null && !opening.isEmpty()) sb.append(opening).append(" · ");
        sb.append(String.format(java.util.Locale.US, "White %.1f%% vs Black %.1f%%", wAcc, bAcc));
        int wErr = w[8] + w[10], bErr = b[8] + b[10];
        if (wErr + bErr == 0) sb.append(" · no blunders");
        else sb.append(String.format(java.util.Locale.US, " · blunders/misses %d–%d", wErr, bErr));
        return sb.toString();
    }

    public static boolean isKeyMoment(String c) {
        return BRILLIANT.equals(c) || GREAT.equals(c) || BLUNDER.equals(c) || MISTAKE.equals(c)
                || INACCURACY.equals(c) || MISS.equals(c);
    }
}

package app.prathxm.chess.extension.stockfish;

/**
 * Self-checking tests for pure-logic methods in MoveClassifier and StockfishExtension.
 * 
 * Run with: javac -d out SelfTest.java && java -ea -cp out app.prathxm.chess.extension.stockfish.SelfTest
 *
 * Covers:
 *  - #37 Move classification thresholds (Brilliant, Blunder, etc.)
 *  - #60 isLiveMatch activity name detection
 *  - FEN deduction logic
 */
public class SelfTest {

    // ────────────────────────────────────────────────────────────────
    // Replicated pure-logic from MoveClassifier for isolated testing
    // ────────────────────────────────────────────────────────────────

    static String classify(float delta, boolean isBest, boolean isInPV, boolean onlyMoveInPV) {
        if (isBest) {
            if (delta > 1.5f && onlyMoveInPV) return "Brilliant";
            if (delta > 0.4f) return "Great Move";
            return "Best Move";
        }
        if (isInPV) return "Excellent";
        if (delta < -3.0f) return "Blunder";
        if (delta < -1.5f) return "Mistake";
        if (delta < -0.5f) return "Inaccuracy";
        if (delta < -0.1f) return "Good Move";
        return "Great Move";
    }

    static String classifyReview(float actualDelta, boolean isBest, boolean isSac, float evalAfter,
                                  boolean isInPV, boolean isMiss) {
        if (isMiss) return "miss";
        if (isBest) {
            if (isSac && actualDelta >= 0.5f && evalAfter >= 0.0f) return "brilliant";
            if (actualDelta > 0.4f) return "greatFind";
            return "best";
        }
        if (isInPV) return "excellent";
        if (actualDelta < -3.0f) return "blunder";
        if (actualDelta < -1.2f) return "mistake";
        if (actualDelta < -0.5f) return "inaccuracy";
        if (actualDelta < -0.1f) return "good";
        return "greatFind";
    }

    // ────────────────────────────────────────────────────────────────
    // Replicated isLiveMatch logic
    // ────────────────────────────────────────────────────────────────

    static boolean isLiveMatch(String activityClassName) {
        if (activityClassName == null) return false;
        String lower = activityClassName.toLowerCase();

        if (lower.contains("computer") || lower.contains("bot") || lower.contains("practice") ||
            lower.contains("analysis") || lower.contains("review") || lower.contains("local") ||
            lower.contains("solo") || lower.contains("tutorial") || lower.contains("puzzle")) {
            return false;
        }
        if (lower.contains("playactivity") || lower.contains("gameactivity") || lower.contains("live")) {
            return true;
        }
        if (lower.contains(".play.")) {
            return true;
        }
        return false;
    }

    // ────────────────────────────────────────────────────────────────
    // Replicated FEN utils
    // ────────────────────────────────────────────────────────────────

    static String expandFenBoard(String fenBoard) {
        StringBuilder sb = new StringBuilder();
        for (char c : fenBoard.toCharArray()) {
            if (c == '/') continue;
            if (Character.isDigit(c)) {
                int emptySquares = c - '0';
                for (int i = 0; i < emptySquares; i++) sb.append('.');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String getSquareName(int index) {
        char file = (char) ('a' + (index % 8));
        int rank = 8 - (index / 8);
        return "" + file + rank;
    }

    // ────────────────────────────────────────────────────────────────
    // Tests
    // ────────────────────────────────────────────────────────────────

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        // === CLASSIFICATION TESTS (#37) ===

        // Best move with only 1 PV line and large delta -> Brilliant
        check("Brilliant: best + only PV + delta=2.0",
            classify(2.0f, true, false, true).equals("Brilliant"));

        // Best move with multiple PV lines -> NOT Brilliant even with high delta
        check("Not Brilliant when multiple PV: delta=2.0",
            !classify(2.0f, true, false, false).equals("Brilliant"));
        check("Great Move when best + delta=2.0 + multi PV",
            classify(2.0f, true, false, false).equals("Great Move"));

        // Best move with small delta -> Best Move
        check("Best Move: delta=0.1",
            classify(0.1f, true, false, false).equals("Best Move"));

        // Best move with medium delta -> Great Move
        check("Great Move: delta=0.5",
            classify(0.5f, true, false, false).equals("Great Move"));

        // In PV but not best -> Excellent
        check("Excellent: in PV",
            classify(-0.2f, false, true, false).equals("Excellent"));

        // Blunder: delta < -3.0
        check("Blunder: delta=-3.5",
            classify(-3.5f, false, false, false).equals("Blunder"));

        // NOT Blunder at -2.5 (old threshold was too loose)
        check("NOT Blunder at -2.5, should be Mistake",
            classify(-2.5f, false, false, false).equals("Mistake"));

        // Mistake: -3.0 < delta < -1.5
        check("Mistake: delta=-2.0",
            classify(-2.0f, false, false, false).equals("Mistake"));

        // Inaccuracy: -1.5 < delta < -0.5
        check("Inaccuracy: delta=-0.8",
            classify(-0.8f, false, false, false).equals("Inaccuracy"));

        // Good Move: -0.5 < delta < -0.1
        check("Good Move: delta=-0.2",
            classify(-0.2f, false, false, false).equals("Good Move"));

        // Great Move: delta >= -0.1
        check("Great Move: delta=0.0",
            classify(0.0f, false, false, false).equals("Great Move"));

        // === REVIEW CLASSIFICATION TESTS (#37) ===

        // Brilliant in review: sacrifice + positive delta + positive eval
        check("Review Brilliant: sac + delta=1.0 + eval=0.5",
            classifyReview(1.0f, true, true, 0.5f, false, false).equals("brilliant"));

        // NOT Brilliant if not sacrifice
        check("Review NOT Brilliant without sac",
            !classifyReview(1.0f, true, false, 0.5f, false, false).equals("brilliant"));

        // NOT Brilliant if delta < 0.5 (old threshold was -0.2)
        check("Review NOT Brilliant if sac but delta=0.3",
            !classifyReview(0.3f, true, true, 0.5f, false, false).equals("brilliant"));

        // NOT Brilliant if eval < 0.0
        check("Review NOT Brilliant if sac but eval=-0.3",
            !classifyReview(0.6f, true, true, -0.3f, false, false).equals("brilliant"));

        // Review blunder at -3.0
        check("Review Blunder: delta=-3.5",
            classifyReview(-3.5f, false, false, 0.0f, false, false).equals("blunder"));

        // Review NOT blunder at -2.5 (fixed from old -2.5 threshold)
        check("Review NOT Blunder at -2.5, should be mistake",
            classifyReview(-2.5f, false, false, 0.0f, false, false).equals("mistake"));

        // Miss classification
        check("Review miss",
            classifyReview(-2.0f, false, false, 0.0f, false, true).equals("miss"));

        // === isLiveMatch TESTS (#60) ===

        // Live match activities
        check("PlayActivity is live",
            isLiveMatch("com.chess.play.PlayActivity"));
        check("GameActivity is live",
            isLiveMatch("com.chess.GameActivity"));
        check("LiveChessActivity is live",
            isLiveMatch("com.chess.live.LiveChessActivity"));
        check(".play. package is live",
            isLiveMatch("com.chess.play.SomeFragment"));

        // Non-live activities
        check("ComputerActivity is NOT live",
            !isLiveMatch("com.chess.computer.ComputerPlayActivity"));
        check("BotActivity is NOT live",
            !isLiveMatch("com.chess.bot.BotGameActivity"));
        check("AnalysisActivity is NOT live",
            !isLiveMatch("com.chess.analysis.AnalysisActivity"));
        check("ReviewActivity is NOT live",
            !isLiveMatch("com.chess.review.GameReviewActivity"));
        check("PuzzleActivity is NOT live",
            !isLiveMatch("com.chess.features.puzzles.PuzzleActivity"));
        check("TutorialActivity is NOT live",
            !isLiveMatch("com.chess.tutorial.TutorialActivity"));
        check("PracticeActivity is NOT live",
            !isLiveMatch("com.chess.practice.PracticeActivity"));
        check("HomeActivity is NOT live",
            !isLiveMatch("com.chess.home.HomeActivity"));
        check("Profile is NOT live (no matching keyword)",
            !isLiveMatch("com.chess.profile.PlayerProfileActivity"));
        check("null is NOT live",
            !isLiveMatch(null));

        // === FEN UTILITY TESTS ===

        check("expandFenBoard starting position",
            expandFenBoard("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").length() == 64);

        check("expandFenBoard empty rank = 8 dots",
            expandFenBoard("8").equals("........"));

        check("getSquareName(0) = a8",
            getSquareName(0).equals("a8"));
        check("getSquareName(63) = h1",
            getSquareName(63).equals("h1"));
        check("getSquareName(4) = e8",
            getSquareName(4).equals("e8"));

        // === SUMMARY ===
        System.out.println("\n" + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }
}

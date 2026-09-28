package app.prathxm.chess.extension.stockfish;

import android.app.Activity;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LocalAnalysisFlow {
    private static final String TAG = "LocalAnalysisFlow";

    private static Class<?> loadClassSafe(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            try {
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                if (tccl != null) {
                    return tccl.loadClass(name);
                }
            } catch (ClassNotFoundException ignored) {}

            try {
                android.content.Context ctx = StockfishExtension.getContext();
                if (ctx != null && ctx.getClassLoader() != null) {
                    return ctx.getClassLoader().loadClass(name);
                }
            } catch (ClassNotFoundException ignored) {}

            throw e;
        }
    }

    /**
     * Returns a Flow (the app's obfuscated coroutine Flow interface, passed in by the patch) that
     * runs a full local Stockfish review of {@code pgn} when collected.
     */
    public static Object createFlow(final Class<?> flowClass, final String pgn, final Object analysisDepthObj) {
        try {
            final AppTypes types = AppTypes.get(flowClass);
            Class<?> g74Class = types.flowClass;

            return Proxy.newProxyInstance(
                g74Class.getClassLoader(),
                new Class<?>[]{g74Class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("collect") && args != null && args.length == 2) {
                            // collect(collector, continuation) is a suspend function: run the
                            // review on a worker thread, suspend the caller and resume it when
                            // the review has finished (see FlowBridge).
                            return FlowBridge.collect(types, args[0], args[1], "stockfish-review",
                                    emitter -> runCollect(types, pgn, analysisDepthObj, emitter));
                        }
                        if (method.getName().equals("toString")) {
                            return "LocalAnalysisFlow(" + (pgn != null ? pgn.length() : 0) + " chars)";
                        }
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return args != null && proxy == args[0];
                        return null;
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create dynamic proxy flow", t);
            return null;
        }
    }

    private static void runCollect(AppTypes types, String pgn, Object analysisDepthObj,
                                   FlowBridge.Emitter emitter) throws Throwable {
        StockfishExtension.isReviewMode = true;
        Activity activity = StockfishExtension.getCurrentActivity();
        try {
            // Fair Play Gating: Prevent any local analysis during active live match
            if (activity != null && StockfishExtension.isLiveMatch(activity) && !StockfishExtension.isReviewMode) {
                logToFile(activity, "Analysis request blocked: Live gameplay detected.", true);
                Log.w(TAG, "Analysis request blocked: Live gameplay detected.");
                return;
            }

            // Stockfish 19 on a phone reaches these depths quickly; noticeably deeper than the
            // old 10/12/15/18 presets so late-game tactics are resolved correctly.
            int searchDepth = 16;
            if (analysisDepthObj != null) {
                String depthName = analysisDepthObj.toString();
                if ("FAST".equals(depthName)) searchDepth = 14;
                else if ("STANDARD".equals(depthName)) searchDepth = 16;
                else if ("DEEP".equals(depthName)) searchDepth = 20;
                else if ("MAXIMUM".equals(depthName)) searchDepth = 24;
            }
            android.content.Context appCtx = StockfishExtension.getContext();
            if (appCtx != null) searchDepth += StockfishSettings.getReviewDepthBoost(appCtx);
            // Safety net so one pathological position cannot stall the whole review.
            final int movetimeCap = 20_000 + searchDepth * 1_000;
            final int reviewMultiPV = 3;

            // Get Reflection Classes
            Class<?> adClass = loadClassSafe("com.chess.entities.AnalysisDepth");

            // Analysis source = the "Ceac" (engine analysis) singleton
            Object sourceEnum = types.ceacSource;

            // Get depthEnum = adObj or AnalysisDepth.STANDARD
            Object depthEnum = analysisDepthObj;
            if (depthEnum == null || !adClass.isInstance(depthEnum)) {
                depthEnum = Enum.valueOf((Class<Enum>) adClass, "STANDARD");
            }

            // Emit initial Progress
            // InProgress(float progress, AnalysisDepth depth, m source)
            Constructor<?> ipConstructor = types.inProgressCtor;
            Object initialProgress = ipConstructor.newInstance(0.0f, depthEnum, sourceEnum);
            emitter.emit(initialProgress);

            // Parse PGN using the app's native parser
            Class<?> qClass = Class.forName("com.chess.chessboard.pgn.q");
            Class<?> fenTypeClass = Class.forName("com.chess.chessboard.fen.FenParser$FenType");
            Object fenTypeC = fenTypeClass.getField("c").get(null);

            Method parsePgnMethod = qClass.getMethod("a", String.class, boolean.class, boolean.class, fenTypeClass);
            Object gameObj = parsePgnMethod.invoke(null, pgn, true, true, fenTypeC);

            Method getMovesMethod;
            try {
                getMovesMethod = gameObj.getClass().getMethod("getMoves");
            } catch (NoSuchMethodException e) {
                getMovesMethod = gameObj.getClass().getMethod("b");
            }
            List<?> moves = (List<?>) getMovesMethod.invoke(gameObj);
            int totalMoves = moves.size();

            Method getStartingPositionMethod;
            try {
                getStartingPositionMethod = gameObj.getClass().getMethod("getStartingPosition");
            } catch (NoSuchMethodException e) {
                getStartingPositionMethod = gameObj.getClass().getMethod("c");
            }
            Object startingPosition = getStartingPositionMethod.invoke(gameObj);
            String startingFen = StockfishExtension.extractFen(startingPosition);

            Class<?> moveConverterClass = Class.forName("com.chess.chessboard.compengine.MoveConverterKt");
            Method moveConvertMethod = moveConverterClass.getMethod("b", Class.forName("com.chess.chessboard.l"));

            // Extract every FEN and played move ONCE (the old code re-extracted FENs through
            // reflection several times per move for its sacrifice check).
            String[] fens = new String[totalMoves + 1];
            String[] playedLans = new String[totalMoves];
            fens[0] = startingFen;
            for (int i = 0; i < totalMoves; i++) {
                Object csrmm = moves.get(i);
                fens[i + 1] = StockfishExtension.extractFen(getPositionAfter(csrmm));
                Method getRawMoveMethod;
                try {
                    getRawMoveMethod = csrmm.getClass().getMethod("getRawMove");
                } catch (NoSuchMethodException e) {
                    getRawMoveMethod = csrmm.getClass().getMethod("a");
                }
                playedLans[i] = (String) moveConvertMethod.invoke(null, getRawMoveMethod.invoke(csrmm));
            }

            // Stockfish-safe move list (standard castling notation) + lightweight boards.
            char[][] boards = new char[totalMoves + 1][];
            boards[0] = BoardUtil.parseBoard(startingFen);
            List<String> engineMoves = new ArrayList<>(totalMoves);
            boolean historyUsable = startingFen != null;
            for (int i = 0; i < totalMoves; i++) {
                String m = playedLans[i];
                if (m == null || m.length() < 4) {
                    historyUsable = false;
                    boards[i + 1] = BoardUtil.parseBoard(fens[i + 1]);
                    continue;
                }
                engineMoves.add(BoardUtil.normalizeCastling(boards[i], m));
                char[] next = boards[i].clone();
                BoardUtil.apply(next, m);
                boards[i + 1] = next;
                // Cross-check against the app's own position; on any disagreement stop sending
                // history (FEN-only analysis is still correct, just less informed).
                if (fens[i + 1] != null && !BoardUtil.placement(next).equals(BoardUtil.placement(fens[i + 1]))) {
                    historyUsable = false;
                    boards[i + 1] = BoardUtil.parseBoard(fens[i + 1]);
                }
            }

            // Run Stockfish on every position (start + after each move) with the full move
            // history, so repetitions and the 50-move rule are seen by the engine.
            StockfishBridge.newGame();
            StockfishProcess.AnalysisResult[] results = new StockfishProcess.AnalysisResult[totalMoves + 1];
            for (int i = 0; i <= totalMoves; i++) {
                emitter.ensureActive();
                List<String> hist = historyUsable ? new ArrayList<>(engineMoves.subList(0, i)) : null;
                results[i] = StockfishBridge.analyzeForReview(startingFen, hist, fens[i], searchDepth, reviewMultiPV, movetimeCap);

                float progress = (float) (i + 1) / (totalMoves + 1);
                Object progObj = ipConstructor.newInstance(progress, depthEnum, sourceEnum);
                emitter.emit(progObj);
            }

            // Map Stockfish analysis outputs to AnalyzedGameData's AnalyzedPositions
            Class<?> apClass = types.agd("$AnalyzedPosition");
            Class<?> colorClass = Class.forName("com.chess.entities.Color");
            Class<?> pmClass = types.agd("$AnalyzedPosition$PlayedMove");
            Class<?> smClass = types.agd("$AnalyzedPosition$SuggestedMove");
            Class<?> bmClass = types.agd("$AnalyzedPosition$BestMove");
            Class<?> scClass = types.agd("$AnalyzedPosition$Scenarios");
            Class<?> evalClass = types.agd("$AnalyzedPosition$Eval");

            Constructor<?> apConstructor = apClass.getConstructor(colorClass, pmClass, smClass, bmClass, String.class, scClass);
            // PlayedMove: (depth, score, mateIn, moveLan, eval, speech, coachEmotion, skills, skillsHash, boardMarkings)
            // Everything after the first 7 parameters is optional and defaults to null/empty.
            Constructor<?> pmConstructor = AppTypes.primaryCtor(pmClass);
            Constructor<?> smConstructor = smClass.getConstructor(
                float.class, Integer.class, String.class, evalClass, List.class, String.class
            );
            Constructor<?> bmConstructor = bmClass.getConstructor(String.class);
            Constructor<?> scConstructor = scClass.getConstructor(boolean.class, boolean.class);
            Constructor<?> evalConstructor = evalClass.getConstructor(List.class, int.class);

            Object colorWhite = Enum.valueOf((Class<Enum>) colorClass, "WHITE");
            Object colorBlack = Enum.valueOf((Class<Enum>) colorClass, "BLACK");

            List<Object> positions = new ArrayList<>();
            // Tally order matches MovesTally: book, brilliant, greatFind, best, excellent, good,
            // inaccuracy, mistake, blunder, forced, miss
            int[] wT = new int[11];
            int[] bT = new int[11];

            // ── Starting position (index 0) ────────────────────────────────────────────
            StockfishProcess.AnalysisResult startResult = results[0];
            String startBestLan = !startResult.moves.isEmpty() ? startResult.moves.get(0) : null;
            Object startSuggestedMove = null;
            Object startBestMove = null;
            if (startBestLan != null) {
                List<String> startPv = linePv(startResult.pv, startBestLan);
                startSuggestedMove = smConstructor.newInstance(
                    startResult.score,
                    startResult.hasMate ? startResult.mateIn : null,
                    startBestLan,
                    evalConstructor.newInstance(startPv, pvCutoff(startPv)),
                    new ArrayList<>(),
                    null
                );
                startBestMove = bmConstructor.newInstance(startBestLan);
            }
            positions.add(apConstructor.newInstance(
                colorWhite, null, startSuggestedMove, startBestMove, null,
                scConstructor.newInstance(false, false)
            ));

            // Win% (white POV, 0..100) of every position, for volatility weights.
            float[] whiteWinPct = new float[totalMoves + 1];
            for (int i = 0; i <= totalMoves; i++) {
                whiteWinPct[i] = ReviewMath.whiteWin(results[i].score) * 100f;
            }
            int volWindow = Math.max(2, Math.min(8, totalMoves / 10));

            // Accuracy samples {accuracy, weight}, overall and per phase (opening/middle/end)
            List<float[]> wAll = new ArrayList<>(), bAll = new ArrayList<>();
            List<List<float[]>> wPhase = new ArrayList<>(), bPhase = new ArrayList<>();
            for (int k = 0; k < 3; k++) { wPhase.add(new ArrayList<>()); bPhase.add(new ArrayList<>()); }

            float prevLoss = -1f;

            for (int i = 0; i < totalMoves; i++) {
                String playedLan = playedLans[i];
                String fenBefore = fens[i];
                boolean isWhite = fenBefore == null || fenBefore.indexOf(" b ") < 0;
                Object color = isWhite ? colorWhite : colorBlack;

                StockfishProcess.AnalysisResult resultBefore = results[i];
                StockfishProcess.AnalysisResult resultAfter = results[i + 1];

                String bestLan = !resultBefore.moves.isEmpty() ? resultBefore.moves.get(0) : null;
                float evalBefore = resultBefore.score;
                float evalAfter = resultAfter.score;

                // ── Expected-points loss ─────────────────────────────────────────────
                // If the played move is one of the MultiPV lines, compare scores from the SAME
                // search. This removes the depth noise between two separate searches that made
                // correct moves look like gains ("Great") or small losses.
                String playedNorm = playedLan != null ? BoardUtil.normalizeCastling(boards[i], playedLan) : null;
                int lineIdx = playedNorm != null ? resultBefore.moves.indexOf(playedNorm) : -1;
                if (lineIdx < 0 && playedLan != null) lineIdx = resultBefore.moves.indexOf(playedLan);

                float winBefore = ReviewMath.win(evalBefore, isWhite);
                float winAfter = ReviewMath.win(evalAfter, isWhite);
                float loss;
                if (lineIdx == 0) {
                    loss = 0f;
                } else if (lineIdx > 0 && lineIdx < resultBefore.lineScores.length) {
                    loss = Math.max(0f, winBefore - ReviewMath.win(resultBefore.lineScores[lineIdx], isWhite));
                } else {
                    loss = Math.max(0f, winBefore - winAfter);
                }
                // A move that delivers checkmate is always best.
                boolean deliversMate = resultAfter.terminal && resultAfter.hasMate;
                if (deliversMate) loss = 0f;

                boolean isBest = lineIdx == 0 || deliversMate;
                boolean forced = resultBefore.moves.size() == 1 && resultBefore.lineScores.length == 1
                        && reviewMultiPV > 1 && !resultBefore.terminal;

                float secondGap = -1f;
                if (resultBefore.lineScores.length >= 2) {
                    secondGap = ReviewMath.win(resultBefore.lineScores[0], isWhite)
                              - ReviewMath.win(resultBefore.lineScores[1], isWhite);
                }

                boolean moverHadMate = resultBefore.hasMate && (isWhite ? resultBefore.mateIn > 0 : resultBefore.mateIn < 0);
                boolean moverStillMates = deliversMate
                        || (resultAfter.hasMate && (isWhite ? resultAfter.mateIn > 0 : resultAfter.mateIn < 0));
                boolean missedMate = !isBest && moverHadMate && !moverStillMates;

                boolean sacrifice = false;
                boolean recapture = false;
                try {
                    if (playedLan != null && !resultAfter.pv.isEmpty()) {
                        sacrifice = BoardUtil.isSacrifice(boards[i], isWhite, playedLan, resultAfter.pv, 5);
                    }
                    if (i > 0 && playedLans[i - 1] != null) {
                        boolean prevCapture = BoardUtil.material(boards[i], isWhite) < BoardUtil.material(boards[i - 1], isWhite);
                        recapture = BoardUtil.isRecapture(playedLans[i - 1], prevCapture, playedLan);
                    }
                } catch (Throwable ignored) {}

                String classification = ReviewMath.classify(isBest, forced, loss, winBefore, winAfter,
                        secondGap, sacrifice, recapture, prevLoss, missedMate);
                prevLoss = loss;

                int tIdx = tallyIndex(classification);
                if (isWhite) wT[tIdx]++; else bT[tIdx]++;

                // ── Accuracy sample ─────────────────────────────────────────────────
                float moveAcc = ReviewMath.moveAccuracy(1f, 1f - loss);
                float weight = volatility(whiteWinPct, i, volWindow);
                int phase = BoardUtil.phase(boards[i], i);
                float[] sample = new float[]{moveAcc, weight};
                if (isWhite) { wAll.add(sample); wPhase.get(phase).add(sample); }
                else { bAll.add(sample); bPhase.get(phase).add(sample); }

                // ── Played move: eval line must START with the played move ────────────
                // (the app replays it from the position before the move; the old code passed
                // a list of alternative first moves, which is not a legal line).
                String safePlayed = playedLan != null ? playedLan : (bestLan != null ? bestLan : "");
                List<String> playedPv = new ArrayList<>();
                playedPv.add(safePlayed);
                if (!resultAfter.terminal) {
                    for (int k = 0; k < resultAfter.pv.size() && k < 7; k++) playedPv.add(resultAfter.pv.get(k));
                }
                Integer playedMateIn = resultAfter.hasMate ? resultAfter.mateIn : null;
                Object[] pmArgs = new Object[pmConstructor.getParameterTypes().length];
                for (int k = 0; k < pmArgs.length; k++) pmArgs[k] = AppTypes.defaultFor(pmConstructor.getParameterTypes()[k]);
                pmArgs[0] = String.valueOf(Math.max(searchDepth, resultAfter.depth)); // depth
                pmArgs[1] = evalAfter;                                                // score
                pmArgs[2] = playedMateIn;                                             // mateIn
                pmArgs[3] = safePlayed;                                               // moveLan
                pmArgs[4] = evalConstructor.newInstance(playedPv, pvCutoff(playedPv)); // eval
                pmArgs[5] = new ArrayList<>();                                        // speech
                pmArgs[6] = null;                                                     // coachEmotion
                Object playedMove = pmConstructor.newInstance(pmArgs);

                String suggestedLan = bestLan != null ? bestLan : safePlayed;
                List<String> suggestedPv = bestLan != null ? linePv(resultBefore.pv, bestLan) : playedPv;
                Object suggestedMove = smConstructor.newInstance(
                    bestLan != null ? evalBefore : evalAfter,
                    bestLan != null ? (resultBefore.hasMate ? resultBefore.mateIn : null) : playedMateIn,
                    suggestedLan,
                    evalConstructor.newInstance(suggestedPv, pvCutoff(suggestedPv)),
                    new ArrayList<>(),
                    null
                );
                Object bestMove = bmConstructor.newInstance(suggestedLan);

                Object scenarios = scConstructor.newInstance(ReviewMath.isKeyMoment(classification),
                        ReviewMath.BOOK.equals(classification));
                positions.add(apConstructor.newInstance(
                    color, playedMove, suggestedMove, bestMove, classification, scenarios
                ));
            }

            // Tallies Construction
            Class<?> mtClass = types.agd("$Tallies$MovesTally");
            Constructor<?> mtConstructor = mtClass.getConstructor(
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class
            );
            Object whiteTally = mtConstructor.newInstance(wT[0], wT[1], wT[2], wT[3], wT[4], wT[5], wT[6], wT[7], wT[8], wT[9], wT[10]);
            Object blackTally = mtConstructor.newInstance(bT[0], bT[1], bT[2], bT[3], bT[4], bT[5], bT[6], bT[7], bT[8], bT[9], bT[10]);

            Class<?> talliesClass = types.agd("$Tallies");
            Constructor<?> talliesConstructor = talliesClass.getConstructor(mtClass, mtClass, String.class, String.class);
            Object tallies = talliesConstructor.newInstance(whiteTally, blackTally, "Game Summary", "Game Summary Play");

            // Accuracy: evaluation-based (win probability), overall and per game phase
            float wAcc = ReviewMath.gameAccuracy(wAll);
            float bAcc = ReviewMath.gameAccuracy(bAll);

            Class<?> accClass = types.agd("$AccuracyScores$Accuracy");
            Constructor<?> accConstructor = accClass.getConstructor(float.class, Float.class, Float.class, Float.class);
            Object whiteAcc = accConstructor.newInstance(wAcc, phaseAcc(wPhase.get(0)), phaseAcc(wPhase.get(1)), phaseAcc(wPhase.get(2)));
            Object blackAcc = accConstructor.newInstance(bAcc, phaseAcc(bPhase.get(0)), phaseAcc(bPhase.get(1)), phaseAcc(bPhase.get(2)));

            Class<?> accScoresClass = types.agd("$AccuracyScores");
            Constructor<?> accScoresConstructor = accScoresClass.getConstructor(accClass, accClass);
            Object accuracyScores = accScoresConstructor.newInstance(whiteAcc, blackAcc);

            // ReportCard Setup
            Class<?> rcClass = types.agd("$ReportCard");
            Class<?> repClass = types.agd("$ReportCard$Report");
            Class<?> glyphsClass = types.agd("$ReportCard$Report$Glyphs");
            Class<?> catClass = types.agd("$ReportCard$CategoryRating");

            Constructor<?> rcConstructor = rcClass.getConstructor(repClass, repClass, String.class);
            Constructor<?> repConstructor = repClass.getConstructor(Integer.class, glyphsClass, List.class);
            Constructor<?> glyphsConstructor = glyphsClass.getConstructor(String.class, String.class, String.class);
            Constructor<?> catConstructor = catClass.getConstructor(String.class, int.class, String.class, int.class);

            Object whiteGlyphs = glyphsConstructor.newInstance(null, null, null);
            Object blackGlyphs = glyphsConstructor.newInstance(null, null, null);

            // Estimate game rating from accuracy (piecewise curve approximating Chess.com)
            int wRating = estimateRating(wAcc);
            int bRating = estimateRating(bAcc);

            String wGrade = wAcc >= 90 ? "Excellent" : wAcc >= 75 ? "Great" : wAcc >= 60 ? "Good" : wAcc >= 40 ? "Fair" : "Poor";
            String bGrade = bAcc >= 90 ? "Excellent" : bAcc >= 75 ? "Great" : bAcc >= 60 ? "Good" : bAcc >= 40 ? "Fair" : "Poor";

            List<Object> wRatings = new ArrayList<>();
            wRatings.add(catConstructor.newInstance("Opening", Math.max(200, wRating - 30), wGrade, 0));
            wRatings.add(catConstructor.newInstance("Tactics", Math.min(2900, wRating + 10), wGrade, 0));
            wRatings.add(catConstructor.newInstance("Endgame", Math.max(200, wRating - 15), wGrade, 0));
            Object whiteReport = repConstructor.newInstance(wRating, whiteGlyphs, wRatings);

            List<Object> bRatings = new ArrayList<>();
            bRatings.add(catConstructor.newInstance("Opening", Math.max(200, bRating - 30), bGrade, 0));
            bRatings.add(catConstructor.newInstance("Tactics", Math.min(2900, bRating + 10), bGrade, 0));
            bRatings.add(catConstructor.newInstance("Endgame", Math.max(200, bRating - 15), bGrade, 0));
            Object blackReport = repConstructor.newInstance(bRating, blackGlyphs, bRatings);

            Object reportCard = rcConstructor.newInstance(whiteReport, blackReport, "Local analysis complete.");

            // Themes Setup
            Class<?> twClass = types.agd("$Themes$ThemesWeights");
            Constructor<?> twConstructor = twClass.getConstructor(Map.class, Map.class);
            Object themesWeights = twConstructor.newInstance(new HashMap<String, Integer>(), new HashMap<String, Integer>());

            Class<?> themesClass = types.agd("$Themes");
            Constructor<?> themesConstructor = themesClass.getConstructor(twClass);
            Object themes = themesConstructor.newInstance(themesWeights);

            // Build the final AnalyzedGameData
            Class<?> agdClass = types.agd("");

            // AnalyzedGameData primary constructor: (startingFen, tallies, accuracyScores, positions,
            // openingInfo, arc, arcPlayerScenarios, playMayContinue, themes, cee, metaData, reportCard,
            // analysisStrength, gameSummary, gameSummaryAudioUrlHash, gameSummaryCoachEmotion,
            // takeaways, gameResult). Fill by type in declaration order; unknown/optional parameters get null/0/empty.
            Constructor<?> agdConstructor = AppTypes.primaryCtor(agdClass);
            Class<?>[] agdTypes = agdConstructor.getParameterTypes();
            Object[] agdArgs = new Object[agdTypes.length];
            int stringSlot = 0;
            String[] strings = {
                startingFen,                          // startingFen
                "",                                   // arc (non-null)
                "depth_" + searchDepth,               // analysisStrength
                "Local Stockfish analysis complete.", // gameSummary
                null,                                 // gameSummaryAudioUrlHash
                null                                  // gameSummaryCoachEmotion
            };
            for (int k = 0; k < agdTypes.length; k++) {
                Class<?> t = agdTypes[k];
                if (t == String.class) agdArgs[k] = stringSlot < strings.length ? strings[stringSlot++] : null;
                else if (t == talliesClass) agdArgs[k] = tallies;
                else if (t == accScoresClass) agdArgs[k] = accuracyScores;
                else if (t == List.class) agdArgs[k] = positions;
                else if (t == themesClass) agdArgs[k] = themes;
                else if (t == rcClass) agdArgs[k] = reportCard;
                else agdArgs[k] = AppTypes.defaultFor(t);             // openingInfo, cee, gameResult, ...
            }
            Object gameData = agdConstructor.newInstance(agdArgs);

            // Get permissions — construct directly with all-true to avoid obfuscated companion field names
            Class<?> permissionsClass = Class.forName("com.chess.entities.GameAnalysisPermissions");
            Class<?> quotaTypeClass = Class.forName("com.chess.entities.GameAnalysisPermissions$QuotaType");
            Constructor<?> permConstructor = permissionsClass.getConstructor(
                boolean.class, boolean.class, boolean.class, boolean.class, quotaTypeClass
            );
            Object fullPermissions = permConstructor.newInstance(true, true, true, true, null);

            // Emit RemoteAnalysisCompleted to trigger Review UI
            Constructor<?> compConstructor = types.completedCtor;
            Object completedResult = compConstructor.newInstance(gameData, fullPermissions, depthEnum);
            emitter.emit(completedResult);

        } catch (java.util.concurrent.CancellationException c) {
            // The review screen was closed: complete the coroutine as cancelled.
            Log.i(TAG, "Local review cancelled");
            throw c;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            if (cause instanceof java.util.concurrent.CancellationException) throw cause;
            logToFile(activity, "EXCEPTION: " + Log.getStackTraceString(cause), true);
            Log.e(TAG, "Local stockfish analysis failed", cause);
            // Report through the app's own Failure state so the screen shows its error UI.
            emitter.emit(types.failureCtor.newInstance(cause));
        }
    }

    /** Tally slot for a classification (MovesTally constructor order). */
    private static int tallyIndex(String c) {
        switch (c) {
            case ReviewMath.BOOK: return 0;
            case ReviewMath.BRILLIANT: return 1;
            case ReviewMath.GREAT: return 2;
            case ReviewMath.BEST: return 3;
            case ReviewMath.EXCELLENT: return 4;
            case ReviewMath.GOOD: return 5;
            case ReviewMath.INACCURACY: return 6;
            case ReviewMath.MISTAKE: return 7;
            case ReviewMath.BLUNDER: return 8;
            case ReviewMath.FORCED: return 9;
            case ReviewMath.MISS: return 10;
            default: return 5;
        }
    }

    /** Engine line that starts with {@code first} (never null, never empty). */
    private static List<String> linePv(List<String> pv, String first) {
        List<String> out = new ArrayList<>();
        if (pv != null && !pv.isEmpty() && first.equals(pv.get(0))) {
            for (int k = 0; k < pv.size() && k < 8; k++) out.add(pv.get(k));
        } else {
            out.add(first);
        }
        return out;
    }

    /** Index of the last move of the line the app should show (app shows cutoff + 1 moves). */
    private static int pvCutoff(List<String> pv) {
        return Math.max(0, Math.min(pv.size(), 8) - 1);
    }

    /** Standard deviation of white win% in a window around ply i (Lichess volatility weight). */
    private static float volatility(float[] winPct, int i, int window) {
        int from = Math.max(0, i - window / 2);
        int to = Math.min(winPct.length - 1, from + window);
        from = Math.max(0, to - window);
        int n = to - from + 1;
        if (n < 2) return 1f;
        double mean = 0;
        for (int k = from; k <= to; k++) mean += winPct[k];
        mean /= n;
        double var = 0;
        for (int k = from; k <= to; k++) var += (winPct[k] - mean) * (winPct[k] - mean);
        return (float) Math.sqrt(var / n);
    }

    private static Float phaseAcc(List<float[]> samples) {
        if (samples == null || samples.size() < 2) return null;
        return ReviewMath.gameAccuracy(samples);
    }

    /**
     * Piecewise linear interpolation approximating Chess.com's accuracy-to-rating curve.
     * Much more realistic than a simple linear mapping.
     */
    private static int estimateRating(float accuracy) {
        float[] accPoints    = {  0,  30,  50,  60,  70,  75,  80,  85,  90,  93,  95,  97,  99, 100};
        int[]   ratingPoints = {200, 400, 700, 1000, 1300, 1500, 1700, 1900, 2100, 2300, 2500, 2650, 2800, 2900};

        if (accuracy <= accPoints[0]) return ratingPoints[0];
        if (accuracy >= accPoints[accPoints.length - 1]) return ratingPoints[ratingPoints.length - 1];

        for (int i = 1; i < accPoints.length; i++) {
            if (accuracy <= accPoints[i]) {
                float t = (accuracy - accPoints[i - 1]) / (accPoints[i] - accPoints[i - 1]);
                return (int) (ratingPoints[i - 1] + t * (ratingPoints[i] - ratingPoints[i - 1]));
            }
        }
        return ratingPoints[ratingPoints.length - 1];
    }
    private static Object getPositionBefore(Object csrmm) throws Exception {
        try {
            return csrmm.getClass().getMethod("getPositionBefore").invoke(csrmm);
        } catch (NoSuchMethodException e) {
            return csrmm.getClass().getMethod("e").invoke(csrmm);
        }
    }

    private static Object getPositionAfter(Object csrmm) throws Exception {
        try {
            return csrmm.getClass().getMethod("getPositionAfter").invoke(csrmm);
        } catch (NoSuchMethodException e) {
            return csrmm.getClass().getMethod("b").invoke(csrmm);
        }
    }

    private static void logToFile(android.content.Context context, String msg, boolean append) {
        try {
            if (context == null) return;
            java.io.File dir = context.getExternalFilesDir(null);
            if (dir == null) return;
            java.io.File logFile = new java.io.File(dir, "game_review_debug.txt");
            java.io.FileWriter fw = new java.io.FileWriter(logFile, append);
            fw.write("[" + new java.util.Date() + "] " + msg + "\n");
            fw.close();
        } catch (Throwable t) {
            Log.e("LocalAnalysisFlow", "Failed to write log to file", t);
        }
    }
}

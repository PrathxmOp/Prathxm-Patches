/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

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

    public static Object createFlow(final String pgn, final Object analysisDepthObj) {
        try {
            ClassLoader classLoader = StockfishExtension.class.getClassLoader();
            Class<?> g74Class = Class.forName("com.google.android.g74");

            return Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{g74Class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("collect")) {
                            // args[0] is the flow collector (a84)
                            // args[1] is the continuation (o02)
                            runCollect(pgn, analysisDepthObj, args[0], args[1]);
                            return getUnitInstance();
                        }
                        if (method.getName().equals("toString")) {
                            return "LocalAnalysisFlow(" + pgn + ")";
                        }
                        return null;
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create dynamic proxy flow", t);
            return null;
        }
    }

    private static void runCollect(String pgn, Object analysisDepthObj, Object collector, Object continuation) {
        try {
            // Fair Play Gating: Prevent any local analysis during active live match
            Activity activity = StockfishExtension.getCurrentActivity();
            if (activity != null && StockfishExtension.isLiveMatch(activity) && !StockfishExtension.isReviewMode) {
                Log.w(TAG, "Analysis request blocked: Live gameplay detected.");
                return;
            }

            int searchDepth = 12;
            if (analysisDepthObj != null) {
                String depthName = analysisDepthObj.toString();
                if ("FAST".equals(depthName)) searchDepth = 10;
                else if ("STANDARD".equals(depthName)) searchDepth = 12;
                else if ("DEEP".equals(depthName)) searchDepth = 15;
                else if ("MAXIMUM".equals(depthName)) searchDepth = 18;
            }

            // Get Reflection Classes
            Class<?> inProgressClass = Class.forName("com.chess.gamereview.repository.h$b");
            Class<?> completedClass = Class.forName("com.chess.gamereview.repository.h$d");
            Class<?> failureClass = Class.forName("com.chess.gamereview.repository.h$a");
            Class<?> adClass = Class.forName("com.chess.entities.AnalysisDepth");
            Class<?> mClass = Class.forName("com.chess.gamereview.repository.m");
            Class<?> maClass = Class.forName("com.chess.gamereview.repository.m$a");
            Class<?> a84Class = Class.forName("com.google.android.a84");
            Class<?> o02Class = Class.forName("com.google.android.o02");

            Method emitMethod = a84Class.getMethod("emit", Object.class, o02Class);

            // Get sourceEnum = m.a.a (singleton)
            Object sourceEnum = maClass.getField("a").get(null);

            // Get depthEnum = adObj or AnalysisDepth.STANDARD
            Object depthEnum = analysisDepthObj;
            if (depthEnum == null || !adClass.isInstance(depthEnum)) {
                depthEnum = Enum.valueOf((Class<Enum>) adClass, "STANDARD");
            }

            // Emit initial Progress
            // InProgress(float progress, AnalysisDepth depth, m source)
            Constructor<?> ipConstructor = inProgressClass.getConstructor(float.class, adClass, mClass);
            Object initialProgress = ipConstructor.newInstance(0.0f, depthEnum, sourceEnum);
            emitMethod.invoke(collector, initialProgress, continuation);

            // Parse PGN using the app's native parser
            Class<?> qClass = Class.forName("com.chess.chessboard.pgn.q");
            Class<?> fenTypeClass = Class.forName("com.chess.chessboard.fen.FenParser$FenType");
            Object fenTypeC = fenTypeClass.getField("c").get(null);

            Method parsePgnMethod = qClass.getMethod("a", String.class, boolean.class, boolean.class, fenTypeClass);
            Object gameObj = parsePgnMethod.invoke(null, pgn, true, true, fenTypeC);

            // Get moves list
            Method getMovesMethod;
            try {
                getMovesMethod = gameObj.getClass().getMethod("getMoves");
            } catch (NoSuchMethodException e) {
                getMovesMethod = gameObj.getClass().getMethod("b");
            }
            List<?> moves = (List<?>) getMovesMethod.invoke(gameObj);
            int totalMoves = moves.size();

            // Run Stockfish sequentially on all game positions (starting pos + after each move)
            StockfishProcess.AnalysisResult[] results = new StockfishProcess.AnalysisResult[totalMoves + 1];

            // Analyze starting position
            Method getStartingPositionMethod;
            try {
                getStartingPositionMethod = gameObj.getClass().getMethod("getStartingPosition");
            } catch (NoSuchMethodException e) {
                getStartingPositionMethod = gameObj.getClass().getMethod("c");
            }
            Object startingPosition = getStartingPositionMethod.invoke(gameObj);
            String startingFen = StockfishExtension.extractFen(startingPosition);

            results[0] = StockfishBridge.analyze(startingFen, searchDepth, 3);
            Object progObj1 = ipConstructor.newInstance(1.0f / (totalMoves + 1), depthEnum, sourceEnum);
            emitMethod.invoke(collector, progObj1, continuation);

            // Analyze subsequent positions
            for (int i = 0; i < totalMoves; i++) {
                Object csrmm = moves.get(i);
                Method getPosAfterMethod;
                try {
                    getPosAfterMethod = csrmm.getClass().getMethod("getPositionAfter");
                } catch (NoSuchMethodException e) {
                    getPosAfterMethod = csrmm.getClass().getMethod("b");
                }
                Object positionAfter = getPosAfterMethod.invoke(csrmm);
                String fenAfter = StockfishExtension.extractFen(positionAfter);

                results[i + 1] = StockfishBridge.analyze(fenAfter, searchDepth, 3);

                float progress = (float)(i + 2) / (totalMoves + 1);
                Object progObj = ipConstructor.newInstance(progress, depthEnum, sourceEnum);
                emitMethod.invoke(collector, progObj, continuation);
            }

            // Map Stockfish analysis outputs to AnalyzedGameData's AnalyzedPositions
            Class<?> apClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition");
            Class<?> colorClass = Class.forName("com.chess.entities.Color");
            Class<?> pmClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition$PlayedMove");
            Class<?> smClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition$SuggestedMove");
            Class<?> bmClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition$BestMove");
            Class<?> scClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition$Scenarios");
            Class<?> evalClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition$Eval");
            Class<?> seClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AnalyzedPosition$SkillsEarned");

            Constructor<?> apConstructor = apClass.getConstructor(colorClass, pmClass, smClass, bmClass, String.class, scClass);
            Constructor<?> pmConstructor = pmClass.getConstructor(
                String.class, float.class, Integer.class, String.class, evalClass, List.class, String.class, seClass
            );
            Constructor<?> smConstructor = smClass.getConstructor(
                float.class, Integer.class, String.class, evalClass, List.class, String.class
            );
            Constructor<?> bmConstructor = bmClass.getConstructor(String.class);
            Constructor<?> scConstructor = scClass.getConstructor(boolean.class, boolean.class);
            Constructor<?> evalConstructor = evalClass.getConstructor(List.class, int.class);

            Object colorWhite = Enum.valueOf((Class<Enum>) colorClass, "WHITE");
            Object colorBlack = Enum.valueOf((Class<Enum>) colorClass, "BLACK");

            List<Object> positions = new ArrayList<>();
            int wBook = 0, wBrilliant = 0, wGreat = 0, wBest = 0, wExcellent = 0, wGood = 0, wInaccuracy = 0, wMistake = 0, wBlunder = 0, wForced = 0, wMiss = 0;
            int bBook = 0, bBrilliant = 0, bGreat = 0, bBest = 0, bExcellent = 0, bGood = 0, bInaccuracy = 0, bMistake = 0, bBlunder = 0, bForced = 0, bMiss = 0;

            // Construct starting position (index 0)
            StockfishProcess.AnalysisResult startResult = results[0];
            String startBestLan = (startResult.moves != null && !startResult.moves.isEmpty())
                ? startResult.moves.get(0)
                : null;
            List<String> startPv = (startResult.moves != null) ? startResult.moves : new ArrayList<>();
            Object startSuggestedMove = null;
            Object startBestMove = null;
            if (startBestLan != null) {
                Object startSuggestedEval = evalConstructor.newInstance(startPv, 3);
                Integer startSuggestedMateIn = startResult.hasMate ? startResult.mateIn : null;
                startSuggestedMove = smConstructor.newInstance(
                    startResult.score,
                    startSuggestedMateIn,
                    startBestLan,
                    startSuggestedEval,
                    new ArrayList<>(),
                    null
                );
                startBestMove = bmConstructor.newInstance(startBestLan);
            }
            Object startScenarios = scConstructor.newInstance(false, false);
            positions.add(apConstructor.newInstance(
                colorWhite,
                null,
                startSuggestedMove,
                startBestMove,
                null,
                startScenarios
            ));

            Class<?> moveConverterClass = Class.forName("com.chess.chessboard.compengine.MoveConverterKt");
            Method moveConvertMethod = moveConverterClass.getMethod("b", Class.forName("com.chess.chessboard.l"));

            for (int i = 0; i < totalMoves; i++) {
                Object csrmm = moves.get(i);
                
                // Get played move
                Method getRawMoveMethod;
                try {
                    getRawMoveMethod = csrmm.getClass().getMethod("getRawMove");
                } catch (NoSuchMethodException e) {
                    getRawMoveMethod = csrmm.getClass().getMethod("a"); // wait, let's verify getter "a" or getRawMove
                }
                Object rawMoveObj = getRawMoveMethod.invoke(csrmm);
                String playedLan = (String) moveConvertMethod.invoke(null, rawMoveObj);

                // Get FEN before to check turn
                Method getPosBeforeMethod;
                try {
                    getPosBeforeMethod = csrmm.getClass().getMethod("getPositionBefore");
                } catch (NoSuchMethodException e) {
                    getPosBeforeMethod = csrmm.getClass().getMethod("e");
                }
                Object positionBefore = getPosBeforeMethod.invoke(csrmm);
                String fenBefore = StockfishExtension.extractFen(positionBefore);
                boolean isWhite = fenBefore.split("\\s+")[1].equals("w");
                Object color = isWhite ? colorWhite : colorBlack;

                StockfishProcess.AnalysisResult resultBefore = results[i];
                StockfishProcess.AnalysisResult resultAfter = results[i + 1];

                // Guard against null/empty moves on terminal positions (checkmate, stalemate).
                // Both SuggestedMove and BestMove constructors require a non-null moveLan.
                String bestLan = (resultBefore.moves != null && !resultBefore.moves.isEmpty())
                    ? resultBefore.moves.get(0)
                    : null;

                // Ensure pv lists are never null when passed to Eval constructor
                List<String> pvBefore = (resultBefore.moves != null) ? resultBefore.moves : new ArrayList<>();
                List<String> pvAfter  = (resultAfter.moves  != null) ? resultAfter.moves  : new ArrayList<>();

                float evalBefore = resultBefore.score;
                float evalAfter = resultAfter.score;
                float actualDelta = isWhite ? (evalAfter - evalBefore) : (evalBefore - evalAfter);

                // Classification Heuristics
                String classification = "good";
                if (bestLan != null && playedLan.equals(bestLan)) {
                    if (actualDelta > 0.4f) {
                        classification = "brilliant";
                        if (isWhite) wBrilliant++; else bBrilliant++;
                    } else {
                        classification = "best";
                        if (isWhite) wBest++; else bBest++;
                    }
                } else if (bestLan != null && pvBefore.contains(playedLan)) {
                    classification = "excellent";
                    if (isWhite) wExcellent++; else bExcellent++;
                } else {
                    if (actualDelta < -3.0f) {
                        classification = "blunder";
                        if (isWhite) wBlunder++; else bBlunder++;
                    } else if (actualDelta < -1.5f) {
                        classification = "mistake";
                        if (isWhite) wMistake++; else bMistake++;
                    } else if (actualDelta < -0.5f) {
                        classification = "inaccuracy";
                        if (isWhite) wInaccuracy++; else bInaccuracy++;
                    } else if (actualDelta < -0.1f) {
                        classification = "good";
                        if (isWhite) wGood++; else bGood++;
                    } else {
                        classification = "greatFind";
                        if (isWhite) wGreat++; else bGreat++;
                    }
                }

                // Construct PlayedMove eval – use safe pvAfter (never null)
                Object playedEval = evalConstructor.newInstance(pvAfter, 3);
                Integer playedMateIn = resultAfter.hasMate ? resultAfter.mateIn : null;
                Object playedMove = pmConstructor.newInstance(
                    String.valueOf(searchDepth),
                    evalAfter,
                    playedMateIn,
                    playedLan,
                    playedEval,
                    new ArrayList<>(),
                    null,
                    null
                );

                // For terminal positions (no best move available), the SuggestedMove and BestMove
                // constructors require a non-null moveLan – skip this position to avoid NPE.
                if (bestLan == null) {
                    // Still add the position but with the played move info only;
                    // use the played move as the "suggested" move to keep the UI intact.
                    Object terminalEval = evalConstructor.newInstance(pvAfter, 0);
                    Object terminalSuggestedMove = smConstructor.newInstance(
                        evalAfter,
                        playedMateIn,
                        playedLan,  // fall back to the actual move played
                        terminalEval,
                        new ArrayList<>(),
                        null
                    );
                    Object terminalBestMove = bmConstructor.newInstance(playedLan);
                    Object scenarios = scConstructor.newInstance(false, false);
                    positions.add(apConstructor.newInstance(
                        color,
                        playedMove,
                        terminalSuggestedMove,
                        terminalBestMove,
                        classification,
                        scenarios
                    ));
                    continue;
                }

                Object suggestedEval = evalConstructor.newInstance(pvBefore, 3);
                Integer suggestedMateIn = resultBefore.hasMate ? resultBefore.mateIn : null;
                Object suggestedMove = smConstructor.newInstance(
                    evalBefore,
                    suggestedMateIn,
                    bestLan,
                    suggestedEval,
                    new ArrayList<>(),
                    null
                );

                Object bestMove = bmConstructor.newInstance(bestLan);
                Object scenarios = scConstructor.newInstance(false, false);

                positions.add(apConstructor.newInstance(
                    color,
                    playedMove,
                    suggestedMove,
                    bestMove,
                    classification,
                    scenarios
                ));
            }

            // Tallies Construction
            Class<?> mtClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$Tallies$MovesTally");
            Constructor<?> mtConstructor = mtClass.getConstructor(
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class
            );
            Object whiteTally = mtConstructor.newInstance(wBook, wBrilliant, wGreat, wBest, wExcellent, wGood, wInaccuracy, wMistake, wBlunder, wForced, wMiss);
            Object blackTally = mtConstructor.newInstance(bBook, bBrilliant, bGreat, bBest, bExcellent, bGood, bInaccuracy, bMistake, bBlunder, bForced, bMiss);

            Class<?> talliesClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$Tallies");
            Constructor<?> talliesConstructor = talliesClass.getConstructor(mtClass, mtClass, String.class, String.class);
            Object tallies = talliesConstructor.newInstance(whiteTally, blackTally, "Game Summary", "Game Summary Play");

            // Accuracy Calculations
            float wAcc = calculateAccuracy(wBook, wBrilliant, wGreat, wBest, wExcellent, wGood, wInaccuracy, wMistake, wBlunder, wForced, wMiss);
            float bAcc = calculateAccuracy(bBook, bBrilliant, bGreat, bBest, bExcellent, bGood, bInaccuracy, bMistake, bBlunder, bForced, bMiss);

            Class<?> accClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AccuracyScores$Accuracy");
            Constructor<?> accConstructor = accClass.getConstructor(float.class, Float.class, Float.class, Float.class);
            Object whiteAcc = accConstructor.newInstance(wAcc, null, null, null);
            Object blackAcc = accConstructor.newInstance(bAcc, null, null, null);

            Class<?> accScoresClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$AccuracyScores");
            Constructor<?> accScoresConstructor = accScoresClass.getConstructor(accClass, accClass);
            Object accuracyScores = accScoresConstructor.newInstance(whiteAcc, blackAcc);

            // ReportCard Setup
            Class<?> rcClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$ReportCard");
            Class<?> repClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$ReportCard$Report");
            Class<?> glyphsClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$ReportCard$Report$Glyphs");
            Class<?> catClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$ReportCard$CategoryRating");

            Constructor<?> rcConstructor = rcClass.getConstructor(repClass, repClass, String.class);
            Constructor<?> repConstructor = repClass.getConstructor(Integer.class, glyphsClass, List.class);
            Constructor<?> glyphsConstructor = glyphsClass.getConstructor(String.class, String.class, String.class);
            Constructor<?> catConstructor = catClass.getConstructor(String.class, int.class, String.class, int.class);

            Object whiteGlyphs = glyphsConstructor.newInstance(null, null, null);
            Object blackGlyphs = glyphsConstructor.newInstance(null, null, null);

            // Estimate game rating from accuracy (linear: 0%→200, 100%→2800)
            int wRating = Math.max(200, Math.min(2800, (int)(200 + wAcc * 26)));
            int bRating = Math.max(200, Math.min(2800, (int)(200 + bAcc * 26)));

            String wGrade = wAcc >= 90 ? "Excellent" : wAcc >= 75 ? "Great" : wAcc >= 60 ? "Good" : wAcc >= 40 ? "Fair" : "Poor";
            String bGrade = bAcc >= 90 ? "Excellent" : bAcc >= 75 ? "Great" : bAcc >= 60 ? "Good" : bAcc >= 40 ? "Fair" : "Poor";

            List<Object> wRatings = new ArrayList<>();
            wRatings.add(catConstructor.newInstance("Opening", wRating, wGrade, 0));
            wRatings.add(catConstructor.newInstance("Tactics", wRating, wGrade, 0));
            wRatings.add(catConstructor.newInstance("Endgame", wRating, wGrade, 0));
            Object whiteReport = repConstructor.newInstance(wRating, whiteGlyphs, wRatings);

            List<Object> bRatings = new ArrayList<>();
            bRatings.add(catConstructor.newInstance("Opening", bRating, bGrade, 0));
            bRatings.add(catConstructor.newInstance("Tactics", bRating, bGrade, 0));
            bRatings.add(catConstructor.newInstance("Endgame", bRating, bGrade, 0));
            Object blackReport = repConstructor.newInstance(bRating, blackGlyphs, bRatings);

            Object reportCard = rcConstructor.newInstance(whiteReport, blackReport, "Local analysis complete.");

            // Themes Setup
            Class<?> twClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$Themes$ThemesWeights");
            Constructor<?> twConstructor = twClass.getConstructor(Map.class, Map.class);
            Object themesWeights = twConstructor.newInstance(new HashMap<String, Integer>(), new HashMap<String, Integer>());

            Class<?> themesClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$Themes");
            Constructor<?> themesConstructor = themesClass.getConstructor(twClass);
            Object themes = themesConstructor.newInstance(themesWeights);

            // Build the final AnalyzedGameData
            Class<?> agdClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData");
            Class<?> openingInfoClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$OpeningInfo");
            Class<?> arcPlayerScenariosClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$ArcPlayerScenarios");
            Class<?> gameContinuationClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$GameContinuation");
            Class<?> ceeInfoClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$CeeInfo");
            Class<?> ceacRequestMetadataClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$CeacRequestMetadata");
            Class<?> takeawaysClass = Class.forName("com.chess.gamereview.repository.AnalyzedGameData$Takeaways");

            Constructor<?> agdConstructor = agdClass.getConstructor(
                String.class, // startingFen
                talliesClass, // tallies
                accScoresClass, // accuracyScores
                List.class, // positions
                Integer.class, // bookPly (lastBookMoveOffset)
                openingInfoClass, // openingInfo
                String.class, // arc
                arcPlayerScenariosClass, // arcPlayerScenarios
                gameContinuationClass, // playMayContinue
                themesClass, // themes
                ceeInfoClass, // cee
                ceacRequestMetadataClass, // metaData
                rcClass, // reportCard
                String.class, // analysisStrength
                String.class, // gameSummary
                String.class, // gameSummaryAudioUrlHash
                String.class, // gameSummaryCoachEmotion
                takeawaysClass // takeaways
            );

            Object gameData = agdConstructor.newInstance(
                startingFen,
                tallies,
                accuracyScores,
                positions,
                0,
                null,
                "",
                null,
                null,
                themes,
                null,
                null,
                reportCard,
                "depth_" + searchDepth,
                "Local Stockfish analysis complete.",
                null,
                null,
                null
            );

            // Get permissions — construct directly with all-true to avoid obfuscated companion field names
            Class<?> permissionsClass = Class.forName("com.chess.entities.GameAnalysisPermissions");
            Class<?> quotaTypeClass = Class.forName("com.chess.entities.GameAnalysisPermissions$QuotaType");
            Constructor<?> permConstructor = permissionsClass.getConstructor(
                boolean.class, boolean.class, boolean.class, boolean.class, quotaTypeClass
            );
            Object fullPermissions = permConstructor.newInstance(true, true, true, true, null);

            // Emit RemoteAnalysisCompleted to trigger Review UI
            Constructor<?> compConstructor = completedClass.getConstructor(agdClass, permissionsClass, adClass);
            Object completedResult = compConstructor.newInstance(gameData, fullPermissions, depthEnum);
            emitMethod.invoke(collector, completedResult, continuation);

        } catch (Throwable t) {
            Log.e(TAG, "Local stockfish analysis failed", t);
            try {
                Class<?> failureClass = Class.forName("com.chess.gamereview.repository.h$a");
                Class<?> a84Class = Class.forName("com.google.android.a84");
                Class<?> o02Class = Class.forName("com.google.android.o02");
                Method emitMethod = a84Class.getMethod("emit", Object.class, o02Class);
                Constructor<?> failConstructor = failureClass.getConstructor(Throwable.class);
                Object failureResult = failConstructor.newInstance(t);
                emitMethod.invoke(collector, failureResult, continuation);
            } catch (Throwable emitErr) {
                // Ignore secondary emit failures
            }
        }
    }

    private static float calculateAccuracy(
        int book, int brilliant, int great, int best, int excellent, int good, int inaccuracy, int mistake, int blunder, int forced, int miss
    ) {
        int total = best + brilliant + excellent + good + inaccuracy + mistake + blunder + book;
        if (total == 0) return 100.0f;

        float weighted = (best + brilliant + book) * 100.0f
                       + excellent * 85.0f
                       + good * 70.0f
                       + inaccuracy * 40.0f
                       + mistake * 15.0f;
        return weighted / total;
    }
    private static Object getUnitInstance() {
        try {
            Class<?> unitClass = Class.forName("kotlin.Unit");
            for (Field field : unitClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == unitClass) {
                    return field.get(null);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to resolve kotlin.Unit instance", t);
        }
        return null;
    }
}

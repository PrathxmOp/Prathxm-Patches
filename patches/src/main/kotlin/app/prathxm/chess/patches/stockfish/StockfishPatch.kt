/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.stockfish

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

private const val EXTENSION_CLASS = "Lapp/prathxm/chess/extension/stockfish/StockfishExtension;"

private val stockfishResourcePatch = resourcePatch {
    execute {
        val arm64Dest = this@execute["lib/arm64-v8a/libstockfish.so"]
        arm64Dest.parentFile.mkdirs()
        object {}.javaClass.classLoader?.getResourceAsStream("stockfish/arm64-v8a/stockfish")?.use { input ->
            arm64Dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not find bundled arm64-v8a stockfish binary")

        val armv7Dest = this@execute["lib/armeabi-v7a/libstockfish.so"]
        armv7Dest.parentFile.mkdirs()
        object {}.javaClass.classLoader?.getResourceAsStream("stockfish/armeabi-v7a/stockfish")?.use { input ->
            armv7Dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not find bundled armeabi-v7a stockfish binary")
    }
}

val stockfishPatch = bytecodePatch(
    name = "Local Stockfish Analysis",
    description = "Enables local Stockfish engine for post-game review & analysis.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    dependsOn(stockfishResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        // ─────────────────────────────────────────────────────────────────
        // Hook 1 – After applyMove, trigger engine analysis on the new FEN
        // ─────────────────────────────────────────────────────────────────
        PositionSetterFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $EXTENSION_CLASS->onBoardChanged(Ljava/lang/Object;Ljava/lang/Object;)V
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 2 – Expose the setMoveArrows method to our extension
        // ─────────────────────────────────────────────────────────────────
        SetMoveArrowsFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $EXTENSION_CLASS->onArrowsChanged(Ljava/lang/Object;Ljava/util/List;)V
            """
        )

        // Hook 4 – Inject KEY_MOVE_HINTS into optional painters
        OptionalPaintersCompanionBFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p4
                invoke-static {v0}, $EXTENSION_CLASS->ensureHintArrowsEnabled([Ljava/lang/Object;)[Ljava/lang/Object;
                move-result-object v0
                check-cast v0, [Lcom/chess/internal/utils/chessboard/ChessBoardViewOptionalPainterType;
                move-object/from16 p4, v0
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 5 – Early initialization at Application startup
        // ─────────────────────────────────────────────────────────────────
        MainApplicationOnCreateFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->ensureEngineReady()V
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 6 – GameAnalysisPermissions Overrides
        // ─────────────────────────────────────────────────────────────────
        GameAnalysisPermissionsGetCanCreateFingerprint.method.addInstructions(
            0,
            """
                iget-boolean v0, p0, Lcom/chess/entities/GameAnalysisPermissions;->canCreate:Z
                const-string v1, "canCreate"
                invoke-static {v0, v1}, $EXTENSION_CLASS->getAnalysisPermission(ZLjava/lang/String;)Z
                move-result v0
                return v0
            """
        )

        GameAnalysisPermissionsGetCanMoveFeedbackFingerprint.method.addInstructions(
            0,
            """
                iget-boolean v0, p0, Lcom/chess/entities/GameAnalysisPermissions;->canMoveFeedback:Z
                const-string v1, "canMoveFeedback"
                invoke-static {v0, v1}, $EXTENSION_CLASS->getAnalysisPermission(ZLjava/lang/String;)Z
                move-result v0
                return v0
            """
        )

        GameAnalysisPermissionsGetCanMoveStrengthFingerprint.method.addInstructions(
            0,
            """
                iget-boolean v0, p0, Lcom/chess/entities/GameAnalysisPermissions;->canMoveStrength:Z
                const-string v1, "canMoveStrength"
                invoke-static {v0, v1}, $EXTENSION_CLASS->getAnalysisPermission(ZLjava/lang/String;)Z
                move-result v0
                return v0
            """
        )

        GameAnalysisPermissionsGetCanViewAccuracyAndMovesFingerprint.method.addInstructions(
            0,
            """
                iget-boolean v0, p0, Lcom/chess/entities/GameAnalysisPermissions;->canCreate:Z
                const-string v1, "canViewAccuracyAndMoves"
                invoke-static {v0, v1}, $EXTENSION_CLASS->getAnalysisPermission(ZLjava/lang/String;)Z
                move-result v0
                return v0
            """
        )

        GameAnalysisPermissionsGetCanViewCoachCommentaryFingerprint.method.addInstructions(
            0,
            """
                iget-boolean v0, p0, Lcom/chess/entities/GameAnalysisPermissions;->canViewCoachCommentary:Z
                const-string v1, "canViewCoachCommentary"
                invoke-static {v0, v1}, $EXTENSION_CLASS->getAnalysisPermission(ZLjava/lang/String;)Z
                move-result v0
                return v0
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 6b – Replace the remote Game Review with local Stockfish analysis.
        //
        // The Flow interface type is passed to the extension (const-class of the method's
        // return type) so the extension never needs to know obfuscated coroutine class names.
        // ─────────────────────────────────────────────────────────────────
        // a(ComputerAnalysisConfiguration config, UserSide, Coach, Set, AnalysisDepth, AnalysisEngine, boolean skillsEnabled)
        val repoMethod = GameAnalysisRepositoryGetGameAnalysisFingerprint.method
        val repoReturnType = repoMethod.returnType
        val pgnReg = if (repoMethod.parameterTypes[0] == "Lcom/chess/entities/ComputerAnalysisConfiguration;") "p1" else "p2"
        val depthIdx = repoMethod.parameterTypes.indexOf("Lcom/chess/entities/AnalysisDepth;")
        val depthReg = if (depthIdx >= 0) "p" + (depthIdx + 1) else "p5"
        repoMethod.addInstructions(
            0,
            """
                const-class v0, $repoReturnType
                move-object/from16 v1, $pgnReg
                move-object/from16 v2, $depthReg
                invoke-static {v0, v1, v2}, $EXTENSION_CLASS->getLocalAnalysisFlowForConfig(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, $repoReturnType
                return-object v0
            """
        )

        // The engine-line preview is rebuilt by the app from the eval PV; returning null makes
        // the review fall back to the (always valid) played/best move, which avoids crashes on
        // lines the app's converter cannot replay.
        GameReviewV2V0DFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return-object v0
            """
        )

        // If the app is about to build a review item from data it cannot replay (no played
        // move / unconvertible suggestion), return a neutral "book" item instead of crashing.
        // The replacement object is built by the extension via reflection so it adapts to the
        // MoveInfo constructor of each Chess.com version.
        GameReviewV2V0JFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $EXTENSION_CLASS->shouldUseDummyMove(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :proceed
                const-class v0, Lcom/chess/gamereview/api/d;
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $EXTENSION_CLASS->buildDummyMoveResult(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                if-eqz v0, :proceed
                check-cast v0, Lcom/chess/gamereview/api/d;
                return-object v0
                :proceed
                nop
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 7 – Force Connectivity Status to Online for local analysis in flight mode
        // ─────────────────────────────────────────────────────────────────
        ConnectivityUtilImplIsOfflineFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return v0
            """
        )

        ConnectivityUtilImplIsOnlineFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 1
                return v0
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 8 – Force GameAnalysisPermissions to unlock analysis/review locally
        // ─────────────────────────────────────────────────────────────────
        GameAnalysisServiceImplGetPermissionsFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->getFullGameAnalysisPermissions()Ljava/lang/Object;
                move-result-object v0
                return-object v0
            """
        )
    }
}


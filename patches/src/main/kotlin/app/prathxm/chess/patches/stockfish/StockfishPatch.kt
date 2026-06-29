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
    name = "Stockfish Engine Integration",
    description = "Injects the local Stockfish chess engine into Chess.com for real-time analysis, best-move arrows, and customizable settings.",
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

        // ─────────────────────────────────────────────────────────────────
        // Hook 3 – Intercept Ad-Removal Getters
        // ─────────────────────────────────────────────────────────────────

        // LoginData.getShow_ads() → boolean (primitive)
        LoginDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                iget-boolean v0, p0, Lcom/chess/net/model/LoginData;->show_ads:Z
                invoke-static {v0}, $EXTENSION_CLASS->shouldShowAds(Z)Z
                move-result v0
                return v0
            """
        )

        // LoginData.getShow_interstitial_ads() → Boolean (boxed)
        LoginDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/chess/net/model/LoginData;->show_interstitial_ads:Ljava/lang/Boolean;
                invoke-static {v0}, $EXTENSION_CLASS->shouldShowAdsObject(Ljava/lang/Boolean;)Ljava/lang/Boolean;
                move-result-object v0
                return-object v0
            """
        )

        // UserData.getShow_ads() → Boolean (boxed)
        UserDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/chess/net/model/UserData;->show_ads:Ljava/lang/Boolean;
                invoke-static {v0}, $EXTENSION_CLASS->shouldShowAdsObject(Ljava/lang/Boolean;)Ljava/lang/Boolean;
                move-result-object v0
                return-object v0
            """
        )

        // UserData.getShow_interstitial_ads() → Boolean (boxed)
        UserDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/chess/net/model/UserData;->show_interstitial_ads:Ljava/lang/Boolean;
                invoke-static {v0}, $EXTENSION_CLASS->shouldShowAdsObject(Ljava/lang/Boolean;)Ljava/lang/Boolean;
                move-result-object v0
                return-object v0
            """
        )

        // LoginData.getPremium_status() → int
        LoginDataGetPremiumStatusFingerprint.method.addInstructions(
            0,
            """
                iget v0, p0, Lcom/chess/net/model/LoginData;->premium_status:I
                invoke-static {v0}, $EXTENSION_CLASS->getPremiumStatus(I)I
                move-result v0
                return v0
            """
        )

        // UserData.getPremium_status() → PremiumStatus
        UserDataGetPremiumStatusFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/chess/net/model/UserData;->premium_status:Lcom/chess/entities/PremiumStatus;
                invoke-static {v0}, $EXTENSION_CLASS->getPremiumStatusObject(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lcom/chess/entities/PremiumStatus;
                return-object v0
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

        GameAnalysisRepositoryGetGameAnalysisFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                move-object/from16 v2, p2
                move-object/from16 v3, p3
                move-object/from16 v4, p4
                move-object/from16 v5, p5
                move-object/from16 v6, p6
                move-object/from16 v7, p7
                invoke-static/range {v0 .. v7}, $EXTENSION_CLASS->getLocalAnalysisFlow(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Set;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Landroid/view/inputmethod/g74;
                return-object v0
            """
        )
    }
}
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
    name = "Ad-Free & Local Analysis",
    description = "Removes advertisements, unlocks ad-free features, and enables local Stockfish engine for post-game review & analysis.",
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
        // Hook 3 – Intercept Ad-Removal Getters (Force Values to prevent UI crashes)
        // ─────────────────────────────────────────────────────────────────

        // LoginData.getShow_ads() → boolean (primitive)
        LoginDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return v0
            """
        )

        // LoginData.getShow_interstitial_ads() → Boolean (boxed)
        LoginDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // UserData.getShow_ads() → Boolean (boxed)
        UserDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // UserData.getShow_interstitial_ads() → Boolean (boxed)
        UserDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // LoginData.getPremium_status() → int (DIAMOND = 3 if logged in, else 0)
        LoginDataGetPremiumStatusFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lcom/chess/net/model/LoginData;->getUsername()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :guest
                invoke-virtual {v0}, Ljava/lang/String;->length()I
                move-result v0
                if-eqz v0, :guest
                const/4 v0, 3
                return v0
                :guest
                const/4 v0, 0
                return v0
            """
        )

        // SharedPreferencesSessionStore.i() → boolean (isGuest = false when user has a session)
        // Fixes: Sign Up button showing in settings and missing chat button in games
        SessionStoreIsGuestFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lcom/chess/apputils/SharedPreferencesSessionStore;->getSession()Lcom/chess/net/model/LoginData;
                move-result-object v0
                if-eqz v0, :no_session
                invoke-virtual {v0}, Lcom/chess/net/model/LoginData;->getUsername()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :no_session
                invoke-virtual {v0}, Ljava/lang/String;->length()I
                move-result v0
                if-eqz v0, :no_session
                const/4 v0, 0
                return v0
                :no_session
                const/4 v0, 1
                return v0
            """
        )

        // UserData.getPremium_status() → PremiumStatus
        UserDataGetPremiumStatusFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->getDiamondStatus()Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lcom/chess/entities/PremiumStatus;
                if-nez v0, :original
                return-object v0
                :original
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
                check-cast v0, Lcom/google/android/g74;
                return-object v0
            """
        )

        GameReviewV2V0DFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return-object v0
            """
        )

        /*
        GameReviewV2V0JFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual/range {p0 .. p0}, Lcom/chess/gamereview/repository/AnalyzedGameData${'$'}AnalyzedPosition;->d()Lcom/chess/gamereview/repository/AnalyzedGameData${'$'}AnalyzedPosition${'$'}PlayedMove;
                move-result-object v0
                if-nez v0, :proceed

                invoke-virtual/range {p1 .. p1}, Lcom/chess/chessboard/history/i;->e()Lcom/chess/chessboard/variants/d;
                move-result-object v0
                invoke-interface {v0}, Lcom/chess/chessboard/variants/b;->getSideToMove()Lcom/chess/entities/Color;
                move-result-object v0

                sget-object v1, Lcom/chess/entities/Score;->Companion:Lcom/chess/entities/Score${'$'}Companion;
                const/4 v2, 0
                const/4 v3, 0
                invoke-virtual {v1, v2, v3, v0}, Lcom/chess/entities/Score${'$'}Companion;->from(FLjava/lang/Integer;Lcom/chess/entities/Color;)Lcom/chess/entities/Score;
                move-result-object v5

                sget-object v6, Lcom/chess/compengine/AnalysisMoveClassification;->g:Lcom/chess/compengine/AnalysisMoveClassification;

                new-instance v7, Lcom/chess/gamereview/api/k;
                move-object/from16 v8, p1
                move-object v9, v6
                const/4 v10, 0
                const/4 v11, 0
                move-object v12, v5
                const/4 v13, 0
                const/4 v14, 0
                invoke-direct/range {v7 .. v14}, Lcom/chess/gamereview/api/k;-><init>(Lcom/chess/chessboard/history/i;Lcom/chess/compengine/AnalysisMoveClassification;Lcom/chess/gamereview/api/n;Lcom/chess/chessboard/history/i;Lcom/chess/entities/Score;Lcom/chess/gamereview/api/k${'$'}a;Lcom/chess/coach/AnimatedCoachExpression;)V

                new-instance v0, Lcom/chess/gamereview/api/d;
                const/4 v1, 0
                invoke-direct {v0, v7, v1}, Lcom/chess/gamereview/api/d;-><init>(Lcom/chess/gamereview/api/k;Lcom/chess/gamereview/api/k;)V
                return-object v0

                :proceed
            """
        )
        */

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


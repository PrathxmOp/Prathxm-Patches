package app.prathxm.chess.patches.misc

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS
import app.prathxm.chess.patches.stockfish.MainApplicationOnCreateFingerprint

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprints for Custom Titles Getter Methods
// ─────────────────────────────────────────────────────────────────────────────

object UserDataGetChessTitleFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getChess_title",
    parameters = listOf(),
    returnType = "Ljava/lang/String;"
)

object LoginDataGetChessTitleFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getChess_title",
    parameters = listOf(),
    returnType = "Ljava/lang/String;"
)

object LeaderBoardUserGetChessTitleFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LeaderBoardUser;",
    name = "getChess_title",
    parameters = listOf(),
    returnType = "Ljava/lang/String;"
)

object CommentDataGetChessTitleFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/CommentData;",
    name = "getChess_title",
    parameters = listOf(),
    returnType = "Ljava/lang/String;"
)

object LiveUserInfoGetChessTitleFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/LiveUserInfo;",
    name = "getChessTitle",
    parameters = listOf(),
    returnType = "Ljava/lang/String;"
)

object DailyUserInfoGetChessTitleFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/DailyUserInfo;",
    name = "getChessTitle",
    parameters = listOf(),
    returnType = "Ljava/lang/String;"
)

object UsernameUiStateGetChessTitleFingerprint : Fingerprint(
    custom = { method, classDef ->
        method.parameterTypes.isEmpty() &&
            method.returnType.startsWith("L") &&
            !method.returnType.startsWith("[") &&
            method.returnType.contains("palette/") && method.returnType.contains("ChessTitle") &&
            classDef.type.contains("palette/") &&
            classDef.type != method.returnType &&
            classDef.methods.any { it.name == "<init>" && it.parameterTypes.size < 10 }
    }
)

object UserProfileHeaderGetChessTitleFingerprint : Fingerprint(
    custom = { method, classDef ->
        method.parameterTypes.isEmpty() &&
            method.returnType.startsWith("L") &&
            !method.returnType.startsWith("[") &&
            method.returnType.contains("palette/") && method.returnType.contains("ChessTitle") &&
            classDef.type.contains("palette/") &&
            classDef.type != method.returnType &&
            classDef.methods.any { it.name == "<init>" && it.parameterTypes.size >= 10 }
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Custom Titles Patch
// ─────────────────────────────────────────────────────────────────────────────

val customTitlesPatch = bytecodePatch(
    name = "Custom Titles",
    description = "Fetches and applies custom titles for users from a remote database. DM PrathxmOp to get yours for fun lol!",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    extendWith("extensions/extension.mpe")

    execute {
        // Initialize the custom titles downloader on startup
        MainApplicationOnCreateFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->init()V
            """
        )

        // Inject overrides into UserData getChess_title()
        UserDataGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overrideModelChessTitle(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """
        )

        // Inject overrides into LoginData getChess_title()
        LoginDataGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overrideModelChessTitle(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """
        )

        // Inject overrides into LeaderBoardUser getChess_title()
        LeaderBoardUserGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overrideModelChessTitle(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """
        )

        // Inject overrides into CommentData getChess_title()
        CommentDataGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overrideModelChessTitle(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """
        )

        // Inject overrides into LiveUserInfo getChessTitle()
        LiveUserInfoGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overrideModelChessTitle(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """
        )

        // Inject overrides into DailyUserInfo getChessTitle()
        DailyUserInfoGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overrideModelChessTitle(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                return-object v0
            """
        )

        // Inject overrides into UsernameUiState getChessTitle()
        UsernameUiStateGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overridePaletteChessTitle(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lcom/chess/palette/compose/component/ChessTitle;
                return-object v0
            """
        )

        // Inject overrides into UserProfileHeader getChessTitle()
        UserProfileHeaderGetChessTitleFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/titles/CustomTitlesExtension;->overridePaletteChessTitle(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lcom/chess/palette/compose/component/ChessTitle;
                return-object v0
            """
        )
    }
}

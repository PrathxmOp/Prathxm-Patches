package app.prathxm.chess.patches.stockfish

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 1 – CBViewModelStateImpl.m() (position setter)
//
// We match on the setter method in CBViewModelStateImpl that accepts the
// generic POSITION parameter (erased to Lcom/chess/chessboard/variants/d;)
// and returns void.
// ─────────────────────────────────────────────────────────────────────────────
object PositionSetterFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Lcom/chess/chessboard/variants/d;" &&
            method.returnType == "V" &&
            method.name != "<init>"
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 2 – CBViewModelStateImpl.a2()  (setMoveArrows)
//
// a2(List<HintArrow>) is the setter for the moveArrows observable field.
// We need its reference to call it from our extension to inject engine arrows.
// It is tiny (just delegates to yf9.b()) and unique within the class.
// ─────────────────────────────────────────────────────────────────────────────
object SetMoveArrowsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;",
    name = "a2",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        // The field backing store tag used for moveArrows
        methodCall(
            definingClass = "L", // obfuscated yf9 class – just match "L"
            name = "b"           // the delegate setter method
        )
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 3 – CBViewModelStateImpl.getPosition()
//
// Used so our extension can read the current board position / state.
// ─────────────────────────────────────────────────────────────────────────────
object GetPositionFingerprint : Fingerprint(
    definingClass = "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;",
    name = "getPosition",
    returnType = "L",  // returns com.chess.chessboard.variants.Position (obfuscated)
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            name = "a" // delegate getter on yf9
        )
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Ad-Removal Fingerprints
// ─────────────────────────────────────────────────────────────────────────────

object LoginDataGetShowAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getShow_ads",
    parameters = listOf(),
    returnType = "Z"
)

object LoginDataGetShowInterstitialAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getShow_interstitial_ads",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object UserDataGetShowAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getShow_ads",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object UserDataGetShowInterstitialAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getShow_interstitial_ads",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object LoginDataGetPremiumStatusFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getPremium_status",
    parameters = listOf(),
    returnType = "I"
)

object SessionStoreIsGuestFingerprint : Fingerprint(
    definingClass = "Lcom/chess/apputils/SharedPreferencesSessionStore;",
    name = "i",
    parameters = listOf(),
    returnType = "Z"
)

object UserDataGetPremiumStatusFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getPremium_status",
    parameters = listOf(),
    returnType = "Lcom/chess/entities/PremiumStatus;"
)

object OptionalPaintersCompanionBFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type.contains("ChessBoardViewOptionalPainterType") &&
            method.name == "b" &&
            method.parameterTypes.size == 7 &&
            method.parameterTypes[3] == "[Lcom/chess/internal/utils/chessboard/ChessBoardViewOptionalPainterType;"
    }
)

object MainApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/chess/MainApplication;",
    name = "onCreate",
    parameters = listOf(),
    returnType = "V"
)

object GameAnalysisPermissionsGetCanCreateFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/GameAnalysisPermissions;",
    name = "getCanCreate",
    parameters = listOf(),
    returnType = "Z"
)

object GameAnalysisPermissionsGetCanMoveFeedbackFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/GameAnalysisPermissions;",
    name = "getCanMoveFeedback",
    parameters = listOf(),
    returnType = "Z"
)

object GameAnalysisPermissionsGetCanMoveStrengthFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/GameAnalysisPermissions;",
    name = "getCanMoveStrength",
    parameters = listOf(),
    returnType = "Z"
)

object GameAnalysisPermissionsGetCanViewAccuracyAndMovesFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/GameAnalysisPermissions;",
    name = "getCanViewAccuracyAndMoves",
    parameters = listOf(),
    returnType = "Z"
)

object GameAnalysisPermissionsGetCanViewCoachCommentaryFingerprint : Fingerprint(
    definingClass = "Lcom/chess/entities/GameAnalysisPermissions;",
    name = "getCanViewCoachCommentary",
    parameters = listOf(),
    returnType = "Z"
)

object GameAnalysisRepositoryGetGameAnalysisFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/gamereview/repository/GameAnalysisRepositoryImpl;" &&
            method.name == "b" &&
            method.parameterTypes.size == 7 &&
            method.parameterTypes[1] == "Ljava/lang/String;" &&
            method.returnType == "Lcom/google/android/g74;"
    }
)

object GameReviewV2V0DFingerprint : Fingerprint(
    definingClass = "Lcom/chess/gamereview/v2/v0;",
    name = "D",
    parameters = listOf("Lcom/chess/chessboard/variants/d;", "Lcom/chess/gamereview/repository/AnalyzedGameData\$AnalyzedPosition\$Eval;"),
    returnType = "Lcom/chess/gamereview/api/n;"
)

object GameReviewV2V0JFingerprint : Fingerprint(
    definingClass = "Lcom/chess/gamereview/v2/v0;",
    name = "J",
    parameters = listOf("Lcom/chess/gamereview/repository/AnalyzedGameData\$AnalyzedPosition;", "Lcom/chess/chessboard/history/i;", "Lcom/chess/entities/GameAnalysisPermissions;", "Z"),
    returnType = "Lcom/chess/gamereview/api/d;"
)

object ConnectivityUtilImplIsOfflineFingerprint : Fingerprint(
    definingClass = "Lcom/chess/utils/android/misc/ConnectivityUtilImpl;",
    name = "b",
    parameters = listOf(),
    returnType = "Z"
)

object ConnectivityUtilImplIsOnlineFingerprint : Fingerprint(
    definingClass = "Lcom/chess/utils/android/misc/ConnectivityUtilImpl;",
    name = "c",
    parameters = listOf(),
    returnType = "Z"
)

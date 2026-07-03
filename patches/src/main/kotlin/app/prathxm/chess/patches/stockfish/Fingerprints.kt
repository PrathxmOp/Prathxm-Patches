package app.prathxm.chess.patches.stockfish

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

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
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        // The field backing store tag used for moveArrows
        methodCall(
            definingClass = "L", // obfuscated yf9 class – just match "L"
            name = "b",          // the delegate setter method
        )
    ),
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;" &&
            method.name == "a2"
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 3 – CBViewModelStateImpl.getPosition()
//
// Used so our extension can read the current board position / state.
// ─────────────────────────────────────────────────────────────────────────────
object GetPositionFingerprint : Fingerprint(
    returnType = "L",  // returns com.chess.chessboard.variants.Position (obfuscated)
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            name = "a", // delegate getter on yf9
        )
    ),
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;" &&
            method.name == "getPosition"
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Ad-Removal Fingerprints
// ─────────────────────────────────────────────────────────────────────────────

object LoginDataGetShowAdsFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/model/LoginData;" &&
            method.name == "getShow_ads" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object LoginDataGetShowInterstitialAdsFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/model/LoginData;" &&
            method.name == "getShow_interstitial_ads" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Ljava/lang/Boolean;"
    }
)

object UserDataGetShowAdsFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/model/UserData;" &&
            method.name == "getShow_ads" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Ljava/lang/Boolean;"
    }
)

object UserDataGetShowInterstitialAdsFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/model/UserData;" &&
            method.name == "getShow_interstitial_ads" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Ljava/lang/Boolean;"
    }
)

object LoginDataGetPremiumStatusFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/model/LoginData;" &&
            method.name == "getPremium_status" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "I"
    }
)

object UserDataGetPremiumStatusFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/model/UserData;" &&
            method.name == "getPremium_status" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Lcom/chess/entities/PremiumStatus;"
    }
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
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/MainApplication;" &&
            method.name == "onCreate" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    }
)

object GameAnalysisPermissionsGetCanCreateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.name == "getCanCreate" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object GameAnalysisPermissionsGetCanMoveFeedbackFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.name == "getCanMoveFeedback" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object GameAnalysisPermissionsGetCanMoveStrengthFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.name == "getCanMoveStrength" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object GameAnalysisPermissionsGetCanViewAccuracyAndMovesFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.name == "getCanViewAccuracyAndMoves" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object GameAnalysisPermissionsGetCanViewCoachCommentaryFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.name == "getCanViewCoachCommentary" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
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
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/gamereview/v2/v0;" &&
            method.name == "D" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Lcom/chess/chessboard/variants/d;" &&
            method.parameterTypes[1] == "Lcom/chess/gamereview/repository/AnalyzedGameData\$AnalyzedPosition\$Eval;" &&
            method.returnType == "Lcom/chess/gamereview/api/n;"
    }
)

object GameReviewV2V0JFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/gamereview/v2/v0;" &&
            method.name == "J" &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[0] == "Lcom/chess/gamereview/repository/AnalyzedGameData\$AnalyzedPosition;" &&
            method.parameterTypes[1] == "Lcom/chess/chessboard/history/i;" &&
            method.parameterTypes[2] == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.parameterTypes[3] == "Z" &&
            method.returnType == "Lcom/chess/gamereview/api/d;"
    }
)




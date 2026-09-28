/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

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
        if (classDef.type == "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;") {
            val positionType = classDef.methods.find { it.name == "getPosition" }?.returnType
            positionType != null &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == positionType &&
                method.returnType == "V" &&
                method.name != "<init>"
        } else {
            false
        }
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 2 – CBViewModelStateImpl setMoveArrows(List<HintArrow>)
//
// Matched by body rather than by obfuscated name (G2 in 4.10.17): the (List)V setter that
// writes delegated property slot 0xb. moveArrows is the 12th (index 0xb) delegated property
// of CBViewModelStateImpl; the only other slot-0xb accessor is the getter, which has a
// different signature.
// ─────────────────────────────────────────────────────────────────────────────
object SetMoveArrowsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    custom = { method, _ ->
        val insns = method.implementation?.instructions?.toList() ?: emptyList()
        // Body: iget-object q; sget-object t; const/16 v2, 0xb; aget-object; invoke-interface setValue; return-void
        insns.any { it.opcode.name == "const/16" && (it as? com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)?.narrowLiteral == 0xb } &&
            insns.any { i ->
                val ref = (i as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference
                ref is com.android.tools.smali.dexlib2.iface.reference.MethodReference && ref.name == "setValue"
            }
    }
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
    parameters = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
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
    // a(ComputerAnalysisConfiguration / CompatGameIdAndType, PGN, UserSide, Coach, Set, AnalysisDepth, AnalysisEngine)
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/gamereview/repository/GameAnalysisRepositoryImpl;" &&
            method.parameterTypes.size == 7 &&
            method.parameterTypes.contains("Lcom/chess/entities/AnalysisDepth;") &&
            method.parameterTypes.contains("Lcom/chess/entities/AnalysisEngine;")
    }
)

object GameReviewV2V0DFingerprint : Fingerprint(
    // Builds the engine-line preview for a review position.
    // v2.f1.M(variants.d, compengine.entities.AnalyzedGameData$AnalyzedPosition$Eval) -> api.o
    custom = { method, classDef ->
        classDef.type.startsWith("Lcom/chess/gamereview/v2/") &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Lcom/chess/chessboard/variants/d;" &&
            method.parameterTypes[1].endsWith("/AnalyzedGameData\$AnalyzedPosition\$Eval;") &&
            method.returnType.startsWith("Lcom/chess/gamereview/api/")
    }
)

object GameReviewV2V0JFingerprint : Fingerprint(
    // Builds the per-move review item (played move + suggestion).
    // v2.f1.T(AnalyzedPosition, history.i, GameAnalysisPermissions, Z) -> api.d
    custom = { method, classDef ->
        classDef.type.startsWith("Lcom/chess/gamereview/v2/") &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[0].endsWith("/AnalyzedGameData\$AnalyzedPosition;") &&
            method.parameterTypes[1] == "Lcom/chess/chessboard/history/i;" &&
            method.parameterTypes[2] == "Lcom/chess/entities/GameAnalysisPermissions;" &&
            method.parameterTypes[3] == "Z" &&
            method.returnType == "Lcom/chess/gamereview/api/d;"
    }
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

object GameAnalysisServiceImplGetPermissionsFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/v1/analysis/GameAnalysisServiceImpl;" &&
            method.name == "a" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Lcom/chess/entities/CompatGameIdAndType;" &&
            method.returnType == "Ljava/lang/Object;"
    }
)


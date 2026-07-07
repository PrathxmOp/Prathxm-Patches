/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.lichesspuzzle

import app.morphe.patcher.Fingerprint

object NewDailyPuzzleGetFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/puzzles/daily/net/NewDailyPuzzleServiceImpl;" &&
            method.name == "a" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Ljava/lang/String;" &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object NewDailyPuzzleSubmitFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/puzzles/daily/net/NewDailyPuzzleServiceImpl;" &&
            method.name == "b" &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[0] == "I" &&
            method.parameterTypes[1] == "Lchesscom/puzzles/v2alpha/DailyPuzzleAction;" &&
            method.parameterTypes[2] == "Lchesscom/puzzles/v2alpha/DailyPuzzleHintState;" &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object PuzzlePaywallGateModeFingerprint : Fingerprint(
    definingClass = "Lcom/chess/home/play/PuzzlePaywallGate;",
    name = "b",
    parameters = listOf("Lcom/chess/navigationinterface/PathPuzzlesMode;"),
    returnType = "Z"
)

object PuzzlePaywallGateErrorFingerprint : Fingerprint(
    definingClass = "Lcom/chess/home/play/PuzzlePaywallGate;",
    name = "c",
    parameters = listOf("Ljava/lang/Throwable;"),
    returnType = "Z"
)

object PuzzlePaywallGateCheckFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/home/play/PuzzlePaywallGate;" &&
            method.name == "d" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Lcom/chess/navigationinterface/PathPuzzlesMode;" &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object PuzzleOfflineLimitSetFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/internal/puzzles/PuzzleOfflineLimit;" &&
            method.name == "f" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "I" &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object SessionStorePremiumFingerprint1 : Fingerprint(
    definingClass = "Lcom/chess/net/v1/users/SessionStore;",
    name = "i",
    returnType = "Z"
)

object SessionStorePremiumFingerprint2 : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/v1/users/SessionStore;" &&
            (method.name == "t" || method.name == "u") &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)
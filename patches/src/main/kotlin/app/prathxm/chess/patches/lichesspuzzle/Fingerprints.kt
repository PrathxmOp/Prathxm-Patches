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
            method.parameterTypes[1] == "Lcom/google/android/o02;" &&
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
            method.parameterTypes[3] == "Lcom/google/android/o02;" &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object PuzzlePaywallGateModeFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/home/play/PuzzlePaywallGate;" &&
            method.name == "b" &&
            method.parameterTypes == listOf("Lcom/chess/navigationinterface/PathPuzzlesMode;") &&
            method.returnType == "Z"
    }
)

object PuzzlePaywallGateErrorFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/home/play/PuzzlePaywallGate;" &&
            method.name == "c" &&
            method.parameterTypes == listOf("Ljava/lang/Throwable;") &&
            method.returnType == "Z"
    }
)

object PuzzlePaywallGateCheckFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/home/play/PuzzlePaywallGate;" &&
            method.name == "d" &&
            method.parameterTypes == listOf("Lcom/chess/navigationinterface/PathPuzzlesMode;", "Lcom/google/android/o02;") &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object PuzzleOfflineLimitSetFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/internal/puzzles/PuzzleOfflineLimit;" &&
            method.name == "f" &&
            method.parameterTypes == listOf("I", "Lcom/google/android/o02;") &&
            method.returnType == "Ljava/lang/Object;"
    }
)

object SessionStorePremiumFingerprint1 : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/v1/users/SessionStore;" &&
            method.name == "i" &&
            method.returnType == "Z"
    }
)

object SessionStorePremiumFingerprint2 : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/net/v1/users/SessionStore;" &&
            method.name == "t" &&
            method.returnType == "Z"
    }
)
package app.prathxm.chess.patches.lichesspuzzle

import app.morphe.patcher.Fingerprint

object NewDailyPuzzleGetFingerprint : Fingerprint(
    definingClass = "Lcom/chess/features/puzzles/daily/net/NewDailyPuzzleServiceImpl;",
    name = "a",
    parameters = listOf("Ljava/lang/String;", "Lcom/google/android/o02;"),
    returnType = "Ljava/lang/Object;"
)

object NewDailyPuzzleSubmitFingerprint : Fingerprint(
    definingClass = "Lcom/chess/features/puzzles/daily/net/NewDailyPuzzleServiceImpl;",
    name = "b",
    parameters = listOf("I", "Lchesscom/puzzles/v2alpha/DailyPuzzleAction;", "Lchesscom/puzzles/v2alpha/DailyPuzzleHintState;", "Lcom/google/android/o02;"),
    returnType = "Ljava/lang/Object;"
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
    definingClass = "Lcom/chess/home/play/PuzzlePaywallGate;",
    name = "d",
    parameters = listOf("Lcom/chess/navigationinterface/PathPuzzlesMode;", "Lcom/google/android/o02;"),
    returnType = "Ljava/lang/Object;"
)

object PuzzleOfflineLimitSetFingerprint : Fingerprint(
    definingClass = "Lcom/chess/internal/puzzles/PuzzleOfflineLimit;",
    name = "f",
    parameters = listOf("I", "Lcom/google/android/o02;"),
    returnType = "Ljava/lang/Object;"
)

object SessionStorePremiumFingerprint1 : Fingerprint(
    definingClass = "Lcom/chess/net/v1/users/SessionStore;",
    name = "i",
    returnType = "Z"
)

object SessionStorePremiumFingerprint2 : Fingerprint(
    definingClass = "Lcom/chess/net/v1/users/SessionStore;",
    name = "t",
    returnType = "Z"
)
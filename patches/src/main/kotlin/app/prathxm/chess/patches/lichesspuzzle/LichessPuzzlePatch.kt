/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.lichesspuzzle

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

private const val EXTENSION_CLASS = "Lapp/prathxm/chess/extension/lichesspuzzle/LichessPuzzleExtension;"

private val lichessPuzzleResourcePatch = resourcePatch {
    compatibleWith(COMPATIBILITY_CHESS)
    finalize {
        document("AndroidManifest.xml").use { document ->
            val applicationList = document.getElementsByTagName("application")
            if (applicationList.length > 0) {
                val application = applicationList.item(0) as Element
                
                var standaloneExists = false
                var journeyExists = false
                val activities = document.getElementsByTagName("activity")
                for (i in 0 until activities.length) {
                    val act = activities.item(i) as Element
                    val name = act.getAttribute("android:name")
                    if (name == "app.prathxm.chess.extension.lichesspuzzle.StandaloneLichessActivity") {
                        standaloneExists = true
                    }
                    if (name == "app.prathxm.chess.extension.lichesspuzzle.LichessPuzzleJourneyActivity") {
                        journeyExists = true
                    }
                }
                
                if (!standaloneExists) {
                    val activity = document.createElement("activity")
                    activity.setAttribute("android:name", "app.prathxm.chess.extension.lichesspuzzle.StandaloneLichessActivity")
                    activity.setAttribute("android:theme", "@android:style/Theme.NoTitleBar")
                    activity.setAttribute("android:exported", "true")
                    application.appendChild(activity)
                }

                if (!journeyExists) {
                    val activity = document.createElement("activity")
                    activity.setAttribute("android:name", "app.prathxm.chess.extension.lichesspuzzle.LichessPuzzleJourneyActivity")
                    activity.setAttribute("android:theme", "@android:style/Theme.NoTitleBar")
                    activity.setAttribute("android:exported", "true")
                    application.appendChild(activity)
                }
            }
        }
    }
}

val lichessPuzzlePatch = bytecodePatch(
    name = "Lichess Puzzles",
    description = "Loads daily puzzles from Lichess and bypasses Chess.com puzzle daily limits.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    dependsOn(lichessPuzzleResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        // Intercept Daily Puzzle entry point to launch our custom Lichess activity,
        // then silently cancel the original Chess.com coroutine using a CancellationException.
        PuzzlePaywallGateCheckFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/prathxm/chess/extension/lichesspuzzle/LichessPuzzleExtension;->launchLichessActivity()V
                new-instance v0, Ljava/util/concurrent/CancellationException;
                const-string v1, "Lichess puzzle launched"
                invoke-direct {v0, v1}, Ljava/util/concurrent/CancellationException;-><init>(Ljava/lang/String;)V
                throw v0
            """
        )

        // Replace Chess.com daily puzzle data with Lichess data.
        NewDailyPuzzleGetFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION_CLASS->getDailyPuzzle(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                return-object v0
            """
        )

        NewDailyPuzzleSubmitFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2, p3, p4}, $EXTENSION_CLASS->submitDailyPuzzleAction(ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                return-object v0
            """
        )

        // Bypass PuzzlePaywallGate mode check
        PuzzlePaywallGateModeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )

        // Bypass PuzzlePaywallGate error check
        PuzzlePaywallGateErrorFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )

        // Bypass SessionStore premium checks (i() and t() methods)
        SessionStorePremiumFingerprint1.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )

        SessionStorePremiumFingerprint2.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )

        // Bypass PuzzleOfflineLimit
        PuzzleOfflineLimitSetFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
                return-object v0
            """
        )
    }
}
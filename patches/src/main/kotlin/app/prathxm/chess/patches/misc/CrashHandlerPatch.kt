/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.misc

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS
import app.prathxm.chess.patches.stockfish.MainApplicationOnCreateFingerprint

private val crashHandlerResourcePatch = resourcePatch {
    compatibleWith(COMPATIBILITY_CHESS)
    finalize {
        document("AndroidManifest.xml").use { document ->
            val applicationList = document.getElementsByTagName("application")
            if (applicationList.length > 0) {
                val application = applicationList.item(0) as Element
                
                var crashActivityExists = false
                val activities = document.getElementsByTagName("activity")
                for (i in 0 until activities.length) {
                    val act = activities.item(i) as Element
                    val name = act.getAttribute("android:name")
                    if (name == "app.prathxm.chess.extension.crash.CrashActivity") {
                        crashActivityExists = true
                        break
                    }
                }
                
                if (!crashActivityExists) {
                    val activity = document.createElement("activity")
                    activity.setAttribute("android:name", "app.prathxm.chess.extension.crash.CrashActivity")
                    activity.setAttribute("android:theme", "@android:style/Theme.NoTitleBar")
                    activity.setAttribute("android:exported", "false")
                    // NOTE: No android:process=":crash" — that spawns a new process which
                    // re-runs Application.onCreate() and fires Firebase init coroutines,
                    // crashing before the UI can render. CrashActivity is always launched
                    // with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK after
                    // killProcess() so the old process is already dead; the new process
                    // that renders this activity starts clean without the crashed state.
                    application.appendChild(activity)
                }
            }
        }
    }
}

val crashHandlerPatch = bytecodePatch(
    name = "Global Crash Handler",
    description = "Catches uncaught exceptions and displays a custom crash screen with details to report issues.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    dependsOn(crashHandlerResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        MainApplicationOnCreateFingerprint.method.addInstructions(
            0,
            """
                move-object v0, p0
                invoke-static {v0}, Lapp/prathxm/chess/extension/crash/CrashHandler;->install(Landroid/content/Context;)V
            """
        )
    }
}

/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.misc

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprints for Firebase Crashlytics
// ─────────────────────────────────────────────────────────────────────────────

object FirebaseCrashlyticsRecordExceptionFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;" &&
            method.name == "recordException" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/Throwable;"
    }
)

object FirebaseCrashlyticsLogFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;" &&
            method.name == "log" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/String;"
    }
)

object FirebaseCrashlyticsSetCustomKeyFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;" &&
            method.name == "setCustomKey" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Ljava/lang/String;" &&
            method.parameterTypes[1] == "Ljava/lang/String;"
    }
)

object FirebaseCrashlyticsSetUserIdFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;" &&
            method.name == "setUserId" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/String;"
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Disable Telemetry & Analytics Patch
// ─────────────────────────────────────────────────────────────────────────────

val disableAnalyticsPatch = bytecodePatch(
    name = "Disable Analytics & Telemetry",
    description = "Completely disables Firebase Crashlytics and telemetry reporting to protect account privacy and prevent bans.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    execute {
        // Firebase Crashlytics Hooks
        FirebaseCrashlyticsRecordExceptionFingerprint.method.addInstructions(0, "return-void")
        FirebaseCrashlyticsLogFingerprint.method.addInstructions(0, "return-void")
        FirebaseCrashlyticsSetCustomKeyFingerprint.method.addInstructions(0, "return-void")
        FirebaseCrashlyticsSetUserIdFingerprint.method.addInstructions(0, "return-void")
    }
}

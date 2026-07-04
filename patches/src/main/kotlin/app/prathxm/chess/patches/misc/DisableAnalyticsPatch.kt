package app.prathxm.chess.patches.misc

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
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
// MainApplication Analytics & Telemetry Initializer Hooks
// ─────────────────────────────────────────────────────────────────────────────

object MainApplicationInstallAnalyticsFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/chess/MainApplication\$installAnalytics\$1;",
            name = "<init>"
        )
    ),
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/MainApplication;" &&
            method.name != "<init>" &&
            method.name != "onCreate"
    }
)

object MainApplicationInstallMonitoringFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/chess/logging/CrashlyticsAndBugsnagMonitoring;",
            name = "<init>"
        )
    ),
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/MainApplication;" &&
            method.name != "<init>" &&
            method.name != "onCreate"
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Disable Telemetry & Analytics Patch
// ─────────────────────────────────────────────────────────────────────────────

val disableAnalyticsPatch = bytecodePatch(
    name = "Disable Analytics & Telemetry",
    description = "Completely disables Firebase Analytics, Google Analytics, Firebase Crashlytics, Facebook Event logging, Bugsnag monitoring, and internal app telemetry to protect account privacy and prevent bans.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    execute {
        // Firebase Crashlytics Hooks
        FirebaseCrashlyticsRecordExceptionFingerprint.method.addInstructions(0, "return-void")
        FirebaseCrashlyticsLogFingerprint.method.addInstructions(0, "return-void")
        FirebaseCrashlyticsSetCustomKeyFingerprint.method.addInstructions(0, "return-void")
        FirebaseCrashlyticsSetUserIdFingerprint.method.addInstructions(0, "return-void")

        // MainApplication Analytics & Monitoring Inits stubbing
        MainApplicationInstallAnalyticsFingerprint.method.addInstructions(0, "return-void")
        MainApplicationInstallMonitoringFingerprint.method.addInstructions(0, "return-void")
    }
}

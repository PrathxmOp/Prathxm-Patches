package app.prathxm.chess.patches.botunlock

import app.morphe.patcher.Fingerprint

// ─────────────────────────────────────────────────────────────────────────────
// Bot-Unlocking Fingerprints
// ─────────────────────────────────────────────────────────────────────────────

object BotPersonalityBotGetCanPlayFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/versusbots/Bot\$PersonalityBot;" &&
            (method.name == "i" || method.name == "k") &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object BotPersonalityBotGetRequiresActivationFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/versusbots/Bot\$PersonalityBot;" &&
            (method.name == "E" || method.name == "B") &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object ProtoBotPersonalityGetCanPlayFingerprint : Fingerprint(
    definingClass = "Lchesscom/bots/v1/BotPersonality;",
    name = "getCan_play",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object ProtoBotPersonalityGetEnabledFingerprint : Fingerprint(
    definingClass = "Lchesscom/bots/v1/BotPersonality;",
    name = "getEnabled",
    parameters = listOf(),
    returnType = "Z"
)

object ProtoBotPersonalityGetPremiumFingerprint : Fingerprint(
    definingClass = "Lchesscom/bots/v1/BotPersonality;",
    name = "getPremium",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object LockedBotsCheckFingerprint : Fingerprint(
    custom = { method, classDef ->
        (classDef.type == "Lcom/chess/features/versusbots/ui/h9;" || classDef.type == "Lcom/chess/features/versusbots/ui/l9;") &&
            method.name == "a" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Lcom/chess/features/versusbots/Bot;" &&
            method.returnType == "Z"
    }
)

package app.prathxm.chess.patches.botunlock

import app.morphe.patcher.Fingerprint

// ─────────────────────────────────────────────────────────────────────────────
// Bot-Unlocking Fingerprints
// ─────────────────────────────────────────────────────────────────────────────

object BotPersonalityBotGetCanPlayFingerprint : Fingerprint(
    definingClass = "Lcom/chess/features/versusbots/Bot\$PersonalityBot;",
    name = "i",
    parameters = listOf(),
    returnType = "Z"
)

object BotPersonalityBotGetRequiresActivationFingerprint : Fingerprint(
    definingClass = "Lcom/chess/features/versusbots/Bot\$PersonalityBot;",
    name = "E",
    parameters = listOf(),
    returnType = "Z"
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
    definingClass = "Lcom/chess/features/versusbots/ui/h9;",
    name = "a",
    parameters = listOf("Lcom/chess/features/versusbots/Bot;"),
    returnType = "Z"
)

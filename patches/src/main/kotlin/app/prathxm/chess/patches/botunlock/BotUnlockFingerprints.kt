/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.botunlock

import app.morphe.patcher.Fingerprint

// ─────────────────────────────────────────────────────────────────────────────
// Bot-Unlocking Fingerprints
// ─────────────────────────────────────────────────────────────────────────────

object BotPersonalityBotGetCanPlayFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/versusbots/Bot\$PersonalityBot;" &&
            method.name == "i" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object BotPersonalityBotGetRequiresActivationFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/versusbots/Bot\$PersonalityBot;" &&
            method.name == "E" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object ProtoBotPersonalityGetCanPlayFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lchesscom/bots/v1/BotPersonality;" &&
            method.name == "getCan_play" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Ljava/lang/Boolean;"
    }
)

object ProtoBotPersonalityGetEnabledFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lchesscom/bots/v1/BotPersonality;" &&
            method.name == "getEnabled" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }
)

object ProtoBotPersonalityGetPremiumFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lchesscom/bots/v1/BotPersonality;" &&
            method.name == "getPremium" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Ljava/lang/Boolean;"
    }
)

object LockedBotsCheckFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/chess/features/versusbots/ui/h9;" &&
            method.name == "a" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Lcom/chess/features/versusbots/Bot;" &&
            method.returnType == "Z"
    }
)

/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.coach

import app.morphe.patcher.Fingerprint

// ─────────────────────────────────────────────────────────────────────────────
// Play Coach daily quota (Chess.com 4.10.17)
//
// com.chess.features.versusbots.coach.CoachGamesQuotaServiceImpl implements the
// quota interface com.chess.features.versusbots.api.i:
//
//   a(Continuation) -> Object (Boolean)   canStartCoachedGame
//        Calls TwirpRetrofitCoachesService.GetCoachQuota and returns remaining != 0.
//        CoachGameSetupViewModel.resolveQuota stores the result; when it is false the
//        "Play" button opens the premium upsell (PlayCoachMonetizationModalShowed)
//        instead of starting the game.
//
//   b(Coach, String gameToken, Continuation) -> Object (Unit)   coachedGameReachedQuotaThreshold
//        Called by BotGameEngine once a coach game has been played long enough to count.
//        Stores the game token in the "coach_quota" DataStore and sends
//        StartedGameAgainstCoach to the server, which consumes the daily free game.
//
// Both are matched by class + signature (not by the obfuscated name alone) so that a
// rename of the interface methods does not silently hook the wrong method.
// ─────────────────────────────────────────────────────────────────────────────

private const val QUOTA_SERVICE = "Lcom/chess/features/versusbots/coach/CoachGamesQuotaServiceImpl;"

object CoachQuotaCanStartFingerprint : Fingerprint(
    definingClass = QUOTA_SERVICE,
    returnType = "Ljava/lang/Object;",
    custom = { method, _ ->
        method.parameterTypes.size == 1 &&
            // The only single-parameter public suspend method whose continuation is typed
            // Continuation<Boolean>; c() (sendPendingQuotaRequests) has the same erased
            // signature, so tell them apart by the network call it makes.
            method.implementation?.instructions?.any { insn ->
                val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference
                ref is com.android.tools.smali.dexlib2.iface.reference.TypeReference &&
                    ref.type.contains("CoachGamesQuotaServiceImpl\$canStartCoachedGame\$")
            } == true
    }
)

object CoachQuotaConsumeFingerprint : Fingerprint(
    definingClass = QUOTA_SERVICE,
    returnType = "Ljava/lang/Object;",
    custom = { method, _ ->
        // (Coach, String, Continuation): the continuation type is obfuscated.
        method.parameterTypes.size == 3 &&
            method.parameterTypes[0] == "Lcom/chess/coach/Coach;" &&
            method.parameterTypes[1] == "Ljava/lang/String;"
    }
)

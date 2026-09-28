/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.coach

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

val unlimitedCoachPatch = bytecodePatch(
    name = "Unlimited Play Coach",
    description = "Removes the one-free-game-per-day limit on Play Coach.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    execute {
        // canStartCoachedGame(): always "quota remaining". Returning the boxed value directly
        // from a suspend function is a normal (non-suspended) completion, so the caller
        // (CoachGameSetupViewModel.resolveQuota) resumes synchronously with TRUE, the setup
        // screen never shows the upsell and no GetCoachQuota request is made.
        CoachQuotaCanStartFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // coachedGameReachedQuotaThreshold(): do not record the game locally and do not send
        // StartedGameAgainstCoach, so playing a coach game never uses up the daily free game.
        CoachQuotaConsumeFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
                return-object v0
            """
        )
    }
}

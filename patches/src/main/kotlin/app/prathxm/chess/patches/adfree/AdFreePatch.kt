/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.adfree

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

private const val EXTENSION_CLASS = "Lapp/prathxm/chess/extension/stockfish/StockfishExtension;"

val adFreePatch = bytecodePatch(
    name = "Ad-Free",
    description = "Removes advertisements",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    extendWith("extensions/extension.mpe")

    execute {
        // LoginData.getShow_ads() → boolean (primitive)
        LoginDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return v0
            """
        )

        // LoginData.getShow_interstitial_ads() → Boolean (boxed)
        LoginDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // UserData.getShow_ads() → Boolean (boxed)
        UserDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // UserData.getShow_interstitial_ads() → Boolean (boxed)
        UserDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // LoginData.getPremium_status() → int (DIAMOND = 3 if logged in, else 0)
        LoginDataGetPremiumStatusFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lcom/chess/net/model/LoginData;->getUsername()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :guest
                invoke-virtual {v0}, Ljava/lang/String;->length()I
                move-result v0
                if-eqz v0, :guest
                const/4 v0, 3
                return v0
                :guest
                const/4 v0, 0
                return v0
            """
        )

        // SharedPreferencesSessionStore.i() → boolean (isGuest = false when user has a session)
        // Fixes: Sign Up button showing in settings and missing chat button in games
        SessionStoreIsGuestFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lcom/chess/apputils/SharedPreferencesSessionStore;->getSession()Lcom/chess/net/model/LoginData;
                move-result-object v0
                if-eqz v0, :no_session
                invoke-virtual {v0}, Lcom/chess/net/model/LoginData;->getUsername()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :no_session
                invoke-virtual {v0}, Ljava/lang/String;->length()I
                move-result v0
                if-eqz v0, :no_session
                const/4 v0, 0
                return v0
                :no_session
                const/4 v0, 1
                return v0
            """
        )

        // UserData.getPremium_status() → PremiumStatus
        UserDataGetPremiumStatusFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->getDiamondStatus()Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lcom/chess/entities/PremiumStatus;
                if-nez v0, :original
                return-object v0
                :original
            """
        )
    }
}

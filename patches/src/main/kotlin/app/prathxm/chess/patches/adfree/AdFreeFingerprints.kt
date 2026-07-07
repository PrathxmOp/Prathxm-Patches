/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.adfree

import app.morphe.patcher.Fingerprint

object LoginDataGetShowAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getShow_ads",
    parameters = listOf(),
    returnType = "Z"
)

object LoginDataGetShowInterstitialAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getShow_interstitial_ads",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object UserDataGetShowAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getShow_ads",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object UserDataGetShowInterstitialAdsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getShow_interstitial_ads",
    parameters = listOf(),
    returnType = "Ljava/lang/Boolean;"
)

object LoginDataGetPremiumStatusFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/LoginData;",
    name = "getPremium_status",
    parameters = listOf(),
    returnType = "I"
)

object SessionStoreIsGuestFingerprint : Fingerprint(
    definingClass = "Lcom/chess/apputils/SharedPreferencesSessionStore;",
    name = "i",
    parameters = listOf(),
    returnType = "Z"
)

object UserDataGetPremiumStatusFingerprint : Fingerprint(
    definingClass = "Lcom/chess/net/model/UserData;",
    name = "getPremium_status",
    parameters = listOf(),
    returnType = "Lcom/chess/entities/PremiumStatus;"
)

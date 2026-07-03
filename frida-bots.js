Java.perform(function() {
    console.log("[*] Bot Unlocker 10.0 - Adaptive Bypass");

    var BooleanClass = Java.use("java.lang.Boolean");

    function safeHook(className, methodName, implementation) {
        try {
            var clazz = Java.use(className);
            var hooked = false;

            // Try hooking by name
            if (clazz[methodName]) {
                clazz[methodName].implementation = implementation;
                console.log("[+] Hooked: " + className + "." + methodName);
                hooked = true;
            }

            // If not hooked, try searching for the method if it was obfuscated
            if (!hooked) {
                var methods = clazz.class.getDeclaredMethods();
                for (var i = 0; i < methods.length; i++) {
                    var m = methods[i];
                    if (m.getName() === methodName) {
                        // This should have been caught by clazz[methodName] but Frida sometimes acts up
                        clazz[methodName].implementation = implementation;
                        console.log("[+] Hooked (Reflect): " + className + "." + methodName);
                        hooked = true;
                        break;
                    }
                }
            }

            if (!hooked) {
                console.log("[-] Method not found: " + className + "." + methodName);
            }
        } catch (e) {
            console.log("[-] Failed to hook " + className + ": " + e);
        }
    }

    // --- 1. Premium Status ---
    try {
        var PS = Java.use("com.chess.entities.PremiumStatus");
        var diamond = PS.valueOf("DIAMOND");

        safeHook("com.chess.net.model.UserData", "getPremium_status", function() { return diamond; });
        // Many obfuscated names use 'component6' for premium_status
        safeHook("com.chess.net.model.UserData", "component6", function() { return diamond; });

        safeHook("com.chess.net.model.LoginData", "getPremium_status", function() { return 4; });
        safeHook("com.chess.net.model.LoginData", "component1", function() { return 4; });
    } catch(e) {}

    // --- 2. Bot State (PersonalityBot) ---
    // i = getCanPlay, E = getIsAssociatedWithCampaignThatRequiresAccountActivation
    safeHook("com.chess.features.versusbots.Bot$PersonalityBot", "getCanPlay", function() { return true; });
    safeHook("com.chess.features.versusbots.Bot$PersonalityBot", "i", function() { return true; });
    safeHook("com.chess.features.versusbots.Bot$PersonalityBot", "getIsAssociatedWithCampaignThatRequiresAccountActivation", function() { return false; });
    safeHook("com.chess.features.versusbots.Bot$PersonalityBot", "E", function() { return false; });

    // --- 3. Proto Messages ---
    safeHook("chesscom.bots.v1.BotPersonality", "getCan_play", function() { return BooleanClass.valueOf(true); });
    safeHook("chesscom.bots.v1.BotPersonality", "getEnabled", function() { return true; });
    safeHook("chesscom.bots.v1.BotPersonality", "getPremium", function() { return BooleanClass.valueOf(false); });
    safeHook("chesscom.bots.v1.BotPersonality", "getCustom_modal_requires_account_activation", function() { return BooleanClass.valueOf(false); });

    // --- 4. UI Unlock (LockedBots) ---
    // h9 is the obfuscated name for LockedBots in some builds
    safeHook("com.chess.features.versusbots.ui.LockedBots", "a", function(bot) { return false; });
    safeHook("com.chess.features.versusbots.ui.h9", "a", function(bot) { return false; });

    console.log("[*] Script loaded. Interaction start karein.");
});

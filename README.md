# ♟️ Prathxm Patches

Custom Morphe patches for **Chess.com**, integrating the powerful Stockfish chess engine for real-time move analysis and a premium experience.

## ❓ About

Prathxm Patches brings a native Stockfish chess engine directly into Chess.com on Android. It hooks into the live game board and provides real-time engine analysis, move classification, and visual overlays — all without ever leaving the app.

## ✨ Features

### 🔧 Engine & Analysis
- **Stockfish 16.1** — Full native engine running locally on device
- **Multi-PV Best Move Arrows** — Color-coded, opacity-ranked arrows (Green → Blue → Orange → Purple → Red)
- **Configurable Depth & MultiPV** — Tune analysis strength (depth 1–20, up to 5 lines)
- **My Side Only Mode** — Only show engine arrows on your turn
- **ELO Strength Limiting** — Cap engine strength (1350–3190 ELO)

### 📊 Visual Overlays
- **Evaluation Bar** — Clean vertical bar showing pawn/mate advantage (stable, no flicker)
- **Win/Draw/Loss Bar** — Horizontal probability strip below the board (e.g. `65% | 22% | 13%`)
- **Threat Arrow** — Crimson red arrow showing opponent's best response
- **Mate Announcement** — Premium pill banner: `♟ Mate in X!` or `☠ Opponent Mates in X!`

### 🏷️ Move Feedback
- **Move Classification** — Toast notifications: Brilliant 💡 / Best 🎯 / Excellent ✨ / Great ✅ / Good 👍 / Inaccuracy ⚠️ / Mistake ❌ / Blunder 💀
- **Blunder Alerts** — Haptic vibration on blunders & mistakes

### 💎 Premium & Global
- **Diamond Premium Unlock** — Full premium status
- **Ads Removal** — Clean, ad-free experience
- **Dark-Themed Settings UI** — Chess.com-matching in-game settings menu with developer credits

### ⚙️ Default Settings

| Feature | Default |
|---------|---------|
| Engine Enabled | ✅ ON |
| Best Move Arrows | ✅ ON |
| Evaluation Bar | ✅ ON |
| My Side Only | ✅ ON |
| Premium Unlock | ✅ ON |
| Ads Removed | ✅ ON |
| Win/Draw/Loss Bar | ❌ OFF |
| Threat Arrows | ❌ OFF |
| Move Classification | ❌ OFF |
| Blunder Alerts | ❌ OFF |
| Mate Announcement | ❌ OFF |
| ELO Limit | ❌ OFF |

> All features are toggleable via long-press on the Chess.com logo.
> You can also double-tap on the Chess.com logo to quickly hide or show all engine overlays (arrows, bars, and banners) instantly in-game.

## 🎮 Mod Menu & Premium Features Guide

### ☝️ How to Access & Use
* **Open Settings Menu:** Long-press on the **Chess.com** logo at the top of the screen during a game. A dark-themed settings panel will open, allowing you to configure all stockfish/premium features.
* **Stealth/Panic Toggle:** Double-tap on the **Chess.com** logo. This immediately halts the engine, clears all arrows, and hides the evaluation bar, WDL bar, and mate announcements for complete discretion. Double-tapping again restores your active overlays and resumes analysis.

### 💎 What is Included in the Premium (Diamond) Patch?
When you check the **"Enable Premium (Diamond Status)"** and **"Remove Ads"** options in the settings menu:
* **Complete Ads Removal:** Blocks all banners, interstitial ads, and video promotions locally for an uninterrupted play experience.
* **Local Diamond Account Spoofing:** Grants your local profile the premium Diamond status styling and UI badges.

> **Note:** The main patch description in metadata was shortened to keep it clean and concise, but all Premium features and settings remain fully functional and included in the code.

## 🩹 Patches List

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.1.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Stockfish Engine Integration](#stockfish-engine-integration) | Injects the local Stockfish chess engine into Chess.com for real-time analysis, best-move arrows, and customizable settings. |  |

</details>

<!-- PATCHES_END -->

### 📙 Contributing

Contributions are welcome! Please read the [contribution guidelines](CONTRIBUTING.md) before submitting a pull request.

### 🛠️ Patching

#### Option 1: Morphe Manager (Recommended — Easy)

The easiest way to patch Chess.com is using **Morphe Manager** on your Android device.

1. Download and install [Morphe Manager](https://morphe.software).
2. Open Morphe Manager and go to **Patch Sources**.
3. Add this repository as a source:
   ```
   https://github.com/PrathxmOp/Prathxm-Patches
   ```
4. Select **Chess.com** from the app list.
5. Enable the **Stockfish Engine** patch and tap **Patch**.
6. Install the output APK.

&nbsp;

#### Option 2: Morphe CLI (Advanced)

Prerequisites:
- Android SDK
- JDK 17+
- [Morphe CLI](https://morphe.software)

##### 🤖 Native Stockfish Binaries
Since the pre-compiled Stockfish binaries exceed GitHub's file size limit (100MB+), they are excluded from the repository. You must download and place them manually before building:
1. Download the Android binaries from the official Stockfish releases:
   - **arm64-v8a**: (e.g. from `stockfish-android-armv8-dotprod.tar`)
   - **armeabi-v7a**: (e.g. from `stockfish-android-armv7-neon.tar`)
2. Place them in the repository under:
   - `extensions/extension/src/main/assets/stockfish/arm64-v8a/stockfish`
   - `extensions/extension/src/main/assets/stockfish/armeabi-v7a/stockfish`

```bash
# 1. Assemble the patch bundle
./gradlew patches:assemble

# 2. Apply the patch to a Chess.com APK
java -jar morphe-cli.jar patch \
  -p patches/build/libs/patches-1.0.0.mpp \
  -o patched-chess.apk \
  "com.chess_X.X.X.apk"

# 3. Install on device
adb install patched-chess.apk
```

## ⚠️ Disclaimer

These patches are intended for **educational and personal use only**. Usage may violate Chess.com's Terms of Service. The author takes no responsibility for any consequences arising from use of these patches.

## 📜 License

Prathxm Patches are licensed under the [GNU General Public License v3.0](LICENSE)

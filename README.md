# ♟️ Prathxm Patches

Custom Morphe patches for **Chess.com**, integrating the powerful Stockfish chess engine for real-time move analysis and a premium experience.

> [!WARNING]
> **Important Disclaimer:** Using this patch to get move recommendations during live online games can result in your Chess.com account being permanently banned. Do not use this tool for cheating. It is intended strictly for learning, offline study, and educational purposes. Use it at your own risk!

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
Some users have reported confusion or suspected the patch doesn't work because they expect server-side features (like Chess.com's cloud-based Coach Report cards or cloud game analysis) to suddenly work for free. **Please read how this patch works to understand why it is fully functional:**

Because Chess.com is a **server-authoritative** application, features that are managed and validated on Chess.com servers cannot be modified locally. 

Here is exactly what this patch does, what it does not do, and how it works:

#### ✅ What is Fully Unlocked & Working (How to use it):
* **Unlimited Free Game Analysis (The Bypass):** Bypasses Chess.com's 1-game-per-day limit on game reviews. 
  * **How it works:** When you tap "Game Review", the app usually contacts Chess.com servers (which blocks you if you used your free daily analysis). The patch **intercepts and redirects** this request to our **built-in, high-performance Stockfish 16.1 engine running locally on your device**.
  * **Result:** It runs the analysis offline on your device and injects the results back into the native review UI. This gives you **unlimited, completely free game reviews** with:
    * 🏷️ **Move Classifications** — Brilliant 💡 / Best 🎯 / Great ✅ / Excellent ✨ / Good 👍 / Inaccuracy ⚠️ / Mistake ❌ / Blunder 💀
    * 📊 **Accuracy Scores** — Per-player accuracy percentage (0–100%)
    * 📈 **Dynamic Game Ratings** — Estimated per-game rating (200–2800) derived from accuracy, with Opening/Tactics/Endgame breakdowns
    * ♟️ **Eval Scores** — Centipawn and mate-in evaluations for every position
    * 📋 **Move Tallies** — Full count of each move category per player
* **Complete Ads Removal:** Blocks and removes all banner advertisements, full-screen interstitial ads, and video promotions locally for a clean, premium, ad-free experience.
* **Local Diamond Status & Badge Spoofing:** Spoofs your local profile to display the premium Diamond theme styling and UI badges in the app.

#### ❌ What is Server-Authoritative (Cannot be patched — yet):
* **Official Server-Side Cloud Reviews:** Tapping the official cloud-based coach review queries their server databases. You must use the local Stockfish analysis board/review screen (which is what the patch redirects) for unlimited analysis.
* **Server-Side Video Lessons & Puzzles:** Interactive video lessons and daily puzzle limits are served directly from Chess.com's databases — **free alternatives are being developed** (see Roadmap below).

> **Note:** The main patch description in metadata is kept concise, but all Premium features and settings are fully compiled, active, and toggleable in the stealth settings menu.

## 🗺️ Free Alternatives Roadmap

We're building open-source replacements for Chess.com's premium features using free, legal data sources:

| # | Feature | Status | Approach |
|---|---------|--------|----------|
| 1 | **Game Review** | ✅ Done | Local Stockfish 16.1 engine |
| 2 | **Premium Status & Ads** | ✅ Done | Client-side patch |
| 3 | **Lessons & Drills** | 🔜 Next | Paywall bypass (content already on device) |
| 4 | **Unlimited Puzzles** | 📋 Planned | [Lichess Puzzle DB](https://database.lichess.org/#puzzles) (CC0, 4M+ puzzles) |
| 5 | **Opening Explorer** | 📋 Planned | [Lichess Explorer API](https://explorer.lichess.ovh) (free) |
| 6 | **Endgame Tablebase** | 📋 Planned | [Lichess Tablebase API](https://tablebase.lichess.ovh) + offline Syzygy |
| 7 | **Coach Commentary** | 📋 Planned | Template-based explanations from Stockfish eval |
| 8 | **Advanced Insights** | 📋 Planned | Local stats engine + paywall bypass |

> All planned features use **legally free** data sources (CC0 licensed databases, public APIs, public domain tablebases).

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

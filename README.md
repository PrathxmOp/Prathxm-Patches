# ♟️ Prathxm Patches

Custom Morphe patches for **Chess.com**, providing an ad-free experience, post-game review, and local Stockfish analysis tools.

## ❓ About

Prathxm Patches integrates the Stockfish chess engine directly into Chess.com on Android for offline play, bot matches, and post-game analysis. It is strictly compliant with fairplay rules—all overlays (arrows, evaluation bars, etc.) are automatically disabled during live online matches to prevent cheating.

> [!NOTE]
> **Why is the patch size so large?**
> The patched APK will be significantly larger than the original app (by ~110MB). This is because we bundle the native **Stockfish 16.1 NNUE** engine binaries directly into the app so that game reviews and engine analysis can run entirely offline on your device, without relying on Chess.com servers.

### 📙 App Requests

> [!TIP]
> **App Requests:** If you want to request support or patches for any other app, please open a request in the [GitHub Discussions](https://github.com/PrathxmOp/Prathxm-Patches/discussions) section rather than opening a GitHub Issue.

## ✨ Features

### 🔧 Engine & Analysis
- **Stockfish 16.1** — Native engine running locally on device for offline analysis and bot games.
- **Post-Game Analysis & Reviews** — Offline local game reviews with move classification.
- **Configurable Settings** — Adjust analysis depth and ELO strength limit (1350-3190 ELO).

### 📊 Visual Overlays (Offline & Bots Only)
- **Evaluation & WDL Bars** — Visual advantage bar showing pawn/mate advantage and win/draw/loss probability.
- **Best Move & Threat Arrows** — Local arrow indicators showing engine recommendations.

### 🧩 Lichess Puzzle Journey
- **Minimalist Puzzle Journey Map** — Progress through puzzle levels with a scenic map view, custom rustic bridge pathing, tree trunk level gem nodes, and active pawn position indicators.
- **Daily Puzzle Integration** — Online fetching from Lichess API with local daily completion tracking in `SharedPreferences` to secure streak data.
- **State-Hardened Board View** — Custom canvas-based board drawing (`LichessBoardView`) with strict paint state resetting to prevent visual artifacts and auto-reset logic on incorrect moves.
- **Interactive Game Sounds** — Full SoundPool integration for move, capture, correct, incorrect, and puzzle-solved sound effects.
- **Premium Themes & Fonts** — Custom wood assets, custom chess typography, and glassmorphic UI overlay styling.

### 💎 Global Features
- **Ads Removal** — Removes all banner, interstitial, and video advertisements locally for a clean experience.

### 💎 What is Included in the Ad-Free & Local Analysis Patch?
This patch removes advertisements and embeds a local Stockfish engine to run client-side game reviews.

> **Tip:** You can long-press on the Chess.com logo at the top of the screen during a game to open the settings menu and configure the engine.

Here is exactly what this patch provides and how it works:

#### ✅ Local Features (How to use it):
* **Local Game Analysis:** Brings powerful offline analysis capabilities directly to your device.
  * **How it works:** When you tap "Game Review", the patch **intercepts and redirects** the request to our **built-in, high-performance Stockfish 16.1 engine running locally on your device**.
  * **Result:** It runs the analysis offline on your device and injects the results back into the native review UI. This gives you **offline game reviews** with:
    * 🏷️ **Move Classifications** — Brilliant 💡 / Best 🎯 / Great ✅ / Excellent ✨ / Good 👍 / Inaccuracy ⚠️ / Mistake ❌ / Blunder 💀
    * 📊 **Accuracy Scores** — Per-player accuracy percentage (0–100%)
    * 📈 **Dynamic Game Ratings** — Estimated per-game rating (200–2800) derived from accuracy, with Opening/Tactics/Endgame breakdowns
    * ♟️ **Eval Scores** — Centipawn and mate-in evaluations for every position
    * 📋 **Move Tallies** — Full count of each move category per player
* **Complete Ads Removal:** Blocks and removes all banner advertisements, full-screen interstitial ads, and video promotions locally for a clean, premium, ad-free experience.

#### 🛡️ Fair Play Protection (Anti-Cheating):
* **Live Match Disablement:** To ensure strict compliance with fair play guidelines, all real-time engine analysis, visual overlays (best move arrows, evaluation bars, etc.), and settings menus are completely and automatically disabled during live online matches against other players. The engine overlays only work for post-game offline analysis and bot games.

#### ❌ What is Server-Authoritative (Cannot be patched):
* **Official Server-Side Cloud Reviews:** Tapping the official cloud-based coach review queries their server databases. You must use the local Stockfish analysis board/review screen (which is what the patch redirects) for unlimited analysis.
* **Server-Side Video Lessons & Puzzles:** Interactive video lessons and daily puzzle limits are served directly from Chess.com's databases.


## 🩹 Patches List

<!-- PATCHES_START EXPANDED -->
> **[v1.3.1](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.3.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ad-Free & Local Analysis](#ad-free-local-analysis) | Removes advertisements, unlocks ad-free features, and enables local Stockfish engine for post-game review & analysis. |  |
| [Lichess Puzzle Journey](#lichess-puzzle-journey) | Embeds a standalone, premium, and fully-featured Lichess Daily Puzzle & level-based Puzzle Journey within the app. |  |
| [Clone Chess.com](#clone-chess-com) | Changes the package name to com.chess.prathxm, allowing the patched app to be installed side-by-side with the original Chess.com app. |  |

</details>

<!-- PATCHES_END -->

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

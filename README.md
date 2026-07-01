# ♟️ Prathxm Patches

> *"You expect me to be perfect but i am full of flaws"*

Custom Morphe patches for **Chess.com**, providing a clean, ad-free experience, locally processed post-game reviews, and offline Lichess puzzle journeys.

---

## ❓ About

Prathxm Patches embeds a native **Stockfish 16.1 NNUE** chess engine directly into the app on Android. This allows you to perform post-game reviews and run analysis completely offline, without depending on external servers. 

Strictly designed in compliance with fair-play rules, all analysis assistance and visual overlays are automatically disabled during live online matches to ensure a completely fair environment.

> [!NOTE]
> **Why is the package size larger?**
> The patched app will be about 110MB larger than the default installation. This is because it includes the high-performance **Stockfish 16.1 NNUE** engine binaries locally, so you can analyze matches offline anytime.

---

## 💬 Support & Feedback

If you have questions, feedback, or want to suggest new features/patches, **please do not open GitHub Issues**. Instead, use the GitHub Discussions section:

👉 **[Join the Community & Ask Questions / Request Features](https://github.com/PrathxmOp/Prathxm-Patches/discussions)**

Using discussions helps keep the project organized and lets other community members contribute.

---

## 💖 Support the Project

Donations keep me motivated to maintain this project, fix bugs, and create new patches! If this project made your chess experience better, please consider supporting my work.

### Donate via UPI: `prathammishraop@ybl`

You can scan the QR code below or click the payment link:

<p align="center">
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=upi://pay?pa=prathammishraop@ybl%26pn=Prathxm%26cu=INR" alt="UPI QR Code" width="180" height="180" /><br>
  <a href="upi://pay?pa=prathammishraop@ybl&pn=Prathxm&cu=INR"><b>⚡ Click Here to Pay / Donate via UPI</b></a>
</p>

---

## ✨ Features

### 🔧 Engine & Analysis (Offline & Bot Games Only)
* **Local Stockfish 16.1** — A full-powered engine running directly on your mobile device.
* **Offline Game Reviews** — Intercepts and processes game review requests locally, offering:
  * 🏷️ **Move Classifications** — Brilliant 💡, Great ✅, Best 🎯, Excellent ✨, Good 👍, Inaccuracy ⚠️, Mistake ❌, and Blunder 💀.
  * 📊 **Accuracy Scores** — Precise percentage metrics for both sides.
  * 📈 **Estimated Ratings** — Calculated ELO ratings for your gameplay with opening/tactical breakdowns.
* **Configurable ELO Limits** — Adjust search depth and limit the engine strength (1350 to 3190 ELO) to tailor bot games.

### 📊 Visual Overlays
* **Evaluation & WDL Bars** — Real-time advantage tracking showing centipawn/mate status and Win/Draw/Loss probabilities.
* **Engine Recommendations** — Arrow indicators displaying the best moves and threats.

### 🧩 Lichess Puzzle Journey
* **Fully Offline Puzzles** — A dedicated local database containing millions of puzzles from the Lichess library.
* **Scenic Journey Map** — Walk through level paths on a custom-styled, premium wood board layout.
* **Thematic Practice** — Filter by themes (Opening, Middlegame, Endgame, specific endgames) or play fast-paced Rush and Battle modes.
* **Choose Your Size** — Download what fits your storage best (20k, 50k, 100k, 500k, or all 6M+ puzzles).
* **Streaks & Milestones** — Progress tracker featuring daily puzzle streaks, active category stats, and custom milestones.

### 💎 Global Features
* **Ad-Free UI** — Block and remove banner ads, full-screen interstitials, and video promotions for a cleaner experience.

---

## 🩹 Patches List

<!-- PATCHES_START EXPANDED -->
> **[v1.4.1](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.4.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ad-Free & Local Analysis](#ad-free-local-analysis) | Removes advertisements, unlocks ad-free features, and enables local Stockfish engine for post-game review & analysis. |  |
| [Clone Chess.com](#clone-chess-com) | Changes the package name to com.chess.prathxm, allowing the patched app to be installed side-by-side with the original Chess.com app. |  |
| [Lichess Puzzles](#lichess-puzzles) | Loads daily puzzles from Lichess and bypasses Chess.com puzzle premium limits. |  |

</details>

<!-- PATCHES_END -->

---

## 🛠️ Installation & Patching

### Option 1: Morphe Manager (Recommended)
1. Download and install [Morphe Manager](https://morphe.software) on your Android device.
2. Go to **Patch Sources** and add this repository:
   ```
   https://github.com/PrathxmOp/Prathxm-Patches
   ```
3. Select **Chess.com** from the app list, choose your patches, and tap **Patch**.
4. Install the output APK.

### Option 2: Morphe CLI (Advanced)
Prerequisites: Android SDK, JDK 17+, and [Morphe CLI](https://morphe.software).

1. Download the Android Stockfish binaries from the official releases:
   - **arm64-v8a** (e.g. `stockfish-android-armv8-dotprod.tar`)
   - **armeabi-v7a** (e.g. `stockfish-android-armv7-neon.tar`)
2. Place them under:
   - `extensions/extension/src/main/assets/stockfish/arm64-v8a/stockfish`
   - `extensions/extension/src/main/assets/stockfish/armeabi-v7a/stockfish`
3. Build and apply:
   ```bash
   # Assemble the bundle
   ./gradlew patches:assemble
   
   # Apply patch
   java -jar morphe-cli.jar patch \
     -p patches/build/libs/patches-1.0.0.mpp \
     -o patched-chess.apk \
     "com.chess_X.X.X.apk"
   ```

---

## ⚠️ Disclaimer

These patches are intended for **educational and personal use only**. Usage may violate the host app's Terms of Service. The author takes no responsibility for account suspensions or other consequences.

## 📜 License

Licensed under the [GNU General Public License v3.0](LICENSE).

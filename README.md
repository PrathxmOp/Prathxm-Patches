# ♟️ Prathxm Patches

> *"You expect me to be perfect but i am full of flaws"*

Custom Morphe patches for **Chess.com**, featuring offline Lichess puzzle journeys, ad-free UI, unlocked bots, and a fully offline **Stockfish 16.1 NNUE** engine for local post-game reviews and analysis.

---

## ❓ About

This project embeds a native **Stockfish 16.1 NNUE** chess engine directly on your device. It intercepts and processes game review requests locally, offering offline move evaluations, accuracy scores, and rating estimations—all without server dependencies.

> [!IMPORTANT]
> **Fair Play Compliance:**
> To ensure fair play, all engine overlays, evaluation bars, and suggestions are automatically disabled during live online matches.

> [!NOTE]
> Including the offline high-performance Stockfish engine binaries increases the final APK size by ~110MB.

---

## ✨ Features

* **Offline Game Reviews:** Move classifications (Brilliant, Great, etc.), accuracy scores, and estimated ELO ratings processed entirely locally.
* **Offline Lichess Puzzles:** Full level-based journey map, daily streaks, custom milestones, and thematic practice filters (Endgames, Openings, etc.).
* **Evaluation & WDL Bars:** Centipawn tracking and win/draw/loss probabilities for analysis and bot games.
* **Global Overlays:** Engine suggestions, threat paths, and arrow indicators.
* **Ad-Free UI:** Removes banners, full-screen interstitials, and video promotions.
* **Unlock Versus Bots:** Play all restricted and locked bots without restrictions.
* **Custom Titles:** Display a custom title (e.g. GM, IM, FM) next to your name inside the app.
* **Privacy Shield:** Disables analytics, telemetry, Crashlytics, and crash logging.

---

## 💬 Discussions & Requests

For questions, feedback, or feature requests, join the discussion board:

👉 **[GitHub Discussions Board](https://github.com/PrathxmOp/Prathxm-Patches/discussions)**

---

## 💖 Support & Custom Titles

Donations keep this project alive! Supporter names are eligible for custom chess titles (e.g. **GM**, **IM**, **FM**) next to their username in-game.

### Donate via UPI: `prathxm@ybl`

Scan the QR code or click the payment link to donate:

<p align="center">
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=upi://pay?pa=prathxm@ybl%26pn=Prathxm%26cu=INR" alt="UPI QR Code" width="180" height="180" /><br>
  <a href="upi://pay?pa=prathxm@ybl&pn=Prathxm&cu=INR"><b>⚡ Click Here to Pay / Donate via UPI</b></a>
</p>

To claim your title, post your donation confirmation and desired title on the **[Discussions Board](https://github.com/PrathxmOp/Prathxm-Patches/discussions)**.

---

## 🩹 Patches List

<!-- PATCHES_START EXPANDED -->
> **[v1.9.0](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.9.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;6 patches total
<details open>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ad-Free & Local Analysis](#ad-free-local-analysis) | Removes advertisements, unlocks ad-free features, and enables local Stockfish engine for post-game review & analysis. |  |
| [Clone Chess.com](#clone-chess-com) | Changes the package name to com.chess.prathxm, allowing the patched app to be installed side-by-side with the original Chess.com app. |  |
| [Custom Titles](#custom-titles) | Allows users to load and display custom titles (e.g. GM, IM, FM, etc.) next to their username inside the app. |  |
| [Disable Analytics & Telemetry](#disable-analytics-telemetry) | Completely disables Firebase Crashlytics and telemetry reporting to protect account privacy and prevent bans. |  |
| [Lichess Puzzles](#lichess-puzzles) | Loads daily puzzles from Lichess and bypasses Chess.com puzzle daily limits. |  |
| [Unlock All Bots](#unlock-all-bots) | Unlocks all locked and restricted bots in the Versus Bots feature. |  |

</details>

<!-- PATCHES_END -->

---

## 🛠️ Installation & Patching

### Option 1: Morphe Manager (Recommended)

1. Install [Morphe Manager](https://morphe.software) on your Android device.
2. Add this repository source by clicking:
   👉 **[Add Patches to Morphe Manager](https://morphe.software/add-source?github=PrathxmOp/Prathxm-Patches)**
3. Choose **Chess.com**, select your patches, tap **Patch**, and install.

### Option 2: Morphe CLI (Advanced)

Prerequisites: Android SDK, JDK 17+, and [Morphe CLI](https://morphe.software).

1. Download and place the Android Stockfish binaries under:
   - `extensions/extension/src/main/assets/stockfish/arm64-v8a/stockfish`
   - `extensions/extension/src/main/assets/stockfish/armeabi-v7a/stockfish`
2. Build and apply:
   ```bash
   # Build the Android patches bundle
   ./gradlew buildAndroid
   
   # Apply patch
   java -jar morphe-cli.jar patch \
     -p patches/build/libs/patches-1.0.0.mpp \
     -o patched-chess.apk \
     "com.chess_X.X.X.apk"
   ```

---

## ⚠️ Disclaimer

For educational and personal use only. Usage may violate the host app's Terms of Service. The author is not responsible for any account suspensions.

## 📜 License

Licensed under the [GNU General Public License v3.0](LICENSE).

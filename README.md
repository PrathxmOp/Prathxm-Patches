<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Engine-Stockfish_16.1-4A90D9?style=for-the-badge&logo=chess.com&logoColor=white" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" />
</p>

<h1 align="center">♟️ Prathxm Patches</h1>

<p align="center">
  Custom Morphe patches for <b>Chess.com</b> on Android. Enables fully offline analysis powered by local Stockfish 16.1 NNUE, ad-free usage, locked bots bypass, and Lichess puzzles.
</p>

<p align="center">
  <a href="https://github.com/PrathxmOp/Prathxm-Patches/discussions"><img src="https://img.shields.io/badge/Discussions-Join_Community-6e5494?style=flat-square&logo=github" /></a>
  <a href="https://github.com/PrathxmOp/Prathxm-Patches/releases"><img src="https://img.shields.io/badge/Releases-Latest-green?style=flat-square&logo=github" /></a>
</p>

---

## 📖 About & Features

Prathxm Patches embeds a native **Stockfish 16.1 NNUE** chess engine directly into the Chess.com Android app for fully offline analysis and game reviews. All analysis features are programmatically disabled during live online matches to ensure fair play.

### 🌟 Key Features
- **Local Stockfish 16.1 & Offline Reviews**: Centipawn evaluation, Win/Draw/Loss tracking, move classification (Brilliant, Great, etc.), ELO estimation, and configurable bot strengths.
- **Offline Lichess Puzzles**: Complete puzzle journey map featuring millions of offline puzzles, streaking, and thematic practice.
- **Ad-Free UI & Unlocked Bots**: Banners, interstitial ads, and video promotions are removed. All Versus Bots are fully unlocked.
- **Panic Mode**: Toggle all overlays instantly.

---

## 🎮 Gestures & Panic Mode

Access features by interacting with the Chess.com logo on the main screen:
- **Tap & Hold** → Opens the settings menu.
- **Double-Tap** → Toggles **Panic Mode** (instantly hides/shows all overlays).

---


## 🩹 Patches

<!-- PATCHES_START -->
> **[v1.12.0-dev.2](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.12.0-dev.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay | 4.10.0 | 4.10.0-googleplay |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ad-Free](#ad-free) | Removes advertisements |  |
| [Clone Chess.com](#clone-chess-com) | Changes the package name to com.chess.prathxm, allowing the patched app to be installed side-by-side with the original Chess.com app. |  |
| [Custom Titles](#custom-titles) | Fetches and applies custom titles for users from a remote database. DM PrathxmOp to get yours for fun lol! |  |
| [Global Crash Handler](#global-crash-handler) | Catches uncaught exceptions and displays a custom crash screen with details to report issues. |  |
| [Lichess Puzzles](#lichess-puzzles) | Loads daily puzzles from Lichess and bypasses Chess.com puzzle premium limits. |  |
| [Local Stockfish Analysis](#local-stockfish-analysis) | Enables local Stockfish engine for post-game review & analysis. |  |
| [Unlock All Bots](#unlock-all-bots) | Unlocks all premium and restricted bots in the Versus Bots feature. |  |

</details>

<!-- PATCHES_END -->

---

## 🛠️ Installation

### Option 1 · Morphe Manager <sup>Recommended</sup>

1. Install [**Morphe Manager**](https://morphe.software) on your Android device.
2. Add this repository as a patch source:
   <p align="center">
     <a href="https://morphe.software/add-source?github=PrathxmOp/Prathxm-Patches"><b>➕ Add Patches to Morphe Manager</b></a>
   </p>
   Or manually add `https://github.com/PrathxmOp/Prathxm-Patches` under **Patch Sources**.
3. Select **Chess.com**, choose your patches, and tap **Patch**.

### Option 2 · Morphe CLI <sup>Advanced</sup>

1. Clone the repository and compile the patches bundle:
   ```bash
   ./gradlew patches:assemble
   ```
2. Apply the compiled patch bundle to your Chess.com APK using `morphe-cli`:
   ```bash
   java -jar morphe-cli.jar patch \
     -p patches/build/libs/patches-X.X.X.mpp \
     -o patched-chess.apk \
     "com.chess.apk"
   ```

---

## 💖 Support the Project

If you like these patches, consider supporting development!
- **🏅 Donate for a Custom Title**: Donors receive a custom title displayed on their profile. DM **PrathxmOp** after donating to claim yours.

<p align="center">
  <b>UPI:</b> <code>prathammishraop@ybl</code><br><br>
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=upi://pay?pa=prathammishraop@ybl%26pn=Prathxm%26cu=INR" alt="UPI QR Code" width="140" height="140" /><br>
  <a href="upi://pay?pa=prathammishraop@ybl&pn=Prathxm&cu=INR"><b>⚡ Tap to Donate via UPI</b></a>
</p>

---

## 💬 Community

Questions, feedback, or title claims? Reach out:
- [**GitHub Discussions**](https://github.com/PrathxmOp/Prathxm-Patches/discussions)
- [**Signal Private Message**](https://signal.me/#eu/5hn89XV1PsUQlPRc0WhEoUEh197WioxzFJj-CTXOGe1Boymy0-FCub3zwWXa_L3a)

---

## ⚠️ Disclaimer & License

- **Disclaimer**: For educational and personal use only. Usage may violate the terms of service. The author is not responsible for any account bans.
- **License**: Licensed under the [GNU General Public License v3.0](LICENSE).

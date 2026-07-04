<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Engine-Stockfish_16.1-4A90D9?style=for-the-badge&logo=chess.com&logoColor=white" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" />
</p>

<h1 align="center">♟️ Prathxm Patches</h1>

<p align="center">
  <i>"You expect me to be perfect but I am full of flaws."</i>
</p>

<p align="center">
  Custom Morphe patches for <b>Chess.com</b> — ad-free experience, fully offline post-game reviews powered by Stockfish, and a complete Lichess puzzle journey with millions of puzzles.
</p>

<p align="center">
  <a href="https://github.com/PrathxmOp/Prathxm-Patches/discussions"><img src="https://img.shields.io/badge/Discussions-Join_Community-6e5494?style=flat-square&logo=github" /></a>
  <a href="https://github.com/PrathxmOp/Prathxm-Patches/releases"><img src="https://img.shields.io/badge/Releases-Latest-green?style=flat-square&logo=github" /></a>
</p>

---

## 📖 About

Prathxm Patches embeds a native **Stockfish 16.1 NNUE** chess engine directly into Chess.com on Android. Perform post-game reviews and run deep analysis completely offline — no external servers, no subscriptions, no limits.

All analysis overlays are **automatically disabled during live online matches** to ensure strict fair-play compliance.

> [!NOTE]
> **Why is the package larger?**  
> The patched app is ~110 MB larger because it bundles the **Stockfish 16.1 NNUE** engine binaries locally, enabling fully offline analysis at any time.

---

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### 🔧 Engine & Analysis
*Available in offline & bot games only*

- **Local Stockfish 16.1** — Full-powered engine on your device
- **Offline Game Reviews** — Locally processed post-game analysis:
  - 🏷️ Move classifications (Brilliant, Great, Best, Excellent, Good, Inaccuracy, Mistake, Blunder)
  - 📊 Accuracy scores for both sides
  - 📈 Estimated ELO ratings with opening/tactical breakdowns
- **Configurable ELO Limits** — Adjust engine strength (1350–3190 ELO) for bot games

### 📊 Visual Overlays
- **Eval & WDL Bars** — Real-time centipawn/mate and Win/Draw/Loss tracking
- **Engine Arrows** — Best move and threat indicators on the board

</td>
<td width="50%" valign="top">

### 🧩 Lichess Puzzle Journey
- **Fully Offline Puzzles** — Millions of puzzles from the Lichess library
- **Journey Map** — Walk through levels on a premium styled board
- **Thematic Practice** — Filter by Opening, Middlegame, Endgame, or play Rush & Battle modes
- **Flexible Downloads** — Choose 20k, 50k, 100k, 500k, or all 6M+ puzzles
- **Streaks & Milestones** — Daily streaks, category stats, and progress tracking

### 💎 Quality of Life
- **Ad-Free UI** — Banners, interstitials, and video promotions removed
- **Side-by-Side Install** — Clone patch lets you keep the original app
- **Unlock All Bots** — Play against any restricted or locked bot for free

</td>
</tr>
</table>

---

## 🎮 How to Use

Once patched, access all features through two simple gestures on the Chess.com logo:

| Gesture | Action |
|:--------|:-------|
| **Tap & Hold** the Chess.com logo | Opens the **Prathxm Menu** — access engine settings, puzzle journey, analysis overlays, and all patch options. |
| **Double-Tap** the Chess.com logo | Activates **Panic Mode** — instantly disables all overlays and analysis. Double-tap again to re-enable everything. |

> [!TIP]
> Panic Mode is useful when you want to quickly toggle all features off without navigating through settings.

---

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.9.2-dev.1](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.9.2-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;5 patches total
<details open>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ad-Free & Local Analysis](#ad-free-local-analysis) | Removes advertisements, unlocks ad-free features, and enables local Stockfish engine for post-game review & analysis. |  |
| [Clone Chess.com](#clone-chess-com) | Changes the package name to com.chess.prathxm, allowing the patched app to be installed side-by-side with the original Chess.com app. |  |
| [Custom Titles](#custom-titles) | Fetches and applies custom titles for users from a remote database. DM PrathxmOp to get yours for fun lol! |  |
| [Lichess Puzzles](#lichess-puzzles) | Loads daily puzzles from Lichess and bypasses Chess.com puzzle premium limits. |  |
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

   Or manually add `https://github.com/PrathxmOp/Prathxm-Patches` in **Patch Sources**.

3. Select **Chess.com**, choose your patches, and tap **Patch**.
4. Install the output APK.

### Option 2 · Morphe CLI <sup>Advanced</sup>

**Prerequisites:** Android SDK, JDK 17+, [Morphe CLI](https://morphe.software)

1. Download Stockfish Android binaries from [official releases](https://stockfishchess.org/download/):
   - `arm64-v8a` → `extensions/extension/src/main/assets/stockfish/arm64-v8a/stockfish`
   - `armeabi-v7a` → `extensions/extension/src/main/assets/stockfish/armeabi-v7a/stockfish`

2. Build and apply:
   ```bash
   # Assemble the patches bundle
   ./gradlew patches:assemble

   # Apply to Chess.com APK
   java -jar morphe-cli.jar patch \
     -p patches/build/libs/patches-1.0.0.mpp \
     -o patched-chess.apk \
     "com.chess_X.X.X.apk"
   ```

---

## 💖 Support the Project

If these patches improved your chess experience, consider supporting the development.

> [!TIP]
> **🏅 Donate to get a Custom Title** — Donors receive a personalized title that is displayed on their profile across all patched apps. DM **PrathxmOp** after donating to claim yours!

<p align="center">
  <b>UPI:</b> <code>prathammishraop@ybl</code><br><br>
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=upi://pay?pa=prathammishraop@ybl%26pn=Prathxm%26cu=INR" alt="UPI QR Code" width="160" height="160" /><br>
  <a href="upi://pay?pa=prathammishraop@ybl&pn=Prathxm&cu=INR"><b>⚡ Tap to Donate via UPI</b></a>
</p>

---

## 💬 Community

Questions, feedback, or feature requests? Use **GitHub Discussions** — not Issues.  
To claim your custom title or for direct support, reach out on **Signal**.

<p align="center">
  <a href="https://github.com/PrathxmOp/Prathxm-Patches/discussions"><b>💬 GitHub Discussions</b></a>
  &nbsp;&nbsp;•&nbsp;&nbsp;
  <a href="https://signal.me/#eu/5hn89XV1PsUQlPRc0WhEoUEh197WioxzFJj-CTXOGe1Boymy0-FCub3zwWXa_L3a"><b>📩 Signal DM</b></a>
</p>

---

## ⚠️ Disclaimer

These patches are for **educational and personal use only**. Usage may violate the host application's Terms of Service. The author assumes no responsibility for account suspensions or other consequences.

## 📜 License

Licensed under the [GNU General Public License v3.0](LICENSE).

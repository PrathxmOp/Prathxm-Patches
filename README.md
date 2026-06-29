# ♟️ Prathxm Patches

Custom Morphe patches for **Chess.com**, integrating the powerful Stockfish chess engine for real-time move analysis and more.

## ❓ About

Prathxm Patches brings a native Stockfish chess engine directly into Chess.com on Android. It hooks into the live game board and displays ranked best-move arrows, giving you real-time engine suggestions without ever leaving the app. Features like premium unlock, ads removal, and configurable engine parameters are also included via an in-game settings menu.

## 🩹 Patches List

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release.
     
     If you wish for the patches list to be collapsed, then remove the word 'EXPANDED' from the comment tag above.

     If you wish to manually keep this list updated then remove the PATCHES_START and PATCHES_END 
     comment blocks entirely. -->

### ♟️ Stockfish Engine
**Compatible app:** Chess.com (`com.chess`)  
**Supported versions:** `4.9.49`, `4.9.49-googleplay`

Integrates the Stockfish chess engine for real-time best-move analysis. Displays suggested moves as color-coded arrows on the board.

#### Features

| Feature | Details |
|---|---|
| 🧠 **Real-time Analysis** | Stockfish runs in the background on every position change |
| 🏹 **Multi-arrow Display** | Shows up to 5 best moves ranked by engine evaluation |
| 🎨 **Color-coded Arrows** | 1st: Green, 2nd: Blue, 3rd: Orange, 4th: Purple, 5th+: Red |
| 📉 **Opacity Ranking** | Higher-ranked moves appear more opaque for quick reading |
| ⚙️ **Analysis Depth** | Configurable search depth (default: 14) |
| 🔢 **MultiPV Support** | Configurable number of top moves to show (default: 1) |
| 👤 **My Side Only** | Optionally show arrows only on your turn |
| 💪 **Strength Limiting** | Optionally cap engine ELO for a more human-like experience |
| 👑 **Premium Unlock** | Unlocks Chess.com Diamond/Premium features |
| 🚫 **Ads Removed** | Removes in-app advertisements |
| 👁️ **Arrow Toggle** | Double-tap the top area of the screen to show/hide arrows |
| ⚙️ **Settings Menu** | Long-press the top area of the screen to open engine settings |

#### How to use

1. Install the patched APK.
2. Open Chess.com and start any game.
3. Stockfish arrows will appear automatically on the board.
4. **Long press** the top area of the screen → opens **Settings menu**.
5. **Double tap** the top area of the screen → **toggles arrows** on/off.

### 💎 Premium Unlock & Limitations

While the patch unlocks the **Diamond** premium status locally and integrates a custom engine, certain Chess.com features are strictly gated on their backend servers and cannot be bypassed. Below is a breakdown of what works and what does not:

#### ➕ Custom Additions (Added by this Patch)
* **Stockfish Engine Integration:** Computes evaluations and best-move arrows locally on your device's native CPU. This is a custom addition that bypasses server analysis limitations entirely.

#### 🟢 What is Unlocked Locally (via Diamond Status)
* **Ads Removal (100% working):** Bypasses all client-side ad verification checks, giving you an entirely ad-free experience.
* **Premium Cosmetic Themes (100% working):** Unlocks premium boards, custom pieces, and themes stored within the app assets.
* **Diamond Branding:** Visual indicators, premium status text, and layout customizations are fully unlocked.

#### 🔴 Server-Side Limitations (Cannot be Unlocked)
* **Game Review (Coach Comments & Accuracy):** The chess analysis and commentary generation are performed on Chess.com's cloud servers. Bypassing client-side checks to request a cloud analysis results in failed network calls or empty commentary because the backend verifies your account subscription server-side.
  * *Tip: The custom Stockfish engine integration effectively replaces the need for cloud-based Game Reviews by showing real-time engine paths and evaluations directly on the board.*
* **Daily Puzzle Limit:** Next puzzle IDs and limits are controlled dynamically by the server.
* **Video Lessons / Guide Content:** Video streams are protected by server-side authentication headers and will return access denied if the account is basic on the server.

&nbsp;

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
   https://github.com/prathxm/Prathxm-Patches
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

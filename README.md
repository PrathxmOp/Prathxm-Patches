# ♟️ Prathxm Patches

Custom Morphe patches for **Chess.com**, integrating the powerful Stockfish chess engine for real-time move analysis and more.

## ❓ About

Prathxm Patches brings a native Stockfish chess engine directly into Chess.com on Android. It hooks into the live game board and displays ranked best-move arrows, giving you real-time engine suggestions without ever leaving the app. Features like premium unlock, ads removal, and configurable engine parameters are also included via an in-game settings menu.

## 🩹 Patches List

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/PrathxmOp/Prathxm-Patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Chess.com&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.9.49 | 4.9.49-googleplay |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Stockfish Engine Integration](#stockfish-engine-integration) | Injects the Stockfish chess engine into Chess.com for real-time best-move analysis. Shows ranked, color-coded best-move arrows on the board (Green → Blue → Orange → Purple → Red) along with premium Diamond feature unlock, ads removal, side-aware turn analysis, and customizable settings via in-game menu. |  |

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

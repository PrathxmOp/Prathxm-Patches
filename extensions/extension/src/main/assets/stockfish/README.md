# Stockfish Native Binaries

Place the extracted Stockfish executables here BEFORE building:

## Required files

```
arm64-v8a/stockfish      ← from stockfish-android-armv8-dotprod.tar
armeabi-v7a/stockfish    ← from stockfish-android-armv7-neon.tar
```

## How to extract

```bash
# arm64 (modern phones)
tar -xf stockfish-android-armv8-dotprod.tar
cp stockfish arm64-v8a/stockfish

# armv7 (older phones)
tar -xf stockfish-android-armv7-neon.tar
cp stockfish armeabi-v7a/stockfish
```

## Verify SHA-256 hashes (from Stockfish release page)

- armv8-dotprod: 8f7116d3f1a7004a6581d4fb0c1ff891ce095bab6d45e52f1578897cf23b61b5
- armv7-neon:    672fbd7533d15fc70dae82df52c1185236975d059a585aa65eda960f1a3266ea

After placing the binaries, run:
  ./gradlew :extensions:extension:assembleRelease

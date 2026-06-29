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

- armv8-dotprod: 0747e48ef4b983b607c6b35ea04239dfbb6adf91210ac42b6e865f12c91ebf51
- armv7-neon:    07a6d8af49502d09494f80d2d544f4537400cce715fd9d6f05cf6db9f9c7bcf3

After placing the binaries, run:
  ./gradlew :extensions:extension:assembleRelease

#!/bin/bash
set -e

# Version of stockfish to download
SF_VERSION="sf_18"

echo "Downloading Stockfish ${SF_VERSION} binaries..."

# Create clean temp directory
TMP_DIR=$(mktemp -d)

# Ensure temp directory is deleted on exit
trap 'rm -rf "$TMP_DIR"' EXIT

cd "$TMP_DIR"

# Download arm64-v8a binary
echo "Downloading arm64-v8a..."
curl -L -o arm64.tar "https://github.com/official-stockfish/Stockfish/releases/download/${SF_VERSION}/stockfish-android-armv8-dotprod.tar"
tar -xf arm64.tar

# Download armeabi-v7a binary
echo "Downloading armeabi-v7a..."
curl -L -o armv7.tar "https://github.com/official-stockfish/Stockfish/releases/download/${SF_VERSION}/stockfish-android-armv7-neon.tar"
tar -xf armv7.tar

# Back to project root
cd - > /dev/null

# Destination directories
PATHS=(
    "patches/src/main/resources/stockfish"
    "extensions/extension/src/main/assets/stockfish"
)

for path in "${PATHS[@]}"; do
    echo "Placing binaries in $path..."
    mkdir -p "$path/arm64-v8a"
    mkdir -p "$path/armeabi-v7a"
    
    cp "$TMP_DIR/stockfish/stockfish-android-armv8-dotprod" "$path/arm64-v8a/stockfish"
    cp "$TMP_DIR/stockfish/stockfish-android-armv7-neon" "$path/armeabi-v7a/stockfish"
    
    chmod +x "$path/arm64-v8a/stockfish"
    chmod +x "$path/armeabi-v7a/stockfish"
done

echo "Stockfish binaries installed successfully!"

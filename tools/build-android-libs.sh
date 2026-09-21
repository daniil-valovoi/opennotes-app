#!/usr/bin/env bash
# Cross-compiles the Rust bridge for Android and drops the .so files where Gradle
# picks them up (app/src/main/jniLibs/<abi>/libsdocx_bridge.so).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"
out="$root/app/src/main/jniLibs"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n 1)"
    export ANDROID_NDK_HOME
fi

if ! command -v cargo-ndk >/dev/null 2>&1; then
    echo "cargo-ndk is required: cargo install cargo-ndk" >&2
    exit 1
fi

rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

cd "$here/sdocx-bridge"
# arm64: phones, armv7: old devices, x86_64: emulator.
cargo ndk \
    -t arm64-v8a \
    -t armeabi-v7a \
    -t x86_64 \
    -o "$out" \
    build --release

echo "Native libraries written to $out"
find "$out" -name 'libsdocx_bridge.so' -exec ls -lh {} \;

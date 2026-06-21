#!/usr/bin/env bash
# Cross-compile the SAC precision gesture helper for arm64-v8a devices.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/src/main/cpp/gesture-helper.cpp"
OUT_DIR="$ROOT/src/main/assets/helper/arm64-v8a"
OUT="$OUT_DIR/gesture-helper"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* 2>/dev/null | sort -V | tail -1)}"
CLANG="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang++"

if [[ ! -x "$CLANG" ]]; then
  CLANG="$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android*-clang++ 2>/dev/null | sort -V | tail -1)"
fi

if [[ ! -x "$CLANG" ]]; then
  echo "error: NDK clang not found (ANDROID_NDK_HOME=$NDK)" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
"$CLANG" -std=c++17 -O2 -static-libstdc++ -pie -o "$OUT" "$SRC" -llog
echo "built $OUT ($(wc -c <"$OUT") bytes)"

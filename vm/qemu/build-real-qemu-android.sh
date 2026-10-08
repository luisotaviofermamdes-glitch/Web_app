#!/usr/bin/env bash
set -euo pipefail

# Builds the real QEMU engine used by Web_app.
# The upstream Android build helper performs the NDK cross compilation.
# This script is intentionally a build step, not a fake placeholder binary.

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$ROOT/.qemu-android-build"
BUILDER="$WORK/qemu-ndk-sh"
NDK_VERSION="29.0.14206865"
NDK="$ANDROID_HOME/ndk/$NDK_VERSION"

if [ ! -d "$NDK" ]; then
  echo "Android NDK $NDK_VERSION is required. Install it before running this script." >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK"

git clone --depth 1 https://github.com/wasdwasd0105/qemu-ndk-sh.git "$BUILDER"

# The upstream helper expects this conventional Linux NDK location.
ln -s "$NDK" "$HOME/android-ndk-r29"

cd "$BUILDER"
chmod +x 2_build_qemu_android.sh

# The upstream helper's generated wrapper was failing with ENOENT on the
# GitHub Actions runner. Keep the wrapper, but place it in /tmp so it is
# independent of the cloned build tree and uses an absolute Bash interpreter.
# The helper still restricts package discovery through PKG_CONFIG_LIBDIR.
PATCHED_WRAPPER="/tmp/webapp-android-pkg-config"
sed -i 's|^WRAP_PC=.*|WRAP_PC="/tmp/webapp-android-pkg-config"|' 2_build_qemu_android.sh
sed -i 's|^#!/usr/bin/env bash$|#!/bin/bash|' 2_build_qemu_android.sh

NDK_PATH="$NDK" API_LEVEL=31 APP_ABI=arm64-v8a JOBS="$(nproc)" ./2_build_qemu_android.sh

# Locate the actual x86_64 guest emulator produced by the build.
QEMU_BIN="$(find "$WORK" -type f -name 'qemu-system-x86_64' -perm -u+x | head -n1)"
if [ -z "$QEMU_BIN" ]; then
  QEMU_BIN="$(find "$BUILDER" -type f -name 'qemu-system-x86_64' -perm -u+x | head -n1)"
fi

if [ -z "$QEMU_BIN" ]; then
  echo "ERROR: real qemu-system-x86_64 was not produced." >&2
  exit 1
fi

mkdir -p "$ROOT/app/src/main/assets/vm/bin"
cp "$QEMU_BIN" "$ROOT/app/src/main/assets/vm/bin/qemu-system-x86_64"
chmod 755 "$ROOT/app/src/main/assets/vm/bin/qemu-system-x86_64"

# Copy firmware/resources when the builder produced them.
FIRMWARE_DST="$ROOT/app/src/main/assets/vm/firmware"
mkdir -p "$FIRMWARE_DST"
find "$BUILDER" -type f \( -name '*.fd' -o -name '*.rom' \) -print0 2>/dev/null | while IFS= read -r -d '' f; do
  cp "$f" "$FIRMWARE_DST/" || true
done

file "$ROOT/app/src/main/assets/vm/bin/qemu-system-x86_64"
stat -c 'QEMU bytes: %s' "$ROOT/app/src/main/assets/vm/bin/qemu-system-x86_64"

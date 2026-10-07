#!/usr/bin/env bash
set -euo pipefail

# Builds the Android-native QEMU runtime used by Web_app.
# The upstream/downstream Android build is kept outside this repository so the
# APK does not contain a fake placeholder executable.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${ROOT}/.qemu-build"
OUT="${ROOT}/app/src/main/jniLibs/arm64-v8a"
QEMU_REPO="https://github.com/wasdwasd0105/qemu-ndk-sh.git"

mkdir -p "$WORK" "$OUT"
cd "$WORK"

if [ ! -d qemu-ndk-sh ]; then
  git clone --depth 1 "$QEMU_REPO" qemu-ndk-sh
fi

cd qemu-ndk-sh

BUILD_SCRIPT=""
for candidate in 2_build_qemu_android.sh build_qemu_android.sh; do
  if [ -f "$candidate" ]; then BUILD_SCRIPT="$candidate"; break; fi
done
if [ -z "$BUILD_SCRIPT" ]; then
  BUILD_SCRIPT="$(find . -maxdepth 3 -type f -name '*build*qemu*android*.sh' | head -n 1 || true)"
fi
if [ -z "$BUILD_SCRIPT" ]; then
  echo "QEMU Android build script not found" >&2
  exit 2
fi

chmod +x "$BUILD_SCRIPT"
"./$BUILD_SCRIPT"

# Locate the resulting Android QEMU binary. The exact output path can vary
# between revisions of the build project.
QEMU_BIN="$(find . -type f \( -name 'qemu-system-x86_64' -o -name 'qemu-system-aarch64' \) -perm -u+x | head -n 1 || true)"
if [ -z "$QEMU_BIN" ]; then
  echo "QEMU executable was not produced" >&2
  exit 3
fi

cp "$QEMU_BIN" "$OUT/qemu-system-x86_64"
chmod 755 "$OUT/qemu-system-x86_64"

# Fail loudly if this is accidentally a tiny placeholder.
SIZE="$(stat -c '%s' "$OUT/qemu-system-x86_64")"
if [ "$SIZE" -lt 1048576 ]; then
  echo "Refusing to package a QEMU binary smaller than 1 MiB: $SIZE bytes" >&2
  exit 4
fi

echo "QEMU runtime: $OUT/qemu-system-x86_64 ($SIZE bytes)"

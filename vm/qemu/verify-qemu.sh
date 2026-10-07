#!/usr/bin/env bash
set -euo pipefail

QEMU="${1:-app/src/main/assets/vm/qemu-system-x86_64}"

if [ ! -f "$QEMU" ]; then
  echo "Missing QEMU binary: $QEMU" >&2
  exit 1
fi

file "$QEMU"

if ! file "$QEMU" | grep -Eq 'ELF 64-bit.*ARM aarch64|ELF 64-bit.*ARM'; then
  echo "ERROR: QEMU must be a real ARM64 ELF executable for arm64-v8a." >&2
  exit 1
fi

SIZE=$(stat -c%s "$QEMU")
if [ "$SIZE" -lt 1048576 ]; then
  echo "ERROR: QEMU binary is suspiciously small: $SIZE bytes" >&2
  exit 1
fi

echo "QEMU verification OK: $SIZE bytes"

# QEMU Android build specification

This file is the build contract for the real VM engine used by Web_app.

## Required output

The Android APK must contain a real, executable QEMU userspace binary for `arm64-v8a` with the `x86_64-softmmu` target enabled. The phone runs QEMU natively as ARM64; QEMU emulates the x86_64 guest CPU.

Required VM capabilities:

- x86_64 machine emulation
- `q35` machine
- KVM when the Android device exposes a compatible virtualization interface; otherwise TCG fallback
- VNC or another Android-display-compatible backend
- USB tablet/mouse input
- user-mode networking/SLIRP
- raw and qcow2 disks
- ISO/CD-ROM boot
- UEFI firmware support
- TPM support path for Windows 11

## Packaging contract

The engine must not be represented by a dummy executable. CI must verify that the QEMU file is an ARM64 ELF executable before packaging it.

Suggested APK layout:

```text
app/src/main/assets/vm/qemu-system-x86_64
app/src/main/assets/vm/firmware/
app/src/main/assets/vm/licenses/
```

The application copies these files from APK assets to its private files directory on first run and marks the QEMU file executable.

## Display contract

The WebView cannot directly consume a raw VNC TCP socket. The final implementation therefore needs one of these:

1. native QEMU/SDL Android surface and Android input bridge; or
2. QEMU VNC + an Android-side TCP-to-WebSocket proxy + noVNC client bundled as app assets.

An `<iframe src="vnc://...">` or a normal HTTP iframe is not a valid implementation.

## Windows guest

The user supplies their own Windows ISO. The application must not silently replace it with a fixed ISO. Windows licensing and activation remain the user's responsibility.

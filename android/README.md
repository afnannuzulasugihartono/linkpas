# LINKPAS Android (TWA)

This directory contains the Android Trusted Web Activity wrapper for LINKPAS.

Canonical identity:
- App name: `LINKPAS`
- Package: `id.barangpas.linkpas`
- Host: `linkpas.vercel.app`
- Version: `0.1.0-beta` (`versionCode` 1)

The canonical Bubblewrap configuration is `twa-manifest.json`. The project structure follows the current Bubblewrap / Android Browser Helper TWA pattern. Android Browser Helper is pinned to `2.7.3`.

Signing material is intentionally NOT stored in Git. `linkpas-release.keystore`, `*.jks`, `*.keystore`, `key.properties`, and local build output are ignored. Release signing is deferred to checkpoint A4.

Checkpoint A3 debug build command is `gradle --no-daemon :app:assembleDebug` from `android/`. Expected output: `android/app/build/outputs/apk/debug/app-debug.apk`.

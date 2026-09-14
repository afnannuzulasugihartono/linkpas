# LINKPAS Android (TWA)

This directory contains the Android Trusted Web Activity wrapper for LINKPAS.

Canonical identity:
- App name: `LINKPAS`
- Package: `id.barangpas.linkpas`
- Host: `linkpas.vercel.app`
- Version: `0.1.0-beta` (`versionCode` 1)

The canonical Bubblewrap configuration is `twa-manifest.json`. The project structure follows the current Bubblewrap / Android Browser Helper TWA pattern. Android Browser Helper is pinned to `2.7.3`.

Signing material is intentionally NOT stored in Git. `linkpas-release.keystore`, `*.jks`, `*.keystore`, `key.properties`, and local build output are ignored.

A3 debug build command: `gradle --no-daemon :app:assembleDebug`.

A5 release flow builds `android/app/build/outputs/apk/release/app-release-unsigned.apk` in CI, then signs that release artifact outside Git with the private LINKPAS release key established in A4.

# A6a split for current release-candidate validation

The original A6a checkpoint was split before execution after the signed A5 APK was found to predate D4a/D4b Android-wrapper changes.

## A6a1 — Rebuild current signed release candidate

Scope:
- Build an unsigned release APK from the current `main` Android source after D4/D5.
- Sign it with the existing A4 release signing identity outside Git.
- Verify signature/certificate identity, package/version, checksum, and presence of the native D4 diagnostics code.
- Do not perform install/TWA acceptance in this checkpoint.

Acceptance:
- Release APK is rebuilt from Android source equivalent to current `main`.
- APK is signed with the same release certificate fingerprint already published in Digital Asset Links.
- Package is `id.barangpas.linkpas` and version is `0.1.0-beta` / code `1`.
- APK signature verification passes.
- A new SHA-256 checksum is recorded.
- APK contains the native D4 diagnostics implementation and `ManageDataLauncherActivity` manifest declaration.
- Signing keystore/password remain outside Git and are not exposed in logs.

## A6a2 — Install, App Links, and verified TWA acceptance

Scope:
- Fresh-install the A6a1 signed release candidate on Android.
- Verify package/version and normal launch.
- Force/recheck App Links/domain verification.
- Distinguish verified TWA/fullscreen from browser/custom-tab fallback using Android runtime evidence, not appearance alone.

Acceptance:
- Signed release candidate installs successfully.
- Normal launch succeeds without fatal wrapper crash.
- `linkpas.vercel.app` is verified for the package.
- Evidence supports verified TWA/fullscreen rather than browser/custom-tab fallback.
- Otherwise A6a2 remains BLOCKED and APK readiness is not claimed.

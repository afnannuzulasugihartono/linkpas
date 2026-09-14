# LINKPAS APK Beta Roadmap

Goal: produce a signed, installable LINKPAS Android APK using TWA, without changing the product scope.

Each checkpoint is intentionally sized for about 15-20 minutes, with a hard stop at 25 minutes.

## A1 — Audit and prepare PWA

Scope:
- Audit manifest, icons, service worker, offline shell, start URL, scope, and production URL.
- Fix only small TWA-blocking PWA issues that are safe and obvious.

Acceptance:
- Production manifest is reachable and valid.
- Required 192x192 and 512x512 icons are reachable.
- Service worker is reachable and registered by the app.
- PWA launches from `https://linkpas.vercel.app/` without a blocking error.
- Any remaining blocker is documented before advancing.

Target: 20-25 minutes.

## A2 — Create Android/TWA project

Scope:
- Lock Android identity.
- Generate the Bubblewrap/TWA project under `android/`.

Locked defaults unless repository reality requires a correction:
- App name: `LINKPAS`
- Package: `id.barangpas.linkpas`
- Host: `linkpas.vercel.app`
- Initial version name: `0.1.0-beta`

Acceptance:
- Android/TWA project exists in `android/`.
- Package/host/app name are consistent.
- No secret or signing material is committed.

Target: 20-25 minutes.

## A3 — Build debug APK

Scope:
- Resolve only build-environment issues required for the first debug build.
- Build the TWA debug APK.

Acceptance:
- Debug APK is produced successfully.
- Build command and resulting APK path are recorded.
- No unrelated product code changes.

Target: 15-20 minutes.

## A4 — Signing identity and Digital Asset Links

Scope:
- Establish the release signing identity outside Git.
- Obtain the signing certificate SHA-256 fingerprint.
- Add `/.well-known/assetlinks.json` for `id.barangpas.linkpas`.

Acceptance:
- Signing key exists outside tracked repository files.
- SHA-256 certificate fingerprint is known.
- `assetlinks.json` contains the correct package and fingerprint.
- Production URL for `assetlinks.json` returns HTTP 200 after deploy.

Target: 20-25 minutes.

## A5 — Build signed release APK

Scope:
- Build the first signed release APK.
- Record version and checksum.

Acceptance:
- `LINKPAS-v0.1.0-beta.apk` (or equivalent release filename) is produced.
- APK signature verifies.
- Package name is `id.barangpas.linkpas`.
- Version name/code are recorded.
- SHA-256 checksum of the APK is recorded.

Target: 15-20 minutes.

## A6 — Android acceptance test

Scope:
Test the signed APK on Android for:
- install;
- launch;
- verified TWA/fullscreen behavior;
- paste/process links;
- deduplication;
- Free 50-link limit;
- copy/export behavior;
- offline core behavior.

Acceptance:
- Every item is PASS, or blockers are recorded precisely.
- No claim of APK readiness if TWA verification falls back to a browser/custom tab unexpectedly.

Target: 20-25 minutes.

## A7 — Fix release-blocking APK issues

Scope:
- Fix only blockers discovered by A6.
- Rebuild/retest only what is necessary.

Acceptance:
- All A6 release blockers are resolved or checkpoint is explicitly BLOCKED.
- Signed APK passes the affected acceptance checks.

If A6 has no release blockers, this checkpoint may complete as a verified no-op.

Target: 20-25 minutes.

## A8 — Publish APK Beta release

Scope:
- Create the official Beta release artifact and release notes.
- Ensure the APK can be retrieved from a stable distribution location.

Acceptance:
- Signed Beta APK is attached/published in the chosen release location.
- Release includes version and SHA-256 checksum.
- Production web app remains healthy.
- `PROJECT_STATE.json` marks milestone `APK_BETA` complete.

Target: 15-20 minutes.

---

## Continuation rule

When the user says **"lanjutkan"**, execute the current checkpoint from `PROJECT_STATE.json` fully, then stop. Never execute two checkpoint IDs in one continuation turn.
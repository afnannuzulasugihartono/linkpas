# LINKPAS APK Beta Roadmap

Goal: produce a signed, installable LINKPAS Android APK using TWA, with an internal Beta Diagnostics layer that makes failures observable before final Android acceptance testing.

Every checkpoint is intentionally sized for **12-15 minutes**, with a **hard stop at 18 minutes**. No checkpoint should be planned to consume 20 minutes or more. If a checkpoint is too large, split it before implementation.

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

Target: 12-15 minutes. Hard stop: 18 minutes.

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

Target: 12-15 minutes. Hard stop: 18 minutes.

## A3 — Build debug APK

Scope:
- Resolve only build-environment issues required for the first debug build.
- Build the TWA debug APK.

Acceptance:
- Debug APK is produced successfully.
- Build command and resulting APK path are recorded.
- No unrelated product code changes.

Target: 12-15 minutes. Hard stop: 18 minutes.

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

Target: 12-15 minutes. Hard stop: 18 minutes. Split into A4a/A4b before execution if environment/setup makes this too large.

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

Target: 12-15 minutes. Hard stop: 18 minutes.

# Beta Diagnostics interlude

The user explicitly requested observability inside LINKPAS Beta so most failures can be diagnosed from Supabase without requiring screenshots. These checkpoints are inserted after A5 and before A6. They are Beta-only infrastructure and must not expand LINKPAS product scope beyond diagnostics.

Privacy baseline for D1-D5:
- Do not collect affiliate-link contents, clipboard contents, buyer email, license keys, passwords, message contents, or other user content by default.
- Collect only technical metadata needed to diagnose failures.
- Never commit Supabase service-role keys or other secrets to Git.
- Diagnostic upload failures must never block LINKPAS core local functionality.

## D1 — Supabase diagnostics backend

Scope:
- Create the minimal backend for Beta diagnostic reports.
- Add a table such as `linkpas_diagnostic_reports` with technical fields for report ID, timestamps, app/build version, platform, error type/message/stack, page, network/service-worker state, breadcrumbs, and structured diagnostic metadata.
- Provide a safe ingestion path suitable for the public Beta app while keeping privileged database access server-side.
- Preserve a readable path through the connected Supabase tooling so ChatGPT can inspect reports when the user says `cek LINKPAS`.

Acceptance:
- Diagnostic table exists with RLS enabled.
- Public clients cannot arbitrarily read diagnostic rows.
- Diagnostic ingestion works without exposing a service-role key in the app.
- One sanitized test report can be written and read back through authorized Supabase tooling.
- Schema and privacy exclusions are documented.

Target: 12-15 minutes. Hard stop: 18 minutes.

## D2 — PWA error collector and breadcrumbs

Scope:
- Add a Beta-only diagnostic collector to the LINKPAS PWA.
- Capture `window.onerror`, `unhandledrejection`, selected important fetch failures, service-worker state/failures, online/offline state, app/build version, and a bounded rolling breadcrumb buffer.
- Send sanitized reports asynchronously to the D1 ingestion path.
- Ensure telemetry failure is non-blocking.

Acceptance:
- A controlled JavaScript error creates one sanitized diagnostic report in Supabase.
- An unhandled promise rejection creates one sanitized report.
- Breadcrumbs are bounded and contain only event names/technical state, not pasted link contents or secrets.
- Core LINKPAS processing still works if Supabase diagnostics is unavailable.
- Existing web tests/build remain green or any blocker is recorded precisely.

Target: 12-15 minutes. Hard stop: 18 minutes.

## D3 — Hidden Beta Diagnostics panel

Scope:
- Add a hidden diagnostics surface inside LINKPAS Beta, not a normal user-facing feature.
- Expose technical status such as app version, package/domain, online state, service-worker status, last diagnostic error, and last report ID.
- Add a manual `Kirim laporan diagnostik` action that sends sanitized current state.
- Use a low-friction hidden entry gesture such as repeated taps on the version label.

Acceptance:
- Diagnostics UI is hidden during normal usage.
- Hidden entry reliably opens the panel.
- Manual report produces a report ID visible in the panel and stored in Supabase.
- Panel does not reveal secrets, raw license keys, affiliate-link contents, or clipboard contents.
- Normal LINKPAS workflow remains unchanged when the panel is never opened.

Target: 12-15 minutes. Hard stop: 18 minutes.

## D4a — Android/TWA native diagnostics implementation and build

Scope:
- Add minimal native diagnostics to the Android wrapper for startup and wrapper-level failures the PWA cannot observe.
- Record only technical metadata: app version/package, sanitized launch host/path, selected browser provider, startup stage, and uncaught native exception metadata.
- Persist a pending uncaught-exception report locally and retry it asynchronously on next startup.
- Add a Beta-only controlled diagnostic trigger that can be invoked by Android test tooling without user content.
- Keep all network reporting best-effort and non-blocking.

Acceptance:
- Native diagnostic code is integrated into `LauncherActivity` without changing normal TWA launch behavior.
- Public diagnostic endpoint/publishable key may be present; no service-role key, signing secret, credential, clipboard data, affiliate-link content, license value, or device identifier is added.
- Transport failure is covered by a test and returns safely without throwing into app startup.
- Sanitization is covered by a test.
- Release APK build succeeds with native diagnostics included.
- Runtime Android/Supabase emission is explicitly deferred to D4b; D4a must not claim it.

Target: 12-15 minutes. Hard stop: 18 minutes.

## D4b — Android runtime diagnostic emission and Supabase validation

Scope:
- Run the D4a Beta diagnostic trigger on an Android emulator or device.
- Verify the native Android event reaches the D1 ingestion path and read the exact report back through authorized Supabase tooling.
- Confirm normal TWA startup remains usable after diagnostics initialization.
- Document which TWA/App Links conditions still require ADB/logcat rather than app telemetry.

Acceptance:
- A controlled native diagnostic event emitted by the Android runtime appears in Supabase with `source=android` and a stable report ID.
- Stored report contains only sanitized technical metadata and no signing secrets, credentials, user content, raw clipboard data, license values, or device identifiers.
- Reporting failure is non-blocking by D4a test evidence, and normal Android startup is not blocked by diagnostics initialization.
- Limitations that still require ADB/logcat are documented.

Target: 12-15 minutes. Hard stop: 18 minutes.

## D5 — End-to-end ChatGPT diagnostics workflow and ADB fallback

Scope:
- Validate the full path: LINKPAS Beta failure → Supabase report → ChatGPT/Supabase inspection.
- Define the operational command semantics for `cek LINKPAS`: inspect the newest relevant reports, correlate breadcrumbs/error metadata, and report likely file/function/root cause without guessing.
- Create a one-click Windows ADB diagnostic helper such as `LINKPAS-Debug.bat` for cases where telemetry is insufficient.
- The helper should collect only the needed package/App Links/logcat diagnostics into a shareable debug bundle and avoid collecting unrelated device content.

Acceptance:
- A controlled Beta failure is visible through Supabase and can be inspected from ChatGPT without a screenshot.
- The latest report has a stable report ID and enough metadata to identify the failure class.
- `LINKPAS-Debug.bat` (or equivalent) is documented and can produce a bounded diagnostic bundle when ADB is available.
- The workflow clearly distinguishes telemetry-visible errors from cases requiring ADB/logcat.
- No automatic claim is made that ChatGPT can see the user's phone screen or receive unsolicited messages into an open chat.

Target: 12-15 minutes. Hard stop: 18 minutes.

## A6 — Android acceptance test

Prerequisite: D1-D5 complete so failures encountered during device testing can be diagnosed without relying on screenshots alone.

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
- Diagnostic reports are consulted when a failure occurs.
- No claim of APK readiness if TWA verification falls back to a browser/custom tab unexpectedly.

Target: 12-15 minutes. Hard stop: 18 minutes. Split the test matrix before execution if all checks cannot reasonably fit.

## A7 — Fix release-blocking APK issues

Scope:
- Fix only blockers discovered by A6.
- Rebuild/retest only what is necessary.

Acceptance:
- All A6 release blockers are resolved or checkpoint is explicitly BLOCKED.
- Signed APK passes the affected acceptance checks.

If A6 has no release blockers, this checkpoint may complete as a verified no-op.

Target: 12-15 minutes. Hard stop: 18 minutes. If multiple unrelated blockers exist, split A7 before implementation.

## A8 — Publish APK Beta release

Scope:
- Create the official Beta release artifact and release notes.
- Ensure the APK can be retrieved from a stable distribution location.

Acceptance:
- Signed Beta APK is attached/published in the chosen release location.
- Release includes version and SHA-256 checksum.
- Production web app remains healthy.
- `PROJECT_STATE.json` marks milestone `APK_BETA` complete.

Target: 12-15 minutes. Hard stop: 18 minutes.

---

## Continuation rule

When the user says **"lanjutkan"**, execute the current checkpoint from `PROJECT_STATE.json` fully, validate it, update state, then stop. Never execute two checkpoint IDs in one continuation turn.
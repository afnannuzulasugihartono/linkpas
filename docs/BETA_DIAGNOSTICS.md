# LINKPAS Beta Diagnostics

This document describes the Beta-only diagnostics backend introduced before Android acceptance testing.

## Backend

- Supabase project: `BarangPas` (`ignmxvommfnkllcpliuw`)
- Table: `public.linkpas_diagnostic_reports`
- Edge Function: `linkpas-diagnostics`
- Function URL: `https://ignmxvommfnkllcpliuw.supabase.co/functions/v1/linkpas-diagnostics`
- Public app callers use the project's publishable/anon API key only. The service-role credential is read only inside the Edge Function environment and is never shipped in LINKPAS or committed to Git.

The table has Row Level Security enabled and no public read/write policies. `anon` and `authenticated` table privileges are explicitly revoked. Public Beta clients therefore cannot directly read diagnostic rows; they can only submit sanitized reports through the Edge Function.

## Stored fields

Each report may contain only technical metadata:

- `report_id`, `created_at`, `source`
- app/build version and package name
- platform/platform version/browser
- error type/message/stack
- page origin/path without query/hash
- online state and service-worker state
- up to 20 bounded breadcrumbs containing only `event`, `at`, and `state`
- allow-listed diagnostic metadata such as app stage, TWA status, browser provider, HTTP status, function/file/line, release channel, and launch URL host/path

The ingestion function caps request size at 32 KiB, truncates text fields, strips email addresses, URLs inside free-text error fields, and long token-like values, removes query/hash from page URLs, drops unknown diagnostic keys, and ignores arbitrary extra payload fields.

## Privacy exclusions

Do not intentionally collect or store:

- affiliate-link contents;
- clipboard contents;
- buyer email;
- raw license keys;
- passwords, auth tokens, or signing material;
- message contents or other user content.

Diagnostic upload failure must remain non-blocking for LINKPAS core local functionality.

## D1 validation evidence

A controlled sanitized report was submitted through the deployed Edge Function and returned HTTP `201` with report ID `afd0ff7a-9453-47fb-8733-0b53dc86a6bd`. The row was read back through authorized Supabase tooling. The test page `https://linkpas.vercel.app/?secret=should-not-store` was stored only as `https://linkpas.vercel.app/`, and the non-allow-listed diagnostic field was discarded.

A direct REST read using the public publishable key returned HTTP `401` / PostgreSQL `42501 permission denied`, confirming that public clients cannot read the diagnostic table directly.

Temporary `pg_net` used only to validate the HTTP path was removed after the test.

## Android native diagnostics (D4a/D4b)

The Android TWA wrapper adds a Beta-only native diagnostics layer for failures that happen before or outside the PWA runtime.

Native reports are intentionally limited to technical metadata:

- app version/code and package name;
- Android SDK level;
- sanitized launch host/path only (query and fragment are not stored);
- resolved default browser-provider package when available;
- startup stage;
- uncaught native exception message/stack after local redaction;
- a single bounded native-stage breadcrumb.

The wrapper installs a best-effort uncaught-exception handler. A pending crash report is stored in app-private `SharedPreferences` and retried asynchronously on the next startup. Diagnostic upload uses the public Supabase publishable key and never includes a service-role credential.

A controlled Beta test hook is available through the boolean Android intent extra `linkpas_native_diag_test=true`. It emits `native_controlled_test` with `source=android` and does not depend on clipboard, affiliate links, license values, account data, or device identifiers.

Native reporting is deliberately non-blocking: transport exceptions/timeouts are caught and return `null`, and normal startup remains immediate. The controlled test path alone may wait for diagnostic completion, with a hard 5-second maximum before TWA handoff. Unit tests cover transport failure and sanitizer behavior.

D4b verified a real Android report in Supabase and confirmed a normal TWA-wrapper launch remained free of AndroidRuntime fatal exceptions after diagnostics initialization. TWA verification success, App Links verification state, browser fallback behavior, and some Chrome/TWA internals still cannot be proven from wrapper telemetry alone and remain ADB/logcat cases.

## D5 operational command: `cek LINKPAS`

When the user says `cek LINKPAS`, ChatGPT should use the connected Supabase tooling and treat the database as the authoritative diagnostic source. The workflow is:

1. Read the newest relevant rows from `public.linkpas_diagnostic_reports`, normally the latest 10 ordered by `created_at desc`. If the user supplies a report ID, try that exact ID first, but also inspect the newest rows if the exact lookup misses; do not guess or rely only on a transcribed on-screen ID.
2. Correlate `source`, `error_type`, app/build version, page, `breadcrumbs`, and allow-listed `diagnostics` fields. Use time proximity and source (`pwa` or `android`) to separate unrelated events.
3. Map to a file/function only when evidence supports it. A stack, file/line/column, or distinctive error message can be correlated to the current repository. If sanitization has replaced a field with `[url]` or `[token]`, state that limitation rather than inventing a filename or function.
4. Report findings under three evidence levels: `OBSERVED` for values directly stored in Supabase/logs, `LIKELY` for a repository correlation supported by those values, and `NEEDS_ADB` when telemetry cannot establish the condition.
5. Use the Windows ADB fallback for verified-TWA/fullscreen versus browser fallback, Digital Asset Links/App Links verification, browser-provider/TWA failures not emitted by the wrapper, or crashes that happen before diagnostics can persist a report.

ChatGPT must not claim it can see the phone screen, continuously watch the phone, or receive unsolicited messages into an already-open chat. The user must initiate inspection (`cek LINKPAS`) or explicitly provide a generated debug bundle.

## D5 end-to-end validation evidence

A controlled production Beta `window.onerror` was triggered through the existing diagnostic test hook. Supabase stored the resulting PWA report with stable database report ID `70357fff-8d80-4a68-8187-bea7ae5e9924`.

The report establishes, without a screenshot:

- `source=pwa`;
- `error_type=window_error`;
- production page sanitized to `https://linkpas.vercel.app/`;
- breadcrumb sequence `app_boot -> app_ready -> window_error`;
- `app_stage=runtime` and `network_state=online`;
- error location line `194`, column `32` while the file URL itself was redacted by the backend sanitizer.

The current `src/diagnostics.js` controlled-test branch throws `LINKPAS_DIAG_TEST_WINDOW_ERROR` at that location, so the supported diagnosis is: `OBSERVED: controlled PWA window error`; `LIKELY: diagnostic controlled-test throw in src/diagnostics.js`; no product/root-cause claim beyond that evidence is made.

## Windows ADB fallback: `LINKPAS-Debug.bat`

`LINKPAS-Debug.bat` is a one-click Windows helper for cases classified as `NEEDS_ADB`. It looks for `adb.exe` on PATH, beside the script in `platform-tools`, or in the default local Android SDK path. It requires exactly one authorized Android device before collection.

The generated `LINKPAS-Debug-YYYYMMDD-HHMMSS.zip` is intentionally bounded to:

- ADB version and device connection state without storing the device serial;
- LINKPAS package path/version/App Links/TWA-relevant package lines;
- `pm get-app-links id.barangpas.linkpas` output;
- LINKPAS-relevant current activity state;
- up to 400 lines of wrapper-process logcat when the wrapper process is running;
- up to 800 recent system logcat lines filtered to LINKPAS package/host and TWA/App Links components.

It deliberately does **not** run `adb bugreport`, take screenshots or screen recordings, read the clipboard, pull user storage, dump arbitrary files, or store device serial numbers. After generation, attach the ZIP in ChatGPT and say `cek LINKPAS`; the bundle is a fallback, not an automatic upload channel.

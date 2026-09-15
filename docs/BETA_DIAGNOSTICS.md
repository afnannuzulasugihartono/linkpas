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

## Android native diagnostics (D4a)

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

Native reporting is deliberately non-blocking: transport exceptions/timeouts are caught and return `null`, and startup continues independently of telemetry success. Unit tests cover transport failure and sanitizer behavior.

Runtime Android emission and Supabase readback are validated separately in D4b. TWA verification success, App Links verification state, browser fallback behavior, and some Chrome/TWA internals cannot be reliably proven from this wrapper telemetry alone and remain candidates for ADB/logcat diagnostics.

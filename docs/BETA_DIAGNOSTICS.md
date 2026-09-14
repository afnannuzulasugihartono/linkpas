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

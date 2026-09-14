create table if not exists public.linkpas_diagnostic_reports (
  report_id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  source text not null check (source in ('pwa','android','manual','test')),
  app_version text,
  build_version text,
  package_name text,
  platform text,
  platform_version text,
  browser text,
  error_type text,
  error_message text,
  error_stack text,
  page text,
  online boolean,
  service_worker_state text,
  breadcrumbs jsonb not null default '[]'::jsonb check (jsonb_typeof(breadcrumbs) = 'array'),
  diagnostics jsonb not null default '{}'::jsonb check (jsonb_typeof(diagnostics) = 'object')
);

alter table public.linkpas_diagnostic_reports enable row level security;

-- No public read/write policies by design. Ingestion is only through the
-- linkpas-diagnostics Edge Function, which uses privileged credentials
-- server-side after validating and sanitizing the public request.
revoke all on table public.linkpas_diagnostic_reports from anon, authenticated;

create index if not exists linkpas_diagnostic_reports_created_at_idx
  on public.linkpas_diagnostic_reports (created_at desc);
create index if not exists linkpas_diagnostic_reports_error_type_idx
  on public.linkpas_diagnostic_reports (error_type);

comment on table public.linkpas_diagnostic_reports is
  'LINKPAS Beta technical diagnostics only. Do not store affiliate-link contents, clipboard contents, buyer email, raw license keys, passwords, or other user content.';

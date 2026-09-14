import { createClient } from 'jsr:@supabase/supabase-js@2';

const ALLOWED_ORIGINS = new Set([
  'https://linkpas.vercel.app',
  'https://linkpas-barang-pas.vercel.app',
]);
const MAX_BODY_BYTES = 32768;
const ALLOWED_SOURCES = new Set(['pwa', 'android', 'manual', 'test']);
const DIAGNOSTIC_KEYS = new Set([
  'app_stage', 'twa_status', 'sw_scope', 'browser_provider', 'request_kind',
  'http_status', 'function_name', 'file', 'line', 'column', 'release_channel',
  'network_state', 'launch_url_host', 'launch_url_path'
]);

function corsHeaders(origin: string | null) {
  const allowed = origin && ALLOWED_ORIGINS.has(origin) ? origin : 'https://linkpas.vercel.app';
  return {
    'Access-Control-Allow-Origin': allowed,
    'Access-Control-Allow-Headers': 'content-type, authorization, apikey',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Vary': 'Origin',
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  };
}

function json(body: unknown, status: number, origin: string | null) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders(origin) });
}

function redact(value: unknown, max = 1000): string | null {
  if (typeof value !== 'string') return null;
  let out = value.slice(0, max);
  out = out.replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[email]');
  out = out.replace(/https?:\/\/[^\s)\]}>'\"]+/gi, '[url]');
  out = out.replace(/\b[A-Za-z0-9_-]{24,}\b/g, '[token]');
  return out;
}

function pagePath(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const raw = value.slice(0, 500);
  try {
    const u = new URL(raw);
    return `${u.origin}${u.pathname}`.slice(0, 500);
  } catch {
    return raw.split(/[?#]/, 1)[0].slice(0, 500);
  }
}

function cleanBreadcrumbs(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.slice(-20).map((item) => {
    if (!item || typeof item !== 'object') return null;
    const row = item as Record<string, unknown>;
    return {
      event: redact(row.event, 80),
      at: redact(row.at, 40),
      state: redact(row.state, 80),
    };
  }).filter((x) => x?.event);
}

function cleanDiagnostics(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
  const out: Record<string, string | number | boolean | null> = {};
  for (const [key, raw] of Object.entries(value as Record<string, unknown>)) {
    if (!DIAGNOSTIC_KEYS.has(key)) continue;
    if (typeof raw === 'number' || typeof raw === 'boolean' || raw === null) out[key] = raw;
    else if (typeof raw === 'string') out[key] = redact(raw, 300);
  }
  return out;
}

function configuredPublicKeys(): string[] {
  const keys: string[] = [];
  const legacy = Deno.env.get('SUPABASE_ANON_KEY');
  if (legacy) keys.push(legacy);
  const raw = Deno.env.get('SUPABASE_PUBLISHABLE_KEYS');
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as Record<string, string>;
      for (const value of Object.values(parsed)) if (typeof value === 'string') keys.push(value);
    } catch { /* invalid env means no modern keys */ }
  }
  return keys;
}

function clientAuthorized(req: Request) {
  const keys = configuredPublicKeys();
  const auth = req.headers.get('authorization') || '';
  const apiKey = req.headers.get('apikey') || '';
  const bearer = auth.startsWith('Bearer ') ? auth.slice(7) : '';
  return keys.includes(apiKey) || keys.includes(bearer);
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get('origin');
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: corsHeaders(origin) });
  if (req.method !== 'POST') return json({ ok: false, error: 'method_not_allowed' }, 405, origin);
  if (origin && !ALLOWED_ORIGINS.has(origin)) return json({ ok: false, error: 'origin_not_allowed' }, 403, origin);
  if (!clientAuthorized(req)) return json({ ok: false, error: 'unauthorized_client' }, 401, origin);

  const len = Number(req.headers.get('content-length') || '0');
  if (Number.isFinite(len) && len > MAX_BODY_BYTES) return json({ ok: false, error: 'payload_too_large' }, 413, origin);

  let rawText = '';
  try { rawText = await req.text(); }
  catch { return json({ ok: false, error: 'read_failed' }, 400, origin); }
  if (new TextEncoder().encode(rawText).byteLength > MAX_BODY_BYTES) return json({ ok: false, error: 'payload_too_large' }, 413, origin);

  let body: Record<string, unknown>;
  try { body = JSON.parse(rawText); }
  catch { return json({ ok: false, error: 'invalid_json' }, 400, origin); }

  const source = typeof body.source === 'string' && ALLOWED_SOURCES.has(body.source) ? body.source : null;
  if (!source) return json({ ok: false, error: 'invalid_source' }, 400, origin);

  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const serviceRole = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: 'server_config' }, 500, origin);

  const row = {
    source,
    app_version: redact(body.app_version, 64),
    build_version: redact(body.build_version, 64),
    package_name: redact(body.package_name, 120),
    platform: redact(body.platform, 80),
    platform_version: redact(body.platform_version, 80),
    browser: redact(body.browser, 160),
    error_type: redact(body.error_type, 100),
    error_message: redact(body.error_message, 1000),
    error_stack: redact(body.error_stack, 6000),
    page: pagePath(body.page),
    online: typeof body.online === 'boolean' ? body.online : null,
    service_worker_state: redact(body.service_worker_state, 100),
    breadcrumbs: cleanBreadcrumbs(body.breadcrumbs),
    diagnostics: cleanDiagnostics(body.diagnostics),
  };

  const admin = createClient(supabaseUrl, serviceRole, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await admin
    .from('linkpas_diagnostic_reports')
    .insert(row)
    .select('report_id,created_at')
    .single();

  if (error) return json({ ok: false, error: 'insert_failed' }, 500, origin);
  return json({ ok: true, report_id: data.report_id, created_at: data.created_at }, 201, origin);
});

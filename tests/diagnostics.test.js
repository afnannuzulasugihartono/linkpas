const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadDiagnostics({ fetchImpl }) {
  const listeners = new Map();
  const context = {
    console,
    URLSearchParams,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    fetch: fetchImpl,
    location: {
      href: 'https://linkpas.vercel.app/',
      search: '',
    },
    navigator: {
      platform: 'test-platform',
      userAgent: 'LINKPAS diagnostics test',
      onLine: true,
      serviceWorker: {
        controller: null,
        addEventListener() {},
      },
    },
    window: {
      LINKPAS_CONFIG: {
        diagnosticsEndpoint: 'https://diagnostics.invalid/',
        diagnosticsPublishableKey: 'test-publishable-key',
        appVersion: '0.1.0-beta',
        buildVersion: '1',
      },
      addEventListener(type, handler) {
        listeners.set(type, handler);
      },
    },
  };
  vm.createContext(context);
  const source = fs.readFileSync(path.join(__dirname, '..', 'src', 'diagnostics.js'), 'utf8');
  vm.runInContext(source, context, { filename: 'diagnostics.js' });
  return { api: context.window.LinkPasDiagnostics, listeners };
}

test('diagnostic upload failure is non-blocking', async () => {
  const { api } = loadDiagnostics({
    fetchImpl: async () => { throw new Error('backend unavailable'); },
  });
  await assert.doesNotReject(async () => {
    const reportId = await api.report({ errorType: 'controlled_test' });
    assert.equal(reportId, null);
  });
  assert.deepEqual(api.getLastSendOutcome(), {
    ok: false,
    status: null,
    error: 'backend unavailable',
  });
});

test('breadcrumbs keep only the newest 20 technical events', () => {
  const { api } = loadDiagnostics({ fetchImpl: async () => ({ ok: true, status: 201, json: async () => ({ report_id: 'test' }) }) });
  for (let i = 0; i < 25; i += 1) api.breadcrumb(`event_${i}`, 'ok');
  const rows = api.getBreadcrumbs();
  assert.equal(rows.length, 20);
  assert.equal(rows[0].event, 'event_5');
  assert.equal(rows.at(-1).event, 'event_24');
  assert.ok(rows.every((row) => Object.keys(row).sort().join(',') === 'at,event,state'));
});

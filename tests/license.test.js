const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('public config points to backend license verifier', () => {
  const config = read('src/config.demo.js');
  assert.match(config, /licenseEndpoint/);
  assert.match(config, /functions\/v1\/linkpas-license/);
});

test('license bootstrap verifies remotely and never reads affiliate input', () => {
  const code = read('src/license.js');
  assert.match(code, /diagnosticFetch\(endpoint/);
  assert.match(code, /diagnostics\?\.fetch \|\|/);
  assert.match(code, /licenseKey/);
  assert.match(code, /localStorage/);
  assert.match(code, /cfg\.mode = 'pro'/);
  assert.doesNotMatch(code, /link-input/);
});

test('public HTML exposes license activation controls', () => {
  const html = read('src/index.html');
  assert.match(html, /id="license-input"/);
  assert.match(html, /id="activate-license-btn"/);
  assert.match(html, /license\.js/);
});

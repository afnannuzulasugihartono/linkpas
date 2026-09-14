const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('manifest is installable PWA metadata', () => {
  const manifest = JSON.parse(read('src/manifest.webmanifest'));
  assert.equal(manifest.short_name, 'LINKPAS');
  assert.equal(manifest.display, 'standalone');
  assert.ok(manifest.icons.some((i) => i.sizes === '192x192'));
  assert.ok(manifest.icons.some((i) => i.sizes === '512x512'));
});

test('HTML links manifest and Apple install metadata', () => {
  const html = read('src/index.html');
  assert.match(html, /rel="manifest"/);
  assert.match(html, /apple-mobile-web-app-capable/);
  assert.match(html, /id="install-btn"/);
  assert.match(html, /pwa\.js/);
  assert.match(html, /pwa\.css/);
});

test('service worker caches app shell for offline use', () => {
  const sw = read('src/sw.js');
  assert.match(sw, /manifest\.webmanifest/);
  assert.match(sw, /icon-192\.png/);
  assert.match(sw, /pwa\.js/);
  assert.match(sw, /pwa\.css/);
  assert.match(sw, /caches\.open/);
});

test('demo and pro bundles contain PWA assets after build', () => {
  for (const mode of ['demo', 'pro']) {
    for (const file of ['manifest.webmanifest', 'sw.js', 'icon-192.png', 'icon-512.png', 'apple-touch-icon.png']) {
      assert.ok(fs.existsSync(path.join(root, 'dist', mode, file)), `${mode}/${file}`);
    }
  }
});

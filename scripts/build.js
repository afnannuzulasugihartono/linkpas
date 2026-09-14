const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const src = path.join(root, 'src');
const out = path.join(root, 'dist', 'demo');
const common = ['index.html', 'styles.css', 'pwa.css', 'diagnostics.js', 'app.js', 'license.js', 'pwa.js', 'core.js', 'manifest.webmanifest', 'sw.js', 'icon-192.png', 'icon-512.png', 'apple-touch-icon.png'];

fs.rmSync(path.join(root, 'dist', 'pro'), { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
for (const file of common) fs.copyFileSync(path.join(src, file), path.join(out, file));
fs.copyFileSync(path.join(src, 'config.demo.js'), path.join(out, 'config.js'));
fs.copyFileSync(path.join(root, 'LICENSE.txt'), path.join(out, 'LICENSE.txt'));

const wellKnownOut = path.join(out, '.well-known');
fs.mkdirSync(wellKnownOut, { recursive: true });
fs.copyFileSync(
  path.join(src, '.well-known', 'assetlinks.json'),
  path.join(wellKnownOut, 'assetlinks.json')
);

console.log('Built LINKPAS public PWA bundle with Beta Diagnostics. Pro is unlocked by backend license entitlement.');

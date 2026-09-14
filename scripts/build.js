const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const src = path.join(root, 'src');
const common = ['index.html', 'styles.css', 'pwa.css', 'app.js', 'pwa.js', 'core.js', 'manifest.webmanifest', 'sw.js', 'icon-192.png', 'icon-512.png', 'apple-touch-icon.png'];

for (const mode of ['demo', 'pro']) {
  const out = path.join(root, 'dist', mode);
  fs.mkdirSync(out, { recursive: true });
  for (const file of common) fs.copyFileSync(path.join(src, file), path.join(out, file));
  fs.copyFileSync(path.join(src, `config.${mode}.js`), path.join(out, 'config.js'));
  fs.copyFileSync(path.join(root, 'LICENSE.txt'), path.join(out, 'LICENSE.txt'));
}
console.log('Built LINKPAS demo + pro PWA bundles.');

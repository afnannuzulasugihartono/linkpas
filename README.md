# LINKPAS — Affiliate Link Toolbox

LINKPAS adalah micro-tool browser-only untuk merapikan ratusan link affiliate sekaligus.

## Fitur MVP
- Ekstrak URL dari teks berantakan
- Deteksi Shopee, TikTok, Tokopedia, Lazada, Blibli, shortlink, dan lainnya
- Tandai shortlink
- Deteksi duplikat exact-normalized URL
- Filter/search hasil
- Copy link unik
- Export TXT dan CSV
- Semua proses lokal di browser, tanpa database dan tanpa API

## Produk
- `dist/demo/` — Demo maksimal 50 link. CTA diarahkan ke `https://lynk.id/barangpas`.
- `dist/pro/` — Pro tanpa limit aplikasi. Cocok dijual sebagai ZIP digital.

## Menjalankan lokal
Karena ini static app tanpa dependency:

```bash
python -m http.server 8080 -d dist/demo
```

Buka http://localhost:8080

## Test

```bash
node --test tests/core.test.js
```

## Catatan penting
LINKPAS **tidak** mengecek apakah link masih hidup, produk tersedia, atau komisi affiliate masih aktif. Tool ini hanya membersihkan dan mengorganisasi link secara lokal. Jangan menghapus query parameter affiliate karena dapat memengaruhi tracking komisi; LINKPAS mempertahankan query string.

## Harga beta yang disarankan
Rp19.000 sekali bayar untuk versi Pro.

# LINKPAS — Affiliate Link Toolbox

LINKPAS adalah web app/PWA untuk merapikan banyak link affiliate langsung di perangkat.

## Live
- Free/PWA: https://linkpas.vercel.app
- Free memproses maksimal 50 link per sekali proses.
- Pro Rp19.000 lifetime di-unlock dengan kode lisensi yang diverifikasi backend.

## Fitur
- Ekstrak URL dari teks berantakan
- Deteksi Shopee, TikTok, Tokopedia, Lazada, Blibli, shortlink, dan lainnya
- Deteksi exact duplicate
- Filter/search hasil
- Copy hasil bersih
- Export TXT dan CSV
- Install ke Home Screen sebagai PWA
- App shell offline setelah kunjungan pertama

## Privasi
Link affiliate diproses lokal di browser dan tidak dikirim ke backend. Backend hanya menerima kode lisensi saat pengguna mengaktifkan / memverifikasi LINKPAS Pro.

## Build & test
```bash
npm run build
npm test
```

Output publik berada di `dist/demo/`. Tidak ada bundle Pro terpisah di repository; entitlement Pro berasal dari backend license verifier.

## Batasan
LINKPAS tidak mengecek apakah produk masih tersedia, link masih hidup, atau komisi affiliate masih aktif. Query string dipertahankan agar tracking affiliate tidak sengaja terhapus.

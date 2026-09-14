(() => {
  const cfg = window.LINKPAS_CONFIG || {};
  const endpoint = cfg.licenseEndpoint || '';
  const KEY_STORAGE = 'linkpas_license_key_v1';
  const CACHE_STORAGE = 'linkpas_license_cache_v1';
  const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

  function readCache() {
    try {
      const cached = JSON.parse(localStorage.getItem(CACHE_STORAGE) || 'null');
      return Boolean(cached && cached.plan === 'pro' && Number.isFinite(cached.verifiedAt) && Date.now() - cached.verifiedAt <= CACHE_TTL_MS);
    } catch {
      return false;
    }
  }

  const savedKey = (localStorage.getItem(KEY_STORAGE) || '').trim().toUpperCase();
  if (savedKey && readCache()) cfg.mode = 'pro';

  const els = {
    input: document.querySelector('#license-input'),
    activate: document.querySelector('#activate-license-btn'),
    status: document.querySelector('#license-status'),
  };

  function status(text, tone = 'info') {
    if (!els.status) return;
    els.status.textContent = text;
    els.status.dataset.tone = tone;
  }

  async function verify(rawKey, { reloadOnSuccess = false, quiet = false } = {}) {
    const licenseKey = String(rawKey || '').trim().toUpperCase();
    if (!endpoint || licenseKey.length < 20) {
      if (!quiet) status('Kode lisensi belum valid.', 'warn');
      return false;
    }

    if (!quiet) status('Memverifikasi lisensi…');
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ licenseKey }),
        cache: 'no-store',
        credentials: 'omit',
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const body = await response.json();
      const valid = body?.valid === true && body?.plan === 'pro';
      if (!valid) {
        localStorage.removeItem(CACHE_STORAGE);
        if (!quiet) status('Kode lisensi tidak aktif / tidak valid.', 'warn');
        return false;
      }

      localStorage.setItem(KEY_STORAGE, licenseKey);
      localStorage.setItem(CACHE_STORAGE, JSON.stringify({ plan: 'pro', verifiedAt: Date.now() }));
      if (!quiet) status('LINKPAS Pro aktif. Memuat ulang aplikasi…', 'success');
      if (reloadOnSuccess || cfg.mode !== 'pro') setTimeout(() => location.reload(), 300);
      return true;
    } catch (error) {
      console.warn('Verifikasi lisensi LINKPAS gagal:', error);
      if (!quiet) status('Verifikasi gagal. Cek koneksi internet lalu coba lagi.', 'warn');
      return false;
    }
  }

  els.activate?.addEventListener('click', () => verify(els.input?.value || '', { reloadOnSuccess: true }));
  els.input?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      els.activate?.click();
    }
  });

  if (savedKey) {
    if (cfg.mode !== 'pro') status('Memverifikasi lisensi tersimpan…');
    verify(savedKey, { quiet: cfg.mode === 'pro' }).then((valid) => {
      if (!valid && cfg.mode === 'pro') {
        localStorage.removeItem(KEY_STORAGE);
        localStorage.removeItem(CACHE_STORAGE);
        location.reload();
      }
    });
  } else {
    status('Sudah beli? Masukkan kode lisensi Pro di sini.');
  }

  window.LinkPasLicense = { verify };
})();

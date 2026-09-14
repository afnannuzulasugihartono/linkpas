(() => {
  const cfg = window.LINKPAS_CONFIG || {};
  const isPro = cfg.mode === 'pro';
  const limit = isPro ? Infinity : (cfg.limit || 50);
  const purchaseUrl = cfg.purchaseUrl || 'https://lynk.id/barangpas';
  const Core = window.LinkPasCore;
  const diagnostics = window.LinkPasDiagnostics;

  const els = {
    input: document.querySelector('#link-input'),
    process: document.querySelector('#process-btn'),
    clear: document.querySelector('#clear-btn'),
    sample: document.querySelector('#sample-btn'),
    copy: document.querySelector('#copy-btn'),
    txt: document.querySelector('#txt-btn'),
    csv: document.querySelector('#csv-btn'),
    search: document.querySelector('#search-input'),
    platform: document.querySelector('#platform-filter'),
    duplicate: document.querySelector('#duplicate-filter'),
    results: document.querySelector('#results-body'),
    empty: document.querySelector('#empty-state'),
    resultsPanel: document.querySelector('#results-panel'),
    statsPanel: document.querySelector('#stats-panel'),
    count: document.querySelector('#input-count'),
    modeBadge: document.querySelector('#mode-badge'),
    upgrade: document.querySelector('#upgrade-card'),
    upgradeLink: document.querySelector('#upgrade-link'),
    limitNote: document.querySelector('#limit-note'),
    stats: {
      total: document.querySelector('#stat-total'),
      unique: document.querySelector('#stat-unique'),
      duplicate: document.querySelector('#stat-duplicate'),
      short: document.querySelector('#stat-short'),
    },
    message: document.querySelector('#message'),
  };

  let result = null;

  diagnostics?.breadcrumb('app_ready', isPro ? 'pro' : 'demo');
  els.modeBadge.textContent = isPro ? 'PRO • Unlimited' : `DEMO • ${limit} link`;
  if (isPro) {
    els.upgrade?.remove();
    els.limitNote.textContent = 'Versi Pro • tidak ada batas jumlah link dari aplikasi.';
  } else {
    els.upgradeLink.href = purchaseUrl;
    els.limitNote.textContent = `Demo memproses maksimal ${limit} link per sekali proses.`;
  }

  const sampleText = [
    'https://s.shopee.co.id/7fContohA',
    'https://s.shopee.co.id/7fContohA',
    'https://vt.tiktok.com/ZSContohB/',
    'https://tokopedia.link/ContohC',
    'https://www.lazada.co.id/products/contoh-produk-i123.html',
    'https://s.id/contoh-link',
    'https://example.com/produk-affiliate',
  ].join('\n');

  function showMessage(text, tone = 'info') {
    els.message.textContent = text;
    els.message.dataset.tone = tone;
    if (text) setTimeout(() => {
      if (els.message.textContent === text) els.message.textContent = '';
    }, 3500);
  }

  function updateCount() {
    const count = Core.extractCandidates(els.input.value).length;
    els.count.textContent = `${count} link terdeteksi`;
  }

  function process() {
    diagnostics?.breadcrumb('process_start');
    result = Core.processText(els.input.value, limit);
    diagnostics?.breadcrumb('process_complete', `processed=${result.processedCount};unique=${result.uniqueCount};truncated=${result.truncatedCount}`);
    render();
    if (!result.processedCount) {
      showMessage('Belum ada URL yang bisa diproses.', 'warn');
      return;
    }
    if (result.truncatedCount > 0) {
      showMessage(`${result.truncatedCount} link tidak diproses karena batas versi Demo.`, 'warn');
    } else {
      showMessage(`${result.uniqueCount} link unik siap digunakan.`, 'success');
    }
  }

  function getFilteredRows() {
    if (!result) return [];
    const q = els.search.value.trim().toLowerCase();
    const platform = els.platform.value;
    const dupMode = els.duplicate.value;

    return result.rows.filter((row) => {
      if (!row.valid) return platform === 'invalid';
      if (platform && platform !== row.platform) return false;
      if (dupMode === 'unique' && row.duplicate) return false;
      if (dupMode === 'duplicate' && !row.duplicate) return false;
      if (q && !`${row.normalized} ${row.platform} ${row.host}`.toLowerCase().includes(q)) return false;
      return true;
    });
  }

  function render() {
    const hasResult = result && result.processedCount > 0;
    els.empty.hidden = hasResult;
    els.resultsPanel.hidden = !hasResult;
    els.statsPanel.hidden = !hasResult;
    if (!hasResult) return;

    els.stats.total.textContent = result.processedCount;
    els.stats.unique.textContent = result.uniqueCount;
    els.stats.duplicate.textContent = result.duplicateCount;
    els.stats.short.textContent = result.shortlinkCount;

    const platforms = Object.keys(result.platformCounts).sort();
    const current = els.platform.value;
    els.platform.innerHTML = '<option value="">Semua platform</option>' +
      platforms.map((p) => `<option value="${escapeHtml(p)}">${escapeHtml(p)} (${result.platformCounts[p]})</option>`).join('') +
      (result.invalidCount ? `<option value="invalid">Invalid (${result.invalidCount})</option>` : '');
    els.platform.value = platforms.includes(current) || current === 'invalid' ? current : '';

    renderRows();
  }

  function renderRows() {
    if (!result) return;
    const rows = getFilteredRows();
    els.results.innerHTML = rows.map((row) => {
      if (!row.valid) {
        return `<tr class="row-invalid"><td>${row.index}</td><td><span class="pill danger">Invalid</span></td><td>—</td><td><code>${escapeHtml(row.original)}</code><div class="subtle">${escapeHtml(row.reason)}</div></td></tr>`;
      }
      const flags = [
        row.isShortlink ? '<span class="pill">Shortlink</span>' : '',
        row.duplicate ? '<span class="pill warn">Duplikat</span>' : '<span class="pill good">Unik</span>',
      ].filter(Boolean).join(' ');
      return `<tr><td>${row.index}</td><td><strong>${escapeHtml(row.platform)}</strong><div class="subtle">${escapeHtml(row.host)}</div></td><td>${flags}</td><td class="url-cell"><code title="${escapeHtml(row.normalized)}">${escapeHtml(row.normalized)}</code><button class="mini-copy" data-copy="${escapeHtmlAttr(row.normalized)}" type="button">Copy</button></td></tr>`;
    }).join('');

    els.results.querySelectorAll('.mini-copy').forEach((button) => {
      button.addEventListener('click', async () => {
        await navigator.clipboard.writeText(button.dataset.copy || '');
        diagnostics?.breadcrumb('copy_single');
        button.textContent = 'Copied';
        setTimeout(() => { button.textContent = 'Copy'; }, 1200);
      });
    });
  }

  function cleanUniqueLinks() {
    return result ? result.uniqueRows.map((row) => row.normalized) : [];
  }

  async function copyClean() {
    const links = cleanUniqueLinks();
    if (!links.length) return showMessage('Proses link dulu.', 'warn');
    await navigator.clipboard.writeText(links.join('\n'));
    diagnostics?.breadcrumb('copy_clean', `count=${links.length}`);
    showMessage(`${links.length} link unik disalin.`, 'success');
  }

  function downloadFile(filename, text, type) {
    const blob = new Blob([text], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function downloadTxt() {
    const links = cleanUniqueLinks();
    if (!links.length) return showMessage('Proses link dulu.', 'warn');
    diagnostics?.breadcrumb('export_txt', `count=${links.length}`);
    downloadFile(`linkpas-clean-${dateStamp()}.txt`, links.join('\n'), 'text/plain;charset=utf-8');
  }

  function downloadCsv() {
    if (!result?.rows?.length) return showMessage('Proses link dulu.', 'warn');
    const validRows = result.rows.filter((row) => row.valid);
    diagnostics?.breadcrumb('export_csv', `count=${validRows.length}`);
    downloadFile(`linkpas-${dateStamp()}.csv`, '\uFEFF' + Core.toCsv(validRows), 'text/csv;charset=utf-8');
  }

  function dateStamp() {
    return new Date().toISOString().slice(0, 10);
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, (char) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#039;', '"': '&quot;'
    }[char]));
  }

  function escapeHtmlAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#096;');
  }

  els.input.addEventListener('input', updateCount);
  els.process.addEventListener('click', process);
  els.clear.addEventListener('click', () => {
    diagnostics?.breadcrumb('clear_input');
    els.input.value = '';
    result = null;
    updateCount();
    els.resultsPanel.hidden = true;
    els.statsPanel.hidden = true;
    els.empty.hidden = false;
    els.input.focus();
  });
  els.sample.addEventListener('click', () => {
    diagnostics?.breadcrumb('sample_loaded');
    els.input.value = sampleText;
    updateCount();
    process();
  });
  els.copy.addEventListener('click', copyClean);
  els.txt.addEventListener('click', downloadTxt);
  els.csv.addEventListener('click', downloadCsv);
  els.search.addEventListener('input', renderRows);
  els.platform.addEventListener('change', renderRows);
  els.duplicate.addEventListener('change', renderRows);

  updateCount();
})();

(() => {
  const cfg = window.LINKPAS_CONFIG || {};
  const diagnostics = window.LinkPasDiagnostics;
  const panel = document.querySelector('#diagnostics-panel');
  const versionLabel = document.querySelector('#version-label');
  const closeButton = document.querySelector('#diagnostics-close-btn');
  const sendButton = document.querySelector('#diagnostics-send-btn');
  const sendStatus = document.querySelector('#diagnostics-send-status');

  if (!panel || !versionLabel || !diagnostics) return;

  const fields = {
    version: document.querySelector('#diagnostics-app-version'),
    packageName: document.querySelector('#diagnostics-package'),
    domain: document.querySelector('#diagnostics-domain'),
    online: document.querySelector('#diagnostics-online'),
    serviceWorker: document.querySelector('#diagnostics-service-worker'),
    lastError: document.querySelector('#diagnostics-last-error'),
    lastReport: document.querySelector('#diagnostics-last-report'),
  };

  const version = cfg.appVersion || '0.1.0-beta';
  const packageName = 'id.barangpas.linkpas';
  versionLabel.textContent = `v${version}`;

  let tapCount = 0;

  function setText(node, value) {
    if (node) node.textContent = String(value ?? '—');
  }

  function render() {
    setText(fields.version, version);
    setText(fields.packageName, packageName);
    setText(fields.domain, location.hostname);
    setText(fields.online, navigator.onLine ? 'Online' : 'Offline');
    setText(fields.serviceWorker, diagnostics.getServiceWorkerState?.() || 'unknown');
    setText(fields.lastError, diagnostics.getLastError?.() || 'Tidak ada');
    setText(fields.lastReport, diagnostics.getLastReportId?.() || 'Belum ada');
  }

  function openPanel() {
    diagnostics.breadcrumb?.('diagnostics_panel_opened');
    panel.hidden = false;
    panel.setAttribute('aria-hidden', 'false');
    render();
    closeButton?.focus();
  }

  function closePanel() {
    panel.hidden = true;
    panel.setAttribute('aria-hidden', 'true');
    if (sendStatus) sendStatus.textContent = '';
    versionLabel.focus();
  }

  versionLabel.addEventListener('click', () => {
    tapCount += 1;
    if (tapCount >= 5) {
      tapCount = 0;
      openPanel();
    }
  });

  closeButton?.addEventListener('click', closePanel);

  sendButton?.addEventListener('click', async () => {
    if (!sendButton) return;
    sendButton.disabled = true;
    if (sendStatus) sendStatus.textContent = 'Mengirim laporan teknis…';
    diagnostics.breadcrumb?.('manual_diagnostic_report');
    const reportId = await diagnostics.report({
      errorType: 'manual_diagnostic',
      errorMessage: 'Manual Beta diagnostics report',
      diagnostics: { app_stage: 'diagnostics_panel' },
    });
    render();
    if (sendStatus) {
      sendStatus.textContent = reportId
        ? `Terkirim. Report ID: ${reportId}`
        : 'Laporan belum terkirim. Coba lagi saat online.';
    }
    sendButton.disabled = false;
  });

  window.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !panel.hidden) closePanel();
  });
  window.addEventListener('online', () => { if (!panel.hidden) render(); });
  window.addEventListener('offline', () => { if (!panel.hidden) render(); });

  window.LinkPasDiagnosticsPanel = {
    open: openPanel,
    close: closePanel,
    isOpen: () => !panel.hidden,
    render,
  };
})();

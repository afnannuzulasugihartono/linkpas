(() => {
  const cfg = window.LINKPAS_CONFIG || {};
  const endpoint = cfg.diagnosticsEndpoint || '';
  const apiKey = cfg.diagnosticsPublishableKey || '';
  const MAX_BREADCRUMBS = 20;
  const breadcrumbs = [];
  let lastReportId = null;
  let sending = false;

  function safeText(value, max = 800) {
    if (value == null) return null;
    return String(value).slice(0, max);
  }

  function breadcrumb(event, state = '') {
    const name = safeText(event, 80);
    if (!name) return;
    breadcrumbs.push({
      event: name,
      at: new Date().toISOString(),
      state: safeText(state, 80) || '',
    });
    if (breadcrumbs.length > MAX_BREADCRUMBS) breadcrumbs.splice(0, breadcrumbs.length - MAX_BREADCRUMBS);
  }

  function serviceWorkerState() {
    if (!('serviceWorker' in navigator)) return 'unsupported';
    if (navigator.serviceWorker.controller) return 'controlled';
    return 'available_not_controlling';
  }

  function basePayload() {
    return {
      source: 'pwa',
      app_version: cfg.appVersion || '0.1.0-beta',
      build_version: cfg.buildVersion || '1',
      package_name: 'id.barangpas.linkpas',
      platform: navigator.platform || 'web',
      platform_version: safeText(navigator.userAgent, 80),
      browser: safeText(navigator.userAgent, 160),
      page: location.href,
      online: navigator.onLine,
      service_worker_state: serviceWorkerState(),
      breadcrumbs: breadcrumbs.slice(-MAX_BREADCRUMBS),
      diagnostics: {
        app_stage: 'runtime',
        release_channel: 'beta',
        network_state: navigator.onLine ? 'online' : 'offline',
        sw_scope: navigator.serviceWorker?.controller?.scriptURL || null,
      },
    };
  }

  async function sendReport(details = {}) {
    if (!endpoint || !apiKey || sending) return null;
    sending = true;
    try {
      const payload = {
        ...basePayload(),
        error_type: safeText(details.errorType, 100),
        error_message: safeText(details.errorMessage, 1000),
        error_stack: safeText(details.errorStack, 6000),
        diagnostics: {
          ...basePayload().diagnostics,
          ...(details.diagnostics || {}),
        },
      };
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: apiKey,
        },
        body: JSON.stringify(payload),
        cache: 'no-store',
        credentials: 'omit',
        keepalive: true,
      });
      if (!response.ok) return null;
      const body = await response.json().catch(() => null);
      lastReportId = body?.report_id || null;
      return lastReportId;
    } catch {
      return null;
    } finally {
      sending = false;
    }
  }

  async function trackedFetch(input, init = {}, requestKind = 'important_fetch') {
    breadcrumb('fetch_start', requestKind);
    try {
      const response = await fetch(input, init);
      if (!response.ok) {
        breadcrumb('fetch_failed', `${requestKind}:${response.status}`);
        void sendReport({
          errorType: 'fetch_failure',
          errorMessage: `Important request failed with HTTP ${response.status}`,
          diagnostics: { request_kind: requestKind, http_status: response.status },
        });
      } else {
        breadcrumb('fetch_ok', requestKind);
      }
      return response;
    } catch (error) {
      breadcrumb('fetch_exception', requestKind);
      void sendReport({
        errorType: 'fetch_exception',
        errorMessage: error instanceof Error ? error.message : 'Network request failed',
        errorStack: error instanceof Error ? error.stack : null,
        diagnostics: { request_kind: requestKind },
      });
      throw error;
    }
  }

  window.addEventListener('error', (event) => {
    breadcrumb('window_error');
    void sendReport({
      errorType: 'window_error',
      errorMessage: event.message || 'Unhandled window error',
      errorStack: event.error?.stack || null,
      diagnostics: {
        file: event.filename || null,
        line: Number.isFinite(event.lineno) ? event.lineno : null,
        column: Number.isFinite(event.colno) ? event.colno : null,
      },
    });
  });

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason;
    breadcrumb('unhandled_rejection');
    void sendReport({
      errorType: 'unhandledrejection',
      errorMessage: reason instanceof Error ? reason.message : safeText(reason, 1000) || 'Unhandled promise rejection',
      errorStack: reason instanceof Error ? reason.stack : null,
    });
  });

  window.addEventListener('online', () => breadcrumb('network_online'));
  window.addEventListener('offline', () => breadcrumb('network_offline'));
  navigator.serviceWorker?.addEventListener('controllerchange', () => breadcrumb('service_worker_controllerchange'));

  breadcrumb('app_boot', navigator.onLine ? 'online' : 'offline');

  window.LinkPasDiagnostics = {
    breadcrumb,
    report: sendReport,
    fetch: trackedFetch,
    getBreadcrumbs: () => breadcrumbs.slice(),
    getLastReportId: () => lastReportId,
    getServiceWorkerState: serviceWorkerState,
  };

  // Beta-only deterministic validation hook. It runs only when explicitly requested by query string.
  const testType = new URLSearchParams(location.search).get('__linkpas_diag_test');
  if (testType === 'window-error') {
    setTimeout(() => { throw new Error('LINKPAS_DIAG_TEST_WINDOW_ERROR'); }, 250);
  } else if (testType === 'promise-rejection') {
    setTimeout(() => { Promise.reject(new Error('LINKPAS_DIAG_TEST_PROMISE_REJECTION')); }, 250);
  }
})();

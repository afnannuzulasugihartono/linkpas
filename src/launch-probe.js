((root, factory) => {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (!root) return;

  const consumed = api.consumeUrl(root.location?.href || '');
  api.currentProbeId = consumed.probeId;
  if (consumed.hadProbe && root.history?.replaceState && consumed.cleanRelativeUrl) {
    try {
      root.history.replaceState(root.history.state, '', consumed.cleanRelativeUrl);
    } catch {
      // Probe cleanup is best effort and must never block LINKPAS.
    }
  }
  root.LinkPasLaunchProbe = api;
})(typeof window !== 'undefined' ? window : null, () => {
  const QUERY_PARAM = '__linkpas_probe';
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  function normalizeProbeId(value) {
    const text = typeof value === 'string' ? value.trim() : '';
    return text.length === 36 && UUID_RE.test(text) ? text.toLowerCase() : null;
  }

  function consumeUrl(href) {
    try {
      const url = new URL(href);
      const values = url.searchParams.getAll(QUERY_PARAM);
      let probeId = null;
      for (let i = values.length - 1; i >= 0; i -= 1) {
        probeId = normalizeProbeId(values[i]);
        if (probeId) break;
      }
      const hadProbe = values.length > 0;
      if (hadProbe) url.searchParams.delete(QUERY_PARAM);
      return {
        probeId,
        hadProbe,
        cleanRelativeUrl: `${url.pathname}${url.search}${url.hash}`,
      };
    } catch {
      return { probeId: null, hadProbe: false, cleanRelativeUrl: null };
    }
  }

  return {
    QUERY_PARAM,
    normalizeProbeId,
    consumeUrl,
    currentProbeId: null,
  };
});

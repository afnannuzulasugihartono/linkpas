(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.LinkPasCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const PLATFORM_RULES = [
    { name: 'Shopee', hosts: ['shopee.co.id', 'www.shopee.co.id', 's.shopee.co.id', 'shope.ee'] },
    { name: 'TikTok', hosts: ['tiktok.com', 'www.tiktok.com', 'vt.tiktok.com', 'vm.tiktok.com'] },
    { name: 'Tokopedia', hosts: ['tokopedia.com', 'www.tokopedia.com', 'tokopedia.link'] },
    { name: 'Lazada', hosts: ['lazada.co.id', 'www.lazada.co.id', 'lazada.com', 'www.lazada.com', 's.lazada.co.id'] },
    { name: 'Blibli', hosts: ['blibli.com', 'www.blibli.com'] },
  ];

  const SHORTLINK_HOSTS = new Set([
    's.shopee.co.id', 'shope.ee', 'vt.tiktok.com', 'vm.tiktok.com',
    'tokopedia.link', 's.lazada.co.id', 's.id', 'bit.ly', 'tinyurl.com'
  ]);

  function stripEdgePunctuation(value) {
    return value
      .trim()
      .replace(/^[\s<({\["']+/, '')
      .replace(/[\s>)}\]"',.;!?]+$/, '');
  }

  function extractCandidates(text) {
    if (!text) return [];
    const raw = text
      .replace(/\r/g, '\n')
      .split(/[\s\n]+/)
      .map(stripEdgePunctuation)
      .filter(Boolean);

    return raw.filter((token) => {
      return /^https?:\/\//i.test(token) || /(?:^|\.)[a-z0-9-]+\.(?:co\.id|com|id|link|ee)(?:\/|$)/i.test(token);
    });
  }

  function parseCandidate(input) {
    const original = stripEdgePunctuation(input);
    let candidate = original;
    if (!/^https?:\/\//i.test(candidate)) candidate = `https://${candidate}`;

    let url;
    try {
      url = new URL(candidate);
    } catch {
      return { original, valid: false, reason: 'Format URL tidak valid' };
    }

    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname.includes('.')) {
      return { original, valid: false, reason: 'Format URL tidak valid' };
    }

    url.hash = '';
    const normalized = url.toString();
    const host = url.hostname.toLowerCase();
    const platform = detectPlatform(host);
    const isShortlink = SHORTLINK_HOSTS.has(host);

    return {
      original,
      normalized,
      host,
      platform,
      isShortlink,
      valid: true,
      reason: '',
    };
  }

  function detectPlatform(host) {
    for (const rule of PLATFORM_RULES) {
      if (rule.hosts.includes(host)) return rule.name;
    }
    if (host === 's.id' || host.endsWith('.s.id')) return 'Shortlink';
    if (SHORTLINK_HOSTS.has(host)) return 'Shortlink';
    return 'Lainnya';
  }

  function processText(text, limit) {
    const candidates = extractCandidates(text);
    const limited = Number.isFinite(limit) ? candidates.slice(0, limit) : candidates;
    const seen = new Map();
    const rows = [];
    let duplicateCount = 0;

    for (let i = 0; i < limited.length; i += 1) {
      const parsed = parseCandidate(limited[i]);
      if (!parsed.valid) {
        rows.push({ ...parsed, index: i + 1, duplicate: false });
        continue;
      }

      const key = parsed.normalized;
      const duplicate = seen.has(key);
      if (duplicate) duplicateCount += 1;
      else seen.set(key, i + 1);

      rows.push({ ...parsed, index: i + 1, duplicate, firstSeenAt: seen.get(key) });
    }

    const validRows = rows.filter((row) => row.valid);
    const uniqueRows = validRows.filter((row) => !row.duplicate);
    const platformCounts = uniqueRows.reduce((acc, row) => {
      acc[row.platform] = (acc[row.platform] || 0) + 1;
      return acc;
    }, {});

    return {
      rows,
      uniqueRows,
      totalCandidates: candidates.length,
      processedCount: limited.length,
      truncatedCount: Math.max(0, candidates.length - limited.length),
      validCount: validRows.length,
      uniqueCount: uniqueRows.length,
      invalidCount: rows.length - validRows.length,
      duplicateCount,
      shortlinkCount: uniqueRows.filter((row) => row.isShortlink).length,
      platformCounts,
    };
  }

  function csvEscape(value) {
    const text = String(value ?? '');
    return `"${text.replace(/"/g, '""')}"`;
  }

  function toCsv(rows) {
    const header = ['No', 'Platform', 'Shortlink', 'Duplikat', 'Host', 'URL'];
    const body = rows.map((row, idx) => [
      idx + 1,
      row.platform,
      row.isShortlink ? 'Ya' : 'Tidak',
      row.duplicate ? 'Ya' : 'Tidak',
      row.host,
      row.normalized,
    ]);
    return [header, ...body].map((line) => line.map(csvEscape).join(',')).join('\n');
  }

  return {
    PLATFORM_RULES,
    SHORTLINK_HOSTS,
    extractCandidates,
    parseCandidate,
    detectPlatform,
    processText,
    toCsv,
  };
});

const test = require('node:test');
const assert = require('node:assert/strict');
const Core = require('../src/core.js');

test('extracts links from messy text', () => {
  const links = Core.extractCandidates('cek https://s.shopee.co.id/ABC, lalu vt.tiktok.com/XYZ');
  assert.deepEqual(links, ['https://s.shopee.co.id/ABC', 'vt.tiktok.com/XYZ']);
});

test('classifies platforms and shortlinks', () => {
  const shopee = Core.parseCandidate('https://s.shopee.co.id/ABC');
  assert.equal(shopee.platform, 'Shopee');
  assert.equal(shopee.isShortlink, true);
  const tokopedia = Core.parseCandidate('https://www.tokopedia.com/toko/produk');
  assert.equal(tokopedia.platform, 'Tokopedia');
  assert.equal(tokopedia.isShortlink, false);
});

test('deduplicates exact normalized URLs', () => {
  const r = Core.processText('https://example.com/a\nhttps://example.com/a\nhttps://example.com/b', Infinity);
  assert.equal(r.processedCount, 3);
  assert.equal(r.uniqueCount, 2);
  assert.equal(r.duplicateCount, 1);
});

test('demo limit truncates without changing source input', () => {
  const input = Array.from({length: 55}, (_, i) => `https://example.com/${i}`).join('\n');
  const r = Core.processText(input, 50);
  assert.equal(r.processedCount, 50);
  assert.equal(r.truncatedCount, 5);
});

test('CSV contains core columns', () => {
  const r = Core.processText('https://s.shopee.co.id/ABC', Infinity);
  const csv = Core.toCsv(r.rows);
  assert.match(csv, /Platform/);
  assert.match(csv, /Shopee/);
});

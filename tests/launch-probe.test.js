const test = require('node:test');
const assert = require('node:assert/strict');
const probe = require('../src/launch-probe.js');

const ID = '123e4567-e89b-42d3-a456-426614174000';

test('valid launch probe is preserved for correlation and removed from visible URL', () => {
  const result = probe.consumeUrl(`https://linkpas.vercel.app/path?x=1&${probe.QUERY_PARAM}=${ID}#ok`);
  assert.equal(result.probeId, ID);
  assert.equal(result.hadProbe, true);
  assert.equal(result.cleanRelativeUrl, '/path?x=1#ok');
  assert.equal(result.cleanRelativeUrl.includes(probe.QUERY_PARAM), false);
});

test('invalid probe is removed but never accepted as correlation data', () => {
  const result = probe.consumeUrl(`https://linkpas.vercel.app/?${probe.QUERY_PARAM}=buyer@example.com`);
  assert.equal(result.probeId, null);
  assert.equal(result.hadProbe, true);
  assert.equal(result.cleanRelativeUrl, '/');
});

test('last valid duplicate probe wins and all probe parameters are removed', () => {
  const other = '550e8400-e29b-41d4-a716-446655440000';
  const result = probe.consumeUrl(`https://linkpas.vercel.app/?${probe.QUERY_PARAM}=bad&${probe.QUERY_PARAM}=${other}`);
  assert.equal(result.probeId, other);
  assert.equal(result.cleanRelativeUrl.includes(probe.QUERY_PARAM), false);
});

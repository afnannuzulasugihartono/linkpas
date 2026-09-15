const test = require('node:test');
const assert = require('node:assert/strict');
const { evaluate, VERDICTS } = require('../src/twa-verdict.js');

const passingEvidence = {
  signerState: 'match',
  appLinksState: 'verified',
  twaCapabilityState: 'default_provider_supported',
  selectedBrowserProvider: 'com.android.chrome',
  twaCapableProvider: 'com.android.chrome',
  twaLaunchRequested: true,
  dalRelationshipState: 'true',
  fallbackInvoked: false,
  pwaProbeObserved: true,
};

test('all mandatory positive evidence yields PASS_VERIFIED_TWA', () => {
  assert.deepEqual(evaluate(passingEvidence), {
    verdict: VERDICTS.PASS_VERIFIED_TWA,
    reason: 'all_mandatory_evidence_positive',
  });
});

test('observed fallback wins over otherwise positive evidence', () => {
  assert.equal(evaluate({ ...passingEvidence, fallbackInvoked: true }).verdict, VERDICTS.FAIL_FALLBACK);
});

test('explicit DAL false yields FAIL_DAL', () => {
  assert.equal(evaluate({ ...passingEvidence, dalRelationshipState: 'false' }).verdict, VERDICTS.FAIL_DAL);
});

test('timeout or unknown DAL evidence can never become PASS', () => {
  assert.equal(evaluate({ ...passingEvidence, dalRelationshipState: 'inconclusive' }).verdict, VERDICTS.INCONCLUSIVE);
  assert.equal(evaluate({ ...passingEvidence, dalRelationshipState: '' }).verdict, VERDICTS.INCONCLUSIVE);
});

test('missing PWA correlation stays inconclusive until automatic evidence is explicitly exhausted', () => {
  assert.equal(evaluate({ ...passingEvidence, pwaProbeObserved: false }).verdict, VERDICTS.INCONCLUSIVE);
  assert.equal(
    evaluate({ ...passingEvidence, pwaProbeObserved: false, automaticEvidenceExhausted: true }).verdict,
    VERDICTS.NEEDS_ADB,
  );
});

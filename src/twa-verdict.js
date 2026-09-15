((root, factory) => {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.LinkPasTwaVerdict = api;
})(typeof window !== 'undefined' ? window : null, () => {
  const VERDICTS = Object.freeze({
    PASS_VERIFIED_TWA: 'PASS_VERIFIED_TWA',
    FAIL_FALLBACK: 'FAIL_FALLBACK',
    FAIL_DAL: 'FAIL_DAL',
    INCONCLUSIVE: 'INCONCLUSIVE',
    NEEDS_ADB: 'NEEDS_ADB',
  });

  const EXPLICIT_APP_LINK_FAILURES = new Set([
    'user_selected',
    'none',
    'missing_host',
    'no_declared_domains',
    'package_not_found',
  ]);

  function result(verdict, reason) {
    return { verdict, reason };
  }

  function sameProvider(a, b) {
    return typeof a === 'string'
      && typeof b === 'string'
      && a.length > 0
      && b.length > 0
      && a === b;
  }

  function providerCapabilityPositive(evidence) {
    if (evidence.twaCapabilityState === 'default_provider_supported') return true;
    return evidence.twaCapabilityState === 'available_provider_only'
      && sameProvider(evidence.selectedBrowserProvider, evidence.twaCapableProvider);
  }

  function evaluate(evidence = {}) {
    const fallbackInvoked = evidence.fallbackInvoked === true || evidence.fallbackState === 'invoked';
    if (fallbackInvoked) {
      return result(VERDICTS.FAIL_FALLBACK, 'custom_tab_fallback_observed');
    }

    const dalState = typeof evidence.dalRelationshipState === 'string'
      ? evidence.dalRelationshipState.toLowerCase()
      : '';
    if (dalState === 'false') {
      return result(VERDICTS.FAIL_DAL, 'browser_dal_rejected');
    }

    if (evidence.signerState === 'mismatch') {
      return result(VERDICTS.FAIL_DAL, 'installed_signer_mismatch');
    }

    if (EXPLICIT_APP_LINK_FAILURES.has(evidence.appLinksState)) {
      return result(VERDICTS.FAIL_DAL, `app_links_${evidence.appLinksState}`);
    }

    const pass = evidence.signerState === 'match'
      && evidence.appLinksState === 'verified'
      && providerCapabilityPositive(evidence)
      && evidence.twaLaunchRequested === true
      && dalState === 'true'
      && evidence.fallbackInvoked === false
      && evidence.pwaProbeObserved === true;

    if (pass) {
      return result(VERDICTS.PASS_VERIFIED_TWA, 'all_mandatory_evidence_positive');
    }

    let reason = 'automatic_evidence_incomplete';
    if (dalState !== 'true') reason = 'dal_inconclusive';
    else if (evidence.signerState !== 'match') reason = 'signer_inconclusive';
    else if (evidence.appLinksState !== 'verified') reason = 'app_links_inconclusive';
    else if (!providerCapabilityPositive(evidence)) reason = 'twa_provider_capability_inconclusive';
    else if (evidence.twaLaunchRequested !== true) reason = 'twa_launch_request_missing';
    else if (evidence.fallbackInvoked !== false) reason = 'fallback_state_inconclusive';
    else if (evidence.pwaProbeObserved !== true) reason = 'pwa_probe_missing';

    if (evidence.automaticEvidenceExhausted === true) {
      return result(VERDICTS.NEEDS_ADB, reason);
    }
    return result(VERDICTS.INCONCLUSIVE, reason);
  }

  return { VERDICTS, evaluate, providerCapabilityPositive };
});

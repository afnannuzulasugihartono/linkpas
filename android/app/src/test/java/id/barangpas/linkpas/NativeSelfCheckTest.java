package id.barangpas.linkpas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeSelfCheckTest {

    @Test
    public void appLinksBelowAndroid12IsUnsupportedRatherThanGuessed() {
        assertEquals("unsupported_api", NativeSelfCheck.mapDomainStateForSdk(30, 2));
    }

    @Test
    public void appLinksKnownStatesRemainDistinct() {
        assertEquals("none", NativeSelfCheck.mapDomainStateForSdk(31, 0));
        assertEquals("user_selected", NativeSelfCheck.mapDomainStateForSdk(31, 1));
        assertEquals("verified", NativeSelfCheck.mapDomainStateForSdk(31, 2));
        assertEquals("missing_host", NativeSelfCheck.mapDomainStateForSdk(31, null));
        assertEquals("unknown_state", NativeSelfCheck.mapDomainStateForSdk(31, 99));
    }

    @Test
    public void twaCapabilityDoesNotPromoteUnknownToSupported() {
        assertEquals("query_failed",
                NativeSelfCheck.classifyTwaCapability(false, false, false));
        assertEquals("not_advertised",
                NativeSelfCheck.classifyTwaCapability(true, false, false));
        assertEquals("available_provider_only",
                NativeSelfCheck.classifyTwaCapability(true, false, true));
        assertEquals("default_provider_supported",
                NativeSelfCheck.classifyTwaCapability(true, true, true));
    }

    @Test
    public void fingerprintComparisonNormalizesSeparatorsAndCase() {
        String canonical = "61:A0:47:4A:18:10:42:11";
        assertTrue(NativeSelfCheck.fingerprintMatches(canonical, "61a0474a18104211"));
        assertFalse(NativeSelfCheck.fingerprintMatches(canonical, "00a0474a18104211"));
        assertFalse(NativeSelfCheck.fingerprintMatches(canonical, null));
    }
}

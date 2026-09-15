package id.barangpas.linkpas;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BrowserTwaEvidenceTest {

    @Test
    public void relationshipCallbackPreservesTrueAndFalse() {
        assertEquals("true", BrowserTwaEvidence.relationshipState(Boolean.TRUE));
        assertEquals("false", BrowserTwaEvidence.relationshipState(Boolean.FALSE));
    }

    @Test
    public void missingCallbackRemainsInconclusiveRatherThanFalse() {
        assertEquals("inconclusive", BrowserTwaEvidence.relationshipState(null));
        assertEquals("timeout", BrowserTwaEvidence.relationshipDetail(null, "timeout"));
    }

    @Test
    public void rejectedOrUnavailableValidationRemainsInconclusive() {
        assertEquals("inconclusive", BrowserTwaEvidence.relationshipState(null));
        assertEquals("request_rejected",
                BrowserTwaEvidence.relationshipDetail(null, "request_rejected"));
        assertEquals("unknown", BrowserTwaEvidence.relationshipDetail(null, null));
    }

    @Test
    public void fallbackEvidenceIsExplicit() {
        assertEquals("invoked", BrowserTwaEvidence.fallbackState(true));
        assertEquals("not_observed", BrowserTwaEvidence.fallbackState(false));
    }
}

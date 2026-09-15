package id.barangpas.linkpas;

final class BrowserTwaEvidence {
    static final String RELATIONSHIP_TRUE = "true";
    static final String RELATIONSHIP_FALSE = "false";
    static final String RELATIONSHIP_INCONCLUSIVE = "inconclusive";

    private BrowserTwaEvidence() {}

    static String relationshipState(Boolean result) {
        if (result == null) return RELATIONSHIP_INCONCLUSIVE;
        return result ? RELATIONSHIP_TRUE : RELATIONSHIP_FALSE;
    }

    static String relationshipDetail(Boolean result, String unresolvedReason) {
        if (result != null) return "callback_result";
        if (unresolvedReason == null || unresolvedReason.trim().isEmpty()) {
            return "unknown";
        }
        return unresolvedReason;
    }

    static String fallbackState(boolean invoked) {
        return invoked ? "invoked" : "not_observed";
    }
}

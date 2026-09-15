package id.barangpas.linkpas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeDiagnosticsTest {

    @Test
    public void sanitizerRedactsUserContentPatterns() {
        String input = "buyer@example.com https://shop.example/item/123 ABCDEFGHIJKLMNOPQRSTUVWXYZ123456";
        String output = NativeDiagnosticSanitizer.redact(input, 1000);

        assertTrue(output.contains("[email]"));
        assertTrue(output.contains("[url]"));
        assertTrue(output.contains("[token]"));
        assertFalse(output.contains("buyer@example.com"));
        assertFalse(output.contains("shop.example"));
    }

    @Test
    public void transportFailureReturnsNullInsteadOfThrowing() {
        String reportId = NativeDiagnosticTransport.post(
                "http://127.0.0.1:1/unavailable",
                "public-test-key",
                "{\"source\":\"android\"}");
        assertNull(reportId);
    }

    @Test
    public void missingConfigurationReturnsNull() {
        assertNull(NativeDiagnosticTransport.post("", "", "{}"));
    }
}

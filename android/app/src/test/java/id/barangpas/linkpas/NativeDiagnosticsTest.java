package id.barangpas.linkpas;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void automaticOutboxIsBoundedAndKeepsNewestEntriesInOrder() {
        String raw = "";
        for (int i = 0; i < 15; i += 1) {
            raw = AutomaticDiagnosticOutbox.append(raw, "payload-" + i);
        }

        assertEquals(12, AutomaticDiagnosticOutbox.size(raw));
        assertEquals("payload-3", AutomaticDiagnosticOutbox.first(raw));
        raw = AutomaticDiagnosticOutbox.dropFirst(raw);
        assertEquals(11, AutomaticDiagnosticOutbox.size(raw));
        assertEquals("payload-4", AutomaticDiagnosticOutbox.first(raw));
    }

    @Test
    public void queuedPayloadKeepsOriginalProbeAcrossLaterLaunches() {
        String firstProbe = "123e4567-e89b-42d3-a456-426614174000";
        String nextProbe = "223e4567-e89b-42d3-a456-426614174001";

        assertEquals(firstProbe, LaunchProbeId.preferExisting(firstProbe, nextProbe));
        assertEquals(nextProbe, LaunchProbeId.preferExisting(null, nextProbe));
        assertNull(LaunchProbeId.preferExisting("buyer@example.com", "not-a-probe"));
    }
}

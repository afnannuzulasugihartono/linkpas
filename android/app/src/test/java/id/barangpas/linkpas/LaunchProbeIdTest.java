package id.barangpas.linkpas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LaunchProbeIdTest {
    @Test
    public void generatedProbeIsBoundedRandomUuid() {
        String probe = LaunchProbeId.create();
        assertTrue(LaunchProbeId.isValid(probe));
        assertTrue(probe.length() == 36);
    }

    @Test
    public void invalidProbeNeverBecomesCurrent() {
        LaunchProbeId.setCurrent("buyer@example.com");
        assertNull(LaunchProbeId.current());
        assertFalse(LaunchProbeId.isValid("buyer@example.com"));
    }

    @Test
    public void validProbeCanBeHeldOnlyInProcessMemory() {
        String probe = "123e4567-e89b-42d3-a456-426614174000";
        LaunchProbeId.setCurrent(probe);
        assertTrue(LaunchProbeId.isValid(LaunchProbeId.current()));
    }
}

package id.barangpas.linkpas;

import java.util.UUID;
import java.util.regex.Pattern;

final class LaunchProbeId {
    static final String QUERY_PARAM = "__linkpas_probe";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static volatile String current;

    private LaunchProbeId() {}

    static String create() {
        return UUID.randomUUID().toString();
    }

    static boolean isValid(String value) {
        return value != null && value.length() == 36 && UUID_PATTERN.matcher(value).matches();
    }

    static void setCurrent(String value) {
        current = isValid(value) ? value : null;
    }

    static String current() {
        return current;
    }

    static String preferExisting(String existingProbe, String currentProbe) {
        if (isValid(existingProbe)) return existingProbe;
        return isValid(currentProbe) ? currentProbe : null;
    }
}

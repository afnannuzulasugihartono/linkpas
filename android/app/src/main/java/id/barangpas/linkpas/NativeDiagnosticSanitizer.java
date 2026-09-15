package id.barangpas.linkpas;

final class NativeDiagnosticSanitizer {
    private NativeDiagnosticSanitizer() {}

    static String redact(String value, int maxLength) {
        if (value == null) return null;
        String out = value.length() > maxLength ? value.substring(0, maxLength) : value;
        out = out.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]");
        out = out.replaceAll("https?://[^\\s)\\]}>'\\\"]+", "[url]");
        out = out.replaceAll("\\b[A-Za-z0-9_-]{24,}\\b", "[token]");
        return out;
    }
}

package id.barangpas.linkpas;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NativeDiagnosticTransport {
    private static final Pattern REPORT_ID = Pattern.compile("\\\"report_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private NativeDiagnosticTransport() {}

    static String post(String endpoint, String apiKey, String payload) {
        HttpURLConnection connection = null;
        try {
            if (endpoint == null || endpoint.isEmpty() || apiKey == null || apiKey.isEmpty()) return null;
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(1200);
            connection.setReadTimeout(1800);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Cache-Control", "no-store");

            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            String body = readAll(connection.getInputStream());
            Matcher matcher = REPORT_ID.matcher(body);
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}

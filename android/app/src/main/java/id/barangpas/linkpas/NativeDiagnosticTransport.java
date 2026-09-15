package id.barangpas.linkpas;

import org.json.JSONObject;

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
    private static volatile String lastFailureCode = "none";

    private NativeDiagnosticTransport() {}

    static String post(String endpoint, String apiKey, String payload) {
        HttpURLConnection connection = null;
        lastFailureCode = "none";
        try {
            if (endpoint == null || endpoint.isEmpty() || apiKey == null || apiKey.isEmpty()) {
                lastFailureCode = "missing_config";
                return null;
            }
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(1200);
            connection.setReadTimeout(1800);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Cache-Control", "no-store");

            String correlatedPayload = withCurrentLaunchProbe(payload);
            byte[] bytes = correlatedPayload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                lastFailureCode = "http_" + status;
                return null;
            }
            String body = readAll(connection.getInputStream());
            Matcher matcher = REPORT_ID.matcher(body);
            if (!matcher.find()) {
                lastFailureCode = "missing_report_id";
                return null;
            }
            lastFailureCode = "ok";
            return matcher.group(1);
        } catch (Exception error) {
            String name = error.getClass().getSimpleName();
            lastFailureCode = (name == null || name.isEmpty()) ? "transport_exception" : name.substring(0, Math.min(name.length(), 80));
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String getLastFailureCode() {
        return lastFailureCode;
    }

    private static String withCurrentLaunchProbe(String payload) {
        String probeId = LaunchProbeId.current();
        if (!LaunchProbeId.isValid(probeId) || payload == null || payload.isEmpty()) return payload;
        try {
            JSONObject root = new JSONObject(payload);
            String errorType = root.optString("error_type", "");
            if (!"native_self_check".equals(errorType)
                    && !"native_twa_evidence".equals(errorType)) {
                return payload;
            }
            JSONObject diagnostics = root.optJSONObject("diagnostics");
            if (diagnostics == null) {
                diagnostics = new JSONObject();
                root.put("diagnostics", diagnostics);
            }
            diagnostics.put("probe_id", probeId);
            return root.toString();
        } catch (Exception ignored) {
            return payload;
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

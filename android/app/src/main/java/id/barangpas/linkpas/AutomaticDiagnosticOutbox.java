package id.barangpas.linkpas;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class AutomaticDiagnosticOutbox {
    private static final String PREFS = "linkpas_native_diagnostics";
    private static final String KEY_OUTBOX = "automatic_outbox_v2";
    private static final String KEY_LAST_REPORT = "last_report_id";
    private static final String KEY_LAST_TRANSPORT_ERROR = "last_transport_error";
    private static final int MAX_ENTRIES = 12;
    private static final long[] RETRY_DELAYS_MS = {0L, 800L, 3000L};
    private static final Object LOCK = new Object();
    private static final AtomicBoolean FLUSHING = new AtomicBoolean(false);

    private AutomaticDiagnosticOutbox() {}

    static void enqueue(Context context, String payload) {
        if (context == null || payload == null || payload.isEmpty()) return;
        Context app = context.getApplicationContext();
        String correlated = NativeDiagnosticTransport.withCurrentLaunchProbe(payload);
        synchronized (LOCK) {
            try {
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String next = append(prefs.getString(KEY_OUTBOX, ""), correlated);
                prefs.edit().putString(KEY_OUTBOX, next).commit();
            } catch (Exception ignored) {
                return;
            }
        }
        flushAsync(app);
    }

    static void flushAsync(Context context) {
        if (context == null || !FLUSHING.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        new Thread(() -> flushLoop(app), "linkpas-auto-diag-outbox").start();
    }

    private static void flushLoop(Context context) {
        boolean transportFailed = false;
        try {
            while (true) {
                String payload = peek(context);
                if (payload == null) return;

                String reportId = null;
                for (long delay : RETRY_DELAYS_MS) {
                    if (delay > 0L) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            transportFailed = true;
                            return;
                        }
                    }
                    reportId = NativeDiagnosticTransport.post(
                            BuildConfig.DIAGNOSTICS_ENDPOINT,
                            BuildConfig.DIAGNOSTICS_PUBLISHABLE_KEY,
                            payload);
                    if (reportId != null) break;
                }

                if (reportId == null) {
                    transportFailed = true;
                    recordFailure(context, NativeDiagnosticTransport.getLastFailureCode());
                    return;
                }

                removeFirst(context);
                recordSuccess(context, reportId);
            }
        } finally {
            FLUSHING.set(false);
            if (!transportFailed && peek(context) != null) flushAsync(context);
        }
    }

    private static String peek(Context context) {
        synchronized (LOCK) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                return first(prefs.getString(KEY_OUTBOX, ""));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void removeFirst(Context context) {
        synchronized (LOCK) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String next = dropFirst(prefs.getString(KEY_OUTBOX, ""));
                prefs.edit().putString(KEY_OUTBOX, next).commit();
            } catch (Exception ignored) {
                // Keep diagnostics best effort only.
            }
        }
    }

    private static void recordSuccess(Context context, String reportId) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_LAST_TRANSPORT_ERROR)
                    .putString(KEY_LAST_REPORT, reportId)
                    .apply();
        } catch (Exception ignored) {
            // Reporting state must never affect app startup.
        }
    }

    private static void recordFailure(Context context, String failure) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_TRANSPORT_ERROR,
                            NativeDiagnosticSanitizer.redact(failure, 80))
                    .apply();
        } catch (Exception ignored) {
            // Reporting state must never affect app startup.
        }
    }

    static String append(String raw, String payload) {
        List<String> rows = parse(raw);
        if (payload != null && !payload.isEmpty()) rows.add(payload);
        while (rows.size() > MAX_ENTRIES) rows.remove(0);
        return serialize(rows);
    }

    static String first(String raw) {
        List<String> rows = parse(raw);
        return rows.isEmpty() ? null : rows.get(0);
    }

    static String dropFirst(String raw) {
        List<String> rows = parse(raw);
        if (!rows.isEmpty()) rows.remove(0);
        return serialize(rows);
    }

    static int size(String raw) {
        return parse(raw).size();
    }

    private static List<String> parse(String raw) {
        List<String> rows = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return rows;
        String[] encoded = raw.split("\\n");
        for (String item : encoded) {
            if (item == null || item.isEmpty()) continue;
            try {
                String decoded = URLDecoder.decode(item, StandardCharsets.UTF_8.name());
                if (!decoded.isEmpty()) rows.add(decoded);
            } catch (Exception ignored) {
                // Drop malformed local diagnostics state rather than blocking startup.
            }
        }
        return rows;
    }

    private static String serialize(List<String> rows) {
        StringBuilder out = new StringBuilder();
        for (String row : rows) {
            if (row == null || row.isEmpty()) continue;
            try {
                String encoded = URLEncoder.encode(row, StandardCharsets.UTF_8.name());
                if (out.length() > 0) out.append('\n');
                out.append(encoded);
            } catch (Exception ignored) {
                // UTF-8 is always available; ignore any unexpected local encoding failure.
            }
        }
        return out.toString();
    }
}

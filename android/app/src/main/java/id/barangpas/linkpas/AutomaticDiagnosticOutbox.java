package id.barangpas.linkpas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.concurrent.atomic.AtomicBoolean;

final class AutomaticDiagnosticOutbox {
    private static final String PREFS = "linkpas_native_diagnostics";
    private static final String KEY_OUTBOX = "automatic_outbox_v1";
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
                String next = append(prefs.getString(KEY_OUTBOX, "[]"), correlated);
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
            // Catch an item enqueued during the small window between the last peek and FLUSHING=false.
            if (!transportFailed && peek(context) != null) flushAsync(context);
        }
    }

    private static String peek(Context context) {
        synchronized (LOCK) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                return first(prefs.getString(KEY_OUTBOX, "[]"));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void removeFirst(Context context) {
        synchronized (LOCK) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String next = dropFirst(prefs.getString(KEY_OUTBOX, "[]"));
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
        JSONArray source = parse(raw);
        JSONArray next = new JSONArray();
        int start = Math.max(0, source.length() - (MAX_ENTRIES - 1));
        for (int i = start; i < source.length(); i++) {
            String value = source.optString(i, null);
            if (value != null && !value.isEmpty()) next.put(value);
        }
        if (payload != null && !payload.isEmpty()) next.put(payload);
        return next.toString();
    }

    static String first(String raw) {
        JSONArray rows = parse(raw);
        String value = rows.optString(0, null);
        return value == null || value.isEmpty() ? null : value;
    }

    static String dropFirst(String raw) {
        JSONArray rows = parse(raw);
        JSONArray next = new JSONArray();
        for (int i = 1; i < rows.length(); i++) {
            String value = rows.optString(i, null);
            if (value != null && !value.isEmpty()) next.put(value);
        }
        return next.toString();
    }

    static int size(String raw) {
        return parse(raw).length();
    }

    private static JSONArray parse(String raw) {
        try {
            return raw == null || raw.isEmpty() ? new JSONArray() : new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}

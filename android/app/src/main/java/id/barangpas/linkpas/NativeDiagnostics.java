package id.barangpas.linkpas;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativeDiagnostics {
    private static final String PREFS = "linkpas_native_diagnostics";
    private static final String KEY_PENDING = "pending_report";
    private static final String KEY_STAGE = "last_stage";
    private static final String KEY_LAST_REPORT = "last_report_id";
    private static final String KEY_LAST_TRANSPORT_ERROR = "last_transport_error";
    private static final String KEY_TEST_LIFECYCLE = "d4b_test_lifecycle";
    private static final AtomicBoolean HANDLER_INSTALLED = new AtomicBoolean(false);

    private NativeDiagnostics() {}

    static boolean isControlledTest(Intent launchIntent) {
        return BuildConfig.VERSION_NAME.contains("-beta")
                && launchIntent != null
                && launchIntent.getBooleanExtra("linkpas_native_diag_test", false);
    }

    static void install(Context context, Intent launchIntent) {
        Context app = context.getApplicationContext();
        markStage(app, "launcher_init");
        installCrashHandler(app);
        flushPendingAsync(app);
    }

    static void emitControlledTestAsync(Context context, Intent launchIntent, Runnable completion) {
        Context app = context.getApplicationContext();
        markControlledTestLifecycle(app, "send_enqueued");
        emitAsync(app, buildPayload(app, launchIntent, "native_controlled_test",
                "Controlled Android diagnostics event", null, "controlled_test"), true, completion);
    }

    static void markStage(Context context, String stage) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_STAGE, NativeDiagnosticSanitizer.redact(stage, 80)).apply();
        } catch (Exception ignored) {
            // Diagnostics must never block startup.
        }
    }

    private static void markControlledTestLifecycle(Context context, String marker) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TEST_LIFECYCLE, NativeDiagnosticSanitizer.redact(marker, 80))
                    .commit();
        } catch (Exception ignored) {
            // Validation-only marker must never affect app flow.
        }
    }

    private static void installCrashHandler(Context context) {
        if (!HANDLER_INSTALLED.compareAndSet(false, true)) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String stage = prefs.getString(KEY_STAGE, "unknown");
                String message = throwable == null ? "Native uncaught exception" : throwable.getMessage();
                String stack = throwable == null ? null : stackTrace(throwable);
                String payload = buildPayload(context, null, "native_uncaught_exception",
                        message == null ? "Native uncaught exception" : message, stack, stage);
                prefs.edit().putString(KEY_PENDING, payload).commit();
            } catch (Exception ignored) {
                // Preserve the original crash path even if diagnostics persistence fails.
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    private static void flushPendingAsync(Context context) {
        SharedPreferences prefs;
        try {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Exception ignored) {
            return;
        }
        String pending = prefs.getString(KEY_PENDING, null);
        if (pending == null || pending.isEmpty()) return;
        new Thread(() -> {
            String reportId = NativeDiagnosticTransport.post(
                    BuildConfig.DIAGNOSTICS_ENDPOINT,
                    BuildConfig.DIAGNOSTICS_PUBLISHABLE_KEY,
                    pending);
            if (reportId != null) {
                prefs.edit()
                        .remove(KEY_PENDING)
                        .remove(KEY_LAST_TRANSPORT_ERROR)
                        .putString(KEY_LAST_REPORT, reportId)
                        .apply();
            } else {
                prefs.edit()
                        .putString(KEY_LAST_TRANSPORT_ERROR,
                                NativeDiagnosticSanitizer.redact(NativeDiagnosticTransport.getLastFailureCode(), 80))
                        .apply();
            }
        }, "linkpas-diag-flush").start();
    }

    private static void emitAsync(Context context, String payload, boolean controlledTest, Runnable completion) {
        new Thread(() -> {
            try {
                if (controlledTest) markControlledTestLifecycle(context, "send_thread_started");
                String reportId = NativeDiagnosticTransport.post(
                        BuildConfig.DIAGNOSTICS_ENDPOINT,
                        BuildConfig.DIAGNOSTICS_PUBLISHABLE_KEY,
                        payload);
                if (controlledTest) markControlledTestLifecycle(context, "transport_returned");
                try {
                    SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
                    if (reportId != null) {
                        editor.remove(KEY_LAST_TRANSPORT_ERROR).putString(KEY_LAST_REPORT, reportId).commit();
                    } else {
                        editor.putString(KEY_LAST_TRANSPORT_ERROR,
                                NativeDiagnosticSanitizer.redact(NativeDiagnosticTransport.getLastFailureCode(), 80)).commit();
                    }
                } catch (Exception ignored) {
                    // Reporting state must not affect app flow.
                }
            } finally {
                if (completion != null) {
                    try {
                        completion.run();
                    } catch (Exception ignored) {
                        // Completion is best effort and must not crash diagnostics.
                    }
                }
            }
        }, "linkpas-diag-send").start();
    }

    private static String buildPayload(Context context, Intent launchIntent, String errorType,
                                       String errorMessage, String errorStack, String stage) {
        try {
            JSONObject root = new JSONObject();
            root.put("source", "android");
            root.put("app_version", BuildConfig.VERSION_NAME);
            root.put("build_version", String.valueOf(BuildConfig.VERSION_CODE));
            root.put("package_name", context.getPackageName());
            root.put("platform", "android");
            root.put("platform_version", "sdk-" + Build.VERSION.SDK_INT);
            String provider = resolveBrowserProvider(context);
            root.put("browser", provider);
            root.put("error_type", NativeDiagnosticSanitizer.redact(errorType, 100));
            root.put("error_message", NativeDiagnosticSanitizer.redact(errorMessage, 1000));
            if (errorStack != null) root.put("error_stack", NativeDiagnosticSanitizer.redact(errorStack, 6000));

            Uri launchUri = sanitizedLaunchUri(launchIntent);
            root.put("page", "https://" + launchUri.getHost() + safePath(launchUri.getPath()));
            root.put("service_worker_state", "not_applicable_native");

            JSONArray breadcrumbs = new JSONArray();
            JSONObject crumb = new JSONObject();
            crumb.put("event", "native_stage");
            crumb.put("at", nowIso());
            crumb.put("state", NativeDiagnosticSanitizer.redact(stage, 80));
            breadcrumbs.put(crumb);
            root.put("breadcrumbs", breadcrumbs);

            JSONObject diagnostics = new JSONObject();
            diagnostics.put("app_stage", NativeDiagnosticSanitizer.redact(stage, 80));
            diagnostics.put("twa_status", "launch_requested_not_verified");
            diagnostics.put("browser_provider", NativeDiagnosticSanitizer.redact(provider, 120));
            diagnostics.put("release_channel", "beta");
            diagnostics.put("launch_url_host", launchUri.getHost());
            diagnostics.put("launch_url_path", safePath(launchUri.getPath()));
            root.put("diagnostics", diagnostics);
            return root.toString();
        } catch (Exception ignored) {
            return "{\"source\":\"android\",\"error_type\":\"native_payload_build_failed\"}";
        }
    }

    private static Uri sanitizedLaunchUri(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "https".equalsIgnoreCase(data.getScheme()) && data.getHost() != null) {
            return new Uri.Builder().scheme("https").authority(data.getHost()).path(safePath(data.getPath())).build();
        }
        return Uri.parse("https://linkpas.vercel.app/");
    }

    private static String safePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        return path.length() > 300 ? path.substring(0, 300) : path;
    }

    private static String resolveBrowserProvider(Context context) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"));
            ResolveInfo resolved = context.getPackageManager().resolveActivity(browserIntent, 0);
            if (resolved != null && resolved.activityInfo != null && resolved.activityInfo.packageName != null) {
                return resolved.activityInfo.packageName;
            }
        } catch (Exception ignored) {
            // Provider detection is best effort only.
        }
        return "unresolved";
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}

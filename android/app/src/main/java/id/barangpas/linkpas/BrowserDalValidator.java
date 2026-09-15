package id.barangpas.linkpas;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import java.util.concurrent.atomic.AtomicBoolean;

final class BrowserDalValidator {
    private static final long CALLBACK_TIMEOUT_MS = 4000L;

    interface ResultCallback {
        void onResult(String relationshipState, String detail);
    }

    private BrowserDalValidator() {}

    static void validateAsync(Context context,
                              @Nullable String providerPackage,
                              ResultCallback resultCallback) {
        String provider = providerPackage == null ? "" : providerPackage.trim();
        if (provider.isEmpty() || "unresolved".equals(provider)) {
            resultCallback.onResult(
                    BrowserTwaEvidence.RELATIONSHIP_INCONCLUSIVE,
                    "provider_unavailable");
            return;
        }

        Context app = context.getApplicationContext();
        new Probe(app, provider, resultCallback).start();
    }

    private static final class Probe {
        private final Context context;
        private final String providerPackage;
        private final ResultCallback resultCallback;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private boolean bound;

        private final CustomTabsCallback customTabsCallback = new CustomTabsCallback() {
            @Override
            public void onRelationshipValidationResult(
                    @CustomTabsService.Relation int relation,
                    @NonNull Uri requestedOrigin,
                    boolean result,
                    @Nullable Bundle extras) {
                if (relation != CustomTabsService.RELATION_HANDLE_ALL_URLS) return;
                if (!isTargetOrigin(requestedOrigin)) return;
                finish(Boolean.valueOf(result), null);
            }
        };

        private final CustomTabsServiceConnection connection = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(
                    @NonNull ComponentName componentName,
                    @NonNull CustomTabsClient client) {
                try {
                    client.warmup(0L);
                    CustomTabsSession session = client.newSession(customTabsCallback);
                    if (session == null) {
                        finish(null, "session_unavailable");
                        return;
                    }

                    Uri origin = Uri.parse("https://" + NativeSelfCheck.HOST);
                    boolean submitted = session.validateRelationship(
                            CustomTabsService.RELATION_HANDLE_ALL_URLS,
                            origin,
                            null);
                    if (!submitted) {
                        finish(null, "request_rejected");
                        return;
                    }

                    mainHandler.postDelayed(
                            () -> finish(null, "timeout"),
                            CALLBACK_TIMEOUT_MS);
                } catch (RuntimeException ignored) {
                    finish(null, "validation_exception");
                }
            }

            @Override
            public void onServiceDisconnected(@NonNull ComponentName componentName) {
                finish(null, "service_disconnected");
            }
        };

        Probe(Context context, String providerPackage, ResultCallback resultCallback) {
            this.context = context;
            this.providerPackage = providerPackage;
            this.resultCallback = resultCallback;
        }

        void start() {
            mainHandler.post(() -> {
                try {
                    bound = CustomTabsClient.bindCustomTabsService(
                            context,
                            providerPackage,
                            connection);
                    if (!bound) {
                        finish(null, "bind_failed");
                    }
                } catch (RuntimeException ignored) {
                    finish(null, "bind_exception");
                }
            });
        }

        private void finish(@Nullable Boolean result, @Nullable String unresolvedReason) {
            if (!completed.compareAndSet(false, true)) return;
            mainHandler.removeCallbacksAndMessages(null);

            if (bound) {
                try {
                    context.unbindService(connection);
                } catch (RuntimeException ignored) {
                    // Probe cleanup is best effort only.
                }
                bound = false;
            }

            resultCallback.onResult(
                    BrowserTwaEvidence.relationshipState(result),
                    BrowserTwaEvidence.relationshipDetail(result, unresolvedReason));
        }

        private static boolean isTargetOrigin(Uri uri) {
            return uri != null
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && NativeSelfCheck.HOST.equalsIgnoreCase(uri.getHost());
        }
    }
}

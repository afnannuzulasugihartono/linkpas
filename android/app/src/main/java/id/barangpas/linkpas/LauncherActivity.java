package id.barangpas.linkpas;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.androidbrowserhelper.trusted.TwaLauncher;

import java.util.concurrent.atomic.AtomicBoolean;

public class LauncherActivity
        extends com.google.androidbrowserhelper.trusted.LauncherActivity {

    private static final long CONTROLLED_TEST_MAX_DELAY_MS = 5000L;

    private final AtomicBoolean controlledLaunchRequested = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean controlledDiagnosticTest;
    private String selectedBrowserProvider = "unresolved";

    @Override
    protected boolean shouldLaunchImmediately() {
        return !controlledDiagnosticTest;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        controlledDiagnosticTest = NativeDiagnostics.isControlledTest(getIntent());
        NativeDiagnostics.install(this, getIntent());
        NativeDiagnostics.markStage(this, "launcher_on_create");
        super.onCreate(savedInstanceState);

        if (controlledDiagnosticTest) {
            NativeDiagnostics.markStage(this, "controlled_test_before_twa");
            NativeDiagnostics.emitControlledTestAsync(
                    this,
                    getIntent(),
                    () -> runOnUiThread(this::launchControlledTwaOnce));
            mainHandler.postDelayed(this::launchControlledTwaOnce, CONTROLLED_TEST_MAX_DELAY_MS);
        } else {
            NativeDiagnostics.markStage(this, "twa_launch_requested");
        }
    }

    @Override
    protected TwaLauncher createTwaLauncher() {
        TwaLauncher launcher = super.createTwaLauncher();
        String provider = launcher.getProviderPackage();
        selectedBrowserProvider = provider == null ? "unresolved" : provider;

        if (BuildConfig.VERSION_NAME.contains("-beta")) {
            NativeDiagnostics.emitTwaEvidenceAsync(
                    this,
                    selectedBrowserProvider,
                    BrowserTwaEvidence.RELATIONSHIP_INCONCLUSIVE,
                    "pending",
                    false,
                    "twa_launch_requested");

            BrowserDalValidator.validateAsync(
                    this,
                    provider,
                    (relationshipState, detail) -> NativeDiagnostics.emitTwaEvidenceAsync(
                            getApplicationContext(),
                            selectedBrowserProvider,
                            relationshipState,
                            detail,
                            false,
                            "dal_relationship_result"));
        }

        return launcher;
    }

    @Override
    protected TwaLauncher.FallbackStrategy getFallbackStrategy() {
        TwaLauncher.FallbackStrategy delegate = super.getFallbackStrategy();
        return (context, twaBuilder, providerPackage, completionCallback) -> {
            String provider = providerPackage == null
                    ? selectedBrowserProvider
                    : providerPackage;
            if (BuildConfig.VERSION_NAME.contains("-beta")) {
                NativeDiagnostics.emitTwaEvidenceAsync(
                        context,
                        provider,
                        BrowserTwaEvidence.RELATIONSHIP_INCONCLUSIVE,
                        "fallback_before_or_without_relationship_callback",
                        true,
                        "custom_tab_fallback");
            }
            delegate.launch(context, twaBuilder, providerPackage, completionCallback);
        };
    }

    private void launchControlledTwaOnce() {
        if (!controlledLaunchRequested.compareAndSet(false, true)) return;
        if (isFinishing()) return;
        NativeDiagnostics.markStage(this, "controlled_test_twa_launch");
        launchTwa();
    }
}

package id.barangpas.linkpas;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

public class LauncherActivity
        extends com.google.androidbrowserhelper.trusted.LauncherActivity {

    private static final long CONTROLLED_TEST_MAX_DELAY_MS = 5000L;

    private final AtomicBoolean controlledLaunchRequested = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean controlledDiagnosticTest;

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

    private void launchControlledTwaOnce() {
        if (!controlledLaunchRequested.compareAndSet(false, true)) return;
        if (isFinishing()) return;
        NativeDiagnostics.markStage(this, "controlled_test_twa_launch");
        launchTwa();
    }
}

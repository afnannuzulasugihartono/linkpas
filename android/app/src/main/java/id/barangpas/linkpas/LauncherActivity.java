package id.barangpas.linkpas;

import android.os.Bundle;

public class LauncherActivity
        extends com.google.androidbrowserhelper.trusted.LauncherActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        NativeDiagnostics.install(this, getIntent());
        NativeDiagnostics.markStage(this, "launcher_on_create");
        super.onCreate(savedInstanceState);
        NativeDiagnostics.markStage(this, "twa_launch_requested");
    }
}

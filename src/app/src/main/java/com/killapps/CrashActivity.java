package com.killapps;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Activity shown when an uncaught exception crashes the app.
 * Displays the full crash log and provides Copy / Exit buttons.
 * Runs in a separate process (":crash") so it survives the main process death.
 */
public class CrashActivity extends AppCompatActivity {

    public static final String EXTRA_CRASH_LOG = "crash_log";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        TextView tvCrashLog = findViewById(R.id.tvCrashLog);
        MaterialButton btnCopy = findViewById(R.id.btnCopyLog);
        MaterialButton btnExit = findViewById(R.id.btnExit);

        // Get crash log from intent
        String crashLog = "";
        if (getIntent() != null && getIntent().hasExtra(EXTRA_CRASH_LOG)) {
            crashLog = getIntent().getStringExtra(EXTRA_CRASH_LOG);
        }

        tvCrashLog.setText(crashLog);

        // Copy button
        final String logToCopy = crashLog;
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("KillApps Crash Log", logToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Exit button
        btnExit.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to the crashed state — force exit
        finishAffinity();
        System.exit(0);
    }
}

package com.killapps;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinearLayout appListContainer;
    private TextView tvStatus;
    private TextView tvAppCount;
    private MaterialButton btnKillAll;
    private MaterialButton btnEnableAccessibility;
    private List<AppItem> appItems = new ArrayList<>();

    private static class AppItem {
        String packageName;
        String label;
        boolean selected;
        CheckBox checkBox;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appListContainer = findViewById(R.id.appListContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvAppCount = findViewById(R.id.tvAppCount);
        btnKillAll = findViewById(R.id.btnKillAll);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);

        btnKillAll.setOnClickListener(v -> startKilling());
        btnEnableAccessibility.setOnClickListener(v -> openAccessibilitySettings());

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        checkOverlayPermission();
    }

    /**
     * Load all installed user apps and display them as checkboxes.
     */
    private void loadApps() {
        appListContainer.removeAllViews();
        appItems.clear();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);

        for (ApplicationInfo info : installedApps) {
            // Skip system apps
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            // Skip ourselves
            if (info.packageName.equals(getPackageName())) continue;

            AppItem item = new AppItem();
            item.packageName = info.packageName;
            item.label = pm.getApplicationLabel(info).toString();
            item.selected = true; // Selected by default

            // Create a checkbox view for this app
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(item.label);
            checkBox.setTextColor(0xFFCCCCCC);
            checkBox.setChecked(true);
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFFF1744));
            checkBox.setPadding(8, 12, 8, 12);
            checkBox.setOnCheckedChangeListener((btn, isChecked) -> item.selected = isChecked);

            item.checkBox = checkBox;
            appItems.add(item);
            appListContainer.addView(checkBox);
        }

        tvAppCount.setText(appItems.size() + " apps found");
    }

    /**
     * Start the force-stop pipeline for all selected apps.
     */
    private void startKilling() {
        // Check Accessibility Service
        if (!AppKillerService.isServiceActive()) {
            tvStatus.setText(getString(R.string.status_no_accessibility));
            Toast.makeText(this, "Please enable the Accessibility Service first", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        // Check Overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant the Overlay permission", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        // Collect selected apps
        List<String> selectedPackages = new ArrayList<>();
        for (AppItem item : appItems) {
            if (item.selected) {
                selectedPackages.add(item.packageName);
            }
        }

        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, "No apps selected", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText(getString(R.string.status_running));
        btnKillAll.setEnabled(false);

        ForceStopEngine engine = ForceStopEngine.getInstance();
        engine.start(getApplicationContext(), selectedPackages, new ForceStopEngine.Listener() {
            @Override
            public void onCompleted(int closedCount) {
                runOnUiThread(() -> {
                    tvStatus.setText(String.format(getString(R.string.status_done), closedCount));
                    btnKillAll.setEnabled(true);
                    // Refresh the app list
                    loadApps();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText("Error: " + message);
                    btnKillAll.setEnabled(true);
                });
            }
        });
    }

    /**
     * Update UI based on whether the Accessibility Service is active.
     */
    private void updateAccessibilityStatus() {
        if (AppKillerService.isServiceActive()) {
            btnEnableAccessibility.setVisibility(View.GONE);
            btnKillAll.setEnabled(true);
        } else {
            btnEnableAccessibility.setVisibility(View.VISIBLE);
            tvStatus.setText(getString(R.string.status_no_accessibility));
        }
    }

    /**
     * Check and request overlay permission if needed.
     */
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required for the progress screen", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Open Android Accessibility Settings to enable the service.
     */
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}

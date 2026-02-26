package com.killapps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * ForceStopEngine - The core UI automation pipeline.
 *
 * Replicates the Kill Apps technique:
 * 1. Open each app's Settings App Info page via Intent
 * 2. Use AccessibilityNodeInfo to find the "Force Stop" button
 * 3. Click it programmatically
 * 4. Find and click the "OK" confirmation dialog button
 * 5. Move to the next app
 *
 * Multi-language support:
 * - Tier 1: Read localized string from the system com.android.settings APK
 * - Tier 2: Hardcoded fallback strings (English, Spanish, Portuguese, French, German)
 * - Tier 3: Cache the successful string for remaining apps
 */
public class ForceStopEngine {

    private static final String TAG = "ForceStopEngine";
    private static ForceStopEngine sInstance;

    // Pipeline states
    private static final int STATE_IDLE = 0;
    private static final int STATE_OPENING_SETTINGS = 1;
    private static final int STATE_WAITING_FORCE_STOP = 2;
    private static final int STATE_WAITING_CONFIRM = 3;

    private volatile boolean mRunning = false;
    private int mState = STATE_IDLE;
    private List<String> mAppsToKill = new ArrayList<>();
    private int mCurrentIndex = 0;
    private int mClosedCount = 0;
    private String mCachedForceStopText = null;
    private Context mContext;
    private Handler mHandler;
    private ProgressOverlay mOverlay;
    private Listener mListener;
    private String mSettingsPackage;
    private Runnable mTimeoutRunnable;

    // Confirmation dialog button resource IDs
    private static final String[] CONFIRM_BUTTON_IDS = {
            "android:id/button1",
            "com.android.settings:id/button1",
            "android:id/button2"
    };

    public interface Listener {
        void onCompleted(int closedCount);
        void onError(String message);
    }

    private ForceStopEngine() {}

    public static ForceStopEngine getInstance() {
        if (sInstance == null) {
            sInstance = new ForceStopEngine();
        }
        return sInstance;
    }

    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Start the force-stop pipeline for the given list of package names.
     */
    public void start(Context context, List<String> packageNames, Listener listener) {
        if (mRunning) return;

        mContext = context;
        mListener = listener;
        mHandler = new Handler(Looper.getMainLooper());
        mAppsToKill = new ArrayList<>(packageNames);
        mCurrentIndex = 0;
        mClosedCount = 0;
        mState = STATE_IDLE;
        mCachedForceStopText = null;
        mRunning = true;

        // Resolve the Settings app package name
        mSettingsPackage = getSettingsPackageName(context);

        // Remove ourselves and Settings from the kill list
        mAppsToKill.remove(context.getPackageName());
        mAppsToKill.remove(mSettingsPackage);

        if (mAppsToKill.isEmpty()) {
            mRunning = false;
            if (mListener != null) mListener.onCompleted(0);
            return;
        }

        // Show the progress overlay using AccessibilityService context if available
        Context overlayContext = AppKillerService.getInstance() != null ? AppKillerService.getInstance() : context;
        mOverlay = new ProgressOverlay(overlayContext);
        mOverlay.show();
        mOverlay.updateProgress(0, mAppsToKill.size(), "");

        // Start processing the first app
        mHandler.postDelayed(this::processNextApp, 300);
    }

    /**
     * Cancel the running kill operation.
     */
    public void stop() {
        Log.d(TAG, "stop()");
        mRunning = false;
        mState = STATE_IDLE;
        if (mTimeoutRunnable != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
        }
        if (mOverlay != null) {
            mOverlay.hide();
            mOverlay = null;
        }
        // Go back to home
        AppKillerService.performHome();
    }

    /**
     * Process the next app in the queue.
     */
    private void processNextApp() {
        if (!mRunning) return;

        if (mCurrentIndex >= mAppsToKill.size()) {
            // All done
            Log.d(TAG, "All apps processed. Closed: " + mClosedCount);
            mRunning = false;
            mState = STATE_IDLE;

            // Navigate back to home
            AppKillerService.performBack();
            mHandler.postDelayed(() -> {
                AppKillerService.performHome();
                if (mOverlay != null) {
                    mOverlay.hide();
                    mOverlay = null;
                }
                if (mListener != null) mListener.onCompleted(mClosedCount);
            }, 500);
            return;
        }

        String packageName = mAppsToKill.get(mCurrentIndex);
        Log.d(TAG, "Processing [" + (mCurrentIndex + 1) + "/" + mAppsToKill.size() + "]: " + packageName);

        // Check if already stopped
        if (isAppStopped(packageName)) {
            Log.d(TAG, packageName + " is already stopped, skipping");
            mCurrentIndex++;
            mHandler.postDelayed(this::processNextApp, 100);
            return;
        }

        // Update overlay
        String appLabel = getAppLabel(packageName);
        if (mOverlay != null) {
            mOverlay.updateProgress(mCurrentIndex, mAppsToKill.size(), appLabel);
        }

        // Open the app's Settings page
        mState = STATE_OPENING_SETTINGS;
        openAppSettings(packageName);

        // Set a timeout in case the Settings page doesn't load
        mTimeoutRunnable = () -> {
            Log.w(TAG, "Timeout waiting for " + packageName + ", skipping...");
            mCurrentIndex++;
            mState = STATE_IDLE;
            processNextApp();
        };
        mHandler.postDelayed(mTimeoutRunnable, 4000);
    }

    /**
     * Open the Android Settings App Info page for a given package.
     */
    private void openAppSettings(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open settings for " + packageName, e);
            mCurrentIndex++;
            mHandler.postDelayed(this::processNextApp, 200);
        }
    }

    /**
     * Called by AppKillerService when an accessibility event fires.
     * This drives the state machine forward.
     */
    public void onAccessibilityEvent(AccessibilityEvent event, AccessibilityNodeInfo root) {
        if (!mRunning || root == null) return;

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        switch (mState) {
            case STATE_OPENING_SETTINGS:
            case STATE_WAITING_FORCE_STOP:
                handleForceStopSearch(root);
                break;

            case STATE_WAITING_CONFIRM:
                handleConfirmDialog(root);
                break;
        }
    }

    // ==== Vendor Detection ==== //
    private boolean isXiaomi() {
        String m = Build.MANUFACTURER.toLowerCase();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    private boolean isHuawei() {
        String m = Build.MANUFACTURER.toLowerCase();
        return m.contains("huawei") || m.contains("honor");
    }

    private boolean isSamsung() {
        String m = Build.MANUFACTURER.toLowerCase();
        return m.contains("samsung");
    }

    /**
     * STATE_WAITING_FORCE_STOP: Search for the "Force Stop" button in the accessibility tree.
     * Uses the 3-tier multi-language detection system.
     */
    private void handleForceStopSearch(AccessibilityNodeInfo root) {
        // Tier 1: Use cached text if available
        if (mCachedForceStopText != null) {
            AccessibilityNodeInfo button = findButtonByText(root, mCachedForceStopText);
            if (button != null) {
                clickForceStopButton(button);
                return;
            }
        }

        // Tier 2: Try system Settings string resource
        String systemString = getSystemForceStopString();
        if (systemString != null) {
            AccessibilityNodeInfo button = findButtonByText(root, systemString);
            if (button != null) {
                mCachedForceStopText = systemString;
                clickForceStopButton(button);
                return;
            }
        }

        // Tier 3: Try all hardcoded fallback strings
        String[] fallbacks = {
                mContext.getString(R.string.force_stop),
                mContext.getString(R.string.force_stop_2),
                mContext.getString(R.string.force_stop_3),
                mContext.getString(R.string.force_stop_4),
                mContext.getString(R.string.force_stop_5),
                mContext.getString(R.string.force_stop_6)
        };

        for (String text : fallbacks) {
            if (text == null) continue;
            AccessibilityNodeInfo button = findButtonByText(root, text);
            if (button != null) {
                mCachedForceStopText = text;
                clickForceStopButton(button);
                return;
            }
        }

        // Button not found yet - might be loading, wait for next event
        mState = STATE_WAITING_FORCE_STOP;
    }

    /**
     * Click the Force Stop button and transition to the confirmation state.
     */
    private void clickForceStopButton(AccessibilityNodeInfo button) {
        if (button.isEnabled() && button.isClickable()) {
            Log.d(TAG, "Force Stop button found and ENABLED - clicking");
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            mState = STATE_WAITING_CONFIRM;

            // Cancel the timeout since we found the button
            if (mTimeoutRunnable != null) {
                mHandler.removeCallbacks(mTimeoutRunnable);
            }

            // Set a new timeout for the confirmation dialog
            mTimeoutRunnable = () -> {
                Log.w(TAG, "Timeout waiting for confirm dialog, moving on...");
                mClosedCount++;
                mCurrentIndex++;
                mState = STATE_IDLE;
                processNextApp();
            };
            mHandler.postDelayed(mTimeoutRunnable, 3000);
        } else {
            Log.d(TAG, "Force Stop button found but DISABLED.");
            
            // Vendor handling for disabled button branch
            if (isXiaomi()) {
                Log.d(TAG, "[Xiaomi] Button disabled: forcefully skipping to next app.");
                if (mTimeoutRunnable != null) mHandler.removeCallbacks(mTimeoutRunnable);
                mCurrentIndex++;
                mState = STATE_IDLE;
                mHandler.postDelayed(this::processNextApp, 200);
            } else if (isHuawei() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "[Huawei] Button appears disabled, but forcing a secondary click attempt.");
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                mState = STATE_WAITING_CONFIRM;
                if (mTimeoutRunnable != null) mHandler.removeCallbacks(mTimeoutRunnable);
                mTimeoutRunnable = () -> {
                    mClosedCount++;
                    mCurrentIndex++;
                    mState = STATE_IDLE;
                    processNextApp();
                };
                mHandler.postDelayed(mTimeoutRunnable, 3000);
            } else {
                Log.d(TAG, "Button disabled = app already stopped or cannot be stopped. Skipping.");
                if (mTimeoutRunnable != null) mHandler.removeCallbacks(mTimeoutRunnable);
                mCurrentIndex++;
                mState = STATE_IDLE;
                mHandler.postDelayed(this::processNextApp, 200);
            }
        }
    }

    /**
     * STATE_WAITING_CONFIRM: Search for the "OK" button in the confirmation dialog.
     */
    private void handleConfirmDialog(AccessibilityNodeInfo root) {
        // Try resource IDs first (most reliable)
        List<String> buttonIds = new ArrayList<>();
        for (String id : CONFIRM_BUTTON_IDS) buttonIds.add(id);

        if (isSamsung() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            buttonIds.add("android:id/action1");
            buttonIds.add("com.android.settings:id/action1");
            buttonIds.add("android:id/action2");
            buttonIds.add("com.android.settings:id/action2");
            buttonIds.add("android:id/action3");
        }

        for (String resId : buttonIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(resId);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isClickable() && node.isEnabled()) {
                        Log.d(TAG, "Confirm button found by ID: " + resId + " - clicking");
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        onAppForceStopConfirmed();
                        return;
                    }
                }
            }
        }

        // Fallback: search for "OK" text button
        AccessibilityNodeInfo okButton = findButtonByText(root, "OK");
        if (okButton == null) okButton = findButtonByText(root, "Aceptar");
        if (okButton == null) okButton = findButtonByText(root, "Accept");
        if (okButton == null && mCachedForceStopText != null) {
            // On some EMUI devices, the confirm button has exactly the same text as the source button "FORCE STOP"
            okButton = findButtonByText(root, mCachedForceStopText);
        }

        if (okButton != null && okButton.isEnabled()) {
            Log.d(TAG, "Confirm button found by text - clicking");
            okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            onAppForceStopConfirmed();
        }
    }

    /**
     * Called after successfully clicking the OK confirmation button.
     */
    private void onAppForceStopConfirmed() {
        if (mTimeoutRunnable != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
        }
        mClosedCount++;
        mCurrentIndex++;
        mState = STATE_IDLE;
        mHandler.postDelayed(this::processNextApp, 300);
    }

    // ==== Utility Methods ====

    /**
     * Find a clickable button containing the given text in the accessibility tree.
     */
    private AccessibilityNodeInfo findButtonByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null || text.trim().isEmpty()) return null;
        
        // 1. First try Android's native text search
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                AccessibilityNodeInfo result = validateAndGetClickableNode(node, text);
                if (result != null) return result;
            }
        }
        
        // 2. Fallback to manual recursive tree search if the native method missed it
        return searchNodeTree(root, text);
    }

    private AccessibilityNodeInfo searchNodeTree(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        
        AccessibilityNodeInfo result = validateAndGetClickableNode(node, text);
        if (result != null) return result;
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childResult = searchNodeTree(node.getChild(i), text);
            if (childResult != null) return childResult;
        }
        return null;
    }

    private AccessibilityNodeInfo validateAndGetClickableNode(AccessibilityNodeInfo node, String expectedText) {
        if (node == null) return null;
        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();
        String content = nodeText != null ? nodeText.toString() : (nodeDesc != null ? nodeDesc.toString() : "");
        
        if (content.toLowerCase().contains(expectedText.toLowerCase())) {
            if (node.isClickable()) {
                return node;
            }
            // Only check immediate parent, DO NOT use a while loop up to the root.
            // Ascending too high might find a master container (holding 3 buttons)
            // that is clickable, clicking which triggers the wrong sub-button (e.g. "Archive").
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                return parent;
            }
        }
        return null;
    }

    /**
     * Tier 1: Read the "Force stop" string directly from the system Settings APK resources.
     * This ensures the button text matches exactly in ANY language.
     */
    private String getSystemForceStopString() {
        try {
            PackageManager pm = mContext.getPackageManager();
            Resources settingsRes = pm.getResourcesForApplication(mSettingsPackage);
            int resId = settingsRes.getIdentifier("force_stop", "string", mSettingsPackage);
            if (resId != 0) {
                String text = settingsRes.getString(resId);
                Log.d(TAG, "System force_stop string: " + text);
                return text;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read system Settings string: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if an app is in stopped state.
     */
    private boolean isAppStopped(String packageName) {
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    /**
     * Get the user-visible label of an app.
     */
    private String getAppLabel(String packageName) {
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /**
     * Resolve the system Settings package name.
     */
    private String getSettingsPackageName(Context context) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            return intent.resolveActivity(context.getPackageManager()).getPackageName();
        }
        return "com.android.settings";
    }

    /**
     * Get all non-system running apps as a list of package names.
     */
    public static List<String> getInstalledUserApps(Context context) {
        List<String> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);
        for (ApplicationInfo info : installedApps) {
            // Skip system apps
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            // Skip ourselves
            if (info.packageName.equals(context.getPackageName())) continue;
            // Skip already stopped apps
            if ((info.flags & ApplicationInfo.FLAG_STOPPED) != 0) continue;
            apps.add(info.packageName);
        }
        return apps;
    }
}

package com.killapps;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Core AccessibilityService that receives UI events from the Android system.
 * When UI tasking is enabled (during a kill cycle), events are forwarded
 * to the ForceStopEngine for button detection and clicking.
 */
public class AppKillerService extends AccessibilityService {

    private static final String TAG = "AppKillerService";
    private static AppKillerService sInstance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "Accessibility Service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        ForceStopEngine engine = ForceStopEngine.getInstance();
        if (engine != null && engine.isRunning()) {
            AccessibilityNodeInfo root = event.getSource();
            if (root == null) {
                root = getRootInActiveWindow();
            }
            engine.onAccessibilityEvent(event, root);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        Log.d(TAG, "Accessibility Service destroyed");
    }

    public static AppKillerService getInstance() {
        return sInstance;
    }

    public static boolean isServiceActive() {
        return sInstance != null;
    }

    /** Performs the BACK global action to dismiss dialogs or navigate back. */
    public static void performBack() {
        if (sInstance != null) {
            sInstance.performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    /** Performs the HOME global action to return to launcher. */
    public static void performHome() {
        if (sInstance != null) {
            sInstance.performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }
}

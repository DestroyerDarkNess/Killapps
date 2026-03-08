package com.killapps;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Core AccessibilityService that receives UI events from the Android system.
 * When UI tasking is enabled (during a kill cycle), events are forwarded
 * to the ForceStopEngine for button detection and clicking.
 */
public class AppKillerService extends AccessibilityService {

    private static final String TAG = "AppKillerService";
    private static AppKillerService sInstance;

    /** Multi-language "Clear all" / "Close all" button texts for the Recents screen. */
    private static final String[] CLEAR_ALL_TEXTS = {
            // English
            "Clear all", "Close all", "Clear All", "Close All",
            "CLEAR ALL", "CLOSE ALL",
            // Spanish
            "Borrar todo", "Cerrar todo", "Limpiar todo",
            // Portuguese
            "Limpar tudo", "Fechar tudo",
            // French
            "Tout effacer", "Tout fermer",
            // German
            "Alle löschen", "Alle schließen",
            // Chinese
            "全部清除", "清除全部", "全部关闭",
            // Korean
            "모두 지우기", "모두 닫기",
            // Arabic
            "مسح الكل", "إغلاق الكل",
            // Italian
            "Cancella tutto", "Chiudi tutto",
            // Turkish
            "Tümünü temizle", "Tümünü kapat",
    };

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

    // ==== Clear Recent Tasks ==== //

    public interface ClearRecentsCallback {
        void onDone(boolean success);
    }

    /**
     * Phase 3: Clear all recent tasks.
     * Opens the Recents screen, searches for the "Clear all" button, and clicks it.
     * Everything is wrapped in try-catch so failures never crash the app.
     *
     * @param callback called when the operation finishes (success or failure)
     */
    public static void clearRecentTasks(ClearRecentsCallback callback) {
        if (sInstance == null) {
            Log.w(TAG, "clearRecentTasks: service not active, skipping");
            if (callback != null) callback.onDone(false);
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());

        try {
            // Step 1: Open Recents screen
            Log.d(TAG, "clearRecentTasks: opening recents");
            sInstance.performGlobalAction(GLOBAL_ACTION_RECENTS);

            // Step 2: Wait for the Recents UI to render, then search for "Clear all"
            handler.postDelayed(() -> {
                try {
                    boolean found = sInstance.findAndClickClearAll();
                    if (found) {
                        Log.d(TAG, "clearRecentTasks: 'Clear all' clicked successfully");
                        // Wait a moment then go home
                        handler.postDelayed(() -> {
                            try {
                                sInstance.performGlobalAction(GLOBAL_ACTION_HOME);
                            } catch (Exception e) {
                                Log.e(TAG, "clearRecentTasks: failed to go home", e);
                            }
                            if (callback != null) callback.onDone(true);
                        }, 500);
                    } else {
                        Log.d(TAG, "clearRecentTasks: 'Clear all' button not found, going home");
                        try {
                            sInstance.performGlobalAction(GLOBAL_ACTION_HOME);
                        } catch (Exception e) {
                            Log.e(TAG, "clearRecentTasks: failed to go home", e);
                        }
                        if (callback != null) callback.onDone(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "clearRecentTasks: error searching for button", e);
                    try {
                        sInstance.performGlobalAction(GLOBAL_ACTION_HOME);
                    } catch (Exception e2) {
                        Log.e(TAG, "clearRecentTasks: failed to go home after error", e2);
                    }
                    if (callback != null) callback.onDone(false);
                }
            }, 800);

        } catch (Exception e) {
            Log.e(TAG, "clearRecentTasks: failed to open recents", e);
            if (callback != null) callback.onDone(false);
        }
    }

    /**
     * Search the current accessibility tree for the "Clear all" / "Close all" button
     * and click it if found.
     */
    private boolean findAndClickClearAll() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "findAndClickClearAll: root is null");
                return false;
            }

            // Try each known "Clear all" text variant
            for (String text : CLEAR_ALL_TEXTS) {
                try {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                    if (nodes != null) {
                        for (AccessibilityNodeInfo node : nodes) {
                            CharSequence nodeText = node.getText();
                            CharSequence nodeDesc = node.getContentDescription();
                            String content = nodeText != null ? nodeText.toString()
                                    : (nodeDesc != null ? nodeDesc.toString() : "");

                            if (content.toLowerCase().contains(text.toLowerCase())) {
                                // Found the button — click it (or its clickable parent)
                                if (node.isClickable()) {
                                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Log.d(TAG, "Clicked 'Clear all' node: " + text);
                                    return true;
                                }
                                // Check immediate parent
                                AccessibilityNodeInfo parent = node.getParent();
                                if (parent != null && parent.isClickable()) {
                                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Log.d(TAG, "Clicked 'Clear all' parent: " + text);
                                    return true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error searching for text: " + text, e);
                    // Continue to next text variant
                }
            }

            // Fallback: recursive tree search
            return searchTreeForClearAll(root);
        } catch (Exception e) {
            Log.e(TAG, "findAndClickClearAll: unexpected error", e);
            return false;
        }
    }

    /**
     * Recursively search the node tree for a "Clear all" button.
     * Used as a fallback when findAccessibilityNodeInfosByText misses it.
     */
    private boolean searchTreeForClearAll(AccessibilityNodeInfo node) {
        if (node == null) return false;

        try {
            CharSequence nodeText = node.getText();
            CharSequence nodeDesc = node.getContentDescription();
            String content = nodeText != null ? nodeText.toString()
                    : (nodeDesc != null ? nodeDesc.toString() : "");

            if (!content.isEmpty()) {
                for (String text : CLEAR_ALL_TEXTS) {
                    if (content.toLowerCase().contains(text.toLowerCase())) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "Clicked 'Clear all' (tree search): " + content);
                            return true;
                        }
                        AccessibilityNodeInfo parent = node.getParent();
                        if (parent != null && parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "Clicked 'Clear all' parent (tree search): " + content);
                            return true;
                        }
                    }
                }
            }

            // Recurse into children
            for (int i = 0; i < node.getChildCount(); i++) {
                try {
                    if (searchTreeForClearAll(node.getChild(i))) {
                        return true;
                    }
                } catch (Exception e) {
                    // Skip problematic child nodes
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "searchTreeForClearAll: error at node", e);
        }
        return false;
    }
}

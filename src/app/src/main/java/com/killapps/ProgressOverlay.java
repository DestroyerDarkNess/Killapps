package com.killapps;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Full-screen overlay that shows a progress bar while apps are being force-stopped.
 * Uses SYSTEM_ALERT_WINDOW to display on top of the Settings pages flashing behind it.
 * This shields the user from seeing the rapid Settings page navigation.
 */
public class ProgressOverlay {

    private static final String TAG = "ProgressOverlay";
    private final Context mContext;
    private final WindowManager mWindowManager;
    private ViewGroup mOverlayView;
    private TextView mTitleText;
    private TextView mAppNameText;
    private TextView mProgressText;
    private ProgressBar mProgressBar;
    private boolean mShown = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public ProgressOverlay(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        createView();
    }

    private void createView() {
        android.view.ContextThemeWrapper themeContext = new android.view.ContextThemeWrapper(mContext, R.style.Theme_KillApps);
        mOverlayView = (ViewGroup) android.view.LayoutInflater.from(themeContext).inflate(R.layout.overlay_progress, null);

        mTitleText = mOverlayView.findViewById(R.id.tvOverlayTitle);
        mProgressBar = mOverlayView.findViewById(R.id.pbOverlayProgress);
        mProgressText = mOverlayView.findViewById(R.id.tvOverlayCounter);
        mAppNameText = mOverlayView.findViewById(R.id.tvOverlayAppName);

        android.view.View btnCancel = mOverlayView.findViewById(R.id.btnOverlayCancel);
        btnCancel.setOnClickListener(v -> {
            ForceStopEngine.getInstance().stop();
        });
    }

    /**
     * Show the overlay on top of all windows.
     */
    public void show() {
        if (mShown) return;
        try {
            int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            if (mContext instanceof android.accessibilityservice.AccessibilityService) {
                overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setBlurBehindRadius(30);
            }

            mHandler.post(() -> {
                try {
                    mWindowManager.addView(mOverlayView, params);
                    mShown = true;
                    Log.d(TAG, "Overlay shown");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to show overlay", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to create overlay params", e);
        }
    }

    /**
     * Hide and remove the overlay.
     */
    public void hide() {
        if (!mShown) return;
        mHandler.post(() -> {
            try {
                mWindowManager.removeView(mOverlayView);
                mShown = false;
                Log.d(TAG, "Overlay hidden");
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide overlay", e);
            }
        });
    }

    /**
     * Update the progress bar and current app label.
     */
    public void updateProgress(int current, int total, String appName) {
        mHandler.post(() -> {
            if (!mShown) return;
            int percent = total > 0 ? (int) ((current / (float) total) * 100) : 0;
            mProgressBar.setProgress(percent);
            mProgressText.setText((current + 1) + " / " + total);
            mAppNameText.setText(appName);
        });
    }

    public boolean isShown() {
        return mShown;
    }
}

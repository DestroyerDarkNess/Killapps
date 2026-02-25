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
import android.widget.LinearLayout;
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
    private LinearLayout mOverlayView;
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
        // Root container
        mOverlayView = new LinearLayout(mContext);
        mOverlayView.setOrientation(LinearLayout.VERTICAL);
        mOverlayView.setGravity(Gravity.CENTER);
        mOverlayView.setBackgroundColor(Color.parseColor("#E0000000")); // 88% opaque black
        mOverlayView.setPadding(80, 80, 80, 80);

        // Title
        mTitleText = new TextView(mContext);
        mTitleText.setText("Closing Apps...");
        mTitleText.setTextColor(Color.WHITE);
        mTitleText.setTextSize(24);
        mTitleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = 60;
        mOverlayView.addView(mTitleText, titleParams);

        // Progress bar
        mProgressBar = new ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setMax(100);
        mProgressBar.setProgress(0);
        mProgressBar.setMinimumHeight(12);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 20);
        barParams.bottomMargin = 40;
        barParams.leftMargin = 40;
        barParams.rightMargin = 40;
        mOverlayView.addView(mProgressBar, barParams);

        // Progress counter text
        mProgressText = new TextView(mContext);
        mProgressText.setText("0 / 0");
        mProgressText.setTextColor(Color.parseColor("#CCCCCC"));
        mProgressText.setTextSize(16);
        mProgressText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        counterParams.bottomMargin = 30;
        mOverlayView.addView(mProgressText, counterParams);

        // Current app name
        mAppNameText = new TextView(mContext);
        mAppNameText.setText("");
        mAppNameText.setTextColor(Color.parseColor("#FF5252"));
        mAppNameText.setTextSize(18);
        mAppNameText.setGravity(Gravity.CENTER);
        mOverlayView.addView(mAppNameText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;

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

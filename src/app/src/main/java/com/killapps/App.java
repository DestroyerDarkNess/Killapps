package com.killapps;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;

public class App extends Application {
    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
        installCrashHandler();
    }

    public static Context getContext() {
        return sContext;
    }

    /**
     * Installs a global uncaught exception handler.
     * When any thread crashes, it captures the stacktrace + device info
     * and launches CrashActivity (in a separate process) to display it.
     */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                // Build the crash report
                StringBuilder report = new StringBuilder();
                report.append("=== KillApps Crash Report ===\n\n");

                // Device info
                report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
                report.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
                report.append("App Version: ").append(getAppVersion()).append("\n");
                report.append("Thread: ").append(thread.getName()).append("\n\n");

                // Full stacktrace
                report.append("=== Stacktrace ===\n\n");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                report.append(sw.toString());

                // Launch CrashActivity in a new process
                Intent intent = new Intent(getApplicationContext(), CrashActivity.class);
                intent.putExtra(CrashActivity.EXTRA_CRASH_LOG, report.toString());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);

                // Kill the current (crashed) process
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            } catch (Exception e) {
                // If our handler itself fails, fall back to the system default
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }

    private String getAppVersion() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}

# Kill Apps - Technical Reverse Engineering Report

**Package:** `com.tafayor.killall`
**APK Size:** 10.8 MB
**Decompiled with:** jadx (8917 classes, 84 minor decompilation errors from R8 obfuscation)

---

## Table of Contents

- [Overview](#overview)
- [Permissions](#permissions)
- [Architecture](#architecture)
- [Core Pipeline](#core-pipeline)
- [Stage 1: PrepareStage](#stage-1-preparestage)
- [Stage 2: MainStage](#stage-2-mainstage)
- [Stage 3: ConfirmStage](#stage-3-confirmstage)
- [Overlay System](#overlay-system)
- [App Detection and Listing](#app-detection-and-listing)
- [Multi-Version Compatibility](#multi-version-compatibility)
- [Key Takeaways](#key-takeaways)

---

## Overview

Kill Apps is **not** using `killBackgroundProcesses()` or any shell command to stop apps.
Instead, it implements a **UI Automation Robot** that programmatically navigates to each app's Android Settings page and clicks the "Force Stop" button using the `AccessibilityService` API.

This achieves the same effect as `adb shell am force-stop <package>` without requiring Root, Shizuku, or ADB access.

---

## Permissions

| Permission | Purpose |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Read UI elements on screen and perform programmatic clicks |
| `SYSTEM_ALERT_WINDOW` | Display the progress overlay on top of Settings pages |
| `QUERY_ALL_PACKAGES` | Enumerate all installed applications |
| `FOREGROUND_SERVICE` | Keep the killing process alive in the background |

No Root. No Shizuku. No ADB. Only standard Android permissions.

---

## Architecture

The app follows a **Task Chain** pattern with a well-defined pipeline:

```
ActionController
  └── CloseAppsAction
        ├── Loads app list (user apps, system apps, or custom selection)
        ├── Shows OverlayWaitScreen (progress bar)
        └── Executes TaskManager with a chain of ForceStopTask objects
              │
              ├── ForceStopTask("com.whatsapp")
              │     ├── PrepareStage  → Waits for Settings UI readiness
              │     ├── MainStage     → Opens Settings App Info page
              │     │     └── StartAction  → Finds + clicks "Force Stop" button
              │     └── ConfirmStage  → Finds + clicks "OK" confirmation dialog
              │
              ├── ForceStopTask("com.instagram.android")
              │     └── (same 3 stages)
              │
              ├── ForceStopTask("com.facebook.orca")
              │     └── (same 3 stages)
              │
              └── ForceStopTask("com.android.settings")  ← Always last (closes Settings itself)
```

Each `ForceStopTask` checks `PackageHelper.isStopped()` before starting. If the app is already stopped, it skips immediately.

---

## Core Pipeline

### Stage 1: PrepareStage

**File:** `PrepareAction.java`

A lightweight pre-check stage. On most Android versions, this is a no-op that returns `TResult.completed()` immediately. On Huawei devices (Android 11+), it is used to handle vendor-specific Settings UI quirks before proceeding.

---

### Stage 2: MainStage

**File:** `MainStage.java` + `StartAction.java`

This is the core of the automation. It executes two critical steps:

**Step A: Open the App Info Settings page**

```java
Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
intent.setData(Uri.parse("package:" + mAppPackage));
intent.setFlags(1417707520); // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK | etc.
getContext().startActivity(intent);
```

This opens the standard Android "App Info" screen for the target package (the same screen you see when you long-press an app and tap "App Info").

**Step B: Find and click the "Force Stop" button**

`StartAction.onExecute()` uses `AccessibilityNodeInfo` to scan the visible UI tree for the Force Stop button. The app implements a sophisticated **3-tier multi-language detection system** to ensure it works regardless of the device's language:

#### Tier 1: Read the localized string directly from the system Settings APK

```java
String settingsString = getSettingsString(STRING_RES_FORCE_STOP); // "force_stop"
```

This function accesses the installed `com.android.settings` APK's internal string resources and extracts the translated text for `force_stop` **in the device's current system language**. If the phone is set to Arabic, Japanese, Russian, or any language, this returns the exact localized label that Android uses for the Force Stop button. This is the primary and most reliable method.

If successful, the result is saved for future use:
```java
ForceStopPref.i().setForceStopSystemString(settingsString);
```

#### Tier 2: Hardcoded fallback strings for common languages

If Tier 1 fails (e.g., on heavily modified vendor ROMs), the app falls back to 6 pre-defined string variants embedded in its own resources:

```java
linkedHashSet.add(settingsString);                    // System-detected (from Tier 1)
linkedHashSet.add(getString(R.string.force_stop));    // "Force stop" (English)
linkedHashSet.add(getString(R.string.force_stop_2));  // "Forzar detención" (Spanish)
linkedHashSet.add(getString(R.string.force_stop_3));  // "Forçar parada" (Portuguese)
linkedHashSet.add(getString(R.string.force_stop_4));  // Additional variant
linkedHashSet.add(getString(R.string.force_stop_5));  // Additional variant
```

Each string is tried sequentially until a matching button is found in the accessibility tree.

#### Tier 3: Cache the result for the remaining apps

Once any of the above strings successfully matches a button, the text and the search method used are cached:

```java
getManager().getProps().putString(PROP_FORCE_STOP_BTN_TEXT, matchedText);
getManager().getProps().putBoolean(PROP_USE_FIND_BUTTON_METHOD, true);
```

For all subsequent apps in the kill queue (potentially 30-40 apps), the cached string is reused directly, skipping the entire detection process and significantly speeding up the operation.

#### Dual search method fallback

For each candidate string, two search strategies are tried in order:

1. **`findButtonByText(text)`** — Uses `AccessibilityNodeInfo.findAccessibilityNodeInfosByText()` for a direct text-based lookup. This is the fastest method.
2. **`searchButtonByText(text)`** — If the direct lookup fails (e.g., on vendor-modified Settings UIs), falls back to a full recursive traversal of the accessibility node tree. This is slower but more robust.

#### Button state handling

Once found, the button's `isEnabled()` state is checked:
- **Enabled:** `performClick(button)` is called to tap it, and the button's screen bounds are saved for the ConfirmStage.
- **Disabled:** The app is already stopped. The task is skipped (`TResult.skipTask()`).
- **Retry mode:** On Android 15+, if the button is not enabled after 5 consecutive events, it gives up gracefully to avoid infinite loops.

On MIUI (Xiaomi), if the button is found but disabled, the task is skipped immediately without retry, as MIUI handles force-stop differently.

---

### Stage 3: ConfirmStage

**File:** `ConfirmAction.java`

After clicking "Force Stop", Android shows an `AlertDialog` asking "Do you want to force stop this app?". The `ConfirmAction` finds the "OK" button using resource IDs:

```java
static String RES_ID_BUTTON_1 = "android:id/button1";          // Standard Android
static String RES_ID_BUTTON_1_2 = "com.android.settings:id/button1"; // Samsung/vendor
static String RES_ID_BUTTON_2 = "android:id/button2";          // Alternative
```

The action also caches the successful button ID for subsequent tasks to speed up the process.

---

## Overlay System

**File:** `OverlayWaitScreen.java`

A full-screen transparent overlay displayed via `WindowManager` with `SYSTEM_ALERT_WINDOW` permission. It renders:
- A semi-transparent dimming background
- A progress bar tracking `current_app / total_apps`
- The name of the app currently being force-stopped
- A cancel button

The overlay serves two purposes:
1. Visual feedback for the user
2. Prevents the user from accidentally interacting with the Settings pages flashing behind (which would break the automation sequence)

---

## App Detection and Listing

**File:** `CloseAppsAction.loadApps()` + `SystemUtil.java`

The app supports multiple listing modes:
- **Close All:** Uses `PackageManager` to enumerate all running user apps (and optionally system apps)
- **Custom Selection:** User picks specific apps from a list stored in `CustomAppDB` (Room/SQLite)
- **Exception List:** Apps in `ExceptionAppDB` are never killed (e.g., the Kill Apps app itself)
- **Persistent Apps:** Apps that survived a previous force-stop attempt are tracked in `PersistentAppDB`

The Settings app package (`com.android.settings`) is always removed from the kill list during iteration and force-stopped **last** as a cleanup step.

---

## Multi-Version Compatibility

The codebase contains **three separate implementations** of the force-stop pipeline:

| Implementation | Target | Package |
|---|---|---|
| `ForceStopTask` + `StartAction` | Android 14+ (modern) | `forcestop/` |
| `ForceStopTask2` + `StartAction2` | Android 12-13 variant | `forcestop2/` |
| `ForceStopTaskLegacy` + `StartActionLegacy` | Android 10-11 | `forcestop/legacy/` |
| `ForceStopTaskLegacy2` | Older devices | `forcestop/legacy2/` |

`UiTaskFactory` dynamically selects the correct implementation based on `ApiHelper.isFromAndroidXX()` checks.

Vendor-specific handling exists for:
- **Xiaomi (MIUI):** Special skip logic via `XiaomiHelper.isMiUi()`
- **Huawei:** Extra `PrepareStage` on Android 11+
- **Samsung:** Alternative Settings resource IDs
- **Google Pixel:** Chrome-specific workaround on Android 14-15

---

## Key Takeaways

1. The app **never** calls `killBackgroundProcesses()`, `am force-stop`, or any system command.
2. It achieves true force-stop by physically automating the Android Settings UI via `AccessibilityService`.
3. The technique is **equivalent to a human manually tapping** Force Stop → OK for each app, but automated at machine speed.
4. It requires **zero elevated permissions** (no Root, no Shizuku, no ADB).
5. The overlay (`SYSTEM_ALERT_WINDOW`) is used purely for visual feedback and to shield the Settings automation from accidental user input.
6. The multi-language button text fallback system (6 variants + system string lookup) ensures compatibility across all Android localizations.
7. Four separate ForceStopTask implementations exist to handle the vastly different Settings UI structures across Android versions 10-16+.

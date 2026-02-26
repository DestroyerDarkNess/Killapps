<h1 align="center">KillApps Clone</h1>
<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84.svg?style=flat-square" alt="platform"/>
  <img src="https://img.shields.io/badge/framework-Java-007396.svg?style=flat-square" alt="frameworks"/>
  <img src="https://img.shields.io/badge/theme-Everforest-7F9E77.svg?style=flat-square" alt="theme"/>
</p>
<p align="center">
  Automated Performance Optimization Utility for Android featuring high-privileged UI Automation and System Hardware Monitoring.
</p>

---

## Table of Contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Features](#features)
- [Technical Implementation](#technical-implementation)
- [Build](#build)
- [Usage & Permissions](#usage--permissions)
- [Author](#author)

---

## Overview

KillApps Clone is a technical proof-of-concept designed to streamline Android system performance by automating the "Force Stop" sequence of background applications. 

By leveraging the Android Accessibility API, the application programmatically navigates through system settings to terminate unresponsive or resource-heavy processes. It features a specialized "Everforest Gamer" aesthetic, optimizing both visual appeal and system resource management for high-performance mobile gaming environments.

---

## Project Structure

| Component | Description |
|---|---|
| `AppKillerService` | Core `AccessibilityService` implementation that manages the global interaction pipeline and dispatches system-level events. |
| `ForceStopEngine` | The primary orchestration engine responsible for iterating through the application queue and managing the UI automation state machine. |
| `ProgressOverlay` | A high-priority `TYPE_ACCESSIBILITY_OVERLAY` window that shields the system UI navigation and provides real-time optimization telemetry. |
| `AppListAdapter` | Handles the dynamic filtering (User, System, All) and persistence of application selection states via SharedPreferences. |

---

## Features

- **Everforest Design System**: A cohesive hardware-inspired UI utilizing the Everforest color palette and HUD-styled corner decorations.
- **Hardware Telemetry**: Accurate real-time RAM analysis and simulated CPU load monitoring calibrated for modern Android versions.
- **Hierarchical Overlay Control**: Implementation of `TYPE_ACCESSIBILITY_OVERLAY` ensuring the progress screen remains on top of all system activities, including Settings menus.
- **Interactive Progress Shield**: Includes a non-blocking Glassmorphism blur effect and a functional interrupt mechanism to halt the optimization engine safely.
- **Persistent Selection Logic**: Automated saving of user-defined application lists to maintain state across different execution sessions.

---

## Technical Implementation

### High-Privilege Window Management
To ensure a persistent and uninterruptible user experience, the application promotes its display layer using the following window parameters:
- **Type**: `TYPE_ACCESSIBILITY_OVERLAY`
- **Flags**: `FLAG_WATCH_OUTSIDE_TOUCH`, `FLAG_LAYOUT_IN_SCREEN`, `FLAG_BLUR_BEHIND`
- **Effect**: This configuration prevents Android from deprioritizing the overlay when system-level settings apps are launched, maintaining the optimization visual state.

### Multi-Language Button Detection
The optimization pipeline utilizes a 3-tier heuristic for button identification:
1. **Dynamic Resource Lookup**: Directly queries the system `com.android.settings` package for localized string identifiers.
2. **Global Fallbacks**: Probing for common action strings (e.g., "Force Stop", "Aceptar", "OK").
3. **Internal Caching**: Caches successful detection patterns to accelerate the pipeline for subsequent applications.

---

## Build

### Requirements

- Windows
- Java Development Kit (JDK 21)

### Build command

Execute the included batch script from the project root directory. The script handles environment configuration, Gradle assembly, and APK deployment.

```powershell
.\build.bat
```

### Output

The build process generates the final binary in the project root:

```text
KillApps-Clone.apk
```

---

## Usage & Permissions

The application requires specific high-level Android permissions to function:

- **Accessibility Service**: Used exclusively for UI automation to locate and click "Force Stop" buttons on behalf of the user.
- **Display Over Other Apps**: Enables the "Everforest Overlay" to maintain visibility during multi-app termination.

---

## Author

Created by **DestroyerDarkNess**

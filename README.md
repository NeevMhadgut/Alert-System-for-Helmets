# CrashDetectionApp

An experimental Android app that roughly mimics the **crash detection** concept introduced on modern smartphones. It runs a foreground service that listens to accelerometer data, tries to detect very sudden impacts, and, when it suspects a crash, shows a full-screen confirmation UI with a countdown to contact emergency services.

> **Important disclaimer:** This project is for learning and experimentation only. The crash detection logic here is extremely naive and **must not** be relied upon for real safety-critical use cases.

## Features

- **Foreground crash detection service**
  - Starts/stops from the main screen.
  - Keeps a persistent notification while running.
  - Listens to accelerometer values and triggers on a simple magnitude threshold.

- **Crash confirmation screen**
  - Full-screen activity shown on top (even on lockscreen, depending on OEM behavior).
  - 15-second countdown: if you don’t cancel, it attempts to call an emergency number.
  - Buttons to **“Call emergency services now”** or **“I’m OK”**.

- **Permissions handling**
  - Requests location, SMS (for future extensions), and notification permissions where needed.
  - Uses `CALL_PHONE` if granted; otherwise falls back to opening the dialer.

## Project structure

- `build.gradle.kts`, `settings.gradle.kts`: top-level Gradle configuration.
- `app/build.gradle.kts`: app module configuration (Kotlin, Material Components, etc.).
- `app/src/main/AndroidManifest.xml`:
  - Declares permissions.
  - Registers:
    - `MainActivity`
    - `CrashDetectedActivity`
    - `CrashDetectionService`
- `app/src/main/java/com/example/crashdetection/ui/MainActivity.kt`
  - Simple UI to start/stop crash detection foreground service.
- `app/src/main/java/com/example/crashdetection/service/CrashDetectionService.kt`
  - Foreground service using accelerometer with a naive impact threshold.
- `app/src/main/java/com/example/crashdetection/ui/CrashDetectedActivity.kt`
  - Countdown + emergency call confirmation screen.
- `app/src/main/res/layout/*.xml`
  - `activity_main.xml`, `activity_crash_detected.xml` layouts.
- `app/src/main/res/values/*.xml`
  - `themes.xml`, `colors.xml`, `strings.xml`.

## How detection works (simplified)

Inside `CrashDetectionService`, we subscribe to the accelerometer:

- Read \(a_x, a_y, a_z\).
- Compute approximate magnitude \( \sqrt{a_x^2 + a_y^2 + a_z^2} \).
- If the magnitude exceeds a hard-coded threshold (e.g. `> 30f`), treat it as a potential crash and launch `CrashDetectedActivity`.

This is **not** a robust algorithm. Real crash detection uses:

- More sensors (gyroscope, GPS, barometer, microphone).
- Sophisticated filtering and heuristics.
- On-device ML models and many safety checks.

## Running the app

1. Open the folder in **Android Studio**.
2. Let Gradle sync.
3. Run the `app` configuration on a **physical device** (strongly recommended; sensors are limited in emulators).
4. On first run, grant:
   - Location permissions.
   - SMS (optional for now, used for future extension).
   - Notifications (Android 13+).
   - Phone/call permission if you want auto-calling.
5. Tap **“Start crash detection”**.

To test the flow without doing anything unsafe, you can:

- Temporarily lower the impact threshold in `CrashDetectionService` (e.g. to `20f` or less).
- Or manually launch `CrashDetectedActivity` from `MainActivity` (for debug builds only).

## Safety and legal notes

- **Do not** rely on this app for real-world crash detection.
- Always test on your own device carefully and at low thresholds or by simulating events, not by creating actual dangerous situations.
- Check and comply with your local regulations about auto-calling emergency numbers; many regions restrict automated emergency calls.

## Possible extensions

- Use Google’s Activity Recognition APIs and location data.
- Log sensor data and train a simple on-device classifier.
- Add configuration for:
  - Thresholds and debounce.
  - Custom emergency contacts (SMS with location, etc.).
  - Different behaviors when driving vs walking.


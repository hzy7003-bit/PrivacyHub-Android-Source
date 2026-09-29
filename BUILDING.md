# Building PrivacyHub 0.10.5-beta

This Source Available snapshot is based on the official private release tag `v0.10.5-beta` at commit `20a8bb55d1f40f460a59252dad15bbd40312095b`. The source files and build inputs are not the private Git history; this repository contains only the reviewed snapshot and Source-repository documentation.

## Requirements

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 34.0.0 (selected by the pinned Android Gradle Plugin)
- Android SDK Platform Tools
- Network access for the Gradle Wrapper distribution and dependencies unless already cached

The project pins Android Gradle Plugin 8.5.2, Kotlin 2.0.20, KSP 2.0.20-1.0.25, and Gradle Wrapper 8.7. Keep Gradle on JDK 17. No Android NDK is required by the app build.

Install the Android SDK through Android Studio's SDK Manager or the official Android command-line tools. Keep machine-specific SDK settings in an ignored `local.properties` file or use `ANDROID_HOME` / `ANDROID_SDK_ROOT`; do not commit local paths or signing data.

## Verify and build a Debug APK

From the repository root, run:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The Debug APK is written under `app/build/outputs/apk/debug/`. It is for local inspection/testing and is not the official PrivacyHub release.

The official Release signing configuration and License signing key are intentionally not included. Do not use a newly generated key to impersonate or replace the project's official signer. Release signing and publication are outside the scope of this snapshot.

## Known instrumentation timing note

On Android 12 and later, the system may defer visibility of a foreground-service notification when it uses the default foreground-service notification behavior. See the Android [`Notification.Builder.setForegroundServiceBehavior`](https://developer.android.com/reference/android/app/Notification.Builder) documentation.

The API 35 `NotificationInteractionTest.bothCollapsedActionsDispatchTheirOwnIntentAndSaveOnce` currently uses a 10-second notification-visibility deadline. In five independent runs on a clean, wipe-data API 35 AVD, it passed three times and intermittently failed twice when the system enqueued the notification just after that deadline (about 10.142 seconds and 10.068 seconds after foreground-service start). In both failures the service entered foreground normally, notification permission was granted, and the channel was enabled at importance 3. Separate passive-notification and foreground-service probes displayed the notification and both actions in SystemUI; the official test also verified both actions on its passing runs.

The product and test files matched the official private `v0.10.5-beta` release tag byte for byte. One subsequent full API 35 instrumentation run passed 39/39 tests, including this test; that single pass does not remove the variability observed in the five independent runs. This is recorded as a timing-sensitive instrumentation limitation, not as evidence of a source mismatch or a guarantee about behavior on every device.

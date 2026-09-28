# Building PrivacyHub 0.10.4-beta

This repository contains a source snapshot based on private release tag `v0.10.4-beta` at commit `7f36026c0621d4e6bed111595f2eced05f2709eb`.

## Requirements

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 34.0.0 (the version selected by this pinned Android Gradle Plugin)
- Android SDK Platform Tools
- A network connection for the Gradle Wrapper distribution and dependencies, unless they are already cached locally

The repository pins Android Gradle Plugin 8.5.2, Kotlin 2.0.20, KSP 2.0.20-1.0.25, and Gradle Wrapper 8.7. Keep the Gradle JDK and terminal JDK on 17 for this release snapshot. No Android NDK is required by the app build.

Set up Android SDK through Android Studio's SDK Manager or the official Android command-line tools. Keep machine-specific SDK settings in an ignored `local.properties` file or use `ANDROID_HOME` / `ANDROID_SDK_ROOT`; do not commit local paths or signing data.

## Verify and build a Debug APK

From the repository root, run:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The Debug APK is written under `app/build/outputs/apk/debug/`. It is for local inspection/testing only and is not the official PrivacyHub release.

The official Release signing configuration is intentionally not included. Do not use a newly generated key to impersonate or replace the project's official signer. Release/signing publication is outside the scope of this snapshot.

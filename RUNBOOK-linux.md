# RUNBOOK — building the Android app on Linux

The `:android` module and the SprossKern engine build fine without a Mac;
only the iOS/watch surfaces need Xcode.
Everything below is plain Gradle.

## Prerequisites

- **JDK 21** (`sudo apt install openjdk-21-jdk` or equivalent) — the Gradle toolchain
  needs it installed; auto-provisioning is off, so a missing JDK 21 fails the build.
  Both the daemon (`gradle/gradle-daemon-jvm.properties`) and every module toolchain
  are pinned to 21, so the JVM that launches `./gradlew` does not matter —
  but a JDK 21 must be discoverable or the daemon never starts.
- **Android SDK command-line tools** — either a full Android Studio install
  or the standalone tools:

  ```sh
  mkdir -p ~/android-sdk/cmdline-tools
  # download "commandlinetools-linux-*.zip" from https://developer.android.com/studio#command-line-tools-only
  unzip commandlinetools-linux-*.zip -d ~/android-sdk/cmdline-tools
  mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
  ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses   # accept all
  ```

  Platform 36 and Build-Tools install themselves on first build
  (licenses must be accepted first).
- **SDK location**: create `local.properties` next to `settings.gradle.kts`
  (it is gitignored):

  ```
  sdk.dir=/home/<you>/android-sdk
  ```

  (`ANDROID_HOME` works too.)
- `adb` for device installs (part of platform-tools; a udev rule may be needed
  for USB debugging on some distros).

## Build & test

```sh
./gradlew :kern:jvmTest                # engine fast gate (must be green)
./gradlew :kern:compileAndroidMain     # engine android compile gate
./gradlew :android:testDebugUnitTest   # app unit tests
./gradlew :android:assembleDebug       # the APK
```

APK: `android/build/outputs/apk/debug/android-debug.apk`

The catalog is bundled automatically:
`sync<Variant>CatalogAssets` copies the in-repo `catalog/` into the APK assets
on every build — never edit assets by hand, edit `catalog/`.

## Install & run

```sh
adb install -r android/build/outputs/apk/debug/android-debug.apk
adb shell am start -n net.spross.app/.SprossActivity
```

Min Android 8.0 (API 26).

## Notes & limits

- The Kotlin targets `iosArm64`/`iosSimulatorArm64` (and the SKIE plugin)
  are declared in `kern` but their tasks are Mac-only —
  do not invoke `linkDebugFramework*`/`assemble*XCFramework` tasks on Linux.
  Normal configuration and all tasks above work without them.
- First build downloads Gradle + dependencies (~2–3 GB into `~/.gradle`);
  plan disk accordingly.
- Release builds (`:android:assembleRelease`) are unsigned;
  signing config is deliberately not set up yet (pre-production).

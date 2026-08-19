# RUNBOOK — building and running the Android app

The `:android` module and the SprossKern engine build fine without a Mac;
only the iOS/watch surfaces need Xcode.
Everything below is plain Gradle and the SDK command-line tools —
it works the same on macOS and Linux except where a section says otherwise.

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

  On macOS with Android Studio installed this is `~/Library/Android/sdk`.
  (`ANDROID_HOME` works too.) Prefer `local.properties`:
  an `ANDROID_SDK_ROOT` left over from an older install points somewhere that
  no longer exists on more machines than not, and every tool honours it silently.
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

## Emulator

`scripts/run-emu.sh` is the Android counterpart of `scripts/run-sim.sh`:
it boots the AVD if it is not already up, builds, installs and launches.

```sh
scripts/run-emu.sh                       # build + (re)launch on the spross AVD
scripts/run-emu.sh --no-build            # reinstall the last APK
scripts/run-emu.sh --clean               # uninstall first ⇒ onboarding runs
scripts/run-emu.sh --shot /tmp/drill.png # screenshot once the app has drawn
scripts/run-emu.sh --avd spross-tablet   # another AVD by name
```

One-time setup, once per machine:

```sh
SDK=$(sed -n 's/^sdk\.dir=//p' local.properties)
sdkmanager --sdk_root="$SDK" emulator "system-images;android-36;default;arm64-v8a"
sdkmanager --sdk_root="$SDK" "cmdline-tools;latest"   # avdmanager, see § One SDK
"$SDK/cmdline-tools/latest/bin/avdmanager" create avd \
  -n spross -k "system-images;android-36;default;arm64-v8a" -d medium_phone
```

**`-d` is not optional.** Without a device profile `avdmanager` falls back to a generic
320×640 at **160 dpi** — one pixel per dp, a screen no phone has shipped since about 2011.
Type renders unantialiased-looking and every width budget on the narrow end of the app is
wrong, so a screen judged there is not a screen anyone will see. `avdmanager list device`
names the rest; `medium_phone` is 1080×2400 at 420 dpi (411 dp wide), which is the modern
middle. Check an existing AVD with `grep hw.lcd config.ini` before trusting a screenshot.

Then in the new AVD's `config.ini` (`$ANDROID_PREFS_ROOT/.android/avd/spross.avd/`):
`hw.sdCard=no` — nothing in the app reads external storage, and an SD card is
allocated in full the moment it is created — and `disk.dataPartition.size=4G`,
down from the 10 G default.

The image line carries three choices:

- **`arm64-v8a` on ARM hosts (Apple Silicon), `x86_64` on Intel/AMD.**
  The emulator virtualizes, it does not emulate the ISA — a mismatched image will not boot.
- **`default`, not `google_apis`.** Nothing in the app touches Play Services,
  so GMS is ~1.7 GB of image buying nothing.
- **Never `aosp_atd`/`google_atd`.** The ATD images are built for headless CI:
  they [drop SystemUI and disable hardware rendering](https://developer.android.com/studio/test/managed-devices),
  so there is no status or navigation bar to judge a screen against,
  and their audio path is undocumented — which disqualifies them for the one thing
  the emulator is here for, hearing whether pronunciation playback is right.

Budget ~5 GB installed — emulator ~1.1 GB, image ~1.8 GB, cmdline-tools ~0.2 GB —
**plus roughly 1.8× `disk.dataPartition.size` free at boot**, which the emulator
demands up front and reports as
`Not enough space to create userdata partition` if it is missing.
At the 4 G above that is ~7 GB free; at the 10 G default, ~18 GB.
The preflight `hasSufficientDiskSpace` check passes well before this second one bites,
so a green preflight is not the answer.

Audio reaches the host speakers by default, so a letter drill can be heard as well as seen.

## Seeing the widget

Nothing places a widget from the command line — `adb shell cmd appwidget` answers
"No shell command implementation" — so the tile goes on the home screen by hand:
long-press the wallpaper, **Widgets**, scroll to **Spross**, then drag its preview onto the screen. 
Resizing is a long-press and then a drag of an edge handle, and the tile refills its grid in place —
so one placed widget is every shape worth looking at.

A freshly placed tile draws the loading spinner for a few seconds before its first
composition arrives, and a phone with no box yet draws the sprout — answer a round first
if the words are what you came to look at.

## Launcher icon

The launcher wears the iOS icon's artwork; `scripts/android-icon.py` re-derives the
adaptive foregrounds from it, and prints the plate color `ic_launcher_background` has to
carry. Run it whenever `App/Resources/Assets.xcassets/AppIcon.appiconset` is repainted.

## One SDK

`local.properties` names the SDK, and everything else must resolve to that same root.
`avdmanager` in particular takes its root from **its own install path**, not from
`ANDROID_SDK_ROOT` — a copy from Homebrew's `android-commandlinetools` cask
answers `Package path is not valid. Valid system image paths are: null`,
because the images live in the other SDK.
So install `cmdline-tools;latest` into the SDK itself and call it by path,
rather than keeping a second SDK on `PATH`.

## Emulator on Linux

Same commands, plus KVM: the host needs `/dev/kvm` readable
(`sudo usermod -aG kvm $USER`, then log back in) or the emulator falls back to
software emulation and is too slow to judge timing-sensitive behavior.
Most cloud containers do not expose nested virtualization at all —
see the cloud section below.

## Notes & limits

- The Kotlin targets `iosArm64`/`iosSimulatorArm64` (and the SKIE plugin)
  are declared in `kern` but their tasks are Mac-only —
  do not invoke `linkDebugFramework*`/`assemble*XCFramework` tasks on Linux.
  Normal configuration and all tasks above work without them.
- First build downloads Gradle + dependencies (~2–3 GB into `~/.gradle`);
  plan disk accordingly.
- `:android:assembleRelease` requires `SPROSS_KEYSTORE` and its password in the
  environment and fails without them — an unsigned APK is one no device will install.
  The release pipeline and the key it uses: `docs/distribution.md`.

## Claude Code on the web / cloud containers

Remote sessions run this same Linux path. These containers ship JDK 21
preinstalled, which is exactly the pinned toolchain above — nothing to
provision, `./gradlew :kern:jvmTest` works immediately with no setup step.

There is no Xcode, `xcodegen`, `xcrun`/`simctl`, or `idb` in these
containers and there never will be — don't probe for them or try to install
them. `:kern:jvmTest` plus the Android gates above are the rudimentary
verification available here; the iOS build gate in `CLAUDE.md` and the
`verify` skill are Mac-only.

The emulator is not available either: these containers expose no `/dev/kvm`,
and a software-emulated Android is too slow to be worth the wait.
Anything that has to be seen or heard running waits for a machine with a GPU
and virtualization — a local checkout or a real device.

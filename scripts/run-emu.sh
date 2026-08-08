#!/bin/sh
# Build, install and launch Spross on an Android emulator.
#   scripts/run-emu.sh                       — build + (re)launch on the spross AVD
#   scripts/run-emu.sh --no-build            — reinstall the last APK, skip Gradle
#   scripts/run-emu.sh --avd spross-tablet   — pick another AVD by name
#   scripts/run-emu.sh --clean               — uninstall first (⇒ onboarding runs)
#   scripts/run-emu.sh --shot /tmp/drill.png — screenshot once the app has drawn
#
# Boots the AVD if it is not already running; an emulator left up is reused.
# One-time AVD setup: see RUNBOOK-android.md § Emulator.
set -eu
cd "$(dirname "$0")/.."

AVD=spross
PKG=net.spross.app
ACTIVITY=.SprossActivity
APK=android/build/outputs/apk/debug/android-debug.apk
BUILD=1
CLEAN=0
SHOT=

while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=0; shift ;;
    --clean) CLEAN=1; shift ;;
    --avd) AVD="$2"; shift 2 ;;
    --shot) SHOT="$2"; shift 2 ;;
    -h|--help) sed -n '2,10p' "$0" | cut -c3-; exit 0 ;;
    *) echo "error: run-emu: unknown option '$1' (see --help)" >&2; exit 1 ;;
  esac
done

# why: ANDROID_SDK_ROOT is wrong or unset on more machines than it is right —
# local.properties is the one location Gradle itself already trusts.
SDK=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | tail -1)
[ -n "$SDK" ] || SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
EMULATOR="$SDK/emulator/emulator"
[ -x "$EMULATOR" ] || {
  echo "error: run-emu: no emulator at $EMULATOR" >&2
  echo "       sdkmanager --sdk_root=\"$SDK\" emulator" >&2
  exit 1
}

# The SDK's own adb, not whatever PATH offers — same root as the emulator, so the
# two can never be a version apart. `-e` resolves "the one emulator", so a
# plugged-in phone never catches the install.
ADB="$SDK/platform-tools/adb -e"

if ! $ADB get-state >/dev/null 2>&1; then
  "$EMULATOR" -list-avds | grep -qx "$AVD" || {
    echo "error: run-emu: no AVD named '$AVD'" >&2
    echo "       $EMULATOR -list-avds" >&2
    exit 1
  }
  # braces: an unbraced $AVD swallows the following multibyte character as part
  # of the name, which under `set -u` aborts the script instead of printing.
  echo "Booting ${AVD}…"
  # why: the emulator holds the terminal for its whole run — detached, so this
  # script can go on to install into it and the window outlives the script.
  nohup "$EMULATOR" -avd "$AVD" >/dev/null 2>&1 &
  $ADB wait-for-device
  until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    sleep 2
  done
fi

if [ "$BUILD" = 1 ]; then
  ./gradlew --console=plain -q :android:assembleDebug
fi
[ -f "$APK" ] || { echo "error: run-emu: no APK at $APK — drop --no-build" >&2; exit 1; }

[ "$CLEAN" = 1 ] && $ADB uninstall "$PKG" >/dev/null 2>&1
$ADB install -r "$APK" >/dev/null
$ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
echo "Launched $PKG on $AVD."

if [ -n "$SHOT" ]; then
  # why: am start returns at spawn, not at first frame — without the pause the
  # screenshot catches a blank window instead of the screen under test.
  sleep 3
  $ADB exec-out screencap -p > "$SHOT"
  echo "Screenshot: $SHOT"
fi

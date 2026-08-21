#!/bin/sh
# Build, install and launch Spross on an iOS Simulator.
#   scripts/run-sim.sh                        — build + (re)launch on iPhone 17 Pro
#   scripts/run-sim.sh --no-build             — reinstall the last build, skip xcodebuild
#   scripts/run-sim.sh --device 'iPhone 16'   — pick another simulator by name
#   scripts/run-sim.sh --clean                — uninstall first (⇒ onboarding runs)
#   scripts/run-sim.sh --shot /tmp/heute.png  — screenshot once the app has drawn
#   scripts/run-sim.sh --mute                 — start with reading aloud switched off
#   scripts/run-sim.sh --shot x.png --sound   — let a screenshot run speak after all
#   scripts/run-sim.sh -- -uitest-source de -uitest-target sw   — DEBUG launch args
#
# Everything after `--` is passed to the app (see AppModel.start(): -uitest-source,
# -uitest-target, -uitest-screen box, -uitest-autostart 1, -uitest-trainer numbers).
#
# --shot implies --mute: a run nobody is sitting at should not start talking. The mute
# rides the argument domain (`-readAloud off`), which OVERRIDES the stored setting for
# this launch without rewriting it — so the in-app toggle turns sound straight back on
# and a hand-launched app still opens with whatever the learner last chose.
set -eu
cd "$(dirname "$0")/.."

DEVICE='iPhone 17 Pro'
BUNDLE_ID=net.spross.app
BUILD=1
CLEAN=0
SHOT=
MUTE=
SOUND=0

while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=0; shift ;;
    --clean) CLEAN=1; shift ;;
    --device) DEVICE="$2"; shift 2 ;;
    --shot) SHOT="$2"; shift 2 ;;
    --mute) MUTE='-readAloud off'; shift ;;
    --sound) SOUND=1; shift ;;
    --) shift; break ;;
    -h|--help) sed -n '2,12p' "$0" | cut -c3-; exit 0 ;;
    *) echo "error: run-sim: unknown option '$1' (see --help)" >&2; exit 1 ;;
  esac
done

# why: `booted` is ambiguous once the paired watch simulator is up too — every
# simctl call here targets one resolved UDID instead.
UDID=$(xcrun simctl list devices available \
  | awk -v want="$DEVICE" -F'[()]' '$0 ~ "^ *" want " \\(" { print $2; exit }')
if [ -z "$UDID" ]; then
  echo "error: run-sim: no available simulator named '$DEVICE'" >&2
  echo "       xcrun simctl list devices available" >&2
  exit 1
fi

if [ "$BUILD" = 1 ]; then
  xcodebuild -project Spross.xcodeproj -scheme Spross \
    -destination "id=$UDID" build
fi

APP=$(xcodebuild -project Spross.xcodeproj -scheme Spross \
        -destination "id=$UDID" -showBuildSettings 2>/dev/null \
      | awk '/ BUILT_PRODUCTS_DIR = /{ print $3; exit }')/Spross.app
[ -d "$APP" ] || { echo "error: run-sim: no build at $APP — drop --no-build" >&2; exit 1; }

xcrun simctl bootstatus "$UDID" -b >/dev/null 2>&1 || xcrun simctl boot "$UDID"
open -a Simulator --args -CurrentDeviceUDID "$UDID"

[ "$CLEAN" = 1 ] && xcrun simctl uninstall "$UDID" "$BUNDLE_ID" >/dev/null 2>&1
xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
xcrun simctl install "$UDID" "$APP"
# why: `set -e` would take a false `[ ] && [ ] && x=y` chain as the script's failure
if [ -n "$SHOT" ] && [ "$SOUND" = 0 ]; then MUTE='-readAloud off'; fi
# unquoted: empty MUTE must vanish rather than pass an empty argument
# shellcheck disable=SC2086
xcrun simctl launch "$UDID" "$BUNDLE_ID" $MUTE "$@" >/dev/null
echo "Launched $BUNDLE_ID on $DEVICE ($UDID)."

if [ -n "$SHOT" ]; then
  # why: launch returns at spawn, not at first frame — without the pause the
  # screenshot catches the splash instead of the screen under test.
  sleep 3
  xcrun simctl io "$UDID" screenshot "$SHOT" >/dev/null 2>&1
  echo "Screenshot: $SHOT"
fi

#!/bin/sh
# Build, install and launch Spross on an iOS Simulator — usage: --help.
usage() { cat <<'EOF'
Usage: scripts/run-sim.sh [options] [SIMULATOR] [-- APP_ARGS…]

Builds Spross (Debug) and (re)launches it on an iPhone simulator, a booted one first.

  SIMULATOR     the simulator, by a case-insensitive part of its name (default: iPhone);
                an exact name wins, then a booted match, then the newest runtime
  --no-build    reinstall the last build, skip xcodebuild
  --clean       the Release app (net.spross.app), uninstalled first so onboarding
                runs; the Dev app keeps its data
  --shot FILE   screenshot once the app has drawn; implies --mute, since a run
                nobody is sitting at should not start talking
  --mute        start with reading aloud switched off, this launch only
  --sound       let a --shot run speak after all
  -h, --help    this text

Everything after `--` is passed to the app (DEBUG only, so not with --clean):
  -uitest-source de -uitest-target sw   a language pair
  -uitest-screen box                    open the Box
  -uitest-autostart 1                   start the session
  -uitest-trainer numbers|letters|countries|dates
                                        open a trainer overview (read in TrainerHubView)
  -uitest-run 1                         start the run from it
EOF
}
set -eu
cd "$(dirname "$0")/.."

DEVICE='iPhone'
BUILD=1
CLEAN=0
CONFIG=Debug
SHOT=
MUTE=
SOUND=0

while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=0; shift ;;
    --clean) CLEAN=1; CONFIG=Release; shift ;;
    --shot) SHOT="$2"; shift 2 ;;
    --mute) MUTE='-readAloud off'; shift ;;
    --sound) SOUND=1; shift ;;
    --) shift; break ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "error: run-sim: unknown option '$1' (see --help)" >&2; exit 1 ;;
    *) DEVICE="$1"; shift ;;
  esac
done

# why: `booted` is ambiguous once the paired watch simulator is up too — every
# simctl call here targets one resolved UDID instead. Among iOS simulators whose name
# contains $DEVICE, an exact name wins, then a booted one, then the LAST match: the
# newest runtime (simctl lists them oldest first), the one `name=` in the build gate picks.
# The UDID is the field shaped like one, since names carry parentheses too ("iPad Pro (M5)").
UDID=$(xcrun simctl list devices available | awk -v want="$DEVICE" '
  /^-- / { ios = ($2 == "iOS"); next }
  !ios { next }
  {
    for (i = 1; i <= NF; i++) if (length($i) == 38 && $i ~ /^\([0-9A-F-]+\)$/) break
    if (i > NF) next
    name = $1
    for (j = 2; j < i; j++) name = name " " $j
    udid = substr($i, 2, 36) "\t" name
    if (!index(tolower(name), tolower(want))) next
    if (tolower(name) == tolower(want)) exact = udid
    if ($0 ~ /\(Booted\)/) booted = udid
    last = udid
  }
  END { print (exact != "" ? exact : booted != "" ? booted : last) }')
NAME=${UDID#*	}; UDID=${UDID%%	*}
if [ -z "$UDID" ]; then
  echo "error: run-sim: no available simulator named '$DEVICE'" >&2
  echo "       xcrun simctl list devices available" >&2
  exit 1
fi

# why: shares its derived-data cache with release.sh/deploy-devices.sh
# (build/xcode.noindex, a name Spotlight skips) — `./gradlew clean` then clears every build cache at once.
DERIVED="$PWD/build/xcode.noindex"

if [ "$BUILD" = 1 ]; then
  # why: the .xcodeproj is gitignored, so a checkout or a new source file leaves it
  # listing the wrong files until regenerated.
  scripts/gen.sh >/dev/null
  echo "Building Spross for ${NAME}…"
  mkdir -p "$DERIVED"
  # why: xcodebuild waits up to 30s for an unknown destination to appear; the simulator
  # is already resolved and listed, so one that is missing will not turn up.
  if ! xcodebuild -project Spross.xcodeproj -scheme Spross -configuration "$CONFIG" \
        -destination "id=$UDID" -destination-timeout 3 -derivedDataPath "$DERIVED" build 2>&1 \
      | tee "$DERIVED/build.log" | awk -f scripts/xcode-progress.awk; then
    echo "Build FAILED — full log: $DERIVED/build.log" >&2
    exit 1
  fi
fi

APP=$(xcodebuild -project Spross.xcodeproj -scheme Spross -configuration "$CONFIG" \
        -destination "id=$UDID" -derivedDataPath "$DERIVED" -showBuildSettings 2>/dev/null \
      | awk '/ BUILT_PRODUCTS_DIR = /{ print $3; exit }')/Spross.app
[ -d "$APP" ] || { echo "error: run-sim: no build at $APP — drop --no-build" >&2; exit 1; }
# why: the bundle id is the CONFIGURATION's (project.yml APP_ID_PREFIX — debug installs
# as net.spross.dev), so read it off what was just built rather than naming it again.
BUNDLE_ID=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP/Info.plist")

xcrun simctl bootstatus "$UDID" -b >/dev/null 2>&1 || xcrun simctl boot "$UDID"
# why: Xcode 27 replaced Simulator.app with DeviceHub.app; older Xcodes only have the former.
# -g opens the device window without pulling the app over whatever the user is working in.
open -g -a DeviceHub --args -CurrentDeviceUDID "$UDID" 2>/dev/null ||
  open -g -a Simulator --args -CurrentDeviceUDID "$UDID"

[ "$CLEAN" = 1 ] && xcrun simctl uninstall "$UDID" "$BUNDLE_ID" >/dev/null 2>&1
xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
xcrun simctl install "$UDID" "$APP"
# why: `set -e` would take a false `[ ] && [ ] && x=y` chain as the script's failure
if [ -n "$SHOT" ] && [ "$SOUND" = 0 ]; then MUTE='-readAloud off'; fi
# unquoted: empty MUTE must vanish rather than pass an empty argument
# shellcheck disable=SC2086
xcrun simctl launch "$UDID" "$BUNDLE_ID" $MUTE "$@" >/dev/null
echo "Launched $BUNDLE_ID on $NAME ($UDID)."

if [ -n "$SHOT" ]; then
  # why: launch returns at spawn, not at first frame — without the pause the
  # screenshot catches the splash instead of the screen under test.
  sleep 3
  xcrun simctl io "$UDID" screenshot "$SHOT" >/dev/null 2>&1
  echo "Screenshot: $SHOT"
fi

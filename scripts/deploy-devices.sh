#!/bin/sh
# Build DuoLernen in Release and install it on all paired physical devices.
#   iPhones (Mars, Pluto) → DuoLernen.app      (Release-iphoneos)
#   Apple Watch (Ruby)    → DuoLernenWatch.app (Release-watchos)
# One scheme build produces both products; devices are matched by name, so the
# script keeps working if a device is re-paired and its UUID changes.
#
#   scripts/deploy-devices.sh             build, then install to all devices
#   scripts/deploy-devices.sh --launch    also open the app on the iPhones
#   scripts/deploy-devices.sh --no-build  reuse the last build, just reinstall
#   scripts/deploy-devices.sh --dry-run   show which devices are reachable, do nothing
# Flags combine, e.g. --no-build --launch.
set -eu
cd "$(dirname "$0")/.."

SCHEME="DuoLernen"
CONFIG="Release"
BUNDLE_ID="dev.tj.DuoLernen"
DERIVED="${TMPDIR:-/tmp}/duolernen-deploy"
IPHONES="Mars Pluto"   # get DuoLernen.app (opened with --launch)
WATCHES="Ruby"         # gets DuoLernenWatch.app (install only)

IOS_APP="$DERIVED/Build/Products/$CONFIG-iphoneos/DuoLernen.app"
WATCH_APP="$DERIVED/Build/Products/$CONFIG-watchos/DuoLernenWatch.app"

DO_BUILD=1; DRY=0; LAUNCH=0
for arg in "$@"; do
  case "$arg" in
    --no-build) DO_BUILD=0 ;;
    --dry-run)  DRY=1 ;;
    --launch)   LAUNCH=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

# UUID for a paired device name, or empty if it is not currently connected.
device_id() {
  xcrun devicectl list devices 2>/dev/null | awk -v n="$1" '$1 == n { print $3; exit }'
}

install_app() {  # name  app_path  launch(0|1)
  name="$1"; app="$2"; launch="$3"
  id="$(device_id "$name")"
  if [ -z "$id" ]; then
    printf '  skip  %-6s — not connected\n' "$name"
    return
  fi
  if [ "$DRY" -eq 1 ]; then
    [ "$launch" -eq 1 ] && tag=' (launch)' || tag=''
    printf '  plan  %-6s ← %s%s\n' "$name" "$(basename "$app")" "$tag"
    return
  fi
  if xcrun devicectl device install app --device "$id" "$app" >/dev/null 2>&1; then
    printf '  ok    %-6s installed\n' "$name"
  else
    printf '  FAIL  %-6s install failed (locked / asleep / out of range?)\n' "$name"
    return
  fi
  if [ "$launch" -eq 1 ]; then
    if xcrun devicectl device process launch --device "$id" "$BUNDLE_ID" >/dev/null 2>&1; then
      printf '  run   %-6s launched\n' "$name"
    else
      printf '  note  %-6s installed but not launched — open it on the device\n' "$name"
    fi
  fi
}

if [ "$DO_BUILD" -eq 1 ] && [ "$DRY" -eq 0 ]; then
  mkdir -p "$DERIVED"
  echo "Building $SCHEME ($CONFIG) for device…"
  if ! xcodebuild -project DuoLernen.xcodeproj -scheme "$SCHEME" -configuration "$CONFIG" \
        -destination 'generic/platform=iOS' -derivedDataPath "$DERIVED" \
        -allowProvisioningUpdates build >"$DERIVED/build.log" 2>&1; then
    echo "Build FAILED — last lines of $DERIVED/build.log:"
    tail -25 "$DERIVED/build.log"
    exit 1
  fi
fi

if [ "$DRY" -eq 0 ] && [ ! -d "$IOS_APP" ]; then
  echo "No build products at $DERIVED — run without --no-build first." >&2
  exit 1
fi

if [ -d "$IOS_APP" ]; then
  VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$IOS_APP/Info.plist" 2>/dev/null || echo '?')"
  echo "DuoLernen $VERSION →"
else
  echo "DuoLernen (dry run) →"
fi

for d in $IPHONES; do install_app "$d" "$IOS_APP" "$LAUNCH"; done
if [ -d "$WATCH_APP" ] || [ "$DRY" -eq 1 ]; then
  for d in $WATCHES; do install_app "$d" "$WATCH_APP" 0; done
else
  echo "  skip  $WATCHES — no watch product in this build"
fi
echo "Done."

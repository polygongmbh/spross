#!/bin/sh
# Build Spross in Release and install it on all paired physical devices.
#   iPhones (Mars, Pluto) → Spross.app      (Release-iphoneos)
#   Apple Watch (Ruby)    → SprossWatch.app (Release-watchos)
# One scheme build produces both products; devices are matched by name, so the
# script keeps working if a device is re-paired and its UUID changes.
#
#   scripts/deploy-devices.sh             build, then install to all devices
#   scripts/deploy-devices.sh Pluto       install to that one device only
#   scripts/deploy-devices.sh --launch    also open the app on the iPhones
#   scripts/deploy-devices.sh --no-build  reuse the last build, just reinstall
#   scripts/deploy-devices.sh --dry-run   show which devices are reachable, do nothing
#   scripts/deploy-devices.sh --debug     Debug build — for iterating, not for judging
# Flags combine, e.g. --no-build --launch Pluto.
# A bare name may be any paired device, listed above or not — watches are
# recognized by model and get SprossWatch.app.
set -eu
cd "$(dirname "$0")/.."

SCHEME="Spross"
BUNDLE_ID="net.spross.app"
# why: $TMPDIR is purged on reboot and under disk pressure, and every purge
# costs a full Release rebuild — the deploy keeps its own gitignored tree.
DERIVED="$PWD/.build/deploy"
IPHONES="Mars Pluto"   # get Spross.app (opened with --launch)
WATCHES="Ruby"         # gets SprossWatch.app (install only)

CONFIG="Release"; DO_BUILD=1; DRY=0; LAUNCH=0; ONLY=''
for arg in "$@"; do
  case "$arg" in
    --no-build) DO_BUILD=0 ;;
    --dry-run)  DRY=1 ;;
    --launch)   LAUNCH=1 ;;
    --debug)    CONFIG="Debug" ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) ONLY="$arg" ;;
  esac
done

# Release optimizes the Kotlin/Native link (35s against 6s) and compiles Swift
# whole-module, so a Debug deploy is minutes cheaper across an afternoon of
# edits — at the cost of an app that no longer runs like the shipped one.
IOS_APP="$DERIVED/Build/Products/$CONFIG-iphoneos/Spross.app"
WATCH_APP="$DERIVED/Build/Products/$CONFIG-watchos/SprossWatch.app"

echo "Paired devices:"
xcrun devicectl list devices || true
echo

# The `devicectl list devices` row for a name, matched case-insensitively —
# empty when it is not paired. Re-queried per lookup rather than reusing the
# listing above, so a device that wakes up during the build is still reached.
device_row() {
  xcrun devicectl list devices 2>/dev/null |
    awk -v n="$1" 'tolower($1) == tolower(n) { print; exit }'
}

# UUID for a paired device name, or empty if it is not currently reachable:
# every paired device stays listed, so the state column is what decides.
device_id() {
  device_row "$1" | awk '$4 ~ /^(available|connected)/ { print $3 }'
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
  if ! xcodebuild -project Spross.xcodeproj -scheme "$SCHEME" -configuration "$CONFIG" \
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
  echo "Spross $VERSION →"
else
  echo "Spross (dry run) →"
fi

if [ -n "$ONLY" ]; then
  ROW="$(device_row "$ONLY")"
  if [ -z "$ROW" ]; then
    echo "  no paired device named '$ONLY' — pick one from the list above" >&2
    exit 2
  fi
  ONLY="$(printf '%s\n' "$ROW" | awk '{ print $1 }')"   # canonical casing
  case "$ROW" in
    *"Apple Watch"*)
      if [ -d "$WATCH_APP" ] || [ "$DRY" -eq 1 ]; then
        install_app "$ONLY" "$WATCH_APP" 0
      else
        echo "  skip  $ONLY — no watch product in this build"
      fi ;;
    *) install_app "$ONLY" "$IOS_APP" "$LAUNCH" ;;
  esac
else
  for d in $IPHONES; do install_app "$d" "$IOS_APP" "$LAUNCH"; done
  if [ -d "$WATCH_APP" ] || [ "$DRY" -eq 1 ]; then
    for d in $WATCHES; do install_app "$d" "$WATCH_APP" 0; done
  else
    echo "  skip  $WATCHES — no watch product in this build"
  fi
fi
echo "Done."

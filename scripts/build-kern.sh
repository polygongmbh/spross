#!/bin/sh
# Xcode pre-build phase: build the SprossKern static framework slice matching
# the active configuration + SDK, then stage it at the configuration-neutral
# path FRAMEWORK_SEARCH_PATHS points to (kmp/kern/build/xcode/<config>/).
# Idempotent — Gradle skips up-to-date links, rsync only syncs deltas.
# Runnable outside Xcode:
#   CONFIGURATION=Debug SDK_NAME=iphonesimulator scripts/build-kern.sh
set -eu
cd "$(dirname "$0")/.."

case "${CONFIGURATION:?CONFIGURATION not set (Debug|Release)}" in
  Debug)   LINK=linkDebugFramework;   BIN=debugFramework ;;
  Release) LINK=linkReleaseFramework; BIN=releaseFramework ;;
  *) echo "error: build-kern.sh: unsupported CONFIGURATION '$CONFIGURATION'" >&2; exit 1 ;;
esac

case "${SDK_NAME:?SDK_NAME not set (iphonesimulator*|iphoneos*)}" in
  iphonesimulator*) SLICE=IosSimulatorArm64; BINDIR=iosSimulatorArm64 ;;
  iphoneos*)        SLICE=IosArm64;          BINDIR=iosArm64 ;;
  *) echo "error: build-kern.sh: unsupported SDK_NAME '$SDK_NAME'" >&2; exit 1 ;;
esac
case "${ARCHS:-arm64}" in
  *arm64*) ;;
  *) echo "error: build-kern.sh: no SprossKern slice for ARCHS='$ARCHS' (arm64 only)" >&2; exit 1 ;;
esac

# why: Xcode script phases run with a minimal PATH and no JAVA_HOME — resolve
# the JDK the way a terminal would, or gradlew dies with a cryptic error.
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  [ -n "$JAVA_HOME" ] && export JAVA_HOME
fi

if ! ./gradlew ":kern:${LINK}${SLICE}"; then
  echo "error: build-kern.sh: Gradle :kern:${LINK}${SLICE} FAILED — SprossKern.framework not updated" >&2
  exit 1
fi

SRC="kmp/kern/build/bin/${BINDIR}/${BIN}/SprossKern.framework"
if [ ! -d "$SRC" ]; then
  echo "error: build-kern.sh: expected framework missing at $SRC" >&2
  exit 1
fi
DEST="kmp/kern/build/xcode/${CONFIGURATION}"
mkdir -p "$DEST"
rsync -a --delete "$SRC" "$DEST/"

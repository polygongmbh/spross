#!/bin/sh
# Xcode pre-build phase: build the SprossKern static framework slice matching
# the active configuration + SDK, then stage it at the configuration-neutral
# path FRAMEWORK_SEARCH_PATHS points to (kern/build/xcode/<config>/).
# Idempotent, and it does not even start Gradle unless a kern source, a build
# file or the staged slice says it must.
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

DEST="kern/build/xcode/${CONFIGURATION}"
# Which slice the staged framework holds — device and simulator share the
# configuration-neutral path, so the stamp is what makes a skip safe.
STAMP="$DEST/.slice"
KERN_INPUTS="kern/src/commonMain kern/src/iosMain kern/build.gradle.kts
             build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml"

# why: the phase runs on every build (basedOnDependencyAnalysis: false), and
# Gradle costs a second warm, fifteen when it has to reconfigure — even with
# nothing to do. Nothing newer than the staged slice means nothing to do.
if [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$SLICE" ] &&
   [ -z "$(find $KERN_INPUTS -newer "$STAMP" -print -quit 2>/dev/null)" ]; then
  exit 0
fi

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

SRC="kern/build/bin/${BINDIR}/${BIN}/SprossKern.framework"
if [ ! -d "$SRC" ]; then
  echo "error: build-kern.sh: expected framework missing at $SRC" >&2
  exit 1
fi
mkdir -p "$DEST"
rsync -a --delete "$SRC" "$DEST/"
printf '%s\n' "$SLICE" > "$STAMP"

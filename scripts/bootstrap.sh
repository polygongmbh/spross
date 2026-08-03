#!/bin/sh
# Fresh-clone setup: verify the Gradle wrapper + JDK, build a first SprossKern
# debug framework for the simulator, and generate Spross.xcodeproj.
set -eu
cd "$(dirname "$0")/.."

if ! ./gradlew --version >/dev/null 2>&1; then
  echo "error: bootstrap: './gradlew --version' failed — is a JDK (21) installed?" >&2
  exit 1
fi

CONFIGURATION=Debug SDK_NAME=iphonesimulator sh scripts/build-kern.sh

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "error: bootstrap: xcodegen not installed (brew install xcodegen)" >&2
  exit 1
fi
xcodegen generate
echo "Bootstrap complete — open Spross.xcodeproj (scheme: Spross)."

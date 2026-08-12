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

# why: the checked-in hooks only run once git is told where they live, and that
# setting is per clone. An existing hooksPath that resolves to a real directory is
# somebody's own arrangement and is left alone; one pointing nowhere is not.
HOOKS=$(git config core.hooksPath || true)
if [ -z "$HOOKS" ] || [ ! -d "$HOOKS" ]; then
  chmod +x scripts/hooks/*
  git config core.hooksPath scripts/hooks
  echo "Installed git hooks (scripts/hooks)."
elif [ "$HOOKS" != "scripts/hooks" ]; then
  echo "note: core.hooksPath is '$HOOKS' — scripts/hooks/pre-commit is not running." >&2
fi

echo "Bootstrap complete — open Spross.xcodeproj (scheme: Spross)."

#!/bin/sh
# Fresh-clone setup: install the git hooks, verify the Gradle wrapper + JDK, build a first
# SprossKern debug framework for the simulator, and generate Spross.xcodeproj.
#
# The hooks go first because everything after them needs a Mac: a clone that stops at the
# missing xcodegen still commits, and the pre-commit checks are text-only by design.
set -eu
cd "$(dirname "$0")/.."

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

# why: the audio scripts' measures need numpy and onnxruntime, and onnxruntime ships wheels a
# Python minor behind Homebrew's python3 — so they get their own venv on the newest Python
# that has them (scripts/audio_env.py re-runs every audio script there).
AUDIO_PYTHON=${AUDIO_PYTHON:-python3.14}
if [ ! -x .venv/bin/python ] && command -v "$AUDIO_PYTHON" >/dev/null 2>&1; then
  "$AUDIO_PYTHON" -m venv .venv && .venv/bin/pip install -q -r scripts/requirements-audio.txt \
    && echo "Created .venv for the audio scripts ($AUDIO_PYTHON)."
fi

if ! ./gradlew --version >/dev/null 2>&1; then
  echo "error: bootstrap: './gradlew --version' failed — is a JDK (21) installed?" >&2
  exit 1
fi

CONFIGURATION=Debug SDK_NAME=iphonesimulator sh scripts/build-kern.sh

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "error: bootstrap: xcodegen not installed (brew install xcodegen)" >&2
  exit 1
fi
scripts/gen.sh

echo "Bootstrap complete — open Spross.xcodeproj (scheme: Spross)."

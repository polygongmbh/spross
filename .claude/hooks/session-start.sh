#!/bin/bash
# Remote (Claude Code on the web) sessions land on Linux containers that ship
# JDK 21 as the system default but no JDK 17 and no Xcode/iOS tooling at all.
# kern/build.gradle.kts pins jvmToolchain(17), so without a JDK 17 install
# Gradle's toolchain resolution fails outright (no download repository is
# configured) — this installs it once per container so `:kern:jvmTest` works
# without probing for or trying to install Xcode, which can never be present
# here. Local/Mac sessions already have both via scripts/bootstrap.sh.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

if [ -d /usr/lib/jvm/java-17-openjdk-amd64 ] || command -v javac-17 >/dev/null 2>&1; then
  echo "JDK 17 already present — skipping install."
  exit 0
fi

apt-get update -qq
apt-get install -y -qq --no-install-recommends openjdk-17-jdk-headless

# why: leave JAVA_HOME on the preinstalled JDK 21 for everything else — Gradle
# auto-detects JDK 17 under /usr/lib/jvm for the :kern toolchain on its own.
if [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
  echo "JDK 17 installed — Gradle will auto-detect it for the :kern toolchain."
else
  echo "warning: session-start: openjdk-17-jdk-headless install did not produce /usr/lib/jvm/java-17-openjdk-amd64" >&2
fi

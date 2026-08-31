#!/bin/sh
# Cut a release: the changelog heading, the version, the gates, the tag, the push.
#
#   scripts/release.sh 5.7.2            cut it and push
#   scripts/release.sh --check 5.7.2    everything up to the tag, nothing written to origin
#   scripts/release.sh --no-app 5.7.2   skip the app build (Linux/cloud, no Xcode)
#
# The tag is the trigger AND the version (docs/distribution.md), so every gate runs
# BEFORE it exists: a red found afterwards costs a release, not a rerun.
# What this cannot decide stays yours — which version number, and what the changelog
# entries say. Write those first; this refuses an empty '## Unreleased'.
set -eu
cd "$(dirname "$0")/.."

CHECK=0; APP_GATE=1; VERSION=''
for arg in "$@"; do
  case "$arg" in
    --check)  CHECK=1 ;;
    --no-app) APP_GATE=0 ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) VERSION="$arg" ;;
  esac
done

case "$VERSION" in
  '') echo "usage: scripts/release.sh [--check] [--no-app] <version>" >&2; exit 2 ;;
esac
# Android's versionCode is derived from the name (5.7.2 → 50702), so a minor or patch
# reaching 100 would collide with the next component up.
echo "$VERSION" | awk -F. '
  NF != 3 || $1 !~ /^[0-9]+$/ || $2 !~ /^[0-9]+$/ || $3 !~ /^[0-9]+$/ {
    print "error: version must read major.minor.patch" > "/dev/stderr"; exit 1 }
  $2 >= 100 || $3 >= 100 {
    print "error: minor and patch stay under 100 — versionCode packs them two digits wide" > "/dev/stderr"; exit 1 }
'

if git rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null; then
  echo "error: v$VERSION already exists — pick the next number" >&2
  exit 1
fi

# The section may already be cut by hand; only an untouched '## Unreleased' is renamed.
if ! grep -q "^## $VERSION\( \|$\)" CHANGELOG.md; then
  BODY=$(awk '/^## Unreleased/ { inside = 1; next } inside && /^## / { exit } inside' CHANGELOG.md | tr -d ' \n')
  if [ -z "$BODY" ]; then
    echo "error: '## Unreleased' is empty — write what moved for the LEARNER first" >&2
    echo "       (what earns an entry: docs/distribution.md)" >&2
    exit 1
  fi
  awk -v v="$VERSION" -v d="$(date +%F)" '
    /^## Unreleased/ && !done { print "## Unreleased"; print ""; print "## " v " — " d; done = 1; next }
    { print }
  ' CHANGELOG.md > CHANGELOG.md.tmp && mv CHANGELOG.md.tmp CHANGELOG.md
  echo "CHANGELOG.md  → ## $VERSION — $(date +%F)"
fi
scripts/release-notes.sh "$VERSION" >/dev/null

awk -v v="$VERSION" '
  /^ *MARKETING_VERSION:/ { sub(/:.*/, ": " v) } { print }
' project.yml > project.yml.tmp && mv project.yml.tmp project.yml
grep -q "MARKETING_VERSION: $VERSION" project.yml || { echo "error: MARKETING_VERSION not rewritten" >&2; exit 1; }
echo "project.yml   → MARKETING_VERSION: $VERSION"

# Spross.xcodeproj is generated and gitignored, so it keeps the OLD number until this
# runs — without it a local build installs $VERSION's code stamped as its predecessor.
if command -v xcodegen >/dev/null 2>&1; then
  scripts/gen.sh >/dev/null
  echo "Spross.xcodeproj regenerated."
fi

echo "Gates…"
./gradlew --console=plain -q :kern:jvmTest -Psweeps :android:testDebugUnitTest

# The app gate is the only one that sees a Swift call site: a Kotlin change can leave
# every Kotlin gate green and still fail to compile against the framework
# (kern/docs/build.md § Swift ergonomics). Unsigned simulator build — a signing or
# provisioning failure is the Mac's, never the release's.
if [ "$APP_GATE" -eq 1 ] && command -v xcodebuild >/dev/null 2>&1; then
  SIM=$(xcrun simctl list devices available | awk -F' \\(' '/^ +iPhone/ { print $1; exit }' | sed 's/^ *//')
  [ -n "$SIM" ] || { echo "error: no iPhone simulator installed — pass --no-app to skip" >&2; exit 1; }
  echo "App build ($SIM)…"
  xcodebuild -project Spross.xcodeproj -scheme Spross \
    -destination "platform=iOS Simulator,name=$SIM" \
    -derivedDataPath .build/release-gate -quiet build
fi

if [ "$CHECK" -eq 1 ]; then
  echo "Green. --check, so nothing was committed; the two edited files are in the tree."
  exit 0
fi

# --only: another party's work may be staged or in flight, and none of it is this release.
git commit -m "chore: release $VERSION" --only CHANGELOG.md project.yml
git tag "v$VERSION"
git push origin "$(git rev-parse --abbrev-ref HEAD)" "v$VERSION"

cat <<DONE

v$VERSION is pushed — .github/workflows/release.yml builds it from the tag.
Your own devices are a separate step: scripts/deploy-devices.sh --launch
DONE

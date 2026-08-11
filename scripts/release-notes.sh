#!/bin/sh
# Print one version's CHANGELOG section — the body of its GitHub release.
#   scripts/release-notes.sh 4.0.0
# Empty is an error: a release whose version never got a changelog heading is a
# release nobody wrote notes for, and that is worth failing the tag build over.
set -eu
cd "$(dirname "$0")/.."

VERSION="${1:?usage: release-notes.sh <version>}"

NOTES=$(awk -v v="$VERSION" '
  BEGIN { esc = v; gsub(/\./, "\\.", esc) }
  $0 ~ "^## " esc "( |$)" { inside = 1; next }
  inside && /^## / { exit }
  inside { print }
' CHANGELOG.md)

# Trim the blank lines the section boundaries leave behind.
NOTES=$(printf '%s\n' "$NOTES" | sed -e '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;};/\n$/ba')

if [ -z "$NOTES" ]; then
  echo "error: CHANGELOG.md has no '## $VERSION' section — rename '## Unreleased' before tagging" >&2
  exit 1
fi

printf '%s\n' "$NOTES"

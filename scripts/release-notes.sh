#!/bin/sh
# Print one version's CHANGELOG section — usage: --help.
usage() { cat <<'EOF'
Usage: scripts/release-notes.sh VERSION

Prints VERSION's CHANGELOG section, the body of its GitHub release.
An empty section is an error: a release whose version never got a changelog
heading is a release nobody wrote notes for, worth failing the tag build over.
EOF
}
set -eu
cd "$(dirname "$0")/.."

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
  ''|-*) usage >&2; exit 2 ;;
esac
VERSION="$1"

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

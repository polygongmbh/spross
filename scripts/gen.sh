#!/bin/sh
# Regenerate Spross.xcodeproj — usage: --help.
usage() { cat <<'EOF'
Usage: scripts/gen.sh [--no-watch]

Regenerates Spross.xcodeproj: iOS app, widget, watch app and complication.
An unchanged spec and source list rewrites nothing, so callers run it before every build.

  --no-watch   iOS app and widget only: the watch targets stay in the project
               but are not embedded, so nothing watch-related needs signing
               for iPhone deployment
  -h, --help   this text
EOF
}
set -e
cd "$(dirname "$0")/.."
# why: one cache for both specs, written only here — a generation that bypassed it would
# leave it vouching for a project that is no longer on disk.
CACHE="--use-cache --cache-path build/xcodegen-cache"
case "${1:-}" in
  ''|--no-watch) ;;
  -h|--help) usage; exit 0 ;;
  *) echo "error: gen: unknown option '$1' (see --help)" >&2; exit 2 ;;
esac
if [ "${1:-}" = "--no-watch" ]; then
  python3 - <<'EOF'
spec = open('project.yml').read()
needle = "      - target: SprossWatch\n        embed: true\n"
assert needle in spec, "watch embed block not found in project.yml — update gen.sh"
open('.project-nowatch.yml', 'w').write(spec.replace(needle, ""))
EOF
  xcodegen generate $CACHE --spec .project-nowatch.yml
  echo "Generated WITHOUT embedded watch app."
else
  xcodegen generate $CACHE
fi

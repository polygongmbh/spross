#!/bin/sh
# Regenerate Spross.xcodeproj.
#   scripts/gen.sh            — full project (iOS app + widget + watch app + complication)
#   scripts/gen.sh --no-watch — iOS app + widget only (watch targets stay in the
#                               project but are not embedded; nothing watch-related
#                               needs signing for iPhone deployment)
# Unchanged spec and source list → no rewrite, so callers run it before every build.
set -e
cd "$(dirname "$0")/.."
# why: one cache for both specs, written only here — a generation that bypassed it would
# leave it vouching for a project that is no longer on disk.
CACHE="--use-cache --cache-path build/xcodegen-cache"
if [ "$1" = "--no-watch" ]; then
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

#!/bin/sh
# Regenerate DuoLernen.xcodeproj.
#   scripts/gen.sh            — full project (iOS app + widget + watch app + complication)
#   scripts/gen.sh --no-watch — iOS app + widget only (watch targets stay in the
#                               project but are not embedded; nothing watch-related
#                               needs signing for iPhone deployment)
set -e
cd "$(dirname "$0")/.."
if [ "$1" = "--no-watch" ]; then
  python3 - <<'EOF'
spec = open('project.yml').read()
needle = "      - target: DuoLernenWatch\n        embed: true\n"
assert needle in spec, "watch embed block not found in project.yml — update gen.sh"
open('.project-nowatch.yml', 'w').write(spec.replace(needle, ""))
EOF
  xcodegen generate --spec .project-nowatch.yml
  echo "Generated WITHOUT embedded watch app."
else
  xcodegen generate
fi

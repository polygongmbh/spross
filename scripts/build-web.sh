#!/usr/bin/env bash
# Assemble the deployable spross.net site into web/dist (docs/website.md).
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew --console=plain -q :kern:jsBrowserDistribution

rm -rf web/dist
mkdir -p web/dist
# why: every page, not a list of them — a legal page that exists but was never
# added to a copy line is a link the site answers 404 for, and nothing says so
# until someone follows it.
cp web/*.html web/site.css web/site.js web/dist/
cp -R web/assets web/dist/assets
# why: the drill's chimes are the app's own bytes — copied from where scripts/sounds.py
# writes them, so a re-tune by ear reaches the website with the two apps.
mkdir -p web/dist/assets/sounds
cp App/Resources/Sounds/*.wav web/dist/assets/sounds/
cp kern/build/dist/js/productionExecutable/kern.js web/dist/

echo "web/dist ready — upload to any static host."

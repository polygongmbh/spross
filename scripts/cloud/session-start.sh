#!/usr/bin/env bash
# Cloud sessions only, at start and on every prompt: Gradle through the Maven Central mirror, and a branch to push to.
[ "${CLAUDE_CODE_REMOTE:-}" = true ] || exit 0
root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0

mkdir -p ~/.gradle/init.d
cp "$root/scripts/cloud/central-mirror.gradle.kts" ~/.gradle/init.d/

# why: a detached or main checkout leaves commits nowhere to be pushed, and the container is dropped with them.
current=$(git -C "$root" symbolic-ref -q --short HEAD)
if [ -z "$current" ] || [ "$current" = main ]; then
    id=${CLAUDE_CODE_REMOTE_SESSION_ID:-$(date +%Y%m%d-%H%M%S)}
    branch="claude/session-${id: -8}"
    git -C "$root" switch -q -c "$branch" 2>/dev/null || git -C "$root" switch -q "$branch"
    echo "On branch $branch: push every commit with git push -u origin $branch."
fi
exit 0

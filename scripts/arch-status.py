#!/usr/bin/env python3
"""Which turn machines have one home in kern and which are written once per platform.

`git status` for the layering. A machine that lives in kern collapses its platform half to
a driver; one that does not gets written twice and drifts — the atlas drill reached 386 lines
of Kotlin and 264 of Swift that way, against 122 and 163 for the drill that has a kern run.

This is the only architecture fact a search cannot answer, because the finding is an ABSENCE:
no query returns the file that was never written. Run with --check to exit 1 on one.
"""
import collections
import os
import re
import sys

SKIP = re.compile(r"(^|/)(build|test|Tests|androidTest)(/|$)")
# The suffix a machine wears differs per layer; the stem is what pairs them across the three.
STEM = re.compile(r"(Run|RunState|Flow|Machine|View|Screen)$")


def sources(root, sub, ext):
    for path, _, names in os.walk(os.path.join(root, sub)):
        if SKIP.search(path):
            continue
        for name in names:
            if name.endswith(ext):
                yield name, os.path.join(path, name)


def stem(name):
    return STEM.sub("", os.path.splitext(name)[0]).split("+")[0]


def lines(path):
    with open(path, errors="replace") as handle:
        return sum(1 for _ in handle)


def survey(root):
    layers = {"kern": collections.defaultdict(int),
              "android": collections.defaultdict(int),
              "ios": collections.defaultdict(int)}
    for name, path in sources(root, "kern/src/commonMain", ".kt"):
        if re.search(r"(Run|RunState|Machine)\.kt$", name):
            layers["kern"][stem(name)] += lines(path)
    for name, path in sources(root, "android/src/main", ".kt"):
        if name.endswith("Flow.kt"):
            layers["android"][stem(name)] += lines(path)
    for name, path in sources(root, "App/Sources", ".swift"):
        if re.search(r"View(\+\w+)?\.swift$", name):
            layers["ios"][stem(name)] += lines(path)
    return layers


def main():
    check = "--check" in sys.argv
    root = next((a for a in sys.argv[1:] if not a.startswith("-")), ".")
    layers = survey(root)
    kern, android, ios = layers["kern"], layers["android"], layers["ios"]
    names = sorted(set(kern) | set(android) | {s for s in ios if s in kern or s in android})

    homeless = []
    rows = []
    for name in names:
        k, a, i = kern.get(name, 0), android.get(name, 0), ios.get(name, 0)
        if not (a or i):
            continue
        if not k:
            homeless.append(name)
        rows.append((name, k, a, i))

    if check and not homeless:
        return 0
    if not check:
        print(f"{'machine':<16}{'kern':>7}{'android':>9}{'iOS':>7}   one home?")
        for name, k, a, i in rows:
            verdict = "yes" if k else "NO — written once per platform"
            print(f"  {name:<14}{k or '—':>7}{a or '—':>9}{i or '—':>7}   {verdict}")
        # why: the report is what a session opens with, so it states the gap and returns 0 —
        # only --check, which a gate calls, fails on it.
        sys.stdout.flush()
        return 0
    for name in homeless:
        print(f"error: {name} has no run in kern — its turn machine is written once per platform.",
              file=sys.stderr)
    return 1 if homeless else 0


if __name__ == "__main__":
    sys.exit(main())

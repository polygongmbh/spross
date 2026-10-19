#!/usr/bin/env python3
"""Which turn machines have one home in kern and which are written once per platform.

`git status` for the layering. A machine that lives in kern collapses its platform half to
a driver; one that does not gets written twice and drifts.

This is the only architecture fact a search cannot answer, because the finding is an ABSENCE:
no query returns the file that was never written. Run with --check to exit 1 on one.

A kern stem is a machine when one of its files defines `reduce(`; its other `*Run`/`*RunState`/
`*Machine` files count toward it. A platform stem whose own type is generic or a protocol is the
shared shell every drill stands on, never a machine of its own.
"""
import collections
import os
import re
import sys

SKIP = re.compile(r"(^|/)(build|test|Tests|androidTest)(/|$)")
# The suffixes a machine wears differ per layer and stack (`NumbersRunView`);
# the stem left after stripping them all is what pairs the three layers.
STEM = re.compile(r"(Run|RunState|Flow|Machine|View|Screen|Face)$")
REDUCE = re.compile(r"\bfun\s+reduce\(")


def sources(root, sub, ext):
    for path, _, names in os.walk(os.path.join(root, sub)):
        if SKIP.search(path):
            continue
        for name in names:
            if name.endswith(ext):
                yield name, os.path.join(path, name)


def base(name):
    return os.path.splitext(name)[0].split("+")[0]


def stem(name):
    current = base(name)
    while (shorter := STEM.sub("", current)) not in (current, ""):
        current = shorter
    return current


def read(path):
    with open(path, errors="replace") as handle:
        return handle.read()


def is_shell(name, text):
    own = re.escape(base(name))
    return re.search(rf"\b(class|struct)\s+{own}\s*<|\b(protocol|interface)\s+{own}\b", text) is not None


def survey(root):
    layers = {"kern": collections.defaultdict(int),
              "android": collections.defaultdict(int),
              "ios": collections.defaultdict(int)}
    machines, shells = set(), set()
    for name, path in sources(root, "kern/src/commonMain", ".kt"):
        if re.search(r"(Run|RunState|Machine)\.kt$", name):
            text = read(path)
            layers["kern"][stem(name)] += text.count("\n")
            if REDUCE.search(text):
                machines.add(stem(name))
    for layer, sub, ext, pattern in (("android", "android/src/main", ".kt", r"Flow\.kt$"),
                                     ("ios", "App/Sources", ".swift", r"(View|Face)(\+\w+)?\.swift$")):
        for name, path in sources(root, sub, ext):
            if re.search(pattern, name):
                text = read(path)
                layers[layer][stem(name)] += text.count("\n")
                if is_shell(name, text):
                    shells.add(stem(name))
    layers["kern"] = {s: n for s, n in layers["kern"].items() if s in machines}
    return layers, shells


def main():
    check = "--check" in sys.argv
    root = next((a for a in sys.argv[1:] if not a.startswith("-")), ".")
    layers, shells = survey(root)
    kern, android, ios = layers["kern"], layers["android"], layers["ios"]
    names = sorted(set(kern) | set(android) | {s for s in ios if s in kern or s in android})

    homeless = []
    rows = []
    for name in names:
        k, a, i = kern.get(name, 0), android.get(name, 0), ios.get(name, 0)
        if not (a or i):
            continue
        if not k and name not in shells:
            homeless.append(name)
        rows.append((name, k, a, i))

    if check and not homeless:
        return 0
    if not check:
        print(f"{'machine':<18}{'kern':>7}{'android':>9}{'iOS':>7}   one home?")
        for name, k, a, i in rows:
            verdict = ("yes" if k else "shared shell — drives the machines above" if name in shells
                       else "NO — written once per platform")
            print(f"  {name:<16}{k or '—':>7}{a or '—':>9}{i or '—':>7}   {verdict}")
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

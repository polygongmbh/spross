#!/usr/bin/env python3
"""The card faces may not mint their own spacing, type size or reserve.

Every prompt card on either phone is one composition of shared primitives, and the
numbers it is built from live in the two token tables — `App/Sources/Design/Theme.swift`
and `android/.../ui/Theme.kt`. A raw pt/dp/sp number inside a card file is how a second
layout gets born: nine of these divergences were introduced and corrected again over
three weeks, and in none of them was an existing shared component skipped — the canonical
form was near-identical code in a sibling file nobody had to look at.

Three checks, no build required (pure text, so it runs on a machine with no Xcode):

  1. The two token tables agree, name for name.
  2. A card file states no number the tables already name.
  3. A card FACE is composed of the shared primitives, not cut fresh.

A number a card genuinely owes to the device rather than to the design — a hit target, a
hairline — is listed in ALLOW. A one-off that neither table can own carries its reason on
the line itself:

    .frame(minHeight: 96)  // card-parity: the widget's own frame, not a card reserve

  card-parity.py           report every finding
  card-parity.py --check   say nothing unless something is wrong; exit 1 if it is
  card-parity.py --fix     rewrite the numbers a table already names, report the rest
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CANON = "App/Sources/Design/Theme.swift"
DROID = "android/src/main/kotlin/net/spross/app/ui/Theme.kt"

# A card FACE is the surface a question is asked on; a card BODY is what a face composes.
# Only a face owes the primitives — a body is already inside one.
IOS_FACES = ["App/Sources/Design/VocabCardView.swift", "App/Sources/Design/CountryPromptCard.swift",
             "App/Sources/Design/HearPromptCard.swift", "App/Sources/Screens/TrainerPromptCard.swift"]
IOS_BODIES = ["App/Sources/Design/CardReveal.swift", "App/Sources/Design/SpokenWord.swift"]
DROID_UI = "android/src/main/kotlin/net/spross/app/ui/"
DROID_FACES = [DROID_UI + n for n in ("CardFace.kt", "CountryPromptCard.kt", "ProduceCard.kt",
                                      "TrainerPrompt.kt", "LetterDrillScreen.kt")]
DROID_BODIES = [DROID_UI + "LetterDrillStages.kt"]

# The primitives a face is built from. Two is the bar: a face that reaches for none of
# them is not a card, it is a rectangle that happens to look like one today.
IOS_PRIMS = ["cardSurface", "CardReveal", "CardEmoji", "SpokenWord"]
DROID_PRIMS = ["CardFace", "CardReveal", "EmojiSlot", "SpokenWord", "Headword", ".panel("]

# Sizes a card may state inline: a hit target and a hairline are device facts, not design.
ALLOW = {"0", "1", "44", "48"}

# Token group → the type that declares it. Both phones spell these alike, so one
# name reaches the Swift struct and the Kotlin class both.
GROUPS = (("spacing", "Spacing"), ("reserve", "Reserve"), ("radius", "Radius"))
WAIVER = "card-parity:"

# (pattern, what it should have said, token table to rewrite from) — group 1 is the number.
IOS_RULES = [
    (re.compile(r"\.font\(\s*\.system\(size:\s*(\d+)"), "a Theme.typography role", None, None),
    (re.compile(r"(?:spacing|padding)\(\s*(\d+(?:\.\d+)?)\s*\)"),
     "Theme.spacing", "Theme.spacing.", "spacing"),
    (re.compile(r"minHeight:\s*(\d+(?:\.\d+)?)\b"), "Theme.reserve", "Theme.reserve.", "reserve"),
    (re.compile(r"cornerRadius:\s*(\d+(?:\.\d+)?)"), "Theme.radius", "Theme.radius.", "radius"),
]
DROID_RULES = [
    (re.compile(r"fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp"),
     "a typography role or Theme.prompt", None, None),
    (re.compile(r"spacedBy\(\s*(\d+(?:\.\d+)?)\.dp"),
     "Theme.spacing", "Theme.spacing.", "spacing"),
    (re.compile(r"\.padding\(\s*(\d+(?:\.\d+)?)\.dp"),
     "Theme.spacing", "Theme.spacing.", "spacing"),
    (re.compile(r"heightIn\(\s*min\s*=\s*(\d+(?:\.\d+)?)\.dp"),
     "Theme.reserve", "Theme.reserve.", "reserve"),
    (re.compile(r"private val [A-Z_]*CARD[A-Z_]*\s*=\s*(\d+(?:\.\d+)?)\.dp"),
     "Theme.reserve", "Theme.reserve.", "reserve"),
    (re.compile(r"RoundedCornerShape\(\s*(\d+(?:\.\d+)?)\.dp"),
     "MaterialTheme.shapes", None, None),
]


def read(rel):
    path = ROOT / rel
    if not path.is_file():
        sys.exit(f"card-parity: {rel} is missing — the layout it holds cannot be checked")
    return path.read_text()


def block(text, opener, closer, pattern):
    """The `name = number` pairs one token table declares, and nothing outside it."""
    start = text.index(opener) + len(opener)
    return dict(re.findall(pattern, text[start:text.index(closer, start)]))


def tables():
    """Both token tables, read as text, keyed by family and token name."""
    swift, kotlin = read(CANON), read(DROID)
    swift_token = r"let (\w+): CGFloat = (\d+)"
    kotlin_token = r"val (\w+) = (\d+)\.dp"
    ios = {group: block(swift, f"struct {struct} {{", "\n    }", swift_token)
           for group, struct in GROUPS}
    droid = {group: block(kotlin, f"class {struct} internal constructor() {{",
                          "\n    }", kotlin_token)
             for group, struct in GROUPS if group != "radius"}
    # Compose has no radius table of its own: the family IS `Shapes`, one slot per size.
    droid["radius"] = {ios_name: found[0] for ios_name, shape in
                       (("card", "large"), ("tile", "medium"), ("control", "small"))
                       if (found := re.findall(rf"\b{shape} = RoundedCornerShape\((\d+)\.dp\)", kotlin))}
    return ios, droid


def parity(ios, droid, report):
    """Every token one table names, the other names too — with the same number."""
    bad = 0
    for family, _ in GROUPS:
        for name in sorted(set(ios[family]) | set(droid[family])):
            here, there = ios[family].get(name), droid[family].get(name)
            if here == there:
                continue
            report(f"FAIL {family}.{name}: iOS says {here}, Android says {there}")
            bad += 1
    return bad


def scan(files, rules, prims, tokens, report, fix):
    """A card file states no number a table already names, and a face composes primitives."""
    bad = 0
    for rel in files:
        path = ROOT / rel
        lines = path.read_text().splitlines(keepends=True)
        changed = False
        for n, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith(("//", "*", "/*")) or WAIVER in line:
                continue
            for pattern, wanted, prefix, group in rules:
                for match in list(pattern.finditer(line)):
                    value = match.group(1)
                    if value.rstrip("0").rstrip(".") in ALLOW or value in ALLOW:
                        continue
                    named = prefix and named_token(tokens, prefix, group, value)
                    if fix and named:
                        # The token carries its own unit, so a `.dp`/`.sp` behind the
                        # number goes with it — `Theme.spacing.sm.dp` compiles as neither.
                        end = match.end(1)
                        end += 3 if line[end:end + 3] in (".dp", ".sp") else 0
                        lines[n] = line = line[:match.start(1)] + named + line[end:]
                        changed = True
                        report(f"fix  {rel}:{n + 1}  {value} → {named}")
                        continue
                    report(f"FAIL {rel}:{n + 1}  {value} is not the card's to state — use {wanted}")
                    bad += 1
        if changed:
            path.write_text("".join(lines))
        if prims:
            used = [k for k in prims if k in "".join(lines)]
            if len(used) < 2:
                report(f"FAIL {rel}: built from {len(used)} shared primitives, not a card composition")
                bad += 1
    return bad


def named_token(tokens, prefix, group, value):
    """The token name a raw number already has, if one of the tables owns it."""
    for name, number in tokens.get(group, {}).items():
        if number == value.rstrip("0").rstrip("."):
            return prefix + name
    return None


def main(argv):
    check, fix = "--check" in argv, "--fix" in argv
    lines = []
    report = lines.append
    ios, droid = tables()
    bad = parity(ios, droid, report)
    for files, rules, prims, tokens in (
        (IOS_FACES, IOS_RULES, IOS_PRIMS, ios), (IOS_BODIES, IOS_RULES, None, ios),
        (DROID_FACES, DROID_RULES, DROID_PRIMS, droid), (DROID_BODIES, DROID_RULES, None, droid),
    ):
        bad += scan(files, rules, prims, tokens, report, fix)
    if not (check and not bad):
        print("\n".join(lines) or "card-parity: the cards are one composition on both phones.")
    if bad and not check:
        print(f"\n{bad} finding(s)" + ("" if fix else " — `--fix` rewrites the mechanical ones"))
    return 1 if bad and check else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

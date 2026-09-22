#!/usr/bin/env python3
"""The alternate-form fields say what the app does with them: `accepts` and `teaches`.

    scripts/rename-alternates.py --check   # name every surviving `variants`/`synonyms`, exit 1
    scripts/rename-alternates.py --fix     # rewrite them, then re-run the catalog formatter

A realization's two alternate lists were `variants` and `synonyms` — linguistics terms
that name neither the content nor the behavior, so every author re-derived the
distinction from the docs. The names now state the rule: a card ACCEPTS `kando` when
grading (accept-only, never scheduled, never shown) and TEACHES `mjini` as knowledge
(prompt-worthy, on the concept's one schedule, shown on the reveal). Semantics did not
move; only the names did.

The rename is 3300 catalog keys plus every reader, which is why it is a script and why
the script stays: `--check` is what says the old names are gone, and a re-run resolves
the drift an in-flight branch carries back in. Both modes are idempotent.

What it rewrites:
- `catalog/**/*.json` in KEY POSITION ONLY (`"variants":`), so a note that happens to
  use the English word is never touched. `catalog/audio/*/manifest.json` is generated
  and carries neither key, so it needs nothing.
- `.kt` / `.swift` / `.py` / `.md`: the whole-word tokens, declarations, readers, the
  string literals the parsers match keys with, and the prose that names the fields.

What it leaves alone — the same words in another sense, listed as (path glob, regex)
in KEEP below, matched against the SPAN so one line can hold both senses:
number readings (`GermanNumbers.variants(7)` is a list of spellings), the count of
headline phrasings (`HeadlineKind.variants`), a local list of prefix strippings, and
the history files, which record what the names were when they were written.
"""
import argparse
import fnmatch
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIELDS = {'variants': 'accepts', 'synonyms': 'teaches'}
EXTENSIONS = ('.kt', '.kts', '.swift', '.py', '.md', '.json')
SELF = os.path.join('scripts', 'rename-alternates.py')

TOKEN = re.compile(r'\b(variants|synonyms)\b')
KEY = re.compile(r'"(variants|synonyms)"(?=\s*:)')
EVERY = r'\b(?:variants|synonyms)\b'

KEEP = [
    # A number's `variants` are the spellings its reading accepts — generator output,
    # nothing the catalog authors.
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/*Numbers.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/Numbers.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/FrenchForms.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/EsperantoForms.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/UkrainianForms.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/SpanishClockForms.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/GermanClock.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/TrainerLanguagePack.kt', EVERY),
    ('kern/src/commonMain/kotlin/net/spross/kern/trainer/PhraseTemplate.kt', EVERY),
    ('kern/src/commonTest/kotlin/net/spross/kern/trainer/SwahiliConcordTests.kt', EVERY),
    ('kern/src/jvmTest/kotlin/net/spross/kern/trainer/PhraseAcceptedFormsTests.kt', EVERY),
    ('kern/src/jvmTest/kotlin/net/spross/kern/trainer/PhraseSlot*Tests.kt', EVERY),
    # `HeadlineKind.variants` counts a headline's phrasings.
    ('kern/src/commonMain/kotlin/net/spross/kern/session/SessionOffer.kt', EVERY),
    ('kern/src/commonTest/kotlin/net/spross/kern/session/SessionOfferTests.kt', EVERY),
    ('android/src/main/kotlin/net/spross/app/ui/HomeStanding.kt', EVERY),
    ('android/src/test/kotlin/net/spross/app/ui/HomeStandingTest.kt', EVERY),
    ('App/Sources/Design/SessionCompletionView.swift', EVERY),
    ('App/Sources/Resources/Localizable.xcstrings', EVERY),
    # A typed answer's prefix strippings, collected under the same English word.
    ('kern/src/commonMain/kotlin/net/spross/kern/session/AnswerNormalizer.kt',
     r'variants = mutableListOf|variants \+= normalized|return variants'),
    # Prose about other things entirely.
    ('CLAUDE.md', r'idiomatic variants'),
    ('docs/date-readings.md', r'hundred-style variants|UkrainianNumbers\.variants'),
    ('docs/backlog.md', r'cross-article synonyms'),
    ('catalog/backlog.md', r'cross-gender synonyms'),
    # History: what a changelog or an archived note said is what it said.
    ('CHANGELOG.md', EVERY),
    ('docs/archive/*', EVERY),
    ('docs/plans/*', EVERY),
]


def tracked_files():
    """Every text file the rename reaches — this script excepted: it has to spell both
    pairs out, and a run over itself would rewrite the rule into its own restatement."""
    listed = subprocess.run(['git', '-C', ROOT, 'ls-files'], check=True,
                            capture_output=True, text=True).stdout.splitlines()
    return [rel for rel in listed if rel.endswith(EXTENSIONS) and rel != SELF]


def kept_spans(rel, text):
    """Where the old words keep their other meaning, and stay."""
    spans = []
    for pattern, sense in KEEP:
        if fnmatch.fnmatch(rel, pattern):
            spans += [m.span() for m in re.finditer(sense, text)]
    return spans


def renamed(rel, text):
    """The file with every field occurrence renamed, and the rest untouched."""
    pattern = KEY if rel.startswith('catalog/') and rel.endswith('.json') else TOKEN
    keep = kept_spans(rel, text)
    out, last = [], 0
    for match in pattern.finditer(text):
        start, end = match.span()
        if any(low <= start and end <= high for low, high in keep):
            continue
        out.append(text[last:start])
        out.append(match.group(0).replace(match.group(1), FIELDS[match.group(1)]))
        last = end
    out.append(text[last:])
    return ''.join(out)


def survivors(rel, text):
    """Line numbers still naming a field the old way, for the report."""
    pattern = KEY if rel.startswith('catalog/') and rel.endswith('.json') else TOKEN
    keep = kept_spans(rel, text)
    lines = []
    for match in pattern.finditer(text):
        start, end = match.span()
        if any(low <= start and end <= high for low, high in keep):
            continue
        lines.append(text.count('\n', 0, start) + 1)
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true', help='name surviving old names, exit 1')
    mode.add_argument('--fix', action='store_true', help='rename them')
    args = parser.parse_args()

    touched, found = [], 0
    for rel in tracked_files():
        path = os.path.join(ROOT, rel)
        with open(path, encoding='utf-8') as f:
            text = f.read()
        if 'variants' not in text and 'synonyms' not in text:
            continue
        hits = survivors(rel, text)
        if not hits:
            continue
        found += len(hits)
        touched.append(rel)
        if args.fix:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(renamed(rel, text))
        else:
            for line in hits:
                print('%s:%d' % (rel, line), file=sys.stderr)

    if args.check:
        if not touched:
            print('accepts/teaches everywhere')
            return
        print('%d old field name(s) in %d file(s) — run scripts/rename-alternates.py --fix'
              % (found, len(touched)), file=sys.stderr)
        sys.exit(1)

    print('\n'.join(touched))
    print('%d occurrence(s) renamed in %d file(s)' % (found, len(touched)))
    if touched:
        # why: a shorter key changes what fits on one line, so the layout owner runs last.
        subprocess.run([os.path.join(ROOT, 'scripts', 'catalog-format.py'), '--fix'], check=True)


if __name__ == '__main__':
    main()

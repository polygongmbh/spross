#!/usr/bin/env python3
"""Keep Localizable.xcstrings honest against the compiler.

How a chrome key must be written, so the catalog stays honest in the first place:
resolution runs through LocalizedStringKey against the environment locale, so a key
keeps its arguments ("heute.session.reviews %@") and is never resolved with
String(localized:), which would read the DEVICE language instead. A %@ argument is
pre-formatted at the call site — `\\(due.formatted())`, never a bare `\\(due)`, which
the extractor writes as %@ while the compiler emits %lld, leaving the project to
rewrite the catalog with dead twins. A key whose wording turns on the number takes a
counted %lld instead, and then owes every plural category (see plural_problems).

Xcode's index-based extractor is weaker than the compiler's: it cannot see a
LocalizedStringKey returned from a computed property, passed to one of our own
LocalizedStringKey parameters, or wrapped in Label/accessibilityLabel. Keys it
misses get flagged `extractionState: "stale"` — cosmetic (they still compile
into every .lproj) but it churns the file on every index.

A plural has to NAME its number: the compiler refuses a variation whose text does not
carry the specifier, so a unit label standing apart from the figure it counts
("🔥 5 · Tage", two Texts for two type sizes) takes a key per form instead.

    scripts/strings.py            # report
    scripts/strings.py --fix      # drop the stale flags and %@ twins, sort, rewrite

Pass --built to also diff the catalog against the keys the compiler actually
emitted, which is the check that catches REAL drift. Needs a build first:

    xcodebuild -project Spross.xcodeproj -scheme Spross \\
      -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \\
      build SWIFT_EMIT_LOC_STRINGS=YES
"""
import glob
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'App/Sources/Resources/Localizable.xcstrings')
LANGUAGES = ('de', 'en')
# CLDR plural categories per chrome language. Both are one/other today; a
# language with more (uk: one/few/many/other) brings its own list, and every
# counted key then owes that language each of them.
CATEGORIES = {'de': ('one', 'other'), 'en': ('one', 'other')}
# Looked up at runtime through DLChrome/DLActionLabel as a plain String
# (a chrome string in the TARGET language cannot go through the environment
# locale), so no extractor can see them.
UNEXTRACTABLE = {
    'heute.session.start',
    'grammar.plural.equals', 'grammar.plural.only', 'grammar.plural %@',
    'grammar.also %@', 'session.answer.placeholder %@', 'session.copy.placeholder %@',
    # A drill tile's a11y label interpolates the glyph into a plain String —
    # no extractor follows an accessibilityLabel built at runtime either way.
    'a11y.letterChoice %@',
    'lang.de', 'lang.en', 'lang.es', 'lang.sw', 'lang.uk',
    # The Box's own-words area title, resolved through DLChrome like the above.
    'box.ownWords',
}
# Whole families built at runtime — a stem plus the variant kern picked this round
# (`AppModel+Queries.headlineKey`, `SessionCompletionView.growthKey`). The words are
# ours, the choice is not, so no call site ever spells the key out.
COMPOSED = (
    'heute.session.reviews.', 'heute.session.warmUp.', 'heute.session.freshSet.',
    'session.finished.growth.',
)


def compiler_keys():
    """Keys from the per-file .stringsdata the Swift compiler emitted."""
    pattern = os.path.expanduser(
        '~/Library/Developer/Xcode/DerivedData/Spross-*/Build/Intermediates.noindex'
        '/Spross.build/*-iphonesimulator/Spross.build/Objects-normal/*/*.stringsdata')
    keys = set()
    for path in glob.glob(pattern):
        raw = subprocess.run(['plutil', '-convert', 'json', '-o', '-', path],
                             capture_output=True).stdout
        for table, entries in json.loads(raw).get('tables', {}).items():
            if table == 'Localizable':
                keys |= {e['key'] for e in entries}
    return keys


def plural_problems(key, lang, localization):
    """A counted key (`… %lld`) owes every category its language uses, and a
    plain one must not carry variations — a `%@` argument is a formatted
    string at runtime, so nothing could select a category from it."""
    variations = localization.get('variations', {}).get('plural')
    if '%lld' not in key:
        return ['%s (%s): plural variations on a key with no counted argument'
                % (key, lang)] if variations else []
    if not variations:
        return ['%s (%s): counted key without plural variations' % (key, lang)]
    missing = sorted(set(CATEGORIES[lang]) - set(variations))
    return ['%s (%s): plural is missing "%s"' % (key, lang, '", "'.join(missing))] if missing else []


def main():
    fix = '--fix' in sys.argv
    catalog = json.load(open(CATALOG))
    strings = catalog['strings']
    problems = []

    stale = sorted(k for k, v in strings.items() if v.get('extractionState') == 'stale')
    for key in stale:
        del strings[key]['extractionState']

    # why: the index extractor writes %@ for every argument, so each counted key
    # comes back as a %@ twin the moment the project is opened. The twin is dead
    # weight — the compiler emits %lld — and taking it out is part of --fix.
    twins = sorted(k for k in strings if k.replace('%@', '%lld') in strings and '%@' in k)
    for key in twins:
        if fix:
            del strings[key]
        else:
            problems.append('%s: dead %%@ twin of a counted key' % key)

    for key, entry in sorted(strings.items()):
        got = entry.get('localizations', {})
        missing = [lang for lang in LANGUAGES if lang not in got]
        if missing:
            problems.append('%s: no %s translation' % (key, '/'.join(missing)))
        for lang in sorted(set(LANGUAGES) & set(got)):
            problems += plural_problems(key, lang, got[lang])

    if '--built' in sys.argv:
        emitted = compiler_keys()
        if not emitted:
            # A plain `xcodebuild build` deletes them again — the flag is required.
            problems.append('no .stringsdata found — build with SWIFT_EMIT_LOC_STRINGS=YES')
        else:
            emitted |= UNEXTRACTABLE
            for key in sorted(emitted - set(strings)):
                problems.append('%s: in the code, missing from the catalog' % key)
            for key in sorted(set(strings) - emitted):
                if key.startswith(COMPOSED):
                    continue
                problems.append('%s: in the catalog, no longer in the code' % key)

    if stale:
        print('%d key(s) Xcode flagged stale%s' % (len(stale), ' — cleared' if fix else ''))
    if fix:
        catalog['strings'] = dict(sorted(strings.items()))
        with open(CATALOG, 'w') as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2, separators=(',', ' : '))
            f.write('\n')
    for problem in problems:
        print(problem)
    print('%d keys%s' % (len(strings), '' if problems else ' — clean'))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())

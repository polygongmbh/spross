#!/usr/bin/env python3
"""Keep Localizable.xcstrings honest against the compiler.

The catalog is the product's ONE home for chrome copy: the Android tables
(ChromeDe.kt/ChromeEn.kt) are generated from it by scripts/chrome.py, so a wording
change is made here and nowhere else.

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

    scripts/strings.py            # report, this file and the Android tables
    scripts/strings.py --fix      # drop the stale flags and %@ twins, sort, rewrite,
                                  # then regenerate the Android tables

This is the ONE command a copy edit owes: it ends by running scripts/chrome.py over
the file it just wrote, so the tables can never be left behind. chrome.py stays a
script of its own — it is the whole of the Android side's business with this catalog,
needs no Xcode, and runs where Xcode cannot.

Edit the catalog however you like — Xcode, an editor, a script — then run --fix,
which restores Xcode's formatting without touching your values. Python's json
defaults omit the space Xcode puts before every colon, so a script that writes the
file and stops leaves a one-value edit inside a 4500-line diff. The report says so,
and scripts/hooks/pre-commit refuses the commit.

Pass --built to also diff the catalog against the keys the compiler actually
emitted, which is the check that catches REAL drift. Needs a build first:

    xcodebuild -project Spross.xcodeproj -scheme Spross \\
      -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \\
      build SWIFT_EMIT_LOC_STRINGS=YES
"""
import glob
import importlib.util
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
    # The two words that fill the address slot of a target-language greeting.
    'heute.greeting.morning.addressee', 'heute.greeting.night.addressee',
}
# Surfaces only the Android app has. The catalog holds every chrome string the product
# says — scripts/chrome.py generates the Kotlin tables from it — so these live here with
# the rest and no Swift will ever ask for them.
ANDROID_ONLY = {
    'a11y.collapsed', 'a11y.expanded', 'a11y.wrong',
    'audio.enable', 'audio.off',
    'box.consolidated', 'box.due', 'box.fresh', 'box.new',
    'session.typoNote', 'settings.about', 'trainer.promptInLanguage %@',
    # The Android tile's no-snapshot face; the iOS widget target's own strings
    # are not extracted into the app's tables, so no Swift will ever ask for these.
    'widget.awaiting.body', 'widget.awaiting.title',
    # The footer's update door — iOS ships through TestFlight and needs no pointer.
    'settings.update.button', 'settings.update.download', 'settings.update.obtainium',
    'settings.update.offer', 'settings.update.title',
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


def serialize(catalog):
    """The catalog byte-for-byte as Xcode writes it: keys sorted, and a space
    before every colon, which Python's default separator omits.

    Nothing edits values through here — this only settles how the file is spelled,
    which is why --fix is something you run after an edit rather than instead of one.
    """
    ordered = dict(catalog, strings=dict(sorted(catalog['strings'].items())))
    return json.dumps(ordered, ensure_ascii=False, indent=2, separators=(',', ' : ')) + '\n'


def check_format(path):
    """Formatting alone, for the pre-commit hook: the other checks answer to a
    build and to keys in flight, and neither should stand between anyone and a
    commit. Exit code is the whole answer.
    """
    on_disk = open(path).read()
    if on_disk == serialize(json.loads(on_disk)):
        return 0
    print('%s: not written by scripts/strings.py — run `scripts/strings.py --fix`, '
          'which restores Xcode\'s formatting and leaves your values alone' % path,
          file=sys.stderr)
    return 1


def chrome():
    """scripts/chrome.py, loaded by path — a hyphenated sibling cannot be imported.

    Lazily, so --check-format stays what the pre-commit hook needs it to be: a read of
    one file it was handed, with nothing else on disk consulted.
    """
    spec = importlib.util.spec_from_file_location(
        'chrome', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'chrome.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    if '--check-format' in sys.argv:
        args = [a for a in sys.argv[1:] if not a.startswith('--')]
        return check_format(args[0] if args else CATALOG)

    fix = '--fix' in sys.argv
    on_disk = open(CATALOG).read()
    catalog = json.loads(on_disk)
    strings = catalog['strings']
    problems = []

    # Caught before any edit below mutates the parse: a catalog someone wrote with
    # a plain json.dump still holds the right strings, and only the diff shows it.
    if on_disk != serialize(json.loads(on_disk)):
        problems.append('formatting is not Xcode\'s — run --fix before committing')

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
            emitted |= UNEXTRACTABLE | ANDROID_ONLY
            for key in sorted(emitted - set(strings)):
                problems.append('%s: in the code, missing from the catalog' % key)
            for key in sorted(set(strings) - emitted):
                if key.startswith(COMPOSED):
                    continue
                problems.append('%s: in the catalog, no longer in the code' % key)

    if stale:
        print('%d key(s) Xcode flagged stale%s' % (len(stale), ' — cleared' if fix else ''))
    if fix:
        with open(CATALOG, 'w') as f:
            f.write(serialize(catalog))
    for problem in problems:
        print(problem)
    print('%d keys%s' % (len(strings), '' if problems else ' — clean'))

    # why: the Android tables are generated from this file, so an edit that stops here
    # ships the two phones saying different things. Chained AFTER the write, and after
    # the %@ twins are gone, so chrome.py reads the catalog this run leaves behind.
    # It reads the same argv, so --fix rewrites the tables and a report reports them.
    return max(1 if problems else 0, chrome().main())


if __name__ == '__main__':
    sys.exit(main())

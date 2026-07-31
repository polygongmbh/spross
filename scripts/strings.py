#!/usr/bin/env python3
"""Keep Localizable.xcstrings honest against the compiler.

Xcode's index-based extractor is weaker than the compiler's: it cannot see a
LocalizedStringKey returned from a computed property, passed to one of our own
LocalizedStringKey parameters, or wrapped in Label/accessibilityLabel. Keys it
misses get flagged `extractionState: "stale"` — cosmetic (they still compile
into every .lproj) but it churns the file on every index.

    scripts/strings.py            # report
    scripts/strings.py --fix      # drop the stale flags, sort, rewrite

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
# Looked up at runtime through DLChrome/DLActionLabel as a plain String
# (a chrome string in the TARGET language cannot go through the environment
# locale), so no extractor can see them.
UNEXTRACTABLE = {
    'heute.session.start',
    'grammar.plural.equals', 'grammar.plural.only', 'grammar.plural %@',
    'grammar.also %@', 'session.answer.placeholder %@',
    'lang.de', 'lang.en', 'lang.sw', 'lang.uk',
}


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


def main():
    fix = '--fix' in sys.argv
    catalog = json.load(open(CATALOG))
    strings = catalog['strings']
    problems = []

    stale = sorted(k for k, v in strings.items() if v.get('extractionState') == 'stale')
    for key in stale:
        del strings[key]['extractionState']

    for key, entry in sorted(strings.items()):
        got = entry.get('localizations', {})
        missing = [lang for lang in LANGUAGES if lang not in got]
        if missing:
            problems.append('%s: no %s translation' % (key, '/'.join(missing)))

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

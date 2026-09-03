#!/usr/bin/env python3
"""What `catalog/audio/` covers, and what it names but does not ship.

    scripts/audio-coverage.py               # per-language coverage table
    scripts/audio-coverage.py --missing de  # the slugs de realizes and cannot say
    scripts/audio-coverage.py --credits     # the docs/audio-licensing.md table rows
    scripts/audio-coverage.py --check       # exit 1 if a manifest names an untracked file

`--check` is the one a gate wants. `CatalogAudioLintTest.everyAudioFileShipsAndIsReferenced`
walks the WORKING TREE, so a recording that was fetched but never `git add`ed looks exactly
like one that ships — the manifests once named 517 files that existed on one machine and in
no checkout, and nothing caught it. Reading `git ls-files` is the only way to tell.

Coverage is measured against SINGLE-WORD realizations as well as all of them: the word packs
resolve one word at a time, so a phrase is not a gap they could ever close.
"""
import argparse
import collections
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')
SECTIONS = ('words', 'letters', 'texts', 'articles', 'calendar', 'countries')


def read_json(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return json.load(f)


def realizations():
    """{lang: {slug: text}} over every area."""
    areas = [area['area'] for group in read_json(CATALOG, 'areas.json') for area in group['areas']]
    out = {}
    for area in areas:
        for name in sorted(os.listdir(os.path.join(CATALOG, 'areas', area))):
            lang, extension = os.path.splitext(name)
            if extension != '.json' or lang == 'concepts':
                continue
            for slug, word in read_json(CATALOG, 'areas', area, name).get('words', {}).items():
                out.setdefault(lang, {})[slug] = word['text']
    return out


def manifests():
    audio = os.path.join(CATALOG, 'audio')
    return {lang: read_json(audio, lang, 'manifest.json')
            for lang in sorted(os.listdir(audio))
            if os.path.isfile(os.path.join(audio, lang, 'manifest.json'))}


def coverage(realized, shipped):
    print('lang  words  realized  covered   single-word  covered   calendar  countries')
    for lang, manifest in shipped.items():
        recorded = set(manifest.get('words', {}))
        mine = realized.get(lang, {})
        single = {slug for slug, text in mine.items() if ' ' not in text}
        hit = [slug for slug in mine if slug in recorded]
        hit_single = [slug for slug in single if slug in recorded]
        print('%-4s  %5d  %8d   %5.1f%%  %11d   %5.1f%%  %9d  %9d' % (
            lang, len(recorded), len(mine), 100 * len(hit) / max(1, len(mine)),
            len(single), 100 * len(hit_single) / max(1, len(single)),
            len(manifest.get('calendar', {})), len(manifest.get('countries', {}))))


def missing(realized, shipped, langs):
    for lang in langs or sorted(shipped):
        recorded = set(shipped[lang].get('words', {}))
        gap = sorted(slug for slug, text in realized.get(lang, {}).items()
                     if slug not in recorded and ' ' not in text)
        print('%s: %d single-word realizations with no recording' % (lang, len(gap)))
        for slug in gap:
            print('  %-28s %s' % (slug, realized[lang][slug]))


def credits(shipped):
    """The `docs/audio-licensing.md` rows, so the table is derived rather than retyped."""
    for lang, manifest in shipped.items():
        authors = manifest['authors']
        for section in SECTIONS:
            rows = manifest.get(section, {})
            if not rows:
                continue
            licenses = collections.Counter(
                entry.get('license') or authors[entry['author']] for entry in rows.values())
            who = collections.Counter(entry['author'] for entry in rows.values())
            where = 'audio/%s/' % lang if section == 'words' else 'audio/%s/%s/' % (lang, section)
            print('| `%s` | %d | %s | %s |' % (
                where, len(rows),
                ' · '.join('%s %d' % (name, n) for name, n in licenses.most_common()),
                ', '.join('%s %d' % (name, n) for name, n in who.most_common(2))))


def check(shipped):
    """Every file a manifest names is tracked in git, not merely present on disk."""
    tracked = set(subprocess.run(['git', '-C', ROOT, 'ls-files', 'catalog/audio'],
                                 capture_output=True, text=True, check=True).stdout.split('\n'))
    named, untracked = 0, []
    for lang, manifest in shipped.items():
        for section in SECTIONS:
            for entry in manifest.get(section, {}).values():
                named += 1
                path = 'catalog/audio/%s/%s' % (lang, entry['file'])
                if path not in tracked:
                    untracked.append(path)
    print('%d manifest entries, %d named but untracked' % (named, len(untracked)))
    for path in untracked[:20]:
        print('  ', path)
    return 1 if untracked else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--missing', nargs='*', metavar='LANG',
                        help='list the slugs a language realizes and cannot say')
    parser.add_argument('--credits', action='store_true',
                        help='emit the licensing table rows')
    parser.add_argument('--check', action='store_true',
                        help='exit 1 if a manifest names a file git does not track')
    args = parser.parse_args()

    shipped = manifests()
    if args.check:
        return check(shipped)
    if args.credits:
        return credits(shipped)
    realized = realizations()
    if args.missing is not None:
        return missing(realized, shipped, args.missing)
    coverage(realized, shipped)


if __name__ == '__main__':
    sys.exit(main() or 0)

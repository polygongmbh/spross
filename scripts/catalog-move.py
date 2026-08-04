#!/usr/bin/env python3
"""Move concepts between catalog areas — concept AND every language's realization.

    scripts/catalog-move.py moves.tsv [--dry-run]
    scripts/catalog-move.py moves.json --create --group home --emoji 🧺 --title de=Die Waschküche

A move is five-plus files per concept (`concepts.json` + one `<lang>.json` per declared
language), which is exactly the mechanical edit that must not be done by hand. The
mapping file is `slug -> destination area`, TSV (`slug<TAB>area`, `#` comments) or JSON
(`{"slug": "area"}` or `[["slug", "area"], …]`); its ORDER is the order the concepts are
appended in the destination.

What is preserved: the realization is carried VERBATIM (grammar, synonyms, variants,
notes — the parsed value is re-emitted, never rebuilt), the source keeps the order of
what stays, and the destination gets words before phrases
(`CatalogLintTest.wordsPrecedePhrasesWithinEachArea`).

What is REFUSED, rather than written (each a non-zero exit naming the concepts):
a phrase parted from a `components` word, a `feminineOf` pair split, an unknown slug, a
destination that already claims the slug, a prompt form that would collide inside the
destination, and a destination area that does not exist without `--create`.

Formatting fidelity is a GATE, not a promise: every area file the run does not touch is
re-serialized and compared to its bytes on disk, so an empty mapping is a proven no-op
and a drifted serializer stops the run instead of reformatting the catalog.
"""
import argparse
import json
import os
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')


def read_text(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return f.read()


def dumps(data):
    """The catalog's own formatting: 1-space indent, authored key order, unescaped UTF-8."""
    return json.dumps(data, ensure_ascii=False, indent=1) + '\n'


def refuse(*lines):
    for line in lines:
        print('refused: %s' % line, file=sys.stderr)
    sys.exit(1)


def load_mapping(path):
    """[(slug, destination area)] in the file's own order."""
    text = read_text(path)
    if path.endswith('.json'):
        data = json.loads(text)
        pairs = list(data.items()) if isinstance(data, dict) else \
            [(e['slug'], e['area']) if isinstance(e, dict) else tuple(e) for e in data]
    else:
        pairs = []
        for line in text.splitlines():
            line = line.split('#', 1)[0].strip()
            if line:
                fields = line.split('\t') if '\t' in line else line.split()
                if len(fields) != 2:
                    refuse('%s: not "slug<TAB>area": %s' % (path, line))
                pairs.append((fields[0], fields[1]))
    seen = [slug for slug, _ in pairs]
    repeated = sorted({slug for slug in seen if seen.count(slug) > 1})
    if repeated:
        refuse('%s: named twice — %s' % (path, ', '.join(repeated)))
    return pairs


class Area:
    """One area folder as authored: the concept list plus a file per language.

    An area with no folder yet is born blank here (`--create`) and is written by the same
    path as an edited one, so a created area is never a second serializer.
    """

    def __init__(self, name, langs, titles=None):
        self.name = name
        self.new = not os.path.isdir(os.path.join(CATALOG, name))
        self.touched = False
        if self.new:
            self.concepts = []
            self.files = {lang: {'title': (titles or {}).get(lang, 'TODO %s' % name), 'words': {}}
                          for lang in langs}
            return
        self.concepts = json.loads(read_text(CATALOG, name, 'concepts.json'))
        self.files = {lang: json.loads(read_text(CATALOG, name, '%s.json' % lang))
                      for lang in langs if os.path.isfile(os.path.join(CATALOG, name, '%s.json' % lang))}

    def kinds(self):
        return {c['slug']: c['kind'] for c in self.concepts}

    def take(self, slug):
        """Removes the concept and every language's realization, returning both."""
        concept = next(c for c in self.concepts if c['slug'] == slug)
        self.concepts = [c for c in self.concepts if c['slug'] != slug]
        words = {lang: file['words'].pop(slug) for lang, file in self.files.items()
                 if slug in file['words']}
        self.touched = True
        return concept, words

    def put(self, arrivals):
        """Appends [(concept, {lang: realization})] — words before the phrase block."""
        kinds = self.kinds()
        words = [a for a in arrivals if a[0]['kind'] != 'phrase']
        phrases = [a for a in arrivals if a[0]['kind'] == 'phrase']
        cut = next((i for i, c in enumerate(self.concepts) if c['kind'] == 'phrase'),
                   len(self.concepts))
        self.concepts = (self.concepts[:cut] + [c for c, _ in words]
                         + self.concepts[cut:] + [c for c, _ in phrases])
        for lang, file in self.files.items():
            keys = list(file['words'])
            cut = next((i for i, k in enumerate(keys) if kinds.get(k) == 'phrase'), len(keys))
            rebuilt = {k: file['words'][k] for k in keys[:cut]}
            for concept, realizations in words:
                if lang in realizations:
                    rebuilt[concept['slug']] = realizations[lang]
            rebuilt.update({k: file['words'][k] for k in keys[cut:]})
            for concept, realizations in phrases:
                if lang in realizations:
                    rebuilt[concept['slug']] = realizations[lang]
            file['words'] = rebuilt
        self.touched = True

    def serialized(self):
        """{relative path: text} for every file this area owns."""
        out = {os.path.join(self.name, 'concepts.json'): dumps(self.concepts)}
        for lang, file in self.files.items():
            out[os.path.join(self.name, '%s.json' % lang)] = dumps(file)
        return out


def prompt_forms(realization):
    """What a card may SHOW — text plus rotating synonyms, NFC-folded, as the lint reads them."""
    return [unicodedata.normalize('NFC', form).strip()
            for form in [realization['text']] + realization.get('synonyms', [])]


def check_collisions(areas, moves):
    """A display-identical prompt inside ONE area is unfixable at runtime — the area IS the cue."""
    clashes = []
    for slug, dst in moves:
        area = areas[dst]
        for lang, file in area.files.items():
            if slug not in file['words']:
                continue
            arriving = set(prompt_forms(file['words'][slug]))
            for other, realization in file['words'].items():
                if other != slug and arriving & set(prompt_forms(realization)):
                    clashes.append('%s: %s and %s both prompt "%s" in %s'
                                   % (dst, slug, other, sorted(arriving & set(prompt_forms(realization)))[0], lang))
    if clashes:
        refuse('the destination would prompt one form for two concepts:', *clashes)


def check_references(areas):
    """`components` and `feminineOf` resolve INSIDE one area — a split pair is a parse error."""
    home = {c['slug']: area.name for area in areas.values() for c in area.concepts}
    broken = []
    for area in areas.values():
        for concept in area.concepts:
            for component in concept.get('components', []):
                if home.get(component) != area.name:
                    broken.append('%s (%s) needs component %s, now in %s'
                                  % (concept['slug'], area.name, component, home.get(component, '?')))
            base = concept.get('feminineOf')
            if base is not None and home.get(base) != area.name:
                broken.append('%s (%s) is feminineOf %s, now in %s'
                              % (concept['slug'], area.name, base, home.get(base, '?')))
    if broken:
        refuse('the move would separate concepts that must share an area:', *broken,
               'move the whole group in one mapping, or leave it where it is.')


def declare_area(name, manifest, langs, group, emoji, titles):
    """The new area's `areas.json` row — the manifest is what makes a folder an area.

    An unlisted folder is the one catalog mistake the parser cannot catch
    (`CatalogLintTest.everyAreaFolderIsRegisteredInTheManifest`), so the row is written
    with the files, never after.
    """
    if os.path.isdir(os.path.join(CATALOG, name)):
        refuse('--create %s: the folder already exists' % name)
    row = next((g for g in manifest if g['group'] == group), None)
    if row is None:
        refuse('--create %s: unknown group "%s" — declared: %s'
               % (name, group, ', '.join(g['group'] for g in manifest)))
    if not emoji or len(emoji) > 12 or any(ord(ch) < 0x2000 for ch in emoji):
        refuse('--create %s: --emoji must be one well-formed emoji, got "%s"' % (name, emoji))
    row['areas'].append({'area': name, 'emoji': emoji})
    missing = [lang for lang in langs if lang not in titles]
    print('creating %s (group %s, %s)' % (name, group, emoji))
    if missing:
        print('  FILL IN the placeholder title in: %s'
              % ', '.join('%s/%s.json' % (name, lang) for lang in missing))
    return dumps(manifest)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('mapping', help='slug -> area, as .tsv (slug<TAB>area) or .json')
    parser.add_argument('--dry-run', action='store_true', help='print the plan, write nothing')
    parser.add_argument('--create', action='store_true',
                        help='create the destination area (exactly one new area per run)')
    parser.add_argument('--group', help='--create: the areas.json group to append it to')
    parser.add_argument('--emoji', help='--create: the area icon')
    parser.add_argument('--title', action='append', default=[], metavar='LANG=TITLE',
                        help='--create: a language title (repeatable; the rest get a placeholder)')
    args = parser.parse_args()

    langs = sorted(json.loads(read_text(CATALOG, 'languages.json')))
    manifest = json.loads(read_text(CATALOG, 'areas.json'))
    names = [area['area'] for group in manifest for area in group['areas']]
    moves = load_mapping(args.mapping)

    written = {}
    titles = dict(pair.split('=', 1) for pair in args.title)
    unknown = sorted({dst for _, dst in moves if dst not in names})
    if unknown and not args.create:
        refuse('unknown destination area: %s — pass --create to make it' % ', '.join(unknown))
    if args.create:
        if len(unknown) != 1:
            refuse('--create makes exactly one area per run; the mapping names %d new ones%s'
                   % (len(unknown), ': ' + ', '.join(unknown) if unknown else ''))
        if set(titles) - set(langs):
            refuse('--title names undeclared languages: %s' % ', '.join(sorted(set(titles) - set(langs))))
        written['areas.json'] = declare_area(unknown[0], manifest, langs, args.group,
                                             args.emoji, titles)
        names = names + unknown

    areas = {name: Area(name, langs, titles) for name in names}
    home = {c['slug']: area.name for area in areas.values() for c in area.concepts}

    missing = [slug for slug, _ in moves if slug not in home]
    if missing:
        refuse('no such concept: %s' % ', '.join(missing))
    settled = [(slug, dst) for slug, dst in moves if home[slug] == dst]
    if settled:
        refuse('the destination already claims: %s'
               % ', '.join('%s (already in %s)' % (slug, dst) for slug, dst in settled))
    for slug, dst in moves:
        if any(lang not in areas[dst].files and slug in areas[home[slug]].files[lang]['words']
               for lang in areas[home[slug]].files):
            refuse('%s: %s has no file for a language that realizes it' % (slug, dst))

    arrivals = {}
    for slug, dst in moves:
        arrivals.setdefault(dst, []).append(areas[home[slug]].take(slug))
    for dst, incoming in arrivals.items():
        areas[dst].put(incoming)
    check_references(areas)
    check_collisions(areas, moves)
    report(areas, written, moves, args)


def report(areas, written, moves, args):
    """Serializes everything, gates the untouched files on byte-identity, then writes."""
    for area in areas.values():
        for path, text in area.serialized().items():
            if area.new:
                written[path] = text
            elif text != read_text(CATALOG, path):
                if not area.touched:
                    refuse('%s: re-serializing an UNTOUCHED file changes it — the writer no '
                           'longer matches the catalog format; fix that before moving anything'
                           % path)
                written[path] = text
        if area.touched and not area.concepts:
            print('warning: %s is now empty — drop the folder and its areas.json row' % area.name)
    for path, text in sorted(written.items()):
        if args.dry_run:
            print('would write %s' % path)
        else:
            os.makedirs(os.path.dirname(os.path.join(CATALOG, path)), exist_ok=True)
            with open(os.path.join(CATALOG, path), 'w', encoding='utf-8') as f:
                f.write(text)
            print('wrote %s' % path)
    print('%d concept(s) moved, %d file(s) %s'
          % (len(moves), len(written), 'to write' if args.dry_run else 'written'))


if __name__ == '__main__':
    main()

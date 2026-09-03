#!/usr/bin/env python3
"""Rename concept slugs — the concept, every realization, every reference, the recordings.

    scripts/catalog-rename-slugs.py renames.tsv --check
    scripts/catalog-rename-slugs.py renames.tsv

The mapping file is `old<TAB>new` per line (`#` comments). A slug is the card id, so one
rename is the concept's row in `concepts.json`, its key in every `<lang>.json`, every
`components` entry, `feminineOf` value and alphabet `example` that names it, and — where a recording exists —
the `audio/<lang>/<slug>.mp3` file plus its `manifest.json` entry, `git mv`'d so history
follows the bytes. That is a dozen-plus files per slug, which is why it is a script.

What is REFUSED, rather than written (non-zero exit naming the slugs): an unknown old
slug, a new slug the catalog already claims, and two renames landing on one slug.

Formatting fidelity is a GATE, as in `catalog-move.py`: every area file the run does not
touch is re-serialized through `catalog-format.py` and compared to its bytes on disk, so
a drifted serializer stops the run instead of reformatting the catalog. Audio manifests
are re-emitted on `audio-catalog.py`'s own contract (2-space indent, sorted keys), which
is what keeps a rename's diff down to the renamed keys.
"""
import argparse
import importlib.util
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')
AREAS = os.path.join(CATALOG, 'areas')
AUDIO = os.path.join(CATALOG, 'audio')


def _load(name, filename):
    """Sibling scripts are hyphenated, so they are loaded by path rather than imported."""
    spec = importlib.util.spec_from_file_location(
        name, os.path.join(os.path.dirname(os.path.abspath(__file__)), filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


catalog_format = _load('catalog_format', 'catalog-format.py')


def read_text(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return f.read()


def refuse(*lines):
    for line in lines:
        print('refused: %s' % line, file=sys.stderr)
    sys.exit(1)


def load_mapping(path):
    """{old: new}, refusing a source or a destination named twice."""
    pairs = []
    for line in read_text(path).splitlines():
        line = line.split('#', 1)[0].strip()
        if line:
            fields = line.split('\t') if '\t' in line else line.split()
            if len(fields) != 2 or fields[0] == fields[1]:
                refuse('%s: not "old<TAB>new": %s' % (path, line))
            pairs.append((fields[0], fields[1]))
    for side, index in (('old', 0), ('new', 1)):
        names = [pair[index] for pair in pairs]
        repeated = sorted({name for name in names if names.count(name) > 1})
        if repeated:
            refuse('%s: %s slug named twice — %s' % (path, side, ', '.join(repeated)))
    return dict(pairs)


def rekeyed(words, mapping):
    """The same dict with renamed keys in place — authored order is content."""
    return {mapping.get(key, key): value for key, value in words.items()}


class Area:
    def __init__(self, name, langs):
        self.name = name
        self.touched = False
        self.concepts = json.loads(read_text(AREAS, name, 'concepts.json'))
        self.files = {lang: json.loads(read_text(AREAS, name, '%s.json' % lang))
                      for lang in langs if os.path.isfile(os.path.join(AREAS, name, '%s.json' % lang))}

    def rename(self, mapping):
        for concept in self.concepts:
            if concept['slug'] in mapping:
                concept['slug'] = mapping[concept['slug']]
                self.touched = True
            if any(c in mapping for c in concept.get('components', [])):
                concept['components'] = [mapping.get(c, c) for c in concept['components']]
                self.touched = True
            if concept.get('feminineOf') in mapping:
                concept['feminineOf'] = mapping[concept['feminineOf']]
                self.touched = True
        for file in self.files.values():
            if any(slug in mapping for slug in file['words']):
                file['words'] = rekeyed(file['words'], mapping)
                self.touched = True

    def serialized(self):
        out = {os.path.join('areas', self.name, 'concepts.json'): catalog_format.formatted(self.concepts)}
        for lang, file in self.files.items():
            out[os.path.join('areas', self.name, '%s.json' % lang)] = catalog_format.formatted(file)
        return out


def alphabet_rewrites(mapping):
    """{relative path: text} for every letter sheet whose `example` names a renamed slug."""
    out = {}
    for lang in sorted(os.listdir(os.path.join(CATALOG, 'alphabet'))):
        if not lang.endswith('.json'):
            continue
        sheet = json.loads(read_text(CATALOG, 'alphabet', lang))
        touched = False
        for entry in sheet.get('entries', []):
            if entry.get('example') in mapping:
                entry['example'] = mapping[entry['example']]
                touched = True
        if touched:
            out[os.path.join('alphabet', lang)] = catalog_format.formatted(sheet)
    return out


def audio_renames(mapping):
    """[(lang, manifest text, [(old file, new file)])] for every language that recorded a renamed slug."""
    out = []
    for lang in sorted(os.listdir(AUDIO)) if os.path.isdir(AUDIO) else []:
        path = os.path.join(AUDIO, lang, 'manifest.json')
        if not os.path.isfile(path):
            continue
        manifest = json.loads(read_text(path))
        moves = []
        for old, new in mapping.items():
            entry = manifest['words'].get(old)
            if entry is None:
                continue
            moves.append((os.path.join('audio', lang, entry['file']),
                          os.path.join('audio', lang, '%s.mp3' % new)))
            entry['file'] = '%s.mp3' % new
            manifest['words'][new] = manifest['words'].pop(old)
        if moves:
            # why: audio-catalog.py's own contract, so the next rebuild does not re-emit us.
            text = json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + '\n'
            out.append((lang, text, moves))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('mapping', help='old -> new, as .tsv (old<TAB>new)')
    parser.add_argument('--check', action='store_true', help='print the plan, write nothing')
    args = parser.parse_args()

    langs = sorted(json.loads(read_text(CATALOG, 'languages.json')))
    names = [area['area'] for group in json.loads(read_text(CATALOG, 'areas.json'))
             for area in group['areas']]
    mapping = load_mapping(args.mapping)
    areas = {name: Area(name, langs) for name in names}
    slugs = {c['slug'] for area in areas.values() for c in area.concepts}

    unknown = sorted(old for old in mapping if old not in slugs)
    if unknown:
        refuse('no such concept: %s' % ', '.join(unknown))
    taken = sorted(new for new in mapping.values() if new in slugs and new not in mapping)
    if taken:
        refuse('the catalog already claims: %s' % ', '.join(taken))

    for area in areas.values():
        area.rename(mapping)
    written = {}
    for area in areas.values():
        for path, text in area.serialized().items():
            if text != read_text(CATALOG, path):
                if not area.touched:
                    refuse('%s: re-serializing an UNTOUCHED file changes it — run '
                           'scripts/catalog-format.py --fix first' % path)
                written[path] = text
    written.update(alphabet_rewrites(mapping))
    moves = []
    for lang, text, files in audio_renames(mapping):
        written[os.path.join('audio', lang, 'manifest.json')] = text
        moves.extend(files)

    for old, new in moves:
        print('%s %s -> %s' % ('would move' if args.check else 'moving', old, new))
        if not args.check:
            subprocess.run(['git', 'mv', old, new], cwd=CATALOG, check=True)
    for path, text in sorted(written.items()):
        print('%s %s' % ('would write' if args.check else 'wrote', path))
        if not args.check:
            with open(os.path.join(CATALOG, path), 'w', encoding='utf-8') as f:
                f.write(text)
    print('%d slug(s) renamed, %d recording(s) moved, %d file(s) %s'
          % (len(mapping), len(moves), len(written), 'to write' if args.check else 'written'))


if __name__ == '__main__':
    main()

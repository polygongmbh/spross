#!/usr/bin/env python3
"""The catalog's canonical JSON layout — one entry per line wherever one fits.

    scripts/catalog-format.py --check     # names every drifted file, exits 1
    scripts/catalog-format.py --fix       # rewrites them

The catalog is hand-edited and read far more often than it is written: an area file is
the contribution and review unit, so a reviewer wants a word LIST, not five lines per
card. The layout that gives them one is "inline what fits, expand what does not", which
is a rule nobody can apply by eye across 7000 entries — hence a formatter, and hence
`--check`, without which the format drifts back one hand-written entry at a time
(`strings.py` guards Xcode's String Catalogs the same way).

The rules, all of them:
- A container is inlined when its one-line form ends by column 100, else its entries go
  one per line at one more space of indent. The outermost container of a file always
  spans lines, so a file always looks like a file.
- Inlined objects carry inner padding (`{ "text": "Name" }`), arrays do not
  (`["fridge"]`), and an empty container is `{}` / `[]`.
- Authored key order is content — a realization reads text-then-grammar-then-notes on
  purpose — so nothing is ever sorted.
- 1-space indent, UTF-8 unescaped, one trailing newline.

`catalog/audio/*/manifest.json` is NOT ours: `audio-catalog.py` generates it on its own
deterministic contract (2-space indent, sorted keys) and would revert us on the next
rebuild. Everything else under `catalog/` is hand-edited and formatted here.

A run is value-preserving by construction — the bytes are re-derived from the parsed
document and `--check` compares text only — and duplicate keys, which parsing would
silently collapse, are refused rather than formatted away.
"""
import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')
WIDTH = 100
GENERATED = os.path.join('catalog', 'audio') + os.sep


def no_duplicate_keys(pairs):
    seen = {}
    for key, value in pairs:
        if key in seen:
            raise ValueError('duplicate key "%s"' % key)
        seen[key] = value
    return seen


def inline(value):
    """The one-line form: objects padded, arrays bare."""
    if isinstance(value, dict) and value:
        return '{ %s }' % ', '.join('%s: %s' % (json.dumps(k, ensure_ascii=False), inline(v))
                                    for k, v in value.items())
    if isinstance(value, list) and value:
        return '[%s]' % ', '.join(inline(v) for v in value)
    return json.dumps(value, ensure_ascii=False)


def render(value, indent):
    if not isinstance(value, (dict, list)) or not value:
        return inline(value)
    flat = inline(value)
    if indent + len(flat) <= WIDTH:
        return flat
    return expand(value, indent)


def expand(value, indent):
    pad = ' ' * indent
    if isinstance(value, dict):
        entries = ['%s %s: %s' % (pad, json.dumps(k, ensure_ascii=False), render(v, indent + 1))
                   for k, v in value.items()]
        return '{\n%s\n%s}' % (',\n'.join(entries), pad)
    entries = ['%s %s' % (pad, render(v, indent + 1)) for v in value]
    return '[\n%s\n%s]' % (',\n'.join(entries), pad)


def formatted(document):
    """The whole file: the root always spans lines, however short it is."""
    if isinstance(document, (dict, list)) and document:
        return expand(document, 0) + '\n'
    return inline(document) + '\n'


def catalog_files():
    """Every hand-edited catalog file, generator output excluded."""
    found = glob.glob(os.path.join(CATALOG, '**', '*.json'), recursive=True)
    return sorted(p for p in found if GENERATED not in os.path.relpath(p, ROOT))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true', help='name drifted files, exit 1')
    mode.add_argument('--fix', action='store_true', help='rewrite drifted files')
    args = parser.parse_args()

    drifted = []
    for path in catalog_files():
        rel = os.path.relpath(path, ROOT)
        with open(path, encoding='utf-8') as f:
            raw = f.read()
        try:
            document = json.loads(raw, object_pairs_hook=no_duplicate_keys)
        except ValueError as error:
            print('%s: %s' % (rel, error), file=sys.stderr)
            sys.exit(1)
        text = formatted(document)
        if text == raw:
            continue
        drifted.append(rel)
        if args.fix:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(text)

    if not drifted:
        print('catalog format clean')
        return
    if args.fix:
        print('\n'.join(drifted))
        print('%d file(s) reformatted' % len(drifted))
        return
    print('\n'.join(drifted), file=sys.stderr)
    print('%d file(s) off the catalog format — run scripts/catalog-format.py --fix'
          % len(drifted), file=sys.stderr)
    sys.exit(1)


if __name__ == '__main__':
    main()

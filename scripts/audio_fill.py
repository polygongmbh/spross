"""Adds what a section of a shipped manifest lacks out of its pack, and leaves what it has alone.

    scripts/audio-catalog.py --fill [--section S] --lang L [--dry-run]

Every section fills alike: a pack row whose form the section does not ship yet passes the
section's own gate, the take judge (`audio_judge.py`) and the editor's `put`, which ships it.
A form the section already ships keeps its file, digest and credit — a different take of it is
a swap, staged and shipped by `audio_ship.py`, never something a fill decides.
The gates per section: a word some card shows (`keep_reachable`), an article some card shows in
front of its word (`keep_article_forms`), a Lingua Libre file named for its form (every section
but letters, whose takes say a letter's name rather than its glyph).
"""
import os

from audio_gates import (UNSHIPPABLE_LICENSES, attribute, digest_of, keep_article_forms,
                         keep_named_by_its_file, keep_reachable, keep_unambiguous, speech_key, takes)
from audio_manifest import audio_dir, put, read_manifest
from audio_ship import pack_mp3, read_rows


def one_per_form(rows):
    """One row per spoken form. [keep_unambiguous] has left only byte-identical twins under
    one speech key — one recording under two spellings — and a form-keyed section holds it once."""
    kept = {}
    for row in sorted(rows, key=lambda row: row['local_file']):
        kept.setdefault(speech_key(row['text']), row)
    return list(kept.values())


def pack_rows(pack, section):
    """A pack's rows, the letters pack's `letter` read as the `text` every other pack keys by."""
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    return [dict(row, text=row['letter']) for row in rows] if section == 'letters' else rows


def fill(packs, lang, section, catalog, dry_run=False):
    """Ships every row of `pack-<lang>-<section>` whose form the section lacks and that passes;
    [catalog] is `audio_forms.load_catalog`'s pair. Prints each drop and a count."""
    pack = os.path.join(packs, 'pack-%s-%s' % (lang, section))
    if not os.path.isdir(pack):
        return
    shown, articled = catalog
    out_dir = audio_dir(lang)
    shipped = read_manifest(out_dir).get(section, {})
    print('pack-%s-%s fill' % (lang, section))
    # why: against what already SHIPS, not just this batch — `keep_unambiguous` sees only the new
    # rows, and a new form claiming a shipped one's sound mutes both. Bytes that already ship
    # under another key (a form restress moved) are that entry, not a new one.
    spoken = {speech_key(form): item['sha256'] for form, item in shipped.items()}
    held = {take['sha256'] for item in shipped.values() for take in takes(item)}
    drops, rows = [], []
    for row in pack_rows(pack, section):
        if not os.path.isfile(pack_mp3(pack, row)):
            continue
        if row.get('license') in UNSHIPPABLE_LICENSES:
            drops.append(('license', row['text'], row['license']))
            continue
        claimed = spoken.get(speech_key(row['text']))
        digest = digest_of(pack_mp3(pack, row))
        if claimed is None and digest not in held:
            rows.append(row)
        elif claimed is not None and claimed != digest:
            drops.append(('shipped-collision', row['text'], '"%s" already ships as another file' % row['text']))
    if section == 'words':
        rows = keep_reachable(rows, lang, shown, drops)
    elif section == 'articles':
        rows = keep_article_forms(rows, lang, articled, drops)
    if section != 'letters':
        rows = keep_named_by_its_file(rows, drops)
    fresh = one_per_form(attribute(keep_unambiguous(rows, os.path.join(pack, 'mp3'), drops), drops))
    outcome = put(lang, [dict(section=section, key=row['text'], mp3=pack_mp3(pack, row), source=row['file'],
                              author=row['author'], license=row['license'],
                              **({'word': row['word']} if section == 'articles' else {}))
                         for row in fresh], dry_run) if fresh else {}
    added = 0
    for (_, key), (why, _) in sorted(outcome.items()):
        if why:
            drops.append((why[0], key, why[1]))
        else:
            added += 1
    for reason, form, detail in sorted(drops):
        print('  drop %-18s %-22s %s' % (reason, form, detail))
    print('  %s %s: %d %s, %d shipped before' % (lang, section, added,
                                                 'would be added' if dry_run else 'added', len(shipped)))

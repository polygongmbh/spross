"""Ships staged swaps: `$W/staged.tsv` rows through the manifest editor, for every section.

    scripts/audio-catalog.py --ship [--lang L] [--dry-run]
    scripts/audio-catalog.py --ship --from-packs [--section S] [--lang L] [--dry-run]

A staged row names the take that ships now (`from_sha256`) and the one to replace it, whose
bytes wait at `$W/staged/<to_sha256>.mp3`. It applies only while its `from_sha256` still ships,
and through `audio_manifest.put`, which judges the take before it replaces anything.
An applied row moves to `$W/shipped.tsv`; a row whose incumbent moved, or whose take the judge
refuses, is named and leaves the journal, so a picker stages it afresh if it still holds.
An empty `from_sha256` is a take for a form that ships nothing, applied while that still holds.
`--from-packs` first stages every pack row naming another Commons file than what ships under
its form — the swaps a sweep or a consolidation wrote into a pack — in any section but `letters`.
"""
import csv
import datetime
import os
import shutil
import sys

from audio_gates import attribute, digest_of, speech_key
from audio_manifest import SECTIONS, AUDIO, audio_dir, put, read_manifest
from audio_routes import WORKSPACE

FIELDS = ('date lang section key from_source from_sha256 to_source to_sha256 author license '
          'mos_from mos_to picker why').split()


def read_rows(path):
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def pack_mp3(pack, row):
    """Where a pack keeps a row's bytes: `mp3/<local_file>`, named by the form it speaks."""
    return os.path.join(pack, 'mp3', row['local_file'])


def write_rows(path, rows, append=False):
    fresh = not append or not os.path.exists(path)
    with open(path, 'a' if append else 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, FIELDS, delimiter='\t', extrasaction='ignore', lineterminator='\n')
        if fresh:
            writer.writeheader()
        writer.writerows(rows)


def same_file(a, b):
    return a.replace('_', ' ') == b.replace('_', ' ')


def from_packs(workspace, langs, sections):
    """A staged row for every shipped entry whose pack names another Commons file, with `mp3` at the pack's bytes."""
    staged = []
    for lang in langs:
        manifest = read_manifest(audio_dir(lang))
        for section in sections:
            pack = os.path.join(workspace, 'pack-%s-%s' % (lang, section))
            if not manifest.get(section) or not os.path.isdir(pack):
                continue
            by_form = {}
            for row in read_rows(os.path.join(pack, 'manifest.tsv')):
                if os.path.isfile(pack_mp3(pack, row)):
                    by_form.setdefault(speech_key(row['text']), []).append(row)
            swaps = []
            for key, item in sorted(manifest[section].items()):
                rows = by_form.get(speech_key(key), [])
                if rows and not any(same_file(row['file'], item['source']) for row in rows):
                    swaps.append(dict(rows[0], key=key, incumbent=item))
            drops = []
            for row in attribute(swaps, drops):
                item, mp3 = row['incumbent'], pack_mp3(pack, row)
                digest = digest_of(mp3)
                if digest != item['sha256']:
                    staged.append(dict(
                        date=datetime.date.today().isoformat(), lang=lang, section=section,
                        key=row['key'], from_source=item['source'], from_sha256=item['sha256'],
                        to_source=row['file'], to_sha256=digest, author=row['author'],
                        license=row['license'], mos_from=item.get('mos', ''), mos_to='',
                        picker='pack', why='pack-%s-%s names another file' % (lang, section), mp3=mp3))
            for reason, key, detail in drops:
                print('  %s %s/%s %s: %s' % (lang, section, key, reason, detail))
    return staged


def article_word(lang, key):
    """The word a card shows behind the article [key] says — what a new `articles` entry records."""
    from audio_forms import load_catalog
    return load_catalog()[1].get(lang, {}).get(speech_key(key))


def ship(workspace, langs, dry_run, packs, section):
    staged_path = os.path.join(workspace, 'staged.tsv')
    journal = read_rows(staged_path) if os.path.exists(staged_path) else []
    langs = langs or sorted(name for name in os.listdir(AUDIO) if os.path.isdir(os.path.join(AUDIO, name)))
    if packs:
        if section == 'letters':
            sys.exit('--from-packs never ships letters: a letter names its glyph, not a form')
        held = {(row['lang'], row['section'], row['key'], row['from_sha256'], row['to_sha256'])
                for row in journal}
        fresh = [row for row in from_packs(workspace, langs, [section] if section else
                                           [name for name in SECTIONS if name != 'letters'])
                 if (row['lang'], row['section'], row['key'], row['from_sha256'], row['to_sha256']) not in held]
        print('  %d staged from the packs' % len(fresh))
        journal += fresh
    due = [row for row in journal if row['lang'] in langs and (not section or row['section'] == section)]
    settled, shipped = [], []
    for lang in sorted({row['lang'] for row in due}):
        manifest = read_manifest(audio_dir(lang))
        latest = {}
        for row in (row for row in due if row['lang'] == lang):
            item = manifest.get(row['section'], {}).get(row['key'])
            # why: an empty `from_sha256` stages a take for a form that ships nothing yet.
            if (item['sha256'] if item else '') != row['from_sha256']:
                print('  moved    %s %s/%s: %s' % (lang, row['section'], row['key'], '%s no longer ships' % row['from_source']
                                                  if row['from_sha256'] else 'ships a take now'))
                settled.append(row)
                continue
            row.setdefault('mp3', os.path.join(workspace, 'staged', '%s.mp3' % row['to_sha256']))
            if not os.path.isfile(row['mp3']) or digest_of(row['mp3']) != row['to_sha256']:
                print('  no bytes %s %s/%s: %s is not %s' % (lang, row['section'], row['key'], row['mp3'], row['to_source']))
                continue
            # why: a key staged twice ships its latest decision, and the earlier leaves with it.
            settled += [latest[(row['section'], row['key'])]] if (row['section'], row['key']) in latest else []
            latest[(row['section'], row['key'])] = row
        rows = list(latest.values())
        outcome = put(lang, [dict(section=row['section'], key=row['key'], mp3=row['mp3'],
                                  source=row['to_source'], author=row['author'], license=row['license'],
                                  **({'word': article_word(lang, row['key'])} if row['section'] == 'articles'
                                     and not row['from_sha256'] else {}))
                             for row in rows], dry_run)
        for row in rows:
            why, mos = outcome[(row['section'], row['key'])]
            row['mos_to'] = '' if mos is None else '%.2f' % mos
            settled.append(row)
            if why:
                print('  refused  %s %s/%s %s: %s' % (lang, row['section'], row['key'], *why))
            else:
                shipped.append(row)
                print('  swap     %s %s/%s %s -> %s (mos %s -> %s)' % (
                    lang, row['section'], row['key'], row['from_source'], row['to_source'],
                    row['mos_from'] or '-', row['mos_to'] or '-'))
    print('  %d %s, %d left staged' % (len(shipped), 'would ship' if dry_run else 'shipped',
                                       len(journal) - len(settled)))
    if dry_run:
        return
    write_rows(os.path.join(workspace, 'shipped.tsv'), shipped, append=True)
    for row in journal:
        target = os.path.join(workspace, 'staged', '%s.mp3' % row['to_sha256'])
        if row.get('mp3', target) != target and not os.path.exists(target):
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(row['mp3'], target)
    done = {id(row) for row in settled}
    left = [row for row in journal if id(row) not in done]
    if left or os.path.exists(staged_path):
        write_rows(staged_path, left)

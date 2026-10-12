#!/usr/bin/env python3
"""The shipped recordings under catalog/audio/<lang>/, out of the pronunciation packs.

    scripts/audio-catalog.py --fill [--section S] --lang L [--dry-run]   # what a section lacks
    scripts/audio-catalog.py --ship [--from-packs] [--lang L] [--dry-run] # staged swaps
    scripts/audio-catalog.py --packs <workspace> --lang L                 # a language with no manifest yet

The packs (`pack-<lang>-<section>/manifest.tsv` + `mp3/`, keyed by the form each row speaks, the letters
by their glyph) are unversioned research input; `catalog/audio/` is the versioned provenance record.
What ships is the Wikimedia Commons transcode UNTOUCHED — re-encoding is an adaptation under BY-SA —
so every entry carries the sha256 verified after the copy, and lint re-hashes what was committed.

Every take that ships passes its section's gates (`audio_fill.py`) and the take judge
(`audio_judge.py`), and goes in through the manifest editor (`audio_manifest.py`), which measures
the bytes that landed into the playback index (`audio_index.py`).
`--articles`, `--calendar` and `--countries` are a section's ship from its pack, then its fill.
Deterministic: sorted keys, 2-space indent, unchanged packs give byte-identical output.
"""
import argparse
import concurrent.futures
import os
import sys

import audio_judge
import audio_manifest
import audio_measure
import audio_ship
from audio_fill import fill
from audio_forms import load_catalog
from audio_index import ANALYSIS, FFMPEG, INDEX_FIELDS, playback_index
from audio_gates import digest_of
from audio_manifest import AUDIO, SECTIONS, audio_dir, read_manifest, write_manifest

# The order a language with no manifest yet is filled in: words first, so a collision across
# sections is decided the way it always was.
FILL_ORDER = ('words', 'letters', 'texts', 'articles', 'calendar', 'countries')


def reindex(lang):
    """Re-derive `gain`/`cap`/`lead`/`gate`/`mos` for a language already under `catalog/audio/`,
    out of the bytes it ships — nothing is copied, converted or renamed.

    why a second entry point at all: the packs are unversioned research input and may be
    long gone from the machine that needs to re-measure, while the mp3 the index describes
    is right here and pinned. Every file's `sha256` is re-verified first, so a re-index can
    never quietly re-describe changed bytes — which is also the whole claim the credits make.
    """
    out_dir = audio_dir(lang)
    manifest = read_manifest(out_dir)
    entries = {(section, key): item
               for section in SECTIONS if section in manifest
               for key, item in manifest[section].items()}
    measured = audio_measure.measure_all(
        FFMPEG, sorted({os.path.join(out_dir, item['file']) for item in entries.values()}))
    moved, limited = [], 0
    for (section, key), item in sorted(entries.items()):
        path = os.path.join(out_dir, item['file'])
        if digest_of(path) != item['sha256']:
            sys.exit('%s: sha256 no longer matches — the bytes changed, re-run the convert'
                     % path)
        if measured[path][0] is None or measured[path][2] is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % path)
        index = playback_index(*measured[path])
        was = item.get('gain', 0)
        for field in ('gain', 'cap', 'gainPhone', 'capPhone', 'lead', 'snr', 'mos', 'gate'):
            item.pop(field, None)
        item.update(index)
        if index.get('gain', 0) != was:
            moved.append(index.get('gain', 0) - was)
        if index.get('cap'):
            limited += 1
    write_manifest(out_dir, manifest)
    moved.sort()
    print('  %s: %d entries, %d re-gained (median %+.1f dB, widest %+.1f), %d held by the '
          'peak ceiling' % (lang, len(entries), len(moved),
                            moved[len(moved) // 2] if moved else 0,
                            max(moved, key=abs) if moved else 0, limited))


def shipped_take(lang, section, key, item, out_dir):
    """A shipped entry as the judge reads a take, its bytes where they ship."""
    return dict(lang=lang, section=section, key=key, source=item['source'], author=item['author'],
                license=item['license'], sha256=item['sha256'], path=os.path.join(out_dir, item['file']))


def prune_poor(languages):
    """Remove every shipped recording the take judge refuses (`audio_judge.py`) — under its
    language's `mos_floor`, heard as bad, one-sided — file and entry, so the device voice or
    silence takes over. Run `audio-packs.py sweep` first, so such a word gets a better take where
    one exists."""
    for lang in languages or sorted(os.listdir(AUDIO)):
        out_dir = audio_dir(lang)
        if not os.path.isdir(out_dir):
            continue
        manifest = read_manifest(out_dir)
        entries = [(section, key, item) for section in SECTIONS
                   for key, item in manifest.get(section, {}).items()]
        # why: the shipped index stands for the bytes its sha256 pins, so only what it does
        # not carry (the side balance, the squash) is measured, a few ffmpeg passes per file.
        with concurrent.futures.ThreadPoolExecutor(max_workers=audio_measure.WORKERS) as pool:
            judged = list(pool.map(lambda e: audio_judge.judge(
                shipped_take(lang, e[0], e[1], e[2], out_dir),
                {field: e[2][field] for field in INDEX_FIELDS if field in e[2]}), entries))
        pruned = []
        for (section, key, item), verdict in zip(entries, judged):
            if verdict.refused:
                os.remove(os.path.join(out_dir, item['file']))
                del manifest[section][key]
                pruned.append('%s/%s %s: %s' % (section, key, verdict.refused[0], verdict.refused[1]))
        print('  %s: %d pruned%s' % (lang, len(pruned), ''.join('\n    ' + p for p in pruned)))
        if pruned:
            write_manifest(out_dir, manifest)


def languages_with(packs, section):
    """Every language with a manifest and a `pack-<lang>-<section>`."""
    return sorted(lang for lang in os.listdir(AUDIO)
                  if os.path.isfile(os.path.join(AUDIO, lang, 'manifest.json'))
                  and os.path.isdir(os.path.join(packs, 'pack-%s-%s' % (lang, section))))


def fill_sections(packs, langs, sections, dry_run):
    catalog = load_catalog()
    for section in sections:
        for lang in langs or languages_with(packs, section):
            fill(packs, lang, section, catalog, dry_run)


def convert(packs, langs):
    """A language with no manifest yet: an empty one, then every section filled."""
    for lang in langs:
        out_dir = audio_dir(lang)
        if os.path.exists(os.path.join(out_dir, 'manifest.json')):
            sys.exit('%s already ships a manifest — --fill adds what it lacks, --ship swaps a take' % lang)
        if not os.path.isdir(os.path.join(packs, 'pack-%s-words' % lang)):
            sys.exit('%s: no pack-%s-words' % (packs, lang))
        os.makedirs(out_dir)
        write_manifest(out_dir, {'language': lang, 'words': {}})
    fill_sections(packs, langs, FILL_ORDER, dry_run=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--packs', default=audio_ship.WORKSPACE,
                        help='the workspace holding pack-<lang>-<section>/ and the swap journal')
    parser.add_argument('--lang', action='append', help='only this language (repeatable)')
    parser.add_argument('--reindex', action='store_true',
                        help='re-measure catalog/audio/<lang>/ in place; no packs needed')
    for section in ('articles', 'calendar', 'countries'):
        parser.add_argument('--' + section, action='store_true',
                            help='--ship --from-packs --section %s, then --fill --section %s' % (section, section))
    parser.add_argument('--fill', action='store_true',
                        help='add what pack-<lang>-<section> has and the shipped section lacks; '
                             'every entry already shipped is left exactly as it is')
    parser.add_argument('--prune-poor', action='store_true',
                        help='remove shipped recordings the take judge refuses — under their '
                             "language's mos floor, heard as bad, one-sided (scripts/audio_judge.py); "
                             'no packs needed')
    parser.add_argument('--ship', action='store_true',
                        help='apply the staged swaps in <packs>/staged.tsv whose incumbent still ships')
    parser.add_argument('--from-packs', action='store_true',
                        help='with --ship, first stage every pack row naming another Commons file '
                             'than what ships under its form (any section but letters)')
    parser.add_argument('--section', choices=SECTIONS, help='with --ship or --fill, only this section')
    parser.add_argument('--dry-run', action='store_true',
                        help='with --ship or --fill, judge and print, write nothing')
    parser.add_argument('--restore', nargs=3, metavar=('LANG', 'SECTION', 'KEY'),
                        help='put back the entry and bytes KEY held at --from REV')
    parser.add_argument('--from', dest='rev', metavar='REV', help='the git revision --restore reads')
    parser.add_argument('--remove', nargs=3, metavar=('LANG', 'SECTION', 'KEY'),
                        help='drop one shipped entry and its file')
    args = parser.parse_args()
    if args.ship:
        return audio_ship.ship(args.packs, args.lang, args.dry_run, args.from_packs, args.section)
    if args.restore:
        if not args.rev:
            parser.error('--restore needs --from REV')
        item = audio_manifest.restore(*args.restore, args.rev)
        return print('  restore %s %s' % (args.restore[2], item['source']))
    if args.remove:
        return print('  remove %s %s' % (args.remove[2], audio_manifest.remove(*args.remove)['source']))
    if args.prune_poor:
        return prune_poor(args.lang)

    # why: gain/lead are this build's numbers to a decimal, so another ffmpeg silently
    # rewrites manifests that were otherwise byte-identical — say so rather than surprise
    # the diff. A warning, not a stop: the gates hold on any build.
    detected = audio_measure.version(FFMPEG)
    if detected != ANALYSIS['ffmpeg']:
        print('warning: measuring with %s; ANALYSIS was taken on %s — expect drifted decimals'
              % (detected, ANALYSIS['ffmpeg']))

    if args.reindex:
        shipped = sorted(name for name in os.listdir(AUDIO)
                         if os.path.isdir(os.path.join(AUDIO, name)))
        for lang in args.lang or shipped:
            print('reindex %s' % lang)
            reindex(lang)
        return
    aliased = [section for section in ('articles', 'calendar', 'countries') if getattr(args, section)]
    if aliased or args.fill:
        sections = aliased or [args.section or 'words']
        for section in aliased:
            audio_ship.ship(args.packs, args.lang, args.dry_run, True, section)
        return fill_sections(args.packs, args.lang, sections, args.dry_run)
    if not args.lang:
        parser.error('a full convert names its new language: --lang L')
    convert(args.packs, args.lang)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Generate catalog/audio/<lang>/ from the pronunciation packs.

    scripts/audio-catalog.py --packs ../data/reference/audio

The packs (`pack-<lang>/manifest.tsv` + slug-named `mp3/`, plus `pack-uk-letters`
for the alphabet) are unversioned research input; `catalog/audio/` is the versioned
provenance record. What ships is the Wikimedia Commons transcode UNTOUCHED —
re-encoding is an adaptation under BY-SA — so every entry carries the sha256 this
script verified after the copy, and lint re-hashes what was committed. Edit packs,
never `catalog/audio/`.

Three stages. Four GATES decide which pack rows may ship and who is credited, each
decision printed (`audio_gates.py`). Survivors are COPIED byte-for-byte, and the copy
that landed is then ANALYSED (`audio_measure.py`) into the optional `gain`/`lead`
playback fields — see [ANALYSIS], which also decides the players' scheme.

Deterministic: sorted keys, 2-space indent, unchanged packs give byte-identical output.
"""
import argparse
import csv
import hashlib
import json
import os
import shutil
import sys

import audio_measure
from audio_gates import attribute, digest_of, keep_reachable, keep_unambiguous

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')

FFMPEG = os.environ.get('FFMPEG', 'ffmpeg')

# The PLAYBACK ANALYSIS INDEX (user ruling 2026-08-01). The packs were recorded by
# different people on different equipment and do not share a loudness; the uk letters are
# both the quietest and the latest to start speaking. Re-encoding them is out — that is an
# adaptation under BY-SA, and it would break the untouched-transcode gate — so what
# corrects them is our own MEASUREMENT of the untouched bytes, carried in the manifest and
# applied by the player. Measurement data carries no licence of its own, the credits'
# "unmodified" claim stays true, and `sha256` keeps meaning exactly what it says.
#
# Measured over all 1126 shipped files: the word packs sit at a median -16.7 LUFS
# (de -16.4, es -21.2, sw -11.4, uk -16.8) and the uk letters at -31.4 — a 14.7 dB deficit.
# The rule was ≤ 6 dB → attenuate everything down to the quietest class; past that the
# whole app would whisper, so the scheme is BOOST against the word-pack median: letters
# take up to +20 dB, the loud sw pack takes about -5, and the players need a boost path.
# (Letters also open with a median 1077 ms of dead air, against 173 ms for words.)
ANALYSIS = {
    'scheme': 'boost',
    'target_lufs': -16.7,
    'deficit_db': 14.7,
    'ffmpeg': 'ffmpeg version 8.1.2',
}

# A recording's first 50 ms of near-silence is its attack, not dead air — starting past it
# clips the consonant off the front. Everything before that the player may skip.
LEAD_KEEP_MS = 50
# ±20 dB is 10× amplitude and the point where a measurement is likelier broken than the
# recording. uk `ж` (-37.4 LUFS, +20.7 measured) is the one entry the clamp catches today.
GAIN_LIMIT_DB = 20.0

# Every licence the packs actually carry → its canonical deed. An unlisted one is a
# hard stop: the credits screen links what it names, and PD has no deed to link.
LICENCE_URLS = {
    'CC BY-SA 4.0': 'https://creativecommons.org/licenses/by-sa/4.0/',
    'CC BY-SA 3.0': 'https://creativecommons.org/licenses/by-sa/3.0/',
    'CC BY-SA 2.5': 'https://creativecommons.org/licenses/by-sa/2.5/',
    'CC BY 4.0': 'https://creativecommons.org/licenses/by/4.0/',
    'CC BY 3.0 us': 'https://creativecommons.org/licenses/by/3.0/us/',
    'CC BY 3.0': 'https://creativecommons.org/licenses/by/3.0/',
    'CC BY 2.0 fr': 'https://creativecommons.org/licenses/by/2.0/fr/',
    # CC0 waives the credit BY and BY-SA demand, but it is a dedication with a deed of
    # its own — unlike a public-domain file, which has nothing to point the reader at.
    'CC0': 'https://creativecommons.org/publicdomain/zero/1.0/',
    'Public domain': None,
}


def read_json(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return json.load(f)


def read_rows(path):
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def load_catalog():
    """(every slug the catalog knows, {lang: {slug: [surface forms]}})."""
    areas = [area['area'] for group in read_json(CATALOG, 'areas.json') for area in group['areas']]
    slugs = set()
    forms = {}
    for area in areas:
        slugs |= {concept['slug'] for concept in read_json(CATALOG, area, 'concepts.json')}
        for name in sorted(os.listdir(os.path.join(CATALOG, area))):
            lang, extension = os.path.splitext(name)
            if extension != '.json' or lang == 'concepts':
                continue
            for slug, word in read_json(CATALOG, area, name).get('words', {}).items():
                # why: reachability is measured against everything a card may SHOW —
                # `text` and its rotating synonyms — plus the variants grading accepts.
                forms.setdefault(lang, {})[slug] = \
                    [word['text']] + word.get('synonyms', []) + word.get('variants', [])
    return slugs, forms


def licence_url(licence, where):
    if licence not in LICENCE_URLS:
        sys.exit('%s: unknown licence "%s" — add its deed to LICENCE_URLS' % (where, licence))
    return LICENCE_URLS[licence]


def entry(file, licence, author, source, digest, index, matches=None):
    """One manifest value; `licenceUrl` is absent exactly where there is no deed."""
    record = {'file': file, 'licence': licence, 'author': author,
              'source': source, 'sha256': digest, **index}
    if matches is not None:
        record['matches'] = matches
    url = licence_url(licence, source)
    if url:
        record['licenceUrl'] = url
    return record


def copy_verified(source, target):
    """Ships the transcode untouched and returns the digest of what actually landed."""
    with open(source, 'rb') as f:
        data = f.read()
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, 'wb') as f:
        f.write(data)
    digest = digest_of(target)
    if digest != hashlib.sha256(data).hexdigest():
        sys.exit('%s: what landed is not what was read from %s' % (target, source))
    return digest


def playback_index(loudness, leading):
    """The optional `gain`/`lead` for one entry — both absent when there is nothing to do."""
    gain = round(min(GAIN_LIMIT_DB, max(-GAIN_LIMIT_DB, ANALYSIS['target_lufs'] - loudness)), 1)
    lead = max(0, round(leading * 1000) - LEAD_KEEP_MS)
    index = {}
    if gain:
        index['gain'] = gain
    if lead:
        index['lead'] = lead
    return index


def copy_and_analyze(copies):
    """`[(id, source, target)]` → `{id: (sha256, playback index)}`: ship the bytes, then measure.

    why: the analysis runs over the file that LANDED, so an index can never describe other
    bytes than the ones its own `sha256` pins — and one batched ffmpeg pass keeps a
    thousand decodes off the converter's wall clock.
    """
    digests = {id: copy_verified(source, target) for id, source, target in copies}
    measured = audio_measure.measure_all(FFMPEG, [target for _, _, target in copies])
    analysed = {}
    for id, _, target in copies:
        loudness, leading = measured[target]
        if loudness is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % target)
        analysed[id] = (digests[id], playback_index(loudness, leading))
    return analysed


def convert_words(lang, pack, out_dir, slugs, forms):
    """Every shipping `words` entry for one language, plus the printed drop list."""
    drops = []
    mp3_dir = os.path.join(pack, 'mp3')
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    kept = attribute(keep_unambiguous(keep_reachable(rows, lang, slugs, forms, drops), mp3_dir, drops), drops)
    analysed = copy_and_analyze([(row['slug'], os.path.join(mp3_dir, row['slug'] + '.mp3'),
                                  os.path.join(out_dir, row['slug'] + '.mp3')) for row in kept])
    words = {}
    for row in kept:
        digest, index = analysed[row['slug']]
        words[row['slug']] = entry(row['slug'] + '.mp3', row['licence'], row['author'],
                                   row['file'], digest, index, matches=row['matched_word'])
    for reason, slug, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, slug, detail))
    counts = {reason: sum(1 for drop in drops if drop[0] == reason)
              for reason in ('unknown-slug', 'unrealized', 'unreachable', 'collision', 'unattributable')}
    print('  %s: %d rows → %d playable (%s)' % (lang, len(rows), len(words),
          ', '.join('%d %s' % (count, reason) for reason, count in counts.items())))
    return words


def convert_letters(pack, out_dir):
    """The alphabet section: codepoint-named files, because glyph names decompose on APFS."""
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    names = {row['letter']: 'letters/u%04x.mp3' % ord(row['letter']) for row in rows}
    analysed = copy_and_analyze([(row['letter'], os.path.join(pack, 'mp3', row['local_file']),
                                  os.path.join(out_dir, names[row['letter']])) for row in rows])
    letters = {}
    for row in rows:
        digest, index = analysed[row['letter']]
        letters[row['letter']] = entry(names[row['letter']], row['licence'], row['author'],
                                       row['file'], digest, index)
    print('  letters: %d recorded' % len(letters))
    return letters


def write_manifest(lang, out_dir, words, letters):
    manifest = {'language': lang, 'words': words}
    if letters:
        manifest['letters'] = letters
    with open(os.path.join(out_dir, 'manifest.json'), 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--packs', required=True, help='directory holding pack-<lang>/')
    parser.add_argument('--lang', action='append', help='convert only this language (repeatable)')
    args = parser.parse_args()

    # why: gain/lead are this build's numbers to a decimal, so another ffmpeg silently
    # rewrites manifests that were otherwise byte-identical — say so rather than surprise
    # the diff. A warning, not a stop: the four gates hold on any build.
    detected = audio_measure.version(FFMPEG)
    if detected != ANALYSIS['ffmpeg']:
        print('warning: measuring with %s; ANALYSIS was taken on %s — expect drifted decimals'
              % (detected, ANALYSIS['ffmpeg']))

    slugs, forms = load_catalog()
    languages = sorted(name[len('pack-'):] for name in os.listdir(args.packs)
                       if name.startswith('pack-') and not name.endswith('-letters')
                       and os.path.isdir(os.path.join(args.packs, name)))
    for lang in args.lang or languages:
        pack = os.path.join(args.packs, 'pack-%s' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s' % (args.packs, lang))
        print('pack-%s' % lang)
        out_dir = os.path.join(CATALOG, 'audio', lang)
        shutil.rmtree(out_dir, ignore_errors=True)
        os.makedirs(out_dir)
        words = convert_words(lang, pack, out_dir, slugs, forms)
        letters_pack = os.path.join(args.packs, 'pack-%s-letters' % lang)
        letters = convert_letters(letters_pack, out_dir) if os.path.isdir(letters_pack) else {}
        write_manifest(lang, out_dir, words, letters)


if __name__ == '__main__':
    main()

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
that landed is then ANALYZED (`audio_measure.py`) into the optional `gain`/`lead`
playback fields — see [ANALYSIS], which also decides the players' scheme.

Deterministic: sorted keys, 2-space indent, unchanged packs give byte-identical output.
"""
import argparse
import csv
import hashlib
import json
import math
import os
import shutil
import sys

import audio_measure
from audio_gates import (attribute, digest_of, keep_named_by_its_file, keep_reachable,
                         keep_unambiguous)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')

FFMPEG = os.environ.get('FFMPEG', 'ffmpeg')

# The PLAYBACK ANALYSIS INDEX (user ruling 2026-08-01). The packs were recorded by
# different people on different equipment and do not share a loudness; the uk letters are
# both the quietest and the latest to start speaking. Re-encoding them is out — that is an
# adaptation under BY-SA, and it would break the untouched-transcode gate — so what
# corrects them is our own MEASUREMENT of the untouched bytes, carried in the manifest and
# applied by the player. Measurement data carries no license of its own, the credits'
# "unmodified" claim stays true, and `sha256` keeps meaning exactly what it says.
#
# Measured over all 1126 shipped files: the word packs sit at a median -16.7 LUFS
# (de -16.4, es -21.2, sw -11.4, uk -16.8) and the uk letters at -31.4 — a 14.7 dB deficit.
# The rule was ≤ 6 dB → attenuate everything down to the quietest class; past that the
# whole app would whisper, so the scheme is BOOST against the word-pack median: letters
# take up to +20 dB, the loud sw pack takes about -5, and the players need a boost path.
# (Letters also open with a median 1077 ms of dead air, against 173 ms for words.)
#
# A boost is also a CLIPPING risk, so the loudness number never decides a gain alone: the
# player adds it to samples that already peak where they peak, and past full scale iOS's EQ
# hard-clips while Android's `LoudnessEnhancer` compresses — one number, two sounds, neither
# the recording. Every gain is therefore CAPPED at the headroom its own file has, measured on
# the same decode: `gain = min(loudness gain, PEAK_CEILING_DBFS - peak)`, floored to the
# decimal it ships at so rounding can never spend the margin. The cap only ever lowers, and it
# binds on 70 of the 1126 files — on 31 of them the loudness number alone would have driven
# the samples past full scale, worst es `here` (peak -3.2, +9.6 dB wanted, +2.1 granted). They
# land under the loudness target instead of distorting: user ruling 2026-08-01, quiet is the
# lesser loss.
#
# WHAT the loudness is measured through changed on 2026-08-06 (user ruling, after a
# Swahili session where the words plainly varied). Flat R128 said that pack was the
# tightest we ship — 467 files inside 3 dB — while the ear said otherwise, and both were
# right: the meter counts energy the phone's speaker cannot radiate. `karibu` and
# `nakupenda` measure 0.1 dB apart flat and 16 apart through `audio_measure.SPEAKER_LENS`,
# which is the number that matches what is heard. Gains come off the LENSED loudness now;
# the flat figure survives as what the packs are described by, nowhere else.
#
# `speaker_lufs` is where the sw pack already sat under the lens, so re-indexing it moved
# the balance without moving the pack — the only way to hear one change at a time. It is
# PROVISIONAL for exactly that reason: the packs do not share a lensed level (uk sits
# ~2.4 dB under sw while both measure -16.7 flat), so whichever number the other three are
# eventually re-indexed to has to be chosen with all four in view.
ANALYSIS = {
    'scheme': 'boost',
    'target_lufs': -16.7,
    'speaker_lufs': -17.5,
    'lensed': ['sw'],
    'deficit_db': 14.7,
    'ffmpeg': 'ffmpeg version 8.1.2',
}

# A recording's first 50 ms of near-silence is its attack, not dead air — starting past it
# clips the consonant off the front. Everything before that the player may skip.
LEAD_KEEP_MS = 50
# ±20 dB is 10× amplitude and the point where a measurement is likelier broken than the
# recording. uk `ж` (-37.4 LUFS, +20.7 measured) is the one entry the clamp catches today.
GAIN_LIMIT_DB = 20.0
# The ceiling a boosted sample may reach. 1 dB of full scale stays unspent: the sample peak
# we measure is not the inter-sample peak a resampler reconstructs, and the players' gain
# stages add their own ringing on top of it.
PEAK_CEILING_DBFS = -1.0

# Every license the packs actually carry → its canonical deed. An unlisted one is a
# hard stop: the credits screen links what it names, and PD has no deed to link.
LICENSE_URLS = {
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


def license_url(license, where):
    if license not in LICENSE_URLS:
        sys.exit('%s: unknown license "%s" — add its deed to LICENSE_URLS' % (where, license))
    return LICENSE_URLS[license]


def entry(file, license, author, source, digest, index, matches=None):
    """One manifest value; `licenseUrl` is absent exactly where there is no deed."""
    record = {'file': file, 'license': license, 'author': author,
              'source': source, 'sha256': digest, **index}
    if matches is not None:
        record['matches'] = matches
    url = license_url(license, source)
    if url:
        record['licenseUrl'] = url
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


def playback_index(loudness, speaker, leading, peak, floor, lensed=False):
    """The optional `gain`/`lead` plus `snr` for one entry — absent when there is nothing to say.

    `lensed` takes the gain off what a phone speaker can radiate (see [ANALYSIS]) instead of
    off the flat loudness. The lens only ever decides the TARGET a file is moved toward;
    the ceiling below still answers to the flat peak, because that is what clips.

    `snr` is peak minus noise floor: how far the word stands above the hiss under it. Unlike
    the other two it changes no playback — it is carried so the lint can see the SHAPE of a
    pack and refuse a rebuild that quietly reintroduces the noise a previous one removed.
    Measured, never applied: filtering the file would be an adaptation under BY-SA and would
    break the sha256 that pins it.
    """
    wanted = (ANALYSIS['speaker_lufs'] - speaker) if lensed else (ANALYSIS['target_lufs'] - loudness)
    boost = round(min(GAIN_LIMIT_DB, max(-GAIN_LIMIT_DB, wanted)), 1)
    # why: floor, never round — a gain rounded up to the shipped decimal spends the safety
    # margin it was granted, and the file it was granted for is the one already near clipping.
    gain = min(boost, math.floor((PEAK_CEILING_DBFS - peak) * 10) / 10)
    lead = max(0, round(leading * 1000) - LEAD_KEEP_MS)
    index = {}
    if gain:
        index['gain'] = gain
    if lead:
        index['lead'] = lead
    if floor is not None:
        index['snr'] = round(peak - floor, 1)
    return index


def copy_and_analyze(copies, lensed=False):
    """`[(id, source, target)]` → `{id: (sha256, playback index)}`: ship the bytes, then measure.

    why: the analysis runs over the file that LANDED, so an index can never describe other
    bytes than the ones its own `sha256` pins — and one batched ffmpeg pass keeps a
    thousand decodes off the converter's wall clock.
    """
    digests = {id: copy_verified(source, target) for id, source, target in copies}
    measured = audio_measure.measure_all(FFMPEG, [target for _, _, target in copies])
    analyzed = {}
    for id, _, target in copies:
        loudness, speaker, leading, peak, floor = measured[target]
        if loudness is None or peak is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % target)
        if lensed and speaker is None:
            sys.exit('%s: nothing above the speaker lens — it cannot be indexed by it' % target)
        analyzed[id] = (digests[id], playback_index(loudness, speaker, leading, peak, floor, lensed))
    return analyzed


def convert_words(lang, pack, out_dir, slugs, forms):
    """Every shipping `words` entry for one language, plus the printed drop list."""
    drops = []
    mp3_dir = os.path.join(pack, 'mp3')
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    reachable = keep_named_by_its_file(keep_reachable(rows, lang, slugs, forms, drops), drops)
    kept = attribute(keep_unambiguous(reachable, mp3_dir, drops), drops)
    analyzed = copy_and_analyze([(row['slug'], os.path.join(mp3_dir, row['slug'] + '.mp3'),
                                  os.path.join(out_dir, row['slug'] + '.mp3')) for row in kept],
                                lensed=lang in ANALYSIS['lensed'])
    words = {}
    for row in kept:
        digest, index = analyzed[row['slug']]
        words[row['slug']] = entry(row['slug'] + '.mp3', row['license'], row['author'],
                                   row['file'], digest, index, matches=row['matched_word'])
    for reason, slug, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, slug, detail))
    counts = {reason: sum(1 for drop in drops if drop[0] == reason)
              for reason in ('unknown-slug', 'unrealized', 'unreachable', 'misnamed',
                             'collision', 'unattributable')}
    print('  %s: %d rows → %d playable (%s)' % (lang, len(rows), len(words),
          ', '.join('%d %s' % (count, reason) for reason, count in counts.items())))
    return words


def letter_file(glyph):
    """`letters/u<cp>…mp3` — one `u<cp>` per codepoint, because glyph names decompose on APFS.

    A sequence rather than a single codepoint: a named row may be a DIGRAPH (es `ch` che,
    and `ll`/`rr` if anyone ever records them). Single-codepoint glyphs are unaffected, so
    nothing already shipped is renamed.
    """
    return 'letters/%s.mp3' % ''.join('u%04x' % ord(char) for char in glyph)


def convert_letters(pack, out_dir):
    """The alphabet section: codepoint-named files, because glyph names decompose on APFS."""
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    names = {row['letter']: letter_file(row['letter']) for row in rows}
    analyzed = copy_and_analyze([(row['letter'], os.path.join(pack, 'mp3', row['local_file']),
                                  os.path.join(out_dir, names[row['letter']])) for row in rows])
    letters = {}
    for row in rows:
        digest, index = analyzed[row['letter']]
        letters[row['letter']] = entry(names[row['letter']], row['license'], row['author'],
                                       row['file'], digest, index)
    print('  letters: %d recorded' % len(letters))
    return letters


def convert_texts(pack, out_dir):
    """The alphabet's `exampleText` words — reference material that carries no slug.

    `sechs`, `Quittung`, the es `pero`/`perro` minimal pair: core to the sheet and to the
    letter drill, and citable by no concept, so the word gates — which resolve a slug
    against the catalog — can never reach them. They index by the FORM they speak, exactly
    as words do, so a recording still only ever plays over the word it actually says.
    Files are ASCII-named for the reason the letters are codepoint-named: macOS normalises
    filenames, and `pingüino.mp3` cannot be looked up by the string a manifest stores.
    """
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    names = {row['text']: 'texts/' + row['local_file'] for row in rows}
    analyzed = copy_and_analyze([(row['text'], os.path.join(pack, 'mp3', row['local_file']),
                                  os.path.join(out_dir, names[row['text']])) for row in rows])
    texts = {}
    for row in rows:
        digest, index = analyzed[row['text']]
        texts[row['text']] = entry(names[row['text']], row['license'], row['author'],
                                   row['file'], digest, index, matches=row['text'])
    print('  texts: %d recorded' % len(texts))
    return texts


def reindex(lang):
    """Re-derive `gain`/`lead`/`snr` for a language already under `catalog/audio/`, out of
    the bytes it ships — nothing is copied, converted or renamed.

    why a second entry point at all: the packs are unversioned research input and may be
    long gone from the machine that needs to re-measure, while the mp3 the index describes
    is right here and pinned. Every file's `sha256` is re-verified first, so a re-index can
    never quietly re-describe changed bytes — which is also the whole claim the credits make.
    """
    out_dir = os.path.join(CATALOG, 'audio', lang)
    manifest = read_json(out_dir, 'manifest.json')
    entries = {(section, key): item
               for section in ('words', 'letters', 'texts') if section in manifest
               for key, item in manifest[section].items()}
    measured = audio_measure.measure_all(
        FFMPEG, sorted({os.path.join(out_dir, item['file']) for item in entries.values()}))
    moved, limited = [], 0
    for (section, key), item in sorted(entries.items()):
        path = os.path.join(out_dir, item['file'])
        if digest_of(path) != item['sha256']:
            sys.exit('%s: sha256 no longer matches — the bytes changed, re-run the convert'
                     % path)
        loudness, speaker, leading, peak, floor = measured[path]
        if loudness is None or peak is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % path)
        index = playback_index(loudness, speaker, leading, peak, floor,
                               lensed=lang in ANALYSIS['lensed'])
        was = item.get('gain', 0)
        for field in ('gain', 'lead', 'snr'):
            item.pop(field, None)
        item.update(index)
        if index.get('gain', 0) != was:
            moved.append(index.get('gain', 0) - was)
        if speaker is not None and index.get('gain', 0) < round(
                ANALYSIS['speaker_lufs'] - speaker, 1) - 0.05:
            limited += 1
    write_manifest(lang, out_dir, manifest.get('words', {}),
                   manifest.get('letters', {}), manifest.get('texts', {}))
    moved.sort()
    print('  %s: %d entries, %d re-gained (median %+.1f dB, widest %+.1f), %d held by the '
          'peak ceiling' % (lang, len(entries), len(moved),
                            moved[len(moved) // 2] if moved else 0,
                            max(moved, key=abs) if moved else 0, limited))


def write_manifest(lang, out_dir, words, letters, texts):
    manifest = {'language': lang, 'words': words}
    if letters:
        manifest['letters'] = letters
    if texts:
        manifest['texts'] = texts
    with open(os.path.join(out_dir, 'manifest.json'), 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--packs', help='directory holding pack-<lang>/')
    parser.add_argument('--lang', action='append', help='convert only this language (repeatable)')
    parser.add_argument('--reindex', action='store_true',
                        help='re-measure catalog/audio/<lang>/ in place; no packs needed')
    args = parser.parse_args()
    if not args.packs and not args.reindex:
        parser.error('--packs is required unless --reindex re-measures what already ships')

    # why: gain/lead are this build's numbers to a decimal, so another ffmpeg silently
    # rewrites manifests that were otherwise byte-identical — say so rather than surprise
    # the diff. A warning, not a stop: the four gates hold on any build.
    detected = audio_measure.version(FFMPEG)
    if detected != ANALYSIS['ffmpeg']:
        print('warning: measuring with %s; ANALYSIS was taken on %s — expect drifted decimals'
              % (detected, ANALYSIS['ffmpeg']))

    if args.reindex:
        shipped = sorted(name for name in os.listdir(os.path.join(CATALOG, 'audio'))
                         if os.path.isdir(os.path.join(CATALOG, 'audio', name)))
        for lang in args.lang or shipped:
            print('reindex %s' % lang)
            reindex(lang)
        return

    slugs, forms = load_catalog()
    languages = sorted(name[len('pack-'):] for name in os.listdir(args.packs)
                       if name.startswith('pack-') and not name.endswith(('-letters', '-texts'))
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
        texts_pack = os.path.join(args.packs, 'pack-%s-texts' % lang)
        texts = convert_texts(texts_pack, out_dir) if os.path.isdir(texts_pack) else {}
        write_manifest(lang, out_dir, words, letters, texts)


if __name__ == '__main__':
    main()

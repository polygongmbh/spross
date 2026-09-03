#!/usr/bin/env python3
"""Generate catalog/audio/<lang>/ from the pronunciation packs.

    scripts/audio-catalog.py --packs ../data/reference/audio

The packs (`pack-<lang>/manifest.tsv` + slug-named `mp3/`, plus `pack-<lang>-letters`
for the alphabet and `pack-<lang>-calendar` for the weekday and month names) are
unversioned research input; `catalog/audio/` is the versioned provenance record. What ships is the Wikimedia Commons transcode UNTOUCHED —
re-encoding is an adaptation under BY-SA — so every entry carries the sha256 this
script verified after the copy, and lint re-hashes what was committed. Edit packs,
never `catalog/audio/`.

Three stages. Four GATES decide which pack rows may ship and who is credited, each
decision printed (`audio_gates.py`). Survivors are COPIED byte-for-byte, and the copy
that landed is then ANALYZED (`audio_measure.py`) into the optional `gain`/`gainPhone`/
`lead` playback fields — see [ANALYSIS], which also decides the players' scheme.

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
from audio_gates import (attribute, digest_of, keep_article_forms, keep_named_by_its_file,
                         keep_reachable, keep_unambiguous, speech_key)

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
# TWO PLANES, because a phone's built-in speaker and a pair of headphones do not agree on
# what a word weighs. R128 counts energy at 150 Hz nearly like energy at 2 kHz; a phone
# speaker reproduces almost none of the first, so two packs that measure level flat are
# heard many dB apart on the device — sw, all sharp open vowels, sat ~9 dB above de
# through `audio_measure.SPEAKER_LENS`. A word lifted for a speaker that needed it is a
# boomy word on headphones, so no single number suits both; every word therefore carries
# TWO gains and a player picks by output route:
#
#   gain      full-range plane (headphones, Bluetooth, car, USB) — flat LUFS against
#             `target_lufs`, the word packs' own median (-18.0, re-derived 2026-08-15).
#   gainPhone the phone-speaker plane — the same loudness through SPEAKER_LENS against
#             `speaker_lufs`, the packs' own lensed median (-26.9, re-derived 2026-08-21:
#             sw-only at -22.6, then all packs through a real phone's gradual roll-off).
#
# The rule was ≤ 6 dB → attenuate everything down to the quietest class; past that the
# whole app would whisper, so the scheme is BOOST against the pack median: letters take
# up to +20 dB and the players need a boost path. (Letters also open with a median 1077 ms
# of dead air, against 173 ms for words.) The lens is a real phone's gradual roll-off now —
# −20 dB below 450 Hz, −8 dB at 800 Hz, flat past 1.2 kHz, see audio_measure.SPEAKER_LENS —
# and it is safe to be: the route split means a phone-plane lift is never heard on
# headphones, so each plane keeps its own number rather than one compromise between them.
#
# A boost is also a CLIPPING risk, so the loudness number never decides a gain alone: the
# player adds it to samples that already peak where they peak, and past full scale iOS's EQ
# hard-clips while Android's `LoudnessEnhancer` compresses — one number, two sounds, neither
# the recording. Every gain is therefore CAPPED at the headroom its own file has, measured on
# the same decode: `gain = min(loudness gain, PEAK_CEILING_DBFS - peak)`, floored to the
# decimal it ships at so rounding can never spend the margin. The cap only ever lowers; the
# files it binds land under the loudness target instead of distorting: user ruling
# 2026-08-01, quiet is the lesser loss.
#
# What the cap held back ships beside the gain as `cap`/`capPhone`, because the cap is only
# true at FULL VOLUME. A listening run's bedtime ramp attenuates before the boost is applied
# and opens exactly that much headroom again, so a player under a fade can hand the deficit
# back — as much of it as the ramp has already taken off — and the word lands on the loudness
# target after all (`fadedGainDb`). It binds 5-25% of every pack but sw, which is the loud
# one and is never capped at all, so without this the fade made a run's levels DRIFT APART
# by pack rather than merely fall.
ANALYSIS = {
    'scheme': 'boost',
    'target_lufs': -18.0,
    'speaker_lufs': -26.9,
    # 9.0.1 reproduces 8.1.2's decimals exactly: a --reindex under it re-gained nothing.
    'ffmpeg': 'ffmpeg version 9.0.1',
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
    'CC BY-SA 2.0': 'https://creativecommons.org/licenses/by-sa/2.0/',
    'CC BY 4.0': 'https://creativecommons.org/licenses/by/4.0/',
    'CC BY 3.0 us': 'https://creativecommons.org/licenses/by/3.0/us/',
    'CC BY 3.0': 'https://creativecommons.org/licenses/by/3.0/',
    'CC BY 2.0 fr': 'https://creativecommons.org/licenses/by/2.0/fr/',
    # CC0 waives the credit BY and BY-SA demand, but it is a dedication with a deed of
    # its own — unlike a public-domain file, which has nothing to point the reader at.
    'CC0': 'https://creativecommons.org/publicdomain/zero/1.0/',
    'Public domain': None,
}

# Licenses we have LOOKED AT and will not bundle. Separate from an unlisted license, which
# stays a hard stop: the difference is whether a human has ruled on it, and a row dropped
# here is a decision, not a surprise.
#
# GFDL is written for documents — it obliges shipping the full license text and keeping a
# "Transparent copy" available, neither of which a credits screen linking a deed does, and
# unlike every CC license here it grants no media-shaped permission. Two Wiktionary German
# recordings carry it (`docs/audio-licensing.md`); the drill says those two words in the
# device voice instead, which costs a learner nothing a license notice would not.
# GPLv3 is a SOFTWARE license: its copyleft reaches the work as a whole, and its
# anti-tivoization and Installation Information terms are famously irreconcilable with App
# Store distribution — which is where this app ships. It also carries no media-shaped
# permission at all. The Esperanto calendar is where it turns up (Kurso de Esperanto
# recorded 16 of the 19 names), and that is the whole cost of refusing it: those names are
# said by the device voice, which Esperanto has on both platforms.
# "Attribution" is Commons' legacy bare template: the uploader asks to be credited and names
# no versioned license, so there is no deed for the credits screen to link and no stated terms
# to hold anyone to. Unlike `Public domain`, which also has no deed, it is a claim of rights
# rather than a waiver of them — so it is refused rather than deeded to null. One recording
# carries it (es `Chile`).
UNSHIPPABLE_LICENSES = {'GFDL', 'GPLv3', 'Attribution'}


def shippable(rows, where):
    """Pack rows whose license we bundle, with every refusal printed."""
    kept = []
    for row in rows:
        if row.get('license') in UNSHIPPABLE_LICENSES:
            print('  drop %-15s %-22s %s' % ('license', row.get('text') or row.get('slug', '?'),
                                             row['license']))
        else:
            kept.append(row)
    return kept


def read_json(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return json.load(f)


SECTIONS = ('words', 'letters', 'texts', 'articles', 'calendar', 'countries')


def read_manifest(out_dir):
    """A shipped manifest with every entry's `license` written back into it.

    The inverse of what [write_manifest] factors out, so the two entry points that rebuild
    ONE section of what already ships (`--reindex`, `--articles`) hand `write_manifest`
    the same inline shape a fresh convert does, and the root maps are re-derived rather
    than carried along stale.
    """
    manifest = read_json(out_dir, 'manifest.json')
    authors = manifest.get('authors', {})
    for name in SECTIONS:
        for item in manifest.get(name, {}).values():
            if 'license' not in item:
                item['license'] = authors[item['author']]
    return manifest


def read_rows(path):
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def load_catalog():
    """(every slug, {lang: {slug: [surface forms]}}, {lang: {slug: (article, text)}}).

    The third is what an ARTICLE recording is measured against — only gendered
    realizations appear in it, because only they show an article to say.
    """
    areas = [area['area'] for group in read_json(CATALOG, 'areas.json') for area in group['areas']]
    slugs = set()
    forms = {}
    targets = {}
    for area in areas:
        slugs |= {concept['slug'] for concept in read_json(CATALOG, 'areas', area, 'concepts.json')}
        for name in sorted(os.listdir(os.path.join(CATALOG, 'areas', area))):
            lang, extension = os.path.splitext(name)
            if extension != '.json' or lang == 'concepts':
                continue
            for slug, word in read_json(CATALOG, 'areas', area, name).get('words', {}).items():
                # why: reachability is measured against everything a card may SHOW —
                # `text` and its rotating synonyms — plus the variants grading accepts.
                forms.setdefault(lang, {})[slug] = \
                    [word['text']] + word.get('synonyms', []) + word.get('variants', [])
                article = word.get('grammar', {}).get('gender')
                if article:
                    targets.setdefault(lang, {})[slug] = (article, word['text'])
    return slugs, forms, targets


def license_url(license, where):
    if license not in LICENSE_URLS:
        sys.exit('%s: unknown license "%s" — add its deed to LICENSE_URLS' % (where, license))
    return LICENSE_URLS[license]


def entry(file, license, author, source, digest, index, matches=None, word=None):
    """One manifest value, licensed inline; `write_manifest` factors that out again."""
    record = {'file': file, 'license': license, 'author': author,
              'source': source, 'sha256': digest, **index}
    if matches is not None:
        record['matches'] = matches
    if word is not None:
        record['word'] = word
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


def playback_index(loudness, speaker, leading, peak, floor, phone):
    """The optional `gain`/`gainPhone`/`lead` plus `snr` for one entry — absent when there is nothing to say.

    Two gains, one per playback plane (see [ANALYSIS]): `gain` moves a file toward the
    full-range target off the flat loudness, `gainPhone` toward the phone-speaker target
    off what a phone can radiate (`speaker`). The lens only ever decides the TARGET the
    phone-plane gain is measured against; the ceiling below still answers to the flat
    peak, because that is what clips on either plane.

    `snr` is peak minus noise floor: how far the word stands above the hiss under it. Unlike
    the other fields it changes no playback — it is carried so the lint can see the SHAPE of a
    pack and refuse a rebuild that quietly reintroduces the noise a previous one removed.
    Measured, never applied: filtering the file would be an adaptation under BY-SA and would
    break the sha256 that pins it.
    """
    full = round(min(GAIN_LIMIT_DB, max(-GAIN_LIMIT_DB, ANALYSIS['target_lufs'] - loudness)), 1)
    # why: floor, never round — a gain rounded up to the shipped decimal spends the safety
    # margin it was granted, and the file it was granted for is the one already near clipping.
    headroom = math.floor((PEAK_CEILING_DBFS - peak) * 10) / 10
    gain = min(full, headroom)
    index = {}
    if gain:
        index['gain'] = gain
    # What the ceiling held back, for a player that has attenuated its way to the headroom
    # again (see [ANALYSIS]). Absent means the loudness number stood as measured.
    cap = round(full - gain, 1)
    if cap:
        index['cap'] = cap
    if phone:
        wanted = round(min(GAIN_LIMIT_DB, max(-GAIN_LIMIT_DB, ANALYSIS['speaker_lufs'] - speaker)), 1)
        # why: always written for words, 0.0 included — the player needs to tell "the phone
        # plane is zero" apart from "no phone plane was measured" (letters/texts), where the
        # full-range gain stands.
        index['gainPhone'] = min(wanted, headroom)
        cap_phone = round(wanted - index['gainPhone'], 1)
        if cap_phone:
            index['capPhone'] = cap_phone
    lead = max(0, round(leading * 1000) - LEAD_KEEP_MS)
    if lead:
        index['lead'] = lead
    if floor is not None:
        index['snr'] = round(peak - floor, 1)
    return index


def copy_and_analyze(copies, phone=False):
    """`[(id, source, target)]` → `{id: (sha256, playback index)}`: ship the bytes, then measure.

    `phone` adds the phone-speaker gain beside the full-range one (see [ANALYSIS]); letters
    and texts stay flat-only — the alphabet's balance was never the question, only the word
    packs that dominate a session.

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
        if phone and speaker is None:
            sys.exit('%s: nothing above the speaker lens — it cannot be indexed by it' % target)
        analyzed[id] = (digests[id], playback_index(loudness, speaker, leading, peak, floor, phone))
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
                                phone=True)
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


def convert_articles(lang, pack, out_dir, targets, forms):
    """Every shipping `articles` entry: recordings that say the article, then the word.

    An addition beside the bare files rather than a replacement of them — the source side
    of a pair reads the learner's own language, where the article is not what is being
    taught. But it does not DEPEND on a bare twin: an entry records the word inside what it
    says, so where the pack has only the article recording, that file answers both the card
    asking with the article and the card asking without it.
    """
    drops = []
    mp3_dir = os.path.join(pack, 'mp3')
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    spoken = keep_named_by_its_file(keep_article_forms(rows, lang, targets, forms, drops), drops)
    kept = attribute(keep_unambiguous(spoken, mp3_dir, drops), drops)
    analyzed = copy_and_analyze([(row['slug'], os.path.join(mp3_dir, row['slug'] + '.mp3'),
                                  os.path.join(out_dir, 'articles', row['slug'] + '.mp3'))
                                 for row in kept], phone=True)
    articles = {}
    for row in kept:
        digest, index = analyzed[row['slug']]
        articles[row['slug']] = entry('articles/' + row['slug'] + '.mp3', row['license'],
                                      row['author'], row['file'], digest, index,
                                      matches=row['matched_word'], word=row['word'])
    for reason, slug, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, slug, detail))
    print('  articles: %d rows → %d spoken with their article' % (len(rows), len(articles)))
    return articles


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


def convert_calendar(pack, out_dir):
    """The calendar's weekday and month names — the dates drill's own vocabulary.

    Form-keyed like `texts`, and for the same reason: no concept covers a weekday, so
    nothing here carries a slug the word gates could resolve. What it does NOT share with
    texts is the playback plane — these are words spoken on a drill card, beside the very
    vocabulary the phone-speaker plane was measured for, so they are analyzed with it
    (`phone=True`) rather than left flat like the alphabet's reference rows.
    """
    drops = []
    # why: the authorship gate, not just the license one — a Commons row may name a bot's
    # guess at the uploader ("X assumed (based on copyright claims)"), and BY and BY-SA
    # both require naming the actual author. It is the same gate the word pack runs.
    rows = attribute(shippable(read_rows(os.path.join(pack, 'manifest.tsv')), pack), drops)
    names = {row['text']: 'calendar/' + row['local_file'] for row in rows}
    analyzed = copy_and_analyze([(row['text'], os.path.join(pack, 'mp3', row['local_file']),
                                  os.path.join(out_dir, names[row['text']])) for row in rows],
                                phone=True)
    calendar = {}
    for row in rows:
        digest, index = analyzed[row['text']]
        calendar[row['text']] = entry(names[row['text']], row['license'], row['author'],
                                      row['file'], digest, index, matches=row['text'])
    for reason, key, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, key, detail))
    print('  calendar: %d recorded' % len(calendar))
    return calendar


def convert_countries(pack, out_dir):
    """The atlas' country and nationality names — the atlas drill's own vocabulary.

    Form-keyed like [convert_calendar], and measured like it. The countries DO carry slugs,
    unlike a weekday, but a slug holds one file and a row holds two names it shows and asks
    for — "Deutschland" and "Deutsche" — so the form is what can key them both.
    """
    drops = []
    rows = attribute(shippable(read_rows(os.path.join(pack, 'manifest.tsv')), pack), drops)
    names = {row['text']: 'countries/' + row['local_file'] for row in rows}
    analyzed = copy_and_analyze([(row['text'], os.path.join(pack, 'mp3', row['local_file']),
                                  os.path.join(out_dir, names[row['text']])) for row in rows],
                                phone=True)
    countries = {}
    for row in rows:
        digest, index = analyzed[row['text']]
        countries[row['text']] = entry(names[row['text']], row['license'], row['author'],
                                       row['file'], digest, index, matches=row['text'])
    for reason, key, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, key, detail))
    print('  countries: %d recorded' % len(countries))
    return countries


def reindex(lang):
    """Re-derive `gain`/`gainPhone`/`lead`/`snr` for a language already under `catalog/audio/`,
    out of the bytes it ships — nothing is copied, converted or renamed.

    why a second entry point at all: the packs are unversioned research input and may be
    long gone from the machine that needs to re-measure, while the mp3 the index describes
    is right here and pinned. Every file's `sha256` is re-verified first, so a re-index can
    never quietly re-describe changed bytes — which is also the whole claim the credits make.
    """
    out_dir = os.path.join(CATALOG, 'audio', lang)
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
        loudness, speaker, leading, peak, floor = measured[path]
        if loudness is None or peak is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % path)
        phone = section in ('words', 'articles', 'calendar', 'countries')
        if phone and speaker is None:
            sys.exit('%s: nothing above the speaker lens — it cannot be indexed by it' % path)
        index = playback_index(loudness, speaker, leading, peak, floor, phone)
        was = item.get('gain', 0)
        for field in ('gain', 'cap', 'gainPhone', 'capPhone', 'lead', 'snr'):
            item.pop(field, None)
        item.update(index)
        if index.get('gain', 0) != was:
            moved.append(index.get('gain', 0) - was)
        if phone and speaker is not None and index.get('gainPhone', 0) < round(
                ANALYSIS['speaker_lufs'] - speaker, 1) - 0.05:
            limited += 1
    write_manifest(lang, out_dir, manifest.get('words', {}), manifest.get('letters', {}),
                   manifest.get('texts', {}), manifest.get('articles', {}),
                   manifest.get('calendar', {}), manifest.get('countries', {}))
    moved.sort()
    print('  %s: %d entries, %d re-gained (median %+.1f dB, widest %+.1f), %d held by the '
          'peak ceiling' % (lang, len(entries), len(moved),
                            moved[len(moved) // 2] if moved else 0,
                            max(moved, key=abs) if moved else 0, limited))


def convert_articles_only(packs, languages):
    """Add (or rebuild) the `articles` section of languages that already ship a manifest.

    why a second entry point: a word pack is research input that goes stale as content
    moves — slugs get renamed and its mp3s are long gone — while what ships is right here
    and pinned by its digests. Re-running the whole convert to gain one section would
    re-derive the other three from a workspace that can no longer produce them; this
    reads the manifest, replaces one section, and leaves the rest byte-identical.
    """
    _, forms, targets = load_catalog()
    found = sorted(name[len('pack-'):-len('-articles')] for name in os.listdir(packs)
                   if name.startswith('pack-') and name.endswith('-articles')
                   and os.path.isdir(os.path.join(packs, name)))
    for lang in languages or found:
        pack = os.path.join(packs, 'pack-%s-articles' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s-articles' % (packs, lang))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        manifest = read_manifest(out_dir)
        print('pack-%s-articles' % lang)
        shutil.rmtree(os.path.join(out_dir, 'articles'), ignore_errors=True)
        articles = convert_articles(lang, pack, out_dir, targets, forms)
        write_manifest(lang, out_dir, manifest['words'], manifest.get('letters', {}),
                       manifest.get('texts', {}), articles, manifest.get('calendar', {}),
                       manifest.get('countries', {}))


def convert_calendar_only(packs, languages):
    """Add (or rebuild) the `calendar` section of languages that already ship a manifest.

    `convert_articles_only`'s reason, and the one that matters most here: the calendar
    arrived long after the word packs were resolved, and rebuilding a whole language to
    gain nineteen weekday and month names would re-derive five hundred words from a
    workspace that has since gone stale.
    """
    found = sorted(name[len('pack-'):-len('-calendar')] for name in os.listdir(packs)
                   if name.startswith('pack-') and name.endswith('-calendar')
                   and os.path.isdir(os.path.join(packs, name)))
    for lang in languages or found:
        pack = os.path.join(packs, 'pack-%s-calendar' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s-calendar' % (packs, lang))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        manifest = read_manifest(out_dir)
        print('pack-%s-calendar' % lang)
        shutil.rmtree(os.path.join(out_dir, 'calendar'), ignore_errors=True)
        calendar = convert_calendar(pack, out_dir)
        write_manifest(lang, out_dir, manifest['words'], manifest.get('letters', {}),
                       manifest.get('texts', {}), manifest.get('articles', {}), calendar,
                       manifest.get('countries', {}))


def convert_countries_only(packs, languages):
    """Add (or rebuild) the `countries` section of languages that already ship a manifest.

    [convert_calendar_only]'s reason, and the same shape: the atlas' names arrived long
    after the word packs were resolved, and there is no sense re-deriving five hundred
    words from a workspace that has moved on in order to gain a hundred and forty.
    """
    found = sorted(name[len('pack-'):-len('-countries')] for name in os.listdir(packs)
                   if name.startswith('pack-') and name.endswith('-countries')
                   and os.path.isdir(os.path.join(packs, name)))
    for lang in languages or found:
        pack = os.path.join(packs, 'pack-%s-countries' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s-countries' % (packs, lang))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        manifest = read_manifest(out_dir)
        print('pack-%s-countries' % lang)
        shutil.rmtree(os.path.join(out_dir, 'countries'), ignore_errors=True)
        countries = convert_countries(pack, out_dir)
        write_manifest(lang, out_dir, manifest['words'], manifest.get('letters', {}),
                       manifest.get('texts', {}), manifest.get('articles', {}),
                       manifest.get('calendar', {}), countries)


def fill_words(packs, languages):
    """Add words the shipped manifest LACKS, leaving every entry it already has untouched.

    The catalog roughly doubled after the word packs were resolved, so half of each language
    reaches no recording — but a plain re-convert is the wrong tool for that. It replaces the
    language wholesale out of a research workspace that has since moved on: the German pack's
    hiss was fixed by reseating rows onto another speaker (`consolidate-pack.py`), several
    packs carry hand-curated appended tails, and none of that survives a re-resolve. So this
    only ever ADDS. A slug already in the manifest keeps its file, its digest and its credit,
    and a re-run after another catalog edit costs only the words that edit introduced.

    A new row is also dropped where it would collide with a SHIPPED entry's spoken form under
    differing bytes: the runtime could pick neither, so filling one word would silence another.
    """
    slugs, forms, _ = load_catalog()
    for lang in languages or sorted(name[len('pack-'):] for name in os.listdir(packs)
                                    if name.startswith('pack-') and '-' not in name[len('pack-'):]
                                    and os.path.isdir(os.path.join(packs, name))):
        pack = os.path.join(packs, 'pack-%s' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s' % (packs, lang))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        manifest = read_manifest(out_dir)
        shipped = manifest.get('words', {})
        print('pack-%s fill' % lang)

        drops = []
        mp3_dir = os.path.join(pack, 'mp3')
        rows = [row for row in shippable(read_rows(os.path.join(pack, 'manifest.tsv')), pack)
                if row['slug'] not in shipped
                and os.path.isfile(os.path.join(mp3_dir, row['slug'] + '.mp3'))]
        reachable = keep_named_by_its_file(keep_reachable(rows, lang, slugs, forms, drops), drops)
        kept = attribute(keep_unambiguous(reachable, mp3_dir, drops), drops)

        # why: against what already SHIPS, not just against this batch — `keep_unambiguous`
        # only sees the new rows, and a new word claiming a shipped word's sound mutes both.
        spoken = {}
        for key, item in shipped.items():
            if item.get('matches'):
                spoken.setdefault(speech_key(item['matches']), set()).add(item['sha256'])
        fresh = []
        for row in kept:
            digest = digest_of(os.path.join(mp3_dir, row['slug'] + '.mp3'))
            claimed = spoken.get(speech_key(row['matched_word']))
            if claimed and digest not in claimed:
                drops.append(('shipped-collision', row['slug'],
                              '"%s" is already spoken by another file' % row['matched_word']))
            else:
                fresh.append(row)

        analyzed = copy_and_analyze([(row['slug'], os.path.join(mp3_dir, row['slug'] + '.mp3'),
                                      os.path.join(out_dir, row['slug'] + '.mp3'))
                                     for row in fresh], phone=True)
        for row in fresh:
            digest, index = analyzed[row['slug']]
            shipped[row['slug']] = entry(row['slug'] + '.mp3', row['license'], row['author'],
                                         row['file'], digest, index, matches=row['matched_word'])
        for reason, slug, detail in sorted(drops):
            print('  drop %-18s %-22s %s' % (reason, slug, detail))
        print('  %s: %d added, %d words now' % (lang, len(fresh), len(shipped)))
        write_manifest(lang, out_dir, shipped, manifest.get('letters', {}),
                       manifest.get('texts', {}), manifest.get('articles', {}),
                       manifest.get('calendar', {}), manifest.get('countries', {}))


def credit_index(sections, where):
    """`(authors, licenses)`: who records under what, and what each license deeds to.

    A license is effectively a property of the SPEAKER — across every shipped pack only
    fourteen entries out of 5828 depart from their own author's usual one — so it is carried
    once per author instead of once per file, and the deed URL once per license instead
    of once per file again. An author's default is the license covering the most of their
    files, ties broken by the alphabetically first license string, so a rebuild of
    unchanged packs picks the same one twice.
    """
    per_author = {}
    for section in sections:
        for item in section.values():
            per_author.setdefault(item['author'], {})
            counts = per_author[item['author']]
            counts[item['license']] = counts.get(item['license'], 0) + 1
    authors = {author: min(sorted(counts), key=lambda name: (-counts[name], name))
               for author, counts in sorted(per_author.items())}
    used = sorted({license for counts in per_author.values() for license in counts})
    return authors, {license: license_url(license, where) for license in used}


def attributed(item, authors):
    """One entry with what the root maps now say for it removed.

    `licenseUrl` goes unconditionally — it is derivable from the license and nothing
    outside `LICENSE_URLS` ever decided it — while `license` survives exactly where the
    entry departs from its author's default, which is the escape hatch a speaker who
    published one file differently needs.
    """
    dropped = {'licenseUrl'}
    if item['license'] == authors[item['author']]:
        dropped.add('license')
    return {key: value for key, value in item.items() if key not in dropped}


def write_manifest(lang, out_dir, words, letters, texts, articles, calendar, countries):
    sections = [section for section in (words, letters, texts, articles, calendar, countries)
                if section]
    authors, licenses = credit_index(sections, 'audio/%s/manifest.json' % lang)
    manifest = {'language': lang, 'authors': authors, 'licenses': licenses,
                'words': {key: attributed(item, authors) for key, item in words.items()}}
    for name, section in (('letters', letters), ('texts', texts), ('articles', articles),
                          ('calendar', calendar), ('countries', countries)):
        if section:
            manifest[name] = {key: attributed(item, authors) for key, item in section.items()}
    with open(os.path.join(out_dir, 'manifest.json'), 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--packs', help='directory holding pack-<lang>/')
    parser.add_argument('--lang', action='append', help='convert only this language (repeatable)')
    parser.add_argument('--reindex', action='store_true',
                        help='re-measure catalog/audio/<lang>/ in place; no packs needed')
    parser.add_argument('--articles', action='store_true',
                        help='convert only pack-<lang>-articles into the shipped manifest, '
                             'leaving every other section byte-identical')
    parser.add_argument('--calendar', action='store_true',
                        help='convert only pack-<lang>-calendar into the shipped manifest, '
                             'leaving every other section byte-identical')
    parser.add_argument('--countries', action='store_true',
                        help='convert only pack-<lang>-countries into the shipped manifest, '
                             'leaving every other section byte-identical')
    parser.add_argument('--fill', action='store_true',
                        help='add words pack-<lang> has and the shipped manifest lacks; '
                             'every entry already shipped is left exactly as it is')
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

    if args.articles:
        return convert_articles_only(args.packs, args.lang)

    if args.calendar:
        return convert_calendar_only(args.packs, args.lang)

    if args.countries:
        return convert_countries_only(args.packs, args.lang)

    if args.fill:
        return fill_words(args.packs, args.lang)

    if args.reindex:
        shipped = sorted(name for name in os.listdir(os.path.join(CATALOG, 'audio'))
                         if os.path.isdir(os.path.join(CATALOG, 'audio', name)))
        for lang in args.lang or shipped:
            print('reindex %s' % lang)
            reindex(lang)
        return

    slugs, forms, targets = load_catalog()
    languages = sorted(name[len('pack-'):] for name in os.listdir(args.packs)
                       if name.startswith('pack-')
                       and not name.endswith(('-letters', '-texts', '-articles', '-calendar',
                                              '-countries'))
                       and os.path.isdir(os.path.join(args.packs, name)))
    for lang in args.lang or languages:
        pack = os.path.join(args.packs, 'pack-%s' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s' % (args.packs, lang))
        print('pack-%s' % lang)
        # why: the convert REPLACES what ships, so a pack that cannot produce its bytes has
        # to say so before the old ones are gone. A workspace goes stale as content moves
        # (a renamed slug leaves a row whose mp3 was never fetched), and a crash halfway
        # through the rebuild used to take the shipped pack with it.
        missing = [row['slug'] for row in read_rows(os.path.join(pack, 'manifest.tsv'))
                   if not os.path.isfile(os.path.join(pack, 'mp3', row['slug'] + '.mp3'))]
        if missing:
            sys.exit('pack-%s: %d rows have no mp3 (%s) — re-fetch the pack; nothing was touched'
                     % (lang, len(missing), ', '.join(sorted(missing)[:5])))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        shutil.rmtree(out_dir, ignore_errors=True)
        os.makedirs(out_dir)
        words = convert_words(lang, pack, out_dir, slugs, forms)
        letters_pack = os.path.join(args.packs, 'pack-%s-letters' % lang)
        letters = convert_letters(letters_pack, out_dir) if os.path.isdir(letters_pack) else {}
        texts_pack = os.path.join(args.packs, 'pack-%s-texts' % lang)
        texts = convert_texts(texts_pack, out_dir) if os.path.isdir(texts_pack) else {}
        articles_pack = os.path.join(args.packs, 'pack-%s-articles' % lang)
        articles = (convert_articles(lang, articles_pack, out_dir, targets, forms)
                    if os.path.isdir(articles_pack) else {})
        calendar_pack = os.path.join(args.packs, 'pack-%s-calendar' % lang)
        calendar = convert_calendar(calendar_pack, out_dir) if os.path.isdir(calendar_pack) else {}
        countries_pack = os.path.join(args.packs, 'pack-%s-countries' % lang)
        countries = (convert_countries(countries_pack, out_dir)
                     if os.path.isdir(countries_pack) else {})
        write_manifest(lang, out_dir, words, letters, texts, articles, calendar, countries)


if __name__ == '__main__':
    main()

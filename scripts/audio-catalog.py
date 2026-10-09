#!/usr/bin/env python3
"""Generate catalog/audio/<lang>/ from the pronunciation packs.

    scripts/audio-catalog.py --packs ../data/reference/audio

The packs (`pack-<lang>-words/manifest.tsv` + `mp3/` keyed by the form each row speaks, plus `pack-<lang>-letters`
for the alphabet and `pack-<lang>-calendar` for the weekday and month names) are
unversioned research input; `catalog/audio/` is the versioned provenance record. What ships is the Wikimedia Commons transcode UNTOUCHED —
re-encoding is an adaptation under BY-SA — so every entry carries the sha256 this
script verified after the copy, and lint re-hashes what was committed. Edit packs,
never `catalog/audio/`.

Three stages. Four GATES decide which pack rows may ship and who is credited, each
decision printed (`audio_gates.py`). Survivors are COPIED byte-for-byte, and the copy
that landed is then ANALYZED (`audio_measure.py`) into the optional `gain`/`cap`/
`lead`/`gate` playback fields — see [ANALYSIS], which also decides the players' scheme.

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
import unicodedata

import audio_measure
from audio_voices import is_one_sided, is_rejected, mos_floor
from audio_gates import (APOSTROPHES, attribute, digest_of, keep_article_forms, keep_named_by_its_file,
                         keep_reachable, keep_unambiguous, speech_key, spoken_target_form)

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
# ONE GAIN for every output, `gain` = `target_phon` minus the recording's perceived loudness
# (`audio_measure.perceived_loudness`, ISO 532-1 N5). The target is set so the catalog-wide
# median word gain is where the LUFS target (-18) had it; only the balance between voices moved
# (2026-10), and it moved by ear as well: sw, a squashed, band-limited voice, plays ~2 dB under
# where flat R128 energy put it. A second, phone-speaker gain measured through a home-made roll-off
# was dropped (2026-10): no standard phone weighting exists, and on the device it turned the
# sw pack down far past where the ear put it.
#
# The rule was ≤ 6 dB → attenuate everything down to the quietest class; past that the
# whole app would whisper, so the scheme is BOOST against the pack median: letters take
# up to +20 dB and the players need a boost path. (Letters also open with a median 1077 ms
# of dead air, against 173 ms for words.)
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
# What the cap held back ships beside the gain as `cap`, because the cap is only
# true at FULL VOLUME. A listening run's bedtime ramp attenuates before the boost is applied
# and opens exactly that much headroom again, so a player under a fade can hand the deficit
# back — as much of it as the ramp has already taken off — and the word lands on the loudness
# target after all (`fadedGainDb`). It binds 5-25% of every pack but sw, which is the loud
# one and is never capped at all, so without this the fade made a run's levels DRIFT APART
# by pack rather than merely fall.
ANALYSIS = {
    'scheme': 'boost',
    'target_phon': 86.1,
    'ffmpeg': 'ffmpeg version 9.0.2',
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


# How far above its measured noise a recording's playback gate opens. Enough that the
# noise's own swing does not flutter the gate, little enough that the word is never under it.
GATE_MARGIN_DB = 6.0
# kern `Playback.GATE_FLOOR_DB`: below it a file is digital silence and needs no gate.
GATE_FLOOR_DB = -100.0

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
            print('  drop %-15s %-22s %s' % ('license', row.get('text') or row.get('letter', '?'),
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


def verb_stem(form, prefixes):
    """kern `verbStem`: [form] without the first optional verb prefix it starts with, else None."""
    trimmed = form.strip()
    return next((trimmed[len(p):] for p in prefixes
                 if len(trimmed) > len(p) and trimmed.lower().startswith(p.lower())), None)


def split_article(form, articles):
    """kern `splitArticle`: (the listed article [form] opens with, the word behind it), else (None, form)."""
    for article in articles:
        elided = article[-1:] in APOSTROPHES
        head = article[:-1] if elided else article + ' '
        if len(form) <= len(head) or not form.lower().startswith(head.lower()):
            continue
        if elided and form[len(head)] not in APOSTROPHES:
            continue
        word = form[len(head) + (1 if elided else 0):].lstrip()
        if word:
            return form[:len(form) - len(word)].rstrip(), word
    return None, form


def load_catalog():
    """({lang: {speech key of every form some card shows}},
    {lang: {speech key of an article + word some card shows: the word}}).

    Which concept shows a form is never asked: a recording is filed under the form it speaks,
    so a form moving between a concept's text, a synonym and a tagged form leaves audio alone.
    The second is what an ARTICLE recording is measured against: a realization's own
    `grammar.gender` in front of each form it shows, and each tagged form with the article
    it was authored with — only those show an article to say.
    """
    areas = [area['area'] for group in read_json(CATALOG, 'areas.json') for area in group['areas']]
    languages = read_json(CATALOG, 'languages.json')
    prefixes = {code: info.get('optionalVerbPrefixes', []) for code, info in languages.items()}
    shown = {}
    articled = {}
    for area in areas:
        for name in sorted(os.listdir(os.path.join(CATALOG, 'areas', area))):
            lang, extension = os.path.splitext(name)
            if extension != '.json' or lang == 'concepts':
                continue
            for word in read_json(CATALOG, 'areas', area, name).get('words', {}).values():
                # why: reachability is measured against everything a card may SHOW —
                # `text` and its rotating `teaches` — plus the `accepts` grading takes.
                shown_by = [word['text']] + word.get('teaches', []) + word.get('accepts', [])
                # why: a tagged form (`forms.f`) is shown too, its authored article split off
                # as kern's join splits it, so the bare word and the articled one both reach it.
                tagged = [split_article(form, languages.get(lang, {}).get('articles') or [])
                          for value in word.get('forms', {}).values()
                          for form in (value if isinstance(value, list) else [value])]
                shown_by += [form for _, form in tagged]
                # why: kern's lookup falls back to a verb's bare stem (`verbStem`), so a
                # recording of `piga simu` reaches a card showing `kupiga simu`.
                shown_here = shown_by + [
                    stem for stem in (verb_stem(form, prefixes.get(lang, [])) for form in shown_by) if stem]
                shown.setdefault(lang, set()).update(speech_key(form) for form in shown_here)
                gender = word.get('grammar', {}).get('gender')
                said = [(gender, form) for form in shown_by[:len(shown_by) - len(tagged)] if gender]
                said += [(article, form) for article, form in tagged if article]
                for article, form in said:
                    articled.setdefault(lang, {}).setdefault(
                        speech_key(spoken_target_form(article, form)), form)
    return shown, articled


def ascii_stem(form):
    """A shipped file's ASCII name for [form]: [a-z0-9-] stays, every other character is `u<hex>`.

    macOS normalises filenames, so a name carrying ü or а cannot be looked up by the string
    the manifest stores — the letters are codepoint-named for the same reason.
    """
    return ''.join(char if char.isascii() and (char.isalnum() or char == '-') else 'u%04x' % ord(char)
                   for char in unicodedata.normalize('NFC', form).strip().lower())


def form_file(section, form):
    """`<section>/<ascii stem>.mp3` — a recording is filed under the form it speaks, never a slug."""
    return '%s/%s.mp3' % (section, ascii_stem(form))


def one_per_form(rows):
    """One row per spoken form. [keep_unambiguous] has left only byte-identical twins under
    one speech key — one recording under two spellings — and a form-keyed section holds it once."""
    kept = {}
    for row in sorted(rows, key=lambda row: row['local_file']):
        kept.setdefault(speech_key(row['text']), row)
    return list(kept.values())


def pack_mp3(pack, row):
    """Where a pack keeps a row's bytes: `mp3/<local_file>`, named by the form it speaks."""
    return os.path.join(pack, 'mp3', row['local_file'])


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


def playback_index(loudness, leading, peak, noise, loudest, mos, perceived):
    """The optional `gain`/`cap`/`lead`/`gate` plus `mos` for one entry — absent when there is nothing to say.

    `mos` is how good the take sounds (`audio_measure.mos`). Unlike the other fields it changes
    no playback — it is carried so the lint can see the SHAPE of a pack and refuse a rebuild
    that quietly reintroduces the noise a previous one removed, and so a fill or prune can
    refuse the clearly bad. Measured, never applied: filtering the file would be an adaptation
    under BY-SA and would break the sha256 that pins it.
    """
    full = round(min(GAIN_LIMIT_DB, max(-GAIN_LIMIT_DB, ANALYSIS['target_phon'] - perceived)), 1)
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
    lead = max(0, round(leading * 1000) - LEAD_KEEP_MS)
    if lead:
        index['lead'] = lead
    if mos is not None:
        index['mos'] = round(mos, 2)
    if noise is not None:
        # why: the noise sits `noise` dB under the loudest frame; a gate a few dB above it
        # quiets the hiss in the pauses and never reaches the word (kern `Playback`).
        gate = round(loudest - noise + GATE_MARGIN_DB, 1)
        if GATE_FLOOR_DB <= gate < 0:
            index['gate'] = gate
    return index


def copy_and_analyze(copies):
    """`[(id, source, target)]` → `{id: (sha256, playback index)}`: ship the bytes, then measure.

    why: the analysis runs over the file that LANDED, so an index can never describe other
    bytes than the ones its own `sha256` pins — and one batched ffmpeg pass keeps a
    thousand decodes off the converter's wall clock.
    """
    digests = {id: copy_verified(source, target) for id, source, target in copies}
    measured = audio_measure.measure_all(FFMPEG, [target for _, _, target in copies])
    analyzed = {}
    for id, _, target in copies:
        if measured[target][0] is None or measured[target][2] is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % target)
        analyzed[id] = (digests[id], playback_index(*measured[target]))
    return analyzed


def convert_words(lang, pack, out_dir, shown):
    """Every shipping `words` entry for one language, plus the printed drop list."""
    drops = []
    mp3_dir = os.path.join(pack, 'mp3')
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    reachable = keep_named_by_its_file(keep_reachable(rows, lang, shown, drops), drops)
    kept = one_per_form(attribute(keep_unambiguous(reachable, mp3_dir, drops), drops))
    analyzed = copy_and_analyze([(row['text'], pack_mp3(pack, row),
                                  os.path.join(out_dir, form_file('words', row['text'])))
                                 for row in kept])
    words = {}
    for row in kept:
        form = row['text']
        digest, index = analyzed[form]
        words[form] = entry(form_file('words', form), row['license'], row['author'],
                            row['file'], digest, index, matches=form)
    for reason, form, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, form, detail))
    counts = {reason: sum(1 for drop in drops if drop[0] == reason)
              for reason in ('unreachable', 'misnamed',
                             'collision', 'unattributable')}
    print('  %s: %d rows → %d playable (%s)' % (lang, len(rows), len(words),
          ', '.join('%d %s' % (count, reason) for reason, count in counts.items())))
    return words


def convert_articles(lang, pack, out_dir, articled):
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
    spoken = keep_named_by_its_file(keep_article_forms(rows, lang, articled, drops), drops)
    kept = one_per_form(attribute(keep_unambiguous(spoken, mp3_dir, drops), drops))
    analyzed = copy_and_analyze([(row['text'], pack_mp3(pack, row),
                                  os.path.join(out_dir, form_file('articles', row['text'])))
                                 for row in kept])
    articles = {}
    for row in kept:
        form = row['text']
        digest, index = analyzed[form]
        articles[form] = entry(form_file('articles', form), row['license'], row['author'],
                               row['file'], digest, index, matches=form, word=row['word'])
    for reason, form, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, form, detail))
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
    letter drill, and shown on no card, so the word gate — which asks what a card shows —
    can never reach them. They index by the FORM they speak, exactly
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

    Form-keyed like `texts`, and for the same reason: no card shows a weekday, so the
    word gate could never reach one.
    """
    drops = []
    # why: the authorship gate, not just the license one — a Commons row may name a bot's
    # guess at the uploader ("X assumed (based on copyright claims)"), and BY and BY-SA
    # both require naming the actual author. It is the same gate the word pack runs.
    rows = attribute(shippable(read_rows(os.path.join(pack, 'manifest.tsv')), pack), drops)
    names = {row['text']: 'calendar/' + row['local_file'] for row in rows}
    analyzed = copy_and_analyze([(row['text'], os.path.join(pack, 'mp3', row['local_file']),
                                  os.path.join(out_dir, names[row['text']])) for row in rows])
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
                                  os.path.join(out_dir, names[row['text']])) for row in rows])
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
    """Re-derive `gain`/`cap`/`lead`/`gate`/`mos` for a language already under `catalog/audio/`,
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
    moves — forms leave the catalog, an interrupted fetch leaves rows with no mp3 — while what ships is right here
    and pinned by its digests. Re-running the whole convert to gain one section would
    re-derive the other three from a workspace that can no longer produce them; this
    reads the manifest, replaces one section, and leaves the rest byte-identical.
    """
    _, articled = load_catalog()
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
        articles = convert_articles(lang, pack, out_dir, articled)
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


def fill_words(packs, languages, reseat=False):
    """Add words the shipped manifest LACKS, leaving every entry it already has untouched.

    The catalog roughly doubled after the word packs were resolved, so half of each language
    reaches no recording — but a plain re-convert is the wrong tool for that. It replaces the
    language wholesale out of a research workspace that has since moved on: the German pack's
    hiss was fixed by reseating rows onto another speaker (`consolidate-pack.py`), several
    packs carry hand-curated appended tails, and none of that survives a re-resolve. So this
    only ever ADDS. A form already in the manifest keeps its file, its digest and its credit,
    and a re-run after another catalog edit costs only the words that edit introduced.

    Pack and manifest are both keyed by the form a recording speaks. A row whose form already
    ships under the same bytes IS that recording; one under differing bytes is dropped, because
    the runtime could pick neither, so filling one word would silence another.
    """
    shown, _ = load_catalog()
    for lang in languages or sorted(name[len('pack-'):-len('-words')] for name in os.listdir(packs)
                                    if name.startswith('pack-') and name.endswith('-words')
                                    and os.path.isdir(os.path.join(packs, name))):
        pack = os.path.join(packs, 'pack-%s-words' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s-words' % (packs, lang))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        manifest = read_manifest(out_dir)
        shipped = manifest.get('words', {})
        print('pack-%s-words fill' % lang)
        if reseat:
            # why: a pack row naming another Commons file than the shipped entry is a swap
            # somebody decided (`consolidate-pack.py`, `requalify-pack.py`) — the entry goes,
            # and the fill below ships the row as it would a new word, measured afresh.
            # Run `sync-from-shipped.py` first, or a drifted pack reseats words nobody chose.
            by_form = {}
            for row in read_rows(os.path.join(pack, 'manifest.tsv')):
                by_form.setdefault(speech_key(row['text']), []).append(row)
            for key, item in sorted(shipped.items()):
                rows = by_form.get(speech_key(key), [])
                # A synced pack holds every shipped word, so a row gone is a decision too:
                # the builder re-resolved it (a take saying more than the card) and found
                # nothing shippable, and the device voice or silence takes over.
                if not rows:
                    print('  unrecord %-16s %s' % (key, item['source']))
                    os.remove(os.path.join(out_dir, item['file']))
                    del shipped[key]
                elif not any(row['file'].replace('_', ' ') == item['source'].replace('_', ' ')
                             for row in rows):
                    print('  reseat %-18s %s -> %s' % (key, item['source'], rows[0]['file']))
                    os.remove(os.path.join(out_dir, item['file']))
                    del shipped[key]

        drops = []
        mp3_dir = os.path.join(pack, 'mp3')
        # why: against what already SHIPS, not just against this batch — `keep_unambiguous`
        # only sees the new rows, and a new word claiming a shipped word's sound mutes both.
        spoken = {speech_key(form): item['sha256'] for form, item in shipped.items()}
        rows = []
        for row in shippable(read_rows(os.path.join(pack, 'manifest.tsv')), pack):
            if not os.path.isfile(pack_mp3(pack, row)):
                continue
            claimed = spoken.get(speech_key(row['text']))
            if claimed is None:
                rows.append(row)
            elif claimed != digest_of(pack_mp3(pack, row)):
                drops.append(('shipped-collision', row['text'],
                              '"%s" already ships as another file' % row['text']))
        reachable = keep_named_by_its_file(keep_reachable(rows, lang, shown, drops), drops)
        fresh = one_per_form(attribute(keep_unambiguous(reachable, mp3_dir, drops), drops))

        analyzed = copy_and_analyze([(row['text'], pack_mp3(pack, row),
                                      os.path.join(out_dir, form_file('words', row['text'])))
                                     for row in fresh])
        for row in fresh:
            form = row['text']
            digest, index = analyzed[form]
            # why: the floor is applied AFTER the copy, because `mos` is measured off the
            # bytes that landed and nothing earlier knows it. The file is then removed
            # again rather than left orphaned in the tree.
            # why: the kern parser refuses a gain past ±GAIN_LIMIT_DB, and so every test that
            # loads the catalog; such a gain is a clipped or broken file, not one to correct.
            if any(abs(index.get(field, 0)) > GAIN_LIMIT_DB for field in ('gain',)):
                drops.append(('unmeasurable', row['text'], 'gain %.1f dB is past ±%.0f — clipped'
                              % (index.get('gain', 0), GAIN_LIMIT_DB)))
                os.remove(os.path.join(out_dir, form_file('words', form)))
                continue
            # A word refused here is spoken by the device voice, as before the fill;
            # `requalify-pack.py` is how a pack finds a cleaner take of it.
            heard = is_rejected(digest, lang, row['author'])
            if heard or index.get('mos', mos_floor(lang)) < mos_floor(lang):
                drops.append(('poor', row['text'], 'heard as bad' if heard else
                              'mos %.2f, floor is %.2f' % (index['mos'], mos_floor(lang))))
                os.remove(os.path.join(out_dir, form_file('words', form)))
                continue
            balance = audio_measure.side_balance(FFMPEG, os.path.join(out_dir, form_file('words', form)))
            if is_one_sided(balance):
                drops.append(('one-sided', row['text'], '%.1f dB between its sides' % balance))
                os.remove(os.path.join(out_dir, form_file('words', form)))
                continue
            shipped[form] = entry(form_file('words', form), row['license'], row['author'],
                                  row['file'], digest, index, matches=form)
        for reason, form, detail in sorted(drops):
            print('  drop %-18s %-22s %s' % (reason, form, detail))
        added = sum(1 for row in fresh if row['text'] in shipped)
        print('  %s: %d added, %d words now' % (lang, added, len(shipped)))
        write_manifest(lang, out_dir, shipped, manifest.get('letters', {}),
                       manifest.get('texts', {}), manifest.get('articles', {}),
                       manifest.get('calendar', {}), manifest.get('countries', {}))


def credit_index(sections, where):
    """`(authors, licenses)`: who records under what, and what each license deeds to.

    A license is effectively a property of the SPEAKER — only a handful of entries across
    every shipped pack depart from their own author's usual one — so it is carried
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


def prune_poor(languages):
    """Remove every shipped recording under its language's `mos_floor` or heard as bad, file
    and entry, so the device voice or silence takes over. Run `requalify-pack.py` first, so
    such a word gets a better take where one exists."""
    root = os.path.join(CATALOG, 'audio')
    for lang in languages or sorted(os.listdir(root)):
        out_dir = os.path.join(root, lang)
        if not os.path.isdir(out_dir):
            continue
        manifest = read_manifest(out_dir)
        floor, pruned = mos_floor(lang), []
        for section in SECTIONS:
            for key, item in list(manifest.get(section, {}).items()):
                heard = is_rejected(item['sha256'], lang, item['author'])
                if heard or item.get('mos', floor) < floor:
                    os.remove(os.path.join(out_dir, item['file']))
                    del manifest[section][key]
                    pruned.append('%s/%s %s%s' % (section, key, item.get('mos', '-'),
                                                  ' (heard as bad)' if heard else ''))
        print('  %s: %d pruned under mos %.2f%s' % (lang, len(pruned), floor,
                                                   ''.join('\n    ' + p for p in pruned)))
        if pruned:
            write_manifest(lang, out_dir, manifest.get('words', {}), manifest.get('letters', {}),
                           manifest.get('texts', {}), manifest.get('articles', {}),
                           manifest.get('calendar', {}), manifest.get('countries', {}))


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
    parser.add_argument('--packs', help='directory holding pack-<lang>-words/ and its sibling packs')
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
                        help='add words pack-<lang>-words has and the shipped manifest lacks; '
                             'every entry already shipped is left exactly as it is')
    parser.add_argument('--reseat', action='store_true',
                        help='with --fill, also replace every shipped word whose pack row now '
                             'names a different Commons file')
    parser.add_argument('--prune-poor', action='store_true',
                        help="remove shipped recordings under their language's mos floor or heard as bad "
                             '(scripts/audio_voices.py); no packs needed')
    args = parser.parse_args()
    if args.prune_poor:
        return prune_poor(args.lang)
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
        return fill_words(args.packs, args.lang, args.reseat)

    if args.reindex:
        shipped = sorted(name for name in os.listdir(os.path.join(CATALOG, 'audio'))
                         if os.path.isdir(os.path.join(CATALOG, 'audio', name)))
        for lang in args.lang or shipped:
            print('reindex %s' % lang)
            reindex(lang)
        return

    shown, articled = load_catalog()
    languages = sorted(name[len('pack-'):-len('-words')] for name in os.listdir(args.packs)
                       if name.startswith('pack-') and name.endswith('-words')
                       and os.path.isdir(os.path.join(args.packs, name)))
    for lang in args.lang or languages:
        pack = os.path.join(args.packs, 'pack-%s-words' % lang)
        if not os.path.isdir(pack):
            sys.exit('%s: no pack-%s-words' % (args.packs, lang))
        print('pack-%s-words' % lang)
        # why: the convert REPLACES what ships, so a pack that cannot produce its bytes has
        # to say so before the old ones are gone. A workspace goes stale as content moves
        # (an interrupted fetch leaves a row with no mp3), and a crash halfway
        # through the rebuild used to take the shipped pack with it.
        missing = [row['text'] for row in read_rows(os.path.join(pack, 'manifest.tsv'))
                   if not os.path.isfile(pack_mp3(pack, row))]
        if missing:
            sys.exit('pack-%s-words: %d rows have no mp3 (%s) — re-fetch the pack; nothing was touched'
                     % (lang, len(missing), ', '.join(sorted(missing)[:5])))
        out_dir = os.path.join(CATALOG, 'audio', lang)
        shutil.rmtree(out_dir, ignore_errors=True)
        os.makedirs(out_dir)
        words = convert_words(lang, pack, out_dir, shown)
        letters_pack = os.path.join(args.packs, 'pack-%s-letters' % lang)
        letters = convert_letters(letters_pack, out_dir) if os.path.isdir(letters_pack) else {}
        texts_pack = os.path.join(args.packs, 'pack-%s-texts' % lang)
        texts = convert_texts(texts_pack, out_dir) if os.path.isdir(texts_pack) else {}
        articles_pack = os.path.join(args.packs, 'pack-%s-articles' % lang)
        articles = (convert_articles(lang, articles_pack, out_dir, articled)
                    if os.path.isdir(articles_pack) else {})
        calendar_pack = os.path.join(args.packs, 'pack-%s-calendar' % lang)
        calendar = convert_calendar(calendar_pack, out_dir) if os.path.isdir(calendar_pack) else {}
        countries_pack = os.path.join(args.packs, 'pack-%s-countries' % lang)
        countries = (convert_countries(countries_pack, out_dir)
                     if os.path.isdir(countries_pack) else {})
        write_manifest(lang, out_dir, words, letters, texts, articles, calendar, countries)


if __name__ == '__main__':
    main()

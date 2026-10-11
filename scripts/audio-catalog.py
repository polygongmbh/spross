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
`lead`/`gate` playback fields — see `audio_index.ANALYSIS`, which also decides the players' scheme.
Reading, writing and editing one shipped manifest is `audio_manifest.py`.

Deterministic: sorted keys, 2-space indent, unchanged packs give byte-identical output.
"""
import argparse
import concurrent.futures
import csv
import os
import shutil
import sys

import audio_judge
import audio_manifest
import audio_measure
from audio_index import ANALYSIS, FFMPEG, INDEX_FIELDS, playback_index
from audio_gates import (APOSTROPHES, UNSHIPPABLE_LICENSES, attribute, digest_of,
                         keep_article_forms, keep_named_by_its_file, keep_reachable,
                         keep_unambiguous, speech_key, spoken_target_form)
# why: workspace scripts load this file by path and read these names off it.
from audio_manifest import (AUDIO, ROOT, SECTIONS, ascii_stem, audio_dir, copy_and_analyze, entry,
                            form_file, letter_file, read_json, read_manifest, write_manifest)

CATALOG = os.path.join(ROOT, 'catalog')


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
    Files are named after the form they speak (`form_file`), never the pack's own name.
    """
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    names = {row['text']: form_file('texts', row['text']) for row in rows}
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
    names = {row['text']: form_file('calendar', row['text']) for row in rows}
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
    names = {row['text']: form_file('countries', row['text']) for row in rows}
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
        out_dir = audio_dir(lang)
        manifest = read_manifest(out_dir)
        print('pack-%s-articles' % lang)
        shutil.rmtree(os.path.join(out_dir, 'articles'), ignore_errors=True)
        articles = convert_articles(lang, pack, out_dir, articled)
        write_manifest(out_dir, dict(manifest, articles=articles))


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
        out_dir = audio_dir(lang)
        manifest = read_manifest(out_dir)
        print('pack-%s-calendar' % lang)
        shutil.rmtree(os.path.join(out_dir, 'calendar'), ignore_errors=True)
        calendar = convert_calendar(pack, out_dir)
        write_manifest(out_dir, dict(manifest, calendar=calendar))


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
        out_dir = audio_dir(lang)
        manifest = read_manifest(out_dir)
        print('pack-%s-countries' % lang)
        shutil.rmtree(os.path.join(out_dir, 'countries'), ignore_errors=True)
        countries = convert_countries(pack, out_dir)
        write_manifest(out_dir, dict(manifest, countries=countries))


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
        out_dir = audio_dir(lang)
        manifest = read_manifest(out_dir)
        shipped = manifest.get('words', {})
        print('pack-%s-words fill' % lang)
        held = {}
        if reseat:
            # why: a pack row naming another Commons file than the shipped entry is a swap
            # somebody decided (`consolidate-pack.py`, `requalify-pack.py`) — the entry is set
            # aside, and the fill below ships the row as it would a new word, measured afresh.
            # Only once the replacement passes every gate does the old take go; one refused
            # puts it back, so a reseat never leaves a word without the recording it had.
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
                    os.rename(os.path.join(out_dir, item['file']),
                              os.path.join(out_dir, item['file'] + '.held'))
                    held[key] = item
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
            # why: judged AFTER the copy, because `mos` is measured off the bytes that landed
            # and nothing earlier knows it; a refused file is removed again rather than left
            # orphaned in the tree. A word refused here is spoken by the device voice, as
            # before the fill; `requalify-pack.py` is how a pack finds a cleaner take of it.
            path = os.path.join(out_dir, form_file('words', form))
            verdict = audio_judge.judge(dict(lang=lang, section='words', key=form, source=row['file'],
                                             author=row['author'], license=row['license'],
                                             sha256=digest, path=path), index)
            if verdict.refused:
                drops.append((verdict.refused[0], form, verdict.refused[1]))
                os.remove(path)
                continue
            shipped[form] = entry(form_file('words', form), row['license'], row['author'],
                                  row['file'], digest, index, matches=form)
        landed = {speech_key(form) for form in shipped}
        for key, item in held.items():
            old = os.path.join(out_dir, item['file'])
            if speech_key(key) in landed:
                os.remove(old + '.held')
            else:
                if os.path.exists(old):
                    os.remove(old)
                os.rename(old + '.held', old)
                shipped[key] = item
                drops.append(('reseat-refused', key, 'keeps %s' % item['source']))
        for reason, form, detail in sorted(drops):
            print('  drop %-18s %-22s %s' % (reason, form, detail))
        added = sum(1 for row in fresh if row['text'] in shipped)
        print('  %s: %d added, %d words now' % (lang, added, len(shipped)))
        write_manifest(out_dir, dict(manifest, words=shipped))


def shipped_take(lang, section, key, item, out_dir):
    """A shipped entry as the judge reads a take, its bytes where they ship."""
    return dict(lang=lang, section=section, key=key, source=item['source'], author=item['author'],
                license=item['license'], sha256=item['sha256'], path=os.path.join(out_dir, item['file']))


def prune_poor(languages):
    """Remove every shipped recording the take judge refuses (`audio_judge.py`) — under its
    language's `mos_floor`, heard as bad, one-sided — file and entry, so the device voice or
    silence takes over. Run `requalify-pack.py` first, so such a word gets a better take where
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
                        help='remove shipped recordings the take judge refuses — under their '
                             "language's mos floor, heard as bad, one-sided (scripts/audio_judge.py); "
                             'no packs needed')
    parser.add_argument('--restore', nargs=3, metavar=('LANG', 'SECTION', 'KEY'),
                        help='put back the entry and bytes KEY held at --from REV')
    parser.add_argument('--from', dest='rev', metavar='REV', help='the git revision --restore reads')
    parser.add_argument('--remove', nargs=3, metavar=('LANG', 'SECTION', 'KEY'),
                        help='drop one shipped entry and its file')
    args = parser.parse_args()
    if args.restore:
        if not args.rev:
            parser.error('--restore needs --from REV')
        item = audio_manifest.restore(*args.restore, args.rev)
        return print('  restore %s %s' % (args.restore[2], item['source']))
    if args.remove:
        return print('  remove %s %s' % (args.remove[2], audio_manifest.remove(*args.remove)['source']))
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
        shipped = sorted(name for name in os.listdir(AUDIO)
                         if os.path.isdir(os.path.join(AUDIO, name)))
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
        out_dir = audio_dir(lang)
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
        write_manifest(out_dir, {'language': lang, 'words': words, 'letters': letters, 'texts': texts,
                                 'articles': articles, 'calendar': calendar, 'countries': countries})


if __name__ == '__main__':
    main()

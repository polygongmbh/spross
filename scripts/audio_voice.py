"""One Lingua Libre speaker's takes of a section's keys, shipped beside what is there: an alternate
where the key ships another voice, its first take where it ships nothing yet.

A letter is looked for by its name, and by its glyph only where the title glosses it
(`LL-Q150 (fra)-Sartus85-h (lettre).wav`) — a bare glyph is as likely a word (it `e`, "and").
Every other section is looked for by the forms it would ship. Every take passes the take judge
on its way in (`audio_manifest.put`).
"""
import re
import sys
import unicodedata

import audio_commons
import audio_lingualibre
import audio_manifest
from audio_forms import catalog_forms, letter_rows
from audio_gates import speech_key, takes
from audio_routes import route


def spoken(lang, section):
    """{lowercased word a title may end in: the key it would ship under}."""
    if section == 'letters':
        out = {}
        for glyph, _, name in letter_rows(lang):
            out[glyph.lower()] = glyph
            out[unicodedata.normalize('NFC', name).lower()] = glyph
        return out
    return {form.lower(): form for _, form in catalog_forms(lang, section)}


def glossed(name, word):
    """Whether [name] titles [word] with a parenthesized gloss after it."""
    return re.search(r'-%s ?\([^()]*\)\.\w+$' % re.escape(word), unicodedata.normalize('NFC', name),
                     re.IGNORECASE) is not None


def found(lang, section, speaker):
    """{key: Commons file} — [speaker]'s take of each key of [section], the first one listed."""
    code = route(lang).get('lingualibre')
    if not code:
        sys.exit('%s: no Lingua Libre category in scripts/audio_routes.py' % lang)
    words = spoken(lang, section)
    names = {glyph: unicodedata.normalize('NFC', name).lower() for glyph, _, name in letter_rows(lang)} \
        if section == 'letters' else {}
    hits = audio_lingualibre.candidates(audio_lingualibre.files(code), code, set(words))
    out = {}
    for word, listed in sorted(hits.items()):
        key = words[word]
        for who, name in listed:
            if who != speaker or (key in names and word != names[key] and not glossed(name, word)):
                continue
            out.setdefault(key, name)
    return out


def add_voice(lang, section, speaker, dry_run):
    """Ships [speaker]'s takes of [section] where that voice does not say the key yet."""
    names = found(lang, section, speaker)
    shipped = audio_manifest.read_manifest(audio_manifest.audio_dir(lang)).get(section, {})
    by_sound = {speech_key(key): key for key in shipped}
    records = audio_commons.meta(sorted(set(names.values())))
    puts = []
    for key, name in sorted(names.items()):
        held = by_sound.get(speech_key(key))
        record = records.get(name, {})
        if held and any(take['author'] == record.get('author') for take in takes(shipped[held])):
            continue
        url = audio_commons.transcode_url(record.get('url', ''))
        try:
            path = url and audio_commons.fetched(name, url)
        except Exception as error:
            print('  next   %-18s (fetch failed: %s)' % (key, str(error)[:80]))
            continue
        if not path:
            print('  next   %-18s (no transcode of %s)' % (key, name))
            continue
        puts.append(dict(section=section, key=held or key, mp3=path, source=name, author=record['author'],
                         license=record['license'], alternate=bool(held)))
    outcome = audio_manifest.put(lang, puts, dry_run) if puts else {}
    for take in puts:
        why, mos = outcome[(section, take['key'])]
        print('  %-7s %-18s %s%s' % ('refuse' if why else 'beside' if take['alternate'] else 'first',
                                     take['key'], take['source'], ' — %s: %s' % why if why else ''))
    added = sum(1 for why, _ in outcome.values() if why is None)
    print('%s %s: %s found %d, %d %s%s' % (lang, section, speaker, len(names), added,
                                          'would ship' if dry_run else 'shipped', ', nothing written' if dry_run else ''))

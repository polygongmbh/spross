#!/usr/bin/env python3
"""Generate catalog/audio/<lang>/ from the pronunciation packs.

    scripts/audio-catalog.py --packs ../data/reference/audio

The packs (`pack-<lang>/manifest.tsv` + slug-named `mp3/`, plus `pack-uk-letters`
for the alphabet) are unversioned research input; `catalog/audio/` is the versioned
provenance record. What ships is the Wikimedia Commons transcode UNTOUCHED —
re-encoding is an adaptation under BY-SA — so every entry carries the sha256 this
script verified after the copy, and lint re-hashes what was committed. Edit packs,
never `catalog/audio/`.

A pack row ships only once it survives four gates, each decision printed:
  · the catalog knows the slug AND the language realizes it — packs go stale as
    content moves, and a manifest entry for a word nobody studies is dead weight;
  · the recording SPEAKS a form the card can show: `speechKey(matched_word)` has to
    equal the key of `text`, a synonym or a variant, because lookup is keyed by what
    stands on the card. This is what drops the sw `ku-` verbs, whose recordings say
    the bare stem — playing "wasilisha" for "kuwasilisha" would teach the wrong word,
    while punctuation ("Hujambo!") and the citation dash ("-zuri") fold away and stay;
  · no two entries claim one speech key with differing bytes: the runtime cannot pick
    between de `husten` cough/to-cough, so the first slug wins and the others lose a
    credit line, not a sound;
  · the author names somebody. "Own work"/"myself" credit nobody while BY and BY-SA
    both require naming, so those rows are re-resolved against the Commons API and
    dropped only when even that comes back empty.

Deterministic: sorted keys, 2-space indent, unchanged packs give byte-identical output.
"""
import argparse
import csv
import hashlib
import html
import json
import os
import re
import shutil
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'catalog')

API = 'https://commons.wikimedia.org/w/api.php'
UA = 'duolernen-audio-catalog/1.0 (educational vocab app; contact feedback@spross.net)'

# Authorship values that name nobody. Matched trimmed and case-insensitively, and
# ONLY as a whole: `User:Tosca` is a name Commons can resolve, not a placeholder.
JUNK_AUTHORS = {'own work', 'myself', ''}

# Every licence the packs actually carry → its canonical deed. An unlisted one is a
# hard stop: the credits screen links what it names, and PD has no deed to link.
LICENCE_URLS = {
    'CC BY-SA 4.0': 'https://creativecommons.org/licenses/by-sa/4.0/',
    'CC BY-SA 3.0': 'https://creativecommons.org/licenses/by-sa/3.0/',
    'CC BY-SA 2.5': 'https://creativecommons.org/licenses/by-sa/2.5/',
    'CC BY 3.0 us': 'https://creativecommons.org/licenses/by/3.0/us/',
    'CC BY 3.0': 'https://creativecommons.org/licenses/by/3.0/',
    'CC BY 2.0 fr': 'https://creativecommons.org/licenses/by/2.0/fr/',
    'Public domain': None,
}

# Kept in step with kern's speechKey (kern/README.md §11) — the index this script
# writes and the lookup that reads it have to fold the same things away.
EDGE_PUNCTUATION = '!?.,;:…"\'«»„“”‘’‹›'


def speech_key(form):
    """Port of kern's `speechKey`: strip a leading stem dash and edge punctuation, NFC, lower."""
    stem = form.strip()
    if stem.startswith('-'):
        stem = stem[1:]
    while stem and (stem[0].isspace() or stem[0] in EDGE_PUNCTUATION):
        stem = stem[1:]
    while stem and (stem[-1].isspace() or stem[-1] in EDGE_PUNCTUATION):
        stem = stem[:-1]
    return unicodedata.normalize('NFC', stem).lower()


def read_json(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return json.load(f)


def read_rows(path):
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def digest_of(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


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


def entry(file, licence, author, source, digest, matches=None):
    """One manifest value; `licenceUrl` is absent exactly where there is no deed."""
    record = {'file': file, 'licence': licence, 'author': author,
              'source': source, 'sha256': digest}
    if matches is not None:
        record['matches'] = matches
    url = licence_url(licence, source)
    if url:
        record['licenceUrl'] = url
    return record


def keep_reachable(rows, lang, slugs, forms, drops):
    """Gates 1 and 2: the catalog still knows the row, and the recording speaks a visible form."""
    realized = forms.get(lang, {})
    kept = []
    for row in rows:
        slug, spoken = row['slug'], row['matched_word']
        if slug not in slugs:
            drops.append(('unknown-slug', slug, 'no such concept in the catalog'))
        elif slug not in realized:
            drops.append(('unrealized', slug, 'the catalog knows it, %s does not say it' % lang))
        elif speech_key(spoken) not in {speech_key(form) for form in realized[slug]}:
            drops.append(('unreachable', slug,
                          'recording says "%s", the card shows "%s"' % (spoken, realized[slug][0])))
        else:
            kept.append(row)
    return kept


def keep_unambiguous(rows, mp3_dir, drops):
    """Gate 3: one speech key, one sound. Byte-identical twins stay; homographs lose the later slug."""
    groups = {}
    for row in rows:
        groups.setdefault(speech_key(row['matched_word']), []).append(row)
    kept = []
    for key, group in sorted(groups.items()):
        digests = {row['slug']: digest_of(os.path.join(mp3_dir, row['slug'] + '.mp3')) for row in group}
        if len(set(digests.values())) == 1:
            kept += group
            continue
        winner = min(digests)
        for row in sorted(group, key=lambda r: r['slug']):
            if digests[row['slug']] == digests[winner]:
                kept.append(row)
            else:
                drops.append(('collision', row['slug'], '"%s" is already %s\'s sound' % (key, winner)))
    return kept


def commons_authors(sources):
    """Commons filename → attribution, for rows whose pack authorship names nobody."""
    resolved = {}
    for start in range(0, len(sources), 50):
        batch = sources[start:start + 50]
        query = urllib.parse.urlencode({
            'action': 'query', 'format': 'json', 'prop': 'imageinfo',
            'iiprop': 'user|extmetadata', 'titles': '|'.join('File:' + name for name in batch),
        })
        request = urllib.request.Request(API + '?' + query, headers={'User-Agent': UA})
        with urllib.request.urlopen(request, timeout=90) as response:
            pages = json.load(response)['query']['pages']
        for page in pages.values():
            info = (page.get('imageinfo') or [{}])[0]
            raw = info.get('extmetadata', {}).get('Artist', {}).get('value') or ''
            artist = ' '.join(html.unescape(re.sub('<[^>]+>', ' ', raw)).split())
            # why: the uploader is a weaker credit than the stated author, but a real
            # one — Commons attributes to the account when the file states nothing.
            if artist.lower() in JUNK_AUTHORS:
                artist = 'Wikimedia Commons user %s' % info['user'] if info.get('user') else ''
            resolved[page['title'].removeprefix('File:').replace('_', ' ')] = artist
        time.sleep(0.5)
    return resolved


def attribute(rows, drops):
    """Gate 4: re-resolve placeholder authorship against Commons, drop what stays anonymous."""
    def unnamed(author):
        return author.strip().lower() in JUNK_AUTHORS

    files = sorted({row['file'] for row in rows if unnamed(row['author'])})
    if not files:
        return rows
    print('  resolving %d unattributed file(s) against Commons…' % len(files))
    resolved = commons_authors(files)
    kept = []
    for row in rows:
        author = resolved.get(row['file'].replace('_', ' '), '') if unnamed(row['author']) else row['author']
        if unnamed(author):
            drops.append(('unattributable', row['slug'], '%s credits nobody' % row['file']))
        else:
            kept.append(dict(row, author=author))
    return kept


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


def convert_words(lang, pack, out_dir, slugs, forms):
    """Every shipping `words` entry for one language, plus the printed drop list."""
    drops = []
    mp3_dir = os.path.join(pack, 'mp3')
    rows = read_rows(os.path.join(pack, 'manifest.tsv'))
    kept = attribute(keep_unambiguous(keep_reachable(rows, lang, slugs, forms, drops), mp3_dir, drops), drops)
    words = {}
    for row in kept:
        name = row['slug'] + '.mp3'
        digest = copy_verified(os.path.join(mp3_dir, name), os.path.join(out_dir, name))
        words[row['slug']] = entry(name, row['licence'], row['author'], row['file'],
                                   digest, matches=row['matched_word'])
    for reason, slug, detail in sorted(drops):
        print('  drop %-15s %-22s %s' % (reason, slug, detail))
    counts = {reason: sum(1 for drop in drops if drop[0] == reason)
              for reason in ('unknown-slug', 'unrealized', 'unreachable', 'collision', 'unattributable')}
    print('  %s: %d rows → %d playable (%s)' % (lang, len(rows), len(words),
          ', '.join('%d %s' % (count, reason) for reason, count in counts.items())))
    return words


def convert_letters(pack, out_dir):
    """The alphabet section: codepoint-named files, because glyph names decompose on APFS."""
    letters = {}
    for row in read_rows(os.path.join(pack, 'manifest.tsv')):
        glyph = row['letter']
        name = 'letters/u%04x.mp3' % ord(glyph)
        digest = copy_verified(os.path.join(pack, 'mp3', row['local_file']),
                               os.path.join(out_dir, name))
        letters[glyph] = entry(name, row['licence'], row['author'], row['file'], digest)
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

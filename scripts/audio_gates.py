#!/usr/bin/env python3
"""Which pack rows may ship, and who gets credited — the four gates of `audio-catalog.py`.

A pack row ships only once it survives all four, each decision printed by the caller:
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

Every gate appends its rejections to a shared `drops` list rather than printing: the
caller owns the report, and the gates stay pure enough to chain.
"""
import hashlib
import html
import json
import os
import re
import time
import unicodedata
import urllib.parse
import urllib.request

API = 'https://commons.wikimedia.org/w/api.php'
UA = 'duolernen-audio-catalog/1.0 (educational vocab app; contact feedback@spross.net)'

# Authorship values that name nobody. Matched trimmed and case-insensitively, and
# ONLY as a whole: `User:Tosca` is a name Commons can resolve, not a placeholder.
JUNK_AUTHORS = {'own work', 'myself', ''}

# Kept in step with kern's speechKey (kern/README.md §11) — the index this script
# writes and the lookup that reads it have to fold the same things away.
EDGE_PUNCTUATION = '!?¡¿.,;:…"\'«»„“”‘’‹›'


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


def digest_of(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


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

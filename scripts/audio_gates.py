#!/usr/bin/env python3
"""Which pack rows may ship, and who gets credited — the gates of `audio-catalog.py`.

A pack row ships only once it survives all of them, each decision printed by the caller:
  · the catalog knows the slug AND the language realizes it — packs go stale as
    content moves, and a manifest entry for a word nobody studies is dead weight;
  · the recording SPEAKS a form the card can show: `speechKey(matched_word)` has to
    equal the key of `text`, a synonym or a variant, because lookup is keyed by what
    stands on the card. This is what drops the sw `ku-` verbs, whose recordings say
    the bare stem — playing "wasilisha" for "kuwasilisha" would teach the wrong word,
    while punctuation ("Hujambo!") and the citation dash ("-zuri") fold away and stay;
  · an ARTICLE row speaks its realization's own article in front of the canonical word,
    the one string a card showing that article ever asks for — a row saying a different
    article would teach the gender wrong, which is what these recordings exist to fix;
  · a Lingua Libre filename ENDS in the word its row claims — that grammar puts the
    speaker and the word in one dash-joined string, so a compound like `Earl-Grey-Tee`
    can be read as a recording of "Tee" by anything that guesses the boundary. Two such
    files shipped before this gate existed;
  · no two entries claim one speech key with differing bytes: the runtime cannot pick
    between de `husten` cough/to-cough, so the first slug wins and the others lose a
    credit line, not a sound;
  · the author names somebody. "Own work"/"myself" credit nobody, and Commons' "X assumed
    (based on copyright claims)" credits a bot's guess at the uploader, while BY and BY-SA
    both require naming — so those rows are re-resolved against the Commons API and
    dropped only when even that comes back with no name of its own.

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
UA = 'spross-audio-catalog/1.0 (educational vocab app; contact spross@polygon.gmbh)'

# Authorship values that name nobody. Matched trimmed and case-insensitively, and
# ONLY as a whole: `User:Tosca` is a name Commons can resolve, not a placeholder.
JUNK_AUTHORS = {'own work', 'myself', ''}

# Commons' wording for a file whose authorship nobody recorded: the name it carries is
# the UPLOADER, inferred by a bot from the copyright tag, and the page says as much. A BY
# or BY-SA notice has to name the author, not a guess about them — so this reads as a
# placeholder however much it looks like a credit, and takes the same path.
ASSUMED_AUTHOR = re.compile(r'assumed \(based on copyright claims\)', re.IGNORECASE)

# Kept in step with kern's speechKey (kern/docs/audio.md) — the index this script
# writes and the lookup that reads it have to fold the same things away.
EDGE_PUNCTUATION = '!?¡¿.,;:…"\'«»„“”‘’‹›'

# The inner apostrophe class, folded to U+02BC exactly as kern folds it: Commons titles
# French elision with U+2019 while the catalog writes U+0027, and the two must key one sound.
APOSTROPHES = '\u0027\u2019\u02bc'


def apostrophe_folded(text):
    return ''.join('\u02bc' if char in APOSTROPHES else char for char in text)


def speech_key(form):
    """Port of kern's `speechKey`: strip a leading stem dash and edge punctuation, NFC,
    lower, fold the inner apostrophe class to U+02BC."""
    stem = form.strip()
    if stem.startswith('-'):
        stem = stem[1:]
    while stem and (stem[0].isspace() or stem[0] in EDGE_PUNCTUATION):
        stem = stem[1:]
    while stem and (stem[-1].isspace() or stem[-1] in EDGE_PUNCTUATION):
        stem = stem[:-1]
    return apostrophe_folded(unicodedata.normalize('NFC', stem).lower())


def digest_of(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


def spoken_target_form(article, text):
    """Port of kern's `spokenTargetForm` for a card showing its canonical word.

    An ELIDED article writes onto its noun — `l'acqua`, never "l' acqua" — because the
    apostrophe is the join, and because that is the string the recording is titled with.
    """
    article = (article or '').strip()
    if not article:
        return text.strip()
    if article[-1] in APOSTROPHES:
        return article + text.strip()
    return '%s %s' % (article, text.strip())


def keep_article_forms(rows, lang, targets, forms, drops):
    """The ARTICLE gate: the row says the realization's own article in front of one of its
    forms — and the row records WHICH form, so one file can answer either way it is asked.

    The article has to be the authored one because a recording is the only thing on the card
    that can teach a gender, and a wrong one teaches it wrong. The WORD it stands in front of
    may be any form the realization carries, not only the canonical text: a recording of
    "der Großvater" is a perfectly good recording of "Großvater", and dropping it for not
    being "der Opa" throws away a file the card could still use.

    What a card asks with is a separate question, answered at lookup: the article form is
    preferred, the bare word answers too. That is why 33 of the catalog's 90 rotatable
    synonyms disagreeing in gender costs nothing here — a recording only ever answers the
    form it actually speaks.
    """
    kept = []
    for row in rows:
        target = targets.get(lang, {}).get(row['slug'])
        if not target:
            drops.append(('unrealized', row['slug'], 'no gendered realization in %s' % lang))
            continue
        article, text = target
        said = speech_key(row['matched_word'])
        spoken = next((form for form in forms.get(lang, {}).get(row['slug'], [])
                       if speech_key(spoken_target_form(article, form)) == said), None)
        if spoken is None:
            drops.append(('not-the-article', row['slug'],
                          'recording says "%s", not "%s" in front of any form it has'
                          % (row['matched_word'], article)))
        else:
            kept.append(dict(row, word=spoken))
    return kept


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


LINGUA_LIBRE = re.compile(r"^LL-Q\d+ ?\([a-z]{3}\)-(?P<rest>.+)\.(?:wav|ogg|flac|mp3)$")


def keep_named_by_its_file(rows, drops):
    """Gate 5: a Lingua Libre filename must be exactly `<the credited speaker>-<the word>`.

    Lingua Libre names a file `<speaker>-<word>` and both halves may carry hyphens, so the
    boundary is a guess — one the resolver used to make by cutting wherever the tail matched
    a word it was looking for. That let every compound answer to its last noun:
    `Mighty Wire-Earl-Grey-Tee` shipped as "Tee" and `Frank C. Müller-1-Raum-Wohnung` as
    "Wohnung", so the card showed one word while the voice said another. Both reached users.

    Checking the TAIL is not enough, and that is the trap: `…müller-1-raum-wohnung` does end
    in "-wohnung". What pins the boundary is the `author` the Commons API returned — an
    independent witness, not a re-parse of the same string. Where it prefixes the filename,
    the rest must be the word and nothing else.

    Skipped where the credit was normalised away from the filename ("Alejandra
    (LinguaLibreBooth)" credits as "Alejandra"): there is then no anchor, and inventing one
    would drop good rows. Convention-named files (`De-Nacht.ogg`) are exempt outright —
    their title was CONSTRUCTED from the word, so there is no boundary to disagree about.
    """
    kept = []
    for row in rows:
        match = LINGUA_LIBRE.match(unicodedata.normalize("NFC", row["file"]))
        author = apostrophe_folded(row["author"].strip().lower())
        # why: the tail is apostrophe-folded like the speech key it is compared against —
        # Commons titles French elision with U+2019 while the catalog writes U+0027.
        rest = apostrophe_folded(unicodedata.normalize("NFC", match.group("rest")).lower()) if match else ""
        if not match or not author or not rest.startswith(author + "-"):
            kept.append(row)
        elif rest[len(author) + 1:] == speech_key(row["matched_word"]):
            kept.append(row)
        else:
            drops.append(("misnamed", row["slug"], '%s is not %s saying "%s"'
                          % (row["file"], row["author"], row["matched_word"])))
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
        return author.strip().lower() in JUNK_AUTHORS or bool(ASSUMED_AUTHOR.search(author))

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

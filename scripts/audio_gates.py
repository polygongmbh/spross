#!/usr/bin/env python3
"""Which pack rows may ship, and who gets credited — the gates of `audio-catalog.py`.

A pack row is keyed by the form it SPEAKS (`text`), like the shipped manifest, so whether
that form is a concept's text, a synonym or a tagged form is the catalog's business and never
the audio's. A row ships only once it survives all of these, each decision printed by the caller:
  · some card in its language shows the form it speaks (`speechKey` equal to a text, teaches,
    accepts or tagged form, or the bare stem of one) — packs go stale as content moves, and
    lookup is keyed by what stands on the card; punctuation ("Hujambo!") and the citation
    dash ("-zuri") fold away;
  · an ARTICLE row speaks the article some card shows in front of that word — a realization's
    own gender or a tagged form's authored article; a different article would teach the
    gender wrong, which is what these recordings exist to fix;
  · a Lingua Libre filename ENDS in the word its row claims — that grammar puts the
    speaker and the word in one dash-joined string, so a compound like `Earl-Grey-Tee`
    can be read as a recording of "Tee" by anything that guesses the boundary. Two such
    files shipped before this gate existed;
  · no two rows claim one speech key with differing bytes: the runtime cannot pick
    between two takes of de `Morgen`/`morgen`, so the first row wins;
    byte-identical twins are one recording and ship once;
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


def stress_free(text):
    """`text` without U+0301, the stress mark a catalog writes where NFC leaves it standing
    (uk `пі́вніч`): Commons titles never carry it, so a title is compared without it."""
    return text.replace('\u0301', '')


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


def keep_article_forms(rows, lang, articled, drops):
    """The ARTICLE gate: the row says an article some card shows in front of a word
    (`articled`: speech key of "die Lehrerin" -> "Lehrerin"), and records WHICH word, so the
    one file answers the card asking with the article and the card asking without it.

    The article has to be one a card shows because a recording is the only thing on the card
    that can teach a gender, and a wrong one teaches it wrong.
    """
    kept = []
    for row in rows:
        word = articled.get(lang, {}).get(speech_key(row['text']))
        if word is None:
            drops.append(('not-the-article', row['text'], 'no card shows "%s"' % row['text']))
        else:
            kept.append(dict(row, word=word))
    return kept


def keep_reachable(rows, lang, shown, drops):
    """Some card in `lang` shows the form the row speaks (`shown`: speech keys, see `load_catalog`)."""
    kept = []
    for row in rows:
        if speech_key(row['text']) in shown.get(lang, set()):
            kept.append(row)
        else:
            drops.append(('unreachable', row['text'], 'no card shows "%s"' % row['text']))
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
        elif rest[len(author) + 1:] == stress_free(speech_key(row["text"])):
            kept.append(row)
        else:
            drops.append(("misnamed", row["text"], '%s is not %s saying "%s"'
                          % (row["file"], row["author"], row["text"])))
    return kept


def keep_unambiguous(rows, mp3_dir, drops):
    """Gate 3: one speech key, one sound. Byte-identical twins stay; of differing takes the first row wins."""
    groups = {}
    for row in rows:
        groups.setdefault(speech_key(row['text']), []).append(row)
    kept = []
    for key, group in sorted(groups.items()):
        digests = {row['local_file']: digest_of(os.path.join(mp3_dir, row['local_file'])) for row in group}
        if len(set(digests.values())) == 1:
            kept += group
            continue
        winner = min(digests)
        for row in sorted(group, key=lambda r: r['local_file']):
            if digests[row['local_file']] == digests[winner]:
                kept.append(row)
            else:
                drops.append(('collision', row['text'], '"%s" is already %s\'s sound' % (key, winner)))
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
            drops.append(('unattributable', row.get('text', '?'),
                          '%s credits nobody' % row['file']))
        else:
            kept.append(dict(row, author=author))
    return kept

"""Wikimedia Commons, asked one way: one `get` with one retry policy and one User-Agent.

What a file says it is (license, speaker, transcode URL) and what it says (a Shtooka page's
{{Pronunciation}}) are cached in the workspace by filename, because neither changes under a file
and re-asking for every candidate is what Commons throttles; a transcode fetched once is kept in
`fetch-cache/` by its Commons name, so a rerun of a sweep fetches only what it has not seen.
"""
import html
import http.client
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from audio_routes import WORKSPACE

API = 'https://commons.wikimedia.org/w/api.php'
UA = 'spross-audio/1.0 (educational vocab app; contact spross@polygon.gmbh)'
# A dropped network or a throttling Commons is waited out this long before an item counts as failed.
PATIENCE_S = 15 * 60
BACKOFF_CAP_S = 60
TIMEOUT_S = 60
RETRIED_STATUS = (429, 500, 502, 503, 504)


def get(url, params=None):
    """The body at [url] (with [params] as its query), or None for a 404.

    A connection error, a timeout, a 429 or a 5xx — and the API's own `maxlag` refusal — is
    retried: as long as `Retry-After` asks, else backing off from 1 s to BACKOFF_CAP_S, until
    PATIENCE_S have passed; then the last error stands.
    """
    full = url + ('?' + urllib.parse.urlencode(params) if params else '')
    request = urllib.request.Request(full, headers={'User-Agent': UA})
    started, attempt = time.monotonic(), 0
    while True:
        asked = None
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_S) as response:
                body = response.read()
                lagged = response.headers.get('X-Database-Lag')
                asked = response.headers.get('Retry-After')
            if not lagged:
                return body
            error = RuntimeError('maxlag %s s' % lagged)
        except urllib.error.HTTPError as failed:
            if failed.code == 404:
                return None
            if failed.code not in RETRIED_STATUS:
                raise
            error, asked = failed, failed.headers.get('Retry-After')
        except (OSError, http.client.HTTPException) as failed:
            error = failed
        wait = float(asked) if asked and asked.isdigit() else min(2 ** attempt, BACKOFF_CAP_S)
        if time.monotonic() - started + wait > PATIENCE_S:
            raise error
        print('  commons: %s — retrying in %.0f s' % (str(error)[:80], wait), file=sys.stderr)
        time.sleep(wait)
        attempt += 1


def api(**params):
    """One `action=query` against the Commons API, parsed."""
    body = get(API, dict(params, action='query', format='json', maxlag='5'))
    return json.loads(body) if body is not None else {}


def transcode_url(url):
    """The mp3 Commons renders of a file at [url] — iOS plays no ogg, flac or wav."""
    if not url:
        return ''
    base, tail = url.rsplit('/', 1)
    return base.replace('/commons/', '/commons/transcoded/') + '/%s/%s.mp3' % (tail, tail)


def credited_author(artist_html, uploader):
    """The name a BY/BY-SA notice has to carry: Lingua Libre's "Speaker: X / Recorder: Y" is the
    speaker, any other Artist field as written, and the uploader where it states nobody."""
    text = ' '.join(html.unescape(re.sub(r'<[^>]+>', ' ', artist_html or '')).split())
    speaker = re.match(r'Speaker:\s*(.+?)(?:\s+Recorder:|$)', text)
    return (speaker.group(1) if speaker else text) or uploader


def _cache(name):
    path = os.path.join(WORKSPACE, name)
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            return path, json.load(f)
    return path, {}


def _save(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False)


_META = None


def meta(files):
    """filename -> {license, license_url, author, url}, 50 files a request, cached in
    `commons-meta-cache.json`; a file Commons does not answer for maps to {} and is asked again."""
    global _META
    if _META is None:
        _META = _cache('commons-meta-cache.json')
    path, cache = _META
    todo = sorted({f for f in files if f not in cache})
    for i in range(0, len(todo), 50):
        pages = api(prop='imageinfo', iiprop='url|user|extmetadata',
                    titles='|'.join('File:' + f for f in todo[i:i + 50])).get('query', {}).get('pages', {})
        for page in pages.values():
            info = (page.get('imageinfo') or [{}])[0]
            em = info.get('extmetadata', {})
            record = {'license': em.get('LicenseShortName', {}).get('value', '?'),
                      'license_url': em.get('LicenseUrl', {}).get('value', ''),
                      'author': credited_author(em.get('Artist', {}).get('value'), info.get('user', '?')),
                      # why: Commons appends `?utm_source=…` to the url, which a transcode path cannot carry.
                      'url': info.get('url', '').split('?', 1)[0]}
            title = page['title'].removeprefix('File:')
            # why: Commons answers in spaces; category listings and dumps differ on the underscore.
            cache[title] = cache[title.replace(' ', '_')] = record
        print('  metadata %d/%d' % (min(i + 50, len(todo)), len(todo)), file=sys.stderr)
        _save(path, {f: r for f, r in cache.items() if r['url']})
    return {f: cache.get(f, {}) for f in files}


_SPOKEN = None
PRONUNCIATION_SAYS = re.compile(r'\{\{Pronunciation\|[^}]*?translation=([^|}]+)')


def spoken_forms(files):
    """filename -> what the file SAYS where its page states more than its title, else "".

    Shtooka recorded French nouns with a determiner (`Fr-crème.ogg` says "de la crème") and
    states it only in the page's {{Pronunciation}} template; Lingua Libre titles say the form
    outright, so only other files are asked. Cached in `commons-spoken-cache.json`.
    """
    global _SPOKEN
    if _SPOKEN is None:
        _SPOKEN = _cache('commons-spoken-cache.json')
    path, cache = _SPOKEN
    todo = sorted({f for f in files if f not in cache and not f.startswith('LL-')})
    for i in range(0, len(todo), 50):
        query = api(prop='revisions', rvprop='content', rvslots='main',
                    titles='|'.join('File:' + f for f in todo[i:i + 50])).get('query', {})
        asked = {n['to']: n['from'] for n in query.get('normalized', [])}
        for page in query.get('pages', {}).values():
            title = asked.get(page['title'], page['title']).removeprefix('File:')
            text = (page.get('revisions') or [{}])[0].get('slots', {}).get('main', {}).get('*', '')
            says = PRONUNCIATION_SAYS.search(text)
            cache[title] = cache[title.replace(' ', '_')] = says.group(1).strip() if says else ''
        _save(path, cache)
    return {f: cache.get(f, '') for f in files}


def uploader_credits(sources):
    """Commons filename -> the credit its page states, the uploading account where it states
    none — for rows whose pack authorship names nobody. Not cached: asked rarely, and the
    point is to re-read the page."""
    from audio_gates import JUNK_AUTHORS
    resolved = {}
    for start in range(0, len(sources), 50):
        batch = sources[start:start + 50]
        pages = api(prop='imageinfo', iiprop='user|extmetadata',
                    titles='|'.join('File:' + name for name in batch)).get('query', {}).get('pages', {})
        for page in pages.values():
            info = (page.get('imageinfo') or [{}])[0]
            raw = info.get('extmetadata', {}).get('Artist', {}).get('value') or ''
            artist = ' '.join(html.unescape(re.sub('<[^>]+>', ' ', raw)).split())
            # why: the uploader is a weaker credit than the stated author, but a real one.
            if artist.lower() in JUNK_AUTHORS:
                artist = 'Wikimedia Commons user %s' % info['user'] if info.get('user') else ''
            resolved[page['title'].removeprefix('File:').replace('_', ' ')] = artist
    return resolved


def exists(titles):
    """The subset of `File:` [titles] Commons has, 50 a request."""
    live = set()
    for i in range(0, len(titles), 50):
        pages = api(titles='|'.join(titles[i:i + 50])).get('query', {}).get('pages', {})
        live |= {p['title'].removeprefix('File:') for pid, p in pages.items() if int(pid) > 0}
    return live


def casings(word):
    """Every capitalization a Commons title might carry for [word], best first: MediaWiki folds
    only a title's first character, so `De-Nacht.ogg` and `De-nacht.ogg` are two pages.
    `.upper()` stays out — `ß`.upper() is `SS`, the abbreviation."""
    return list(dict.fromkeys([word, word.capitalize(), word.title()]))


def allimages(prefix):
    """Every file title starting with [prefix], one page — a word has a handful of recordings."""
    return [image['name'] for image in api(list='allimages', aiprefix=prefix, ailimit='500')
            .get('query', {}).get('allimages', [])]


def numbered_siblings(word, patterns):
    """Commons files that are [word] under a naming convention, numbered siblings included
    (`De-Tier.ogg`, `De-Tier2.ogg`), every casing; `De-Tier` also prefixes `De-Tierarzt`, so the
    prefix listing is filtered exactly."""
    found = []
    prefixes = dict.fromkeys(pattern.split('{word}')[0] + cased
                             for pattern in patterns for cased in casings(word))
    for prefix in prefixes:
        exact = re.compile(r'^' + re.escape(prefix) + r'\d*\.[a-z0-9]+$', re.IGNORECASE)
        for name in allimages(prefix):
            if exact.match(name) and name not in found:
                found.append(name)
    return found


def fetch(url, dest):
    """[url]'s bytes onto [dest]; False where Commons has no such file."""
    body = get(url)
    if body is None:
        return False
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest + '.part', 'wb') as f:
        f.write(body)
    os.replace(dest + '.part', dest)
    return True


def fetched(name, url):
    """The transcode of Commons file [name], fetched once into `fetch-cache/`; None where there is none."""
    dest = os.path.join(WORKSPACE, 'fetch-cache', name.replace('/', '_') + '.mp3')
    if os.path.exists(dest) or fetch(url, dest):
        return dest
    return None

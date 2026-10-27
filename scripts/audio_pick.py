"""Other takes of what ships, fetched and judged, and the swap a picker decides staged in the journal.

An INCUMBENT is a shipped entry, or a pack row whose form its section wants and ships nothing for;
its score is the shipped manifest's wherever the bytes ship (by sha256). A recording another
section ships too (es `perro`, word and alphabet text alike) is one file for one sound and never
up for a swap; letters never are — a challenger spelled like a glyph is a word (fr `a`, "has").
CHALLENGERS are every other take of the form by the language's route (`audio_routes.py`): its
Lingua Libre category's and its convention's numbered siblings, minus what the take judge refuses
from the record alone (`audio_judge.source_refusal`), a Shtooka page saying more than the form,
and an articled take titled as a name.
Fetches run one at a time with no pause, into `$W/fetch-cache/`; each take is measured in a process
pool as it lands, measures cached by sha256 in `$W/measure-cache.json`, so a rerun is cheap.
A swap is a row in `$W/staged.tsv` (bytes in `$W/staged/`), shipped by `audio-catalog.py --ship`;
a key already staged against the same incumbent is passed over.
"""
import collections
import concurrent.futures
import datetime
import json
import os
import shutil

import audio_commons
import audio_judge
import audio_lingualibre
import audio_measure
import audio_resolve
from audio_forms import catalog_forms, titled_as
from audio_gates import digest_of, speech_key, takes
from audio_manifest import audio_dir, read_manifest
from audio_routes import WORKSPACE, pack_dir, route
from audio_ship import read_rows, write_rows

PICKED = ('words', 'articles', 'calendar', 'countries', 'texts')


class Measures:
    """sha256 -> what `audio_judge.measure` read off those bytes, kept across runs."""

    def __init__(self):
        self.path = os.path.join(WORKSPACE, 'measure-cache.json')
        self.known = {}
        if os.path.exists(self.path):
            with open(self.path, encoding='utf-8') as f:
                self.known = json.load(f)
        self.pool = concurrent.futures.ProcessPoolExecutor(max_workers=audio_measure.WORKERS)

    def submit(self, sha, path, scored=True):
        """A future of the measures of [path]; an unreadable take answers None rather than raising."""
        held = self.known.get(sha)
        if held and 'balance' in held and ('mos' in held or not scored):
            if 'pop' not in held:
                return self.pool.submit(audio_judge.measure_pop, path)
            done = concurrent.futures.Future()
            done.set_result(held)
            return done
        return self.pool.submit(audio_judge.measure, path, scored)

    def result(self, sha, future):
        try:
            measured = future.result()
        except Exception as error:
            print('  unreadable %s: %s' % (sha[:12], str(error).splitlines()[0][-80:]))
            return None
        self.known[sha] = dict(self.known.get(sha, {}), **measured)
        return self.known[sha]

    def save(self):
        with open(self.path + '.part', 'w', encoding='utf-8') as f:
            json.dump(self.known, f)
        os.replace(self.path + '.part', self.path)

    def close(self):
        self.save()
        self.pool.shutdown()


def wanted_keys(lang, section, catalog):
    """Speech keys of the forms [section] would ship: what cards show, said with an article, or listed."""
    shown, articled = catalog
    if section == 'words':
        return shown.get(lang, set())
    if section == 'articles':
        return set(articled.get(lang, {}))
    return {speech_key(form) for _, form in catalog_forms(lang, section)}


def incumbents(lang, section, catalog):
    """Every incumbent of one section, each a take dict plus `mos`, `shipped` and `shared`."""
    manifest = read_manifest(audio_dir(lang))
    known = {take['sha256']: take.get('mos') for name, entries in manifest.items()
             if isinstance(entries, dict) and name != 'authors' and name != 'licenses'
             for item in entries.values() if isinstance(item, dict) and 'sha256' in item for take in takes(item)}
    elsewhere = {take['sha256'] for name, entries in manifest.items() if name not in (section, 'authors', 'licenses')
                 and isinstance(entries, dict) for item in entries.values() if isinstance(item, dict)
                 for take in takes(item)}
    shipped = manifest.get(section, {})
    out = [dict(lang=lang, section=section, key=key, source=item['source'], author=item['author'],
                license=item['license'], sha256=item['sha256'], mos=item.get('mos'), shipped=True,
                path=os.path.join(audio_dir(lang), item['file'])) for key, item in sorted(shipped.items())]
    claimed = {speech_key(key) for key in shipped}
    wanted = wanted_keys(lang, section, catalog)
    pack = pack_dir(lang, section)
    rows = read_rows(os.path.join(pack, 'manifest.tsv')) if os.path.isdir(pack) else []
    for row in rows:
        path = os.path.join(pack, 'mp3', row['local_file'])
        key = speech_key(row['text'])
        if key in claimed or key not in wanted or not os.path.isfile(path):
            continue
        claimed.add(key)
        sha = digest_of(path)
        out.append(dict(lang=lang, section=section, key=row['text'], source=row['file'], author=row['author'],
                        license=row['license'], sha256=sha, mos=known.get(sha), shipped=False, path=path))
    for take in out:
        take['shared'] = take['sha256'] in elsewhere
    return out


def challengers(lang, takes):
    """{incumbent key: [Commons file]} by the language's route, and the keys whose probe failed."""
    way = route(lang)
    words = {take['key']: take['key'].lower() for take in takes}
    options = {key: [] for key in words}
    failed = set()
    if way.get('lingualibre'):
        found = audio_lingualibre.candidates(audio_lingualibre.files(way['lingualibre']), way['lingualibre'],
                                             set(words.values()))
        for key, word in words.items():
            options[key] += [name for _, name in found.get(word, [])]
    for key, word in words.items():
        if not way.get('words'):
            continue
        try:
            siblings = audio_commons.numbered_siblings(word, way['words'])
        except Exception as error:
            print('  ? %s: sibling probe failed (%s) — keeps its take' % (key, str(error)[:80]))
            failed.add(key)
            continue
        options[key] += [name for name in siblings if name not in options[key]]
    return options, failed


def candidate_refusal(take, name, record):
    """Why Commons file [name] may not replace [take], out of its record alone, else None."""
    candidate = dict(take, source=name, author=record.get('author', '?'), license=record.get('license', '?'))
    refused = audio_judge.source_refusal(candidate)
    if refused:
        return refused
    if not name.startswith('LL-') and audio_resolve.says_more(name, take['key'], take['lang']):
        return 'says more', 'its page says "%s"' % audio_commons.spoken_forms([name])[name]
    if take['section'] == 'articles' and not titled_as(name, take['key']):
        return 'titled as a name', name
    return None


def fetch(name, record, key):
    """The local path of [name]'s transcode, None where Commons renders none, False (printed)
    where Commons stayed unreachable past `audio_commons.PATIENCE_S`."""
    url = audio_commons.transcode_url(record.get('url', ''))
    if not url:
        return None
    try:
        return audio_commons.fetched(name, url)
    except Exception as error:
        print('  next   %-18s (fetch failed: %s)' % (key, str(error)[:80]))
        return False


def judged(take, name, record, path, measured):
    """`Judged` of a fetched challenger, refused as unreadable where its measures failed."""
    candidate = dict(take, source=name, author=record.get('author', '?'), license=record.get('license', '?'),
                     path=path, sha256=digest_of(path))
    if measured is None:
        return audio_judge.Judged(None, ('unreadable', 'ffmpeg could not read it'), False, False, False)
    return audio_judge.judge(candidate, measured=measured)


def credit_groups(lang):
    """Takes per (voice, license) over everything [lang] ships — where the credits screen counts."""
    manifest = read_manifest(audio_dir(lang))
    return collections.Counter((take['author'], take['license']) for name, entries in manifest.items()
                               if isinstance(entries, dict) and name not in ('authors', 'licenses')
                               for item in entries.values() if isinstance(item, dict) for take in takes(item))


def voices(lang):
    """Takes per credited voice over everything [lang] ships."""
    weight = collections.Counter()
    for (author, _), count in credit_groups(lang).items():
        weight[author] += count
    return weight


def staged_keys():
    path = os.path.join(WORKSPACE, 'staged.tsv')
    return {(row['lang'], row['section'], row['key'], row['from_sha256'])
            for row in (read_rows(path) if os.path.exists(path) else [])}


def stage(take, name, record, path, mos_to, picker, why, speaks=''):
    """Appends one swap to the journal and its bytes to `$W/staged/`; [speaks] is the bare form a take
    of a particled one says, which it ships under in the whole form's place."""
    sha = digest_of(path)
    target = os.path.join(WORKSPACE, 'staged', '%s.mp3' % sha)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copyfile(path, target)
    write_rows(os.path.join(WORKSPACE, 'staged.tsv'), [dict(
        date=datetime.date.today().isoformat(), lang=take['lang'], section=take['section'], key=take['key'],
        from_source=take['source'], from_sha256=take['sha256'] if take['shipped'] else '',
        to_source=name, to_sha256=sha, author=record.get('author', '?'), license=record.get('license', '?'),
        mos_from='' if take['mos'] is None else '%.2f' % take['mos'],
        mos_to='' if mos_to is None else '%.2f' % mos_to, picker=picker, why=why, speaks=speaks)], append=True)

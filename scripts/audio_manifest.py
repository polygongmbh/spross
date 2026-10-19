"""One language's shipped manifest, read and written whole, and the editor that changes one entry.

`catalog/audio/<lang>/manifest.json` is the record of what ships: every entry pins its bytes
by `sha256`, credits its author and carries its playback index (`audio_index.py`).
Every path that changes it goes through here, so an entry and its file move together and the
root `authors`/`licenses` maps are re-derived on every write rather than carried along stale.

`AUDIO_CATALOG` points every read and write at another tree — a scratch copy to try a run on.
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unicodedata

import audio_index
import audio_judge
from audio_gates import LICENSE_URLS, digest_of, speech_key, takes as shipped_takes
from audio_routes import ROOT, SECTIONS

AUDIO = os.environ.get('AUDIO_CATALOG') or os.path.join(ROOT, 'catalog', 'audio')


def audio_dir(lang):
    return os.path.join(AUDIO, lang)


def read_json(*parts):
    with open(os.path.join(*parts), encoding='utf-8') as f:
        return json.load(f)


def inline_licenses(manifest):
    """[manifest] with every entry's `license` written back into it — what [write_manifest] factors out."""
    authors = manifest.get('authors', {})
    for name in SECTIONS:
        for item in manifest.get(name, {}).values():
            for take in shipped_takes(item):
                if 'license' not in take:
                    take['license'] = authors[take['author']]
    return manifest


def read_manifest(out_dir):
    """A shipped manifest with every entry's `license` written back into it, so a caller edits
    entries in the shape a fresh convert builds them and [write_manifest] re-derives the root maps."""
    return inline_licenses(read_json(out_dir, 'manifest.json'))


def license_url(license, where):
    if license not in LICENSE_URLS:
        sys.exit('%s: unknown license "%s" — add its deed to LICENSE_URLS' % (where, license))
    return LICENSE_URLS[license]


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
        for take in (take for item in section.values() for take in shipped_takes(item)):
            counts = per_author.setdefault(take['author'], {})
            counts[take['license']] = counts.get(take['license'], 0) + 1
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
    out = {key: value for key, value in item.items() if key not in dropped}
    if 'alternates' in out:
        out['alternates'] = [attributed(take, authors) for take in out['alternates']]
    return out


def write_manifest(out_dir, manifest):
    """Writes [manifest] (licensed inline, as [read_manifest] hands it out) with its root maps re-derived."""
    lang = manifest['language']
    sections = {name: manifest.get(name) or {} for name in SECTIONS}
    authors, licenses = credit_index([s for s in sections.values() if s],
                                     'audio/%s/manifest.json' % lang)
    written = {'language': lang, 'authors': authors, 'licenses': licenses}
    for name, section in sections.items():
        if section or name == 'words':
            written[name] = {key: attributed(item, authors) for key, item in section.items()}
    with open(os.path.join(out_dir, 'manifest.json'), 'w', encoding='utf-8') as f:
        json.dump(written, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def ascii_stem(form):
    """A shipped file's ASCII name for [form]: [a-z0-9-] stays, a space is `_`, every other
    character is `u<hex>`.

    macOS normalises filenames, so a name carrying ü or а cannot be looked up by the string
    the manifest stores — the letters are codepoint-named for the same reason.
    """
    return ''.join(char if char.isascii() and (char.isalnum() or char == '-')
                   else '_' if char == ' ' else 'u%04x' % ord(char)
                   for char in unicodedata.normalize('NFC', form).strip().lower())


def form_file(section, form):
    """`<section>/<ascii stem>.mp3` — a recording is filed under the form it speaks, never a slug."""
    return '%s/%s.mp3' % (section, ascii_stem(form))


def letter_file(glyph):
    """`letters/u<cp>…mp3` — one `u<cp>` per codepoint, because glyph names decompose on APFS.

    A sequence rather than a single codepoint: a named row may be a DIGRAPH (es `ch` che,
    and `ll`/`rr` if anyone ever records them). Single-codepoint glyphs are unaffected, so
    nothing already shipped is renamed.
    """
    return 'letters/%s.mp3' % ''.join('u%04x' % ord(char) for char in glyph)


def section_file(section, key):
    return letter_file(key) if section == 'letters' else form_file(section, key)


def alternate_file(section, key, item):
    """`<section file>.<n>.mp3` for a further take of [item]'s key, n the least from 2 no take
    of it holds — `ascii_stem` and `letter_file` never write a dot, so nothing else collides."""
    held = {take['file'] for take in shipped_takes(item)}
    stem = section_file(section, key)[:-len('.mp3')]
    return next(name for name in ('%s.%d.mp3' % (stem, n) for n in range(2, 100)) if name not in held)


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


def copy_and_analyze(copies):
    """`[(id, source, target)]` → `{id: (sha256, playback index)}`: ship the bytes, then measure.

    why: the analysis runs over the file that LANDED, so an index can never describe other
    bytes than the ones its own `sha256` pins — and one batched ffmpeg pass keeps a
    thousand decodes off the converter's wall clock.
    """
    digests = {id: copy_verified(source, target) for id, source, target in copies}
    indexes = audio_index.analyze([target for _, _, target in copies])
    analyzed = {}
    for id, _, target in copies:
        if indexes[target] is None:
            sys.exit('%s: decodes to silence — there is nothing to index' % target)
        analyzed[id] = (digests[id], indexes[target])
    return analyzed


def put(lang, takes, dry_run=False):
    """Ship [takes] — dicts of `section key mp3 source author license` and, for an article, `word` —
    into [lang]'s manifest, each replacing its key's first take, or with `alternate` set adding
    a further one beside it. → `{(section, key): (refusal or None, mos)}`.

    Every take is copied aside, measured and judged (`audio_judge.py`) first; only one that passes replaces the
    entry and file standing under its key, so a refused take leaves the recording it would have
    replaced exactly where it was; a replaced first take keeps its key's alternates.
    [dry_run] judges and writes nothing.
    """
    out_dir = audio_dir(lang)
    manifest = read_manifest(out_dir)
    aside = tempfile.mkdtemp(prefix='audio-put-')
    try:
        paths = [os.path.join(aside, '%d.mp3' % i) for i in range(len(takes))]
        for take, path in zip(takes, paths):
            shutil.copyfile(take['mp3'], path)
        indexes = audio_index.analyze(paths) if paths else {}
        outcome = {}
        for take, path in zip(takes, paths):
            section, key = take['section'], take['key']
            shipped = manifest.setdefault(section, {})
            digest = digest_of(path)
            judged = audio_judge.judge(dict(take, lang=lang, sha256=digest, path=path), indexes[path])
            old = shipped.get(key)
            if take.get('alternate'):
                why = judged.refused or (
                    ('no first take', '"%s" ships nothing to stand beside' % key) if not old else
                    ('already ships', '"%s" ships these bytes' % key)
                    if digest in {held['sha256'] for held in shipped_takes(old)} else None)
                outcome[(section, key)] = why, judged.mos
                if why or dry_run:
                    continue
                file = alternate_file(section, key, old)
                if copy_verified(path, os.path.join(out_dir, file)) != digest:
                    sys.exit('%s: landed as other bytes than were measured' % file)
                old.setdefault('alternates', []).append(
                    entry(file, take['license'], take['author'], take['source'], digest, indexes[path]))
                continue
            why = judged.refused or next(
                (('shipped-collision', '"%s" already ships as other bytes' % other)
                 for other, item in shipped.items()
                 if other != key and speech_key(other) == speech_key(key) and item['sha256'] != digest), None)
            outcome[(section, key)] = why, judged.mos
            if why or dry_run:
                continue
            file = section_file(section, key)
            if old and old['file'] != file:
                os.remove(os.path.join(out_dir, old['file']))
            landed = copy_verified(path, os.path.join(out_dir, file))
            if landed != digest:
                sys.exit('%s: landed as other bytes than were measured' % file)
            word = take.get('word') or (old or {}).get('word')
            shipped[key] = entry(file, take['license'], take['author'], take['source'], digest,
                                 indexes[path], None if section == 'letters' else key, word)
            kept = []
            for held in (old or {}).get('alternates', []):
                if held['sha256'] != digest:
                    kept.append(held)
                else:
                    os.remove(os.path.join(out_dir, held['file']))
            if kept:
                shipped[key]['alternates'] = kept
        if not dry_run and any(why is None for why, _ in outcome.values()):
            write_manifest(out_dir, manifest)
        return outcome
    finally:
        shutil.rmtree(aside, ignore_errors=True)


def remove(lang, section, key, source=None):
    """Drops one entry and every take's file, or with [source] only the alternate taken from that
    Commons file; returns what went."""
    out_dir = audio_dir(lang)
    manifest = read_manifest(out_dir)
    item = manifest.get(section, {}).get(key)
    if item is None:
        sys.exit('%s: no %s entry "%s"' % (lang, section, key))
    if source:
        gone = next((take for take in item.get('alternates', []) if take['source'] == source), None)
        if gone is None:
            sys.exit('%s: %s "%s" has no alternate from %s' % (lang, section, key, source))
        item['alternates'].remove(gone)
        if not item['alternates']:
            del item['alternates']
        dropped = [gone]
    else:
        dropped = shipped_takes(manifest[section].pop(key))
    for take in dropped:
        os.remove(os.path.join(out_dir, take['file']))
    write_manifest(out_dir, manifest)
    return dropped[0]


def rename(lang, section, old, new):
    """Re-keys one entry onto [new]: key, `matches` and file name follow it, while bytes,
    `sha256`, credit and index stay."""
    out_dir = audio_dir(lang)
    manifest = read_manifest(out_dir)
    item = manifest[section].pop(old)
    was, now = section_file(section, old)[:-len('.mp3')], section_file(section, new)[:-len('.mp3')]
    for take in shipped_takes(item):
        moved = now + take['file'][len(was):]
        os.rename(os.path.join(out_dir, take['file']), os.path.join(out_dir, moved))
        take['file'] = moved
    if 'matches' in item:
        item['matches'] = new
    manifest[section][new] = item
    write_manifest(out_dir, manifest)


def git_show(rev, path):
    run = subprocess.run(['git', 'show', '%s:./%s' % (rev, path)], cwd=ROOT, capture_output=True)
    if run.returncode != 0:
        sys.exit('git show %s:%s — %s' % (rev, path, run.stderr.decode().strip()))
    return run.stdout


def restore(lang, section, key, rev):
    """Puts back the entry [key] held at git [rev], bytes and all, its `sha256` verified;
    whatever ships under [key] now goes."""
    tracked = 'catalog/audio/%s' % lang
    then = inline_licenses(json.loads(git_show(rev, tracked + '/manifest.json')))
    item = then.get(section, {}).get(key)
    if item is None:
        sys.exit('%s: %s held no %s entry "%s"' % (lang, rev, section, key))
    data = {}
    for take in shipped_takes(item):
        data[take['file']] = git_show(rev, '%s/%s' % (tracked, take['file']))
        if hashlib.sha256(data[take['file']]).hexdigest() != take['sha256']:
            sys.exit('%s/%s at %s: bytes differ from its sha256' % (tracked, take['file'], rev))
    out_dir = audio_dir(lang)
    manifest = read_manifest(out_dir)
    old = manifest.setdefault(section, {}).get(key)
    for take in shipped_takes(old) if old else []:
        if take['file'] not in data:
            os.remove(os.path.join(out_dir, take['file']))
    for take in shipped_takes(item):
        target = os.path.join(out_dir, take['file'])
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, 'wb') as f:
            f.write(data[take['file']])
        if digest_of(target) != take['sha256']:
            sys.exit('%s: what landed is not what %s held' % (target, rev))
    manifest[section][key] = item
    write_manifest(out_dir, manifest)
    return item

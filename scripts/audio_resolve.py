"""Resolves catalog forms to Commons recordings into a pack — metadata only, no take is judged.

    scripts/audio-packs.py resolve <kind>... [lang...] [--out DIR] [--full] [--append TSV]

A pack (`pack-<lang>-<kind>/manifest.tsv` + `mp3/<local_file>`) is keyed by the form a recording
speaks, the letters by their glyph, and a run resolves only what it lacks: a row is kept while its
form is still wanted and its Commons record may ship, so a moved catalog never reseats a form
that already ships. `--full` re-resolves every form. The route per language and kind is
`audio_routes.py`; a match the app refuses to ship (a refused license, a credit naming nobody, a
Shtooka take saying more than its form, an articled take titled as a name) is no match, so the
next route gets its turn. Each run writes `missing.txt` beside the manifest: what nobody recorded.
"""
import collections
import csv
import os
import sys

import audio_commons
import audio_lingualibre
from audio_forms import catalog_forms, contested_readings, letter_rows, own_variants, titled_as, variants
from audio_gates import UNSHIPPABLE_LICENSES, speech_key, stress_free
from audio_manifest import ascii_stem
from audio_routes import WORKSPACE, pack_dir, route

FIELDS = ['local_file', 'file', 'license', 'author', 'mp3_url']


def says_more(name, text, lang):
    """True when Commons states [name] says something else than [text] (`spoken_forms`)."""
    said = audio_commons.spoken_forms([name])[name]
    return bool(said) and variants(said, lang)[0] not in variants(text, lang)


def refused(name, text, lang, record, articled):
    """Why a resolved match is no match, out of its Commons record alone, else None."""
    if record.get('license') in UNSHIPPABLE_LICENSES:
        return 'license'
    # why: a placeholder credit is re-resolved at ship (`audio_gates.attribute`); this one never resolves.
    if record.get('author', '').startswith('No machine-readable'):
        return 'unattributable'
    if says_more(name, text, lang):
        return 'says more'
    if articled and not titled_as(name, text):
        return 'titled as a name'
    return None


def by_convention(texts, patterns, lang):
    """variant -> (variant, Commons file) for [texts] under `{word}` [patterns]: the first LIVE
    candidate in preference order (lowercase casing first), never sorted order, so nothing already
    matched moves."""
    order, seen = {}, set()
    for text in texts:
        for v in variants(text, lang):
            names = order.setdefault(v, [])
            for cased in audio_commons.casings(v):
                for pattern in patterns:
                    name = pattern.replace('{word}', cased)
                    if name not in seen:
                        seen.add(name)
                        names.append(name)
    live = audio_commons.exists(sorted('File:' + name for name in seen))
    return {v: (v, next(name for name in names if name in live))
            for v, names in order.items() if any(name in live for name in names)}


def by_dump(path):
    """variant -> (the word as the dump spells it, Commons file) out of a kaikki word→file TSV."""
    index = {}
    with open(path, encoding='utf-8') as f:
        for row in csv.DictReader(f, delimiter='\t'):
            index.setdefault(row['word'].lower(), (row['word'], row['audio_file']))
    return index


def read_pack(pack):
    path = os.path.join(pack, 'manifest.tsv')
    if not os.path.exists(path):
        return []
    with open(path, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def write_pack(pack, rows, key):
    """The one pack writer: `<key> local_file file license author mp3_url`, extras dropped."""
    os.makedirs(pack, exist_ok=True)
    with open(os.path.join(pack, 'manifest.tsv'), 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, [key] + FIELDS, delimiter='\t', lineterminator='\n', extrasaction='ignore')
        writer.writeheader()
        writer.writerows(rows)


def row_for(key, value, local, name, record):
    return {key: value, 'local_file': local, 'file': name, 'license': record.get('license', '?'),
            'author': record.get('author', '?'), 'mp3_url': audio_commons.transcode_url(record.get('url', ''))}


def resolve_words(lang, kind, pack, full):
    """The words or articles pack: every form a card shows, bare or with its article."""
    articled = kind == 'articles'
    entries = list(catalog_forms(lang, kind))
    if not entries:
        print('%s: no card shows %s' % (lang, 'an article' if articled else 'a form'), file=sys.stderr)
        return [], [], set()
    old = [] if full else read_pack(pack)
    audio_commons.spoken_forms([row['file'] for row in old])
    kept, stale = [], set()
    for row in old:
        if refused(row['file'], row['text'], lang, row, articled):
            stale.add(row['local_file'])
        else:
            kept.append(row)
    said = {speech_key(row['text']) for row in kept}
    voices = collections.Counter(row['author'] for row in kept)
    todo = [(area, form) for area, form in entries
            if not any(speech_key(v) in said for v in own_variants(form, lang))]
    texts = list(dict.fromkeys(form for _, form in todo))
    way = route(lang)
    index = by_dump(os.path.join(WORKSPACE, way['dump'])) if way.get('dump') and texts else {}

    def unmatched():
        return [t for t in texts if not any(v in index for v in variants(t, lang))]

    def lingua_libre():
        rest = unmatched()
        if rest and way.get('lingualibre'):
            wanted = {v for t in rest for v in variants(t, lang)}
            index.update({k: (k, name) for k, name in audio_lingualibre.resolve(wanted, way['lingualibre'], voices).items()
                          if k not in index})

    def convention():
        rest = unmatched()
        if rest and way.get('words'):
            index.update({k: v for k, v in by_convention(rest, way['words'], lang).items() if k not in index})

    for step in (convention, lingua_libre) if way.get('convention_first') else (lingua_libre, convention):
        step()
        names = sorted({name for _, name in index.values()})
        records = audio_commons.meta(names)
        audio_commons.spoken_forms(names)
        for key in [k for k, (_, name) in index.items() if refused(name, k, lang, records.get(name, {}), False)]:
            del index[key]

    found, misses = {}, []
    contested = contested_readings([form for _, form in entries], lang)
    for area, form in todo:
        own = variants(form, lang)[0]
        tried = [v for v in variants(form, lang) if v == own or stress_free(v) not in contested]
        hit = next((index[v] for v in tried if v in index), None)
        if hit is not None and articled and not titled_as(hit[1], form):
            hit = None
        if hit is None:
            misses.append((area, form))
        else:
            # why: an articled row keeps the catalog's spelling (`der Ausweis`); a bare row is the
            # form matched, or the stressed form a plain title was found for.
            word, name = hit
            text = form if articled else (own if word == stress_free(own) else word)
            found.setdefault(speech_key(text), (text, name))
    records = audio_commons.meta(sorted({name for _, name in found.values()}))
    new = [row_for('text', text, ascii_stem(text) + '.mp3', name, records.get(name, {}))
           for text, name in found.values()]
    wanted = {speech_key(form) for _, form in entries}
    rows = kept + new
    covered = wanted & {speech_key(row['text']) for row in rows}
    print('%s %s: %d of %d unresolved found, %d rows kept, %d / %d forms' % (
        lang, kind, len(new), len(texts), len(kept), len(covered), len(wanted)), file=sys.stderr)
    return rows, list(dict.fromkeys(misses)), stale


def resolve_forms(lang, kind, pack, full):
    """A calendar, countries or texts pack: the convention first, Lingua Libre for what it missed."""
    forms = [form for _, form in catalog_forms(lang, kind)]
    way = route(lang)
    patterns = way.get('forms', [])
    kept = {} if full else {row['text']: row for row in read_pack(pack)
                            if row['text'] in forms and not refused(row['file'], row['text'], lang, row, False)}
    todo = [form for form in forms if form not in kept]
    probe = {form: [p.replace('{text}', cased) for cased in audio_commons.casings(form) for p in patterns]
             for form in todo}
    live = audio_commons.exists(sorted({'File:' + c for cs in probe.values() for c in cs})) if patterns else set()
    records = audio_commons.meta(sorted(live))
    hits, index = {}, None
    for form in todo:
        name = next((c for c in probe[form] if c in live and not refused(c, form, lang, records.get(c, {}), False)), None)
        if name is None and way.get('forms_lingualibre'):
            if index is None:
                index = audio_lingualibre.resolve({v for f in todo for v in variants(f, lang)},
                                                  way['forms_lingualibre'],
                                                  collections.Counter(row['author'] for row in kept.values()))
            name = index.get(form.lower())
            picked = audio_commons.meta([name]) if name else {}
            name = None if name and refused(name, form, lang, picked[name], False) else name
        if name:
            hits[form] = name
    records = audio_commons.meta(sorted(set(hits.values())))
    rows, misses = [], []
    for form in forms:
        if form in kept:
            rows.append(kept[form])
        elif form in hits:
            rows.append(row_for('text', form, ascii_stem(form) + '.mp3', hits[form], records.get(hits[form], {})))
        else:
            misses.append((kind, form))
    print('%s %s: %d found, %d kept, %d / %d forms' % (lang, kind, len(hits), len(kept), len(rows), len(forms)),
          file=sys.stderr)
    return rows, misses, set()


def resolve_letters(lang, pack, full):
    """The letters pack: a letter's take says its NAME, and is looked up by its glyph."""
    patterns = route(lang).get('letters', [])
    if not patterns:
        print('%s letters: no route — the pack stays as it is' % lang, file=sys.stderr)
        return None, [], set()
    named = letter_rows(lang)
    kept = {} if full else {row['letter']: row for row in read_pack(pack) if row['letter'] in {g for g, _, _ in named}}
    probe = {glyph: candidates(glyph, upper, name, patterns) for glyph, upper, name in named if glyph not in kept}
    live = audio_commons.exists(sorted({'File:' + c for cs in probe.values() for c in cs}))
    hits = {glyph: next(c for c in cs if c in live) for glyph, cs in probe.items() if any(c in live for c in cs)}
    records = audio_commons.meta(sorted(set(hits.values())))
    rows = [kept.get(glyph) or row_for('letter', glyph, glyph + '.mp3', hits[glyph], records.get(hits[glyph], {}))
            for glyph, _, _ in named if glyph in kept or glyph in hits]
    print('%s letters: %d found, %d kept, %d / %d named rows' % (lang, len(hits), len(kept), len(rows), len(named)),
          file=sys.stderr)
    return rows, [('letters', glyph) for glyph, _, _ in named if glyph not in kept and glyph not in hits], set()


def candidates(glyph, upper, name, patterns):
    """Commons titles to probe for one letter, best first, every token in a pattern substituted
    in every casing; `{upper}` only where the alphabet authors one (`'ß'.upper()` is `SS`)."""
    out = []
    for pattern in patterns:
        fields = {'glyph': glyph, 'name': name, **({'upper': upper} if upper else {})}
        used = [key for key in fields if '{%s}' % key in pattern]
        if ('{upper}' in pattern and not upper) or not used:
            continue
        titles = [pattern]
        for key in used:
            titles = [t.replace('{%s}' % key, cased) for t in titles for cased in audio_commons.casings(fields[key])]
        out += [t for t in titles if '{' not in t and t not in out]
    return out


def fetch_rows(pack, rows, stale):
    """Every row's transcode onto `mp3/<local_file>`, skipping what is on disk unless re-picked."""
    failed = []
    for row in rows:
        dest = os.path.join(pack, 'mp3', row['local_file'])
        if (os.path.exists(dest) and row['local_file'] not in stale) or not row['mp3_url']:
            continue
        try:
            if not audio_commons.fetch(row['mp3_url'], dest):
                failed.append(row.get('text') or row.get('letter'))
        except Exception as error:
            print('  ! %s: %s' % (row['local_file'], error), file=sys.stderr)
            failed.append(row.get('text') or row.get('letter'))
    if failed:
        print('  %d not fetched — rerun to retry: %s' % (len(failed), ', '.join(failed[:10])), file=sys.stderr)
    return len(failed)


def resolve(lang, kind, out=None, full=False):
    """Resolves one pack and fetches its new transcodes; the count of rows left without bytes."""
    pack = out or pack_dir(lang, kind)
    if kind == 'letters':
        rows, misses, stale = resolve_letters(lang, pack, full)
    elif kind in ('words', 'articles'):
        rows, misses, stale = resolve_words(lang, kind, pack, full)
    else:
        rows, misses, stale = resolve_forms(lang, kind, pack, full)
    if not rows:
        return 0
    key = 'letter' if kind == 'letters' else 'text'
    write_pack(pack, rows, key)
    with open(os.path.join(pack, 'missing.txt'), 'w', encoding='utf-8') as f:
        f.writelines('%s\t%s\n' % miss for miss in misses)
    return fetch_rows(pack, rows, stale)


def append(lang, kind, tsv):
    """Adds hand-named rows `text · file [· author]` to a pack without re-resolving it."""
    pack = pack_dir(lang, kind)
    rows = read_pack(pack)
    have = {speech_key(row['text']) for row in rows}
    added = []
    with open(tsv, encoding='utf-8') as f:
        for line in f:
            if line.strip() and not line.startswith('#'):
                cells = line.rstrip('\n').split('\t')
                if speech_key(cells[0]) not in have:
                    added.append(cells)
    records = audio_commons.meta(sorted({cells[1] for cells in added}))
    new = [dict(row_for('text', cells[0], ascii_stem(cells[0]) + '.mp3', cells[1], records.get(cells[1], {})),
                **({'author': cells[2]} if len(cells) > 2 else {})) for cells in added]
    for row in new:
        print('  + %-14s %s  %s  %s' % (row['text'], row['file'], row['license'], row['author']), file=sys.stderr)
    write_pack(pack, rows + new, 'text')
    return fetch_rows(pack, new, set())

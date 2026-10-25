"""Which forms the catalog shows a learner, per kind of recording — what a pack is resolved for
and what a shipped recording has to reach.
"""
import glob
import os
import re
import sys
import unicodedata

from audio_gates import bare_form, speech_key, split_article, spoken_target_form, stress_free
from audio_manifest import ROOT, read_json

CATALOG = os.path.join(ROOT, 'catalog')


def load_catalog():
    """({lang: {speech key of every form some card shows}},
    {lang: {speech key of an article + word some card shows: the word}}).

    Which concept shows a form is never asked: a recording is filed under the form it speaks,
    so a form moving between a concept's text, a synonym and a tagged form leaves audio alone.
    The second is what an ARTICLE recording is measured against: a realization's own
    `grammar.article` in front of each form it shows, and each tagged form with the article
    it was authored with — only those show an article to say.
    """
    areas = [area['area'] for group in read_json(CATALOG, 'areas.json') for area in group['areas']]
    languages = read_json(CATALOG, 'languages.json')
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
                # why: kern's lookup falls back to a form's bare form (`bareForm`), so a recording of
                # `piga simu` reaches a card showing `kupiga simu`, `irren` one showing `sich irren`.
                shown_here = shown_by + [
                    stem for stem in (bare_form(form, languages.get(lang, {})) for form in shown_by) if stem]
                shown.setdefault(lang, set()).update(speech_key(form) for form in shown_here)
                authored = word.get('grammar', {}).get('article')
                said = [(authored, form) for form in shown_by[:len(shown_by) - len(tagged)] if authored]
                said += [(article, form) for article, form in tagged if article]
                for article, form in said:
                    articled.setdefault(lang, {}).setdefault(
                        speech_key(spoken_target_form(article, form)), form)
    return shown, articled


def catalog_forms(lang, kind):
    """(where, form) for every form a [kind] pack is resolved for: what the cards show for
    `words` and `articles`, the alphabet's example words for `texts`, the dates drill's names for
    `calendar`, the atlas' for `countries`."""
    if kind in ('words', 'articles'):
        return card_forms(lang, kind == 'articles')
    source = {'texts': example_texts, 'calendar': calendar_forms, 'countries': country_forms}[kind]
    return [(kind, form) for form in source(lang)]


def card_forms(lang, articled):
    """(area, form) for every form a card in [lang] shows: each realization's text and its tagged
    forms bare, or — [articled] — every one of them a card shows with an article (`der Ausweis`,
    `die Hunde`). Which concept a form belongs to is never recorded, so a form moving between a
    concept's text and a tagged form keeps its recording; phrases are included."""
    articles = read_json(CATALOG, 'languages.json').get(lang, {}).get('articles') or []
    out = []
    for path in sorted(glob.glob(os.path.join(CATALOG, 'areas', '*', '%s.json' % lang))):
        area = os.path.basename(os.path.dirname(path))
        for word in (read_json(path).get('words') or {}).values():
            text = (word.get('text') or '').strip()
            if not text:
                continue
            authored = (word.get('grammar') or {}).get('article')
            if not articled:
                out.append((area, text))
            elif authored:
                out.append((area, spoken_target_form(authored, text)))
            for value in (word.get('forms') or {}).values():
                for form in (value if isinstance(value, list) else [value]):
                    article, bare = split_article(form, articles)
                    if not articled and bare:
                        out.append((area, bare))
                    elif articled and article and bare:
                        out.append((area, form.strip()))
    return out


def example_texts(lang):
    """Every `exampleText` the alphabet states — reference words no concept covers (de `sechs`,
    the es `pero`/`perro` minimal pair); none where [lang] has no alphabet (en, sw)."""
    path = os.path.join(CATALOG, 'alphabet', '%s.json' % lang)
    if not os.path.isfile(path):
        return []
    entries = read_json(path)['entries']
    return list(dict.fromkeys(e['exampleText'] for e in entries if e.get('exampleText')))


def calendar_forms(lang):
    """Every weekday and month name the calendar states, and the synonyms that take their turn
    as a prompt; `abbr` is written, never said."""
    calendar = read_json(CATALOG, 'dates', '%s.json' % lang)
    return list(dict.fromkeys(form for row in calendar['weekdays'] + calendar['months']
                              for form in [row['text']] + row.get('synonyms', [])))


def country_forms(lang):
    """Every country and nationality name the atlas shows; `variants` are accept-only and never shown."""
    forms = []
    for row in read_json(CATALOG, 'countries', '%s.json' % lang).values():
        forms.append(row['text'])
        if row.get('nationality'):
            forms.append(row['nationality']['text'])
    return list(dict.fromkeys(forms))


def letter_rows(lang):
    """(glyph, upper, name) for every alphabet row that may carry a letter recording: one the
    alphabet names, its glyph carried by no other named row (de authors `ch` three times)."""
    named = [e for e in read_json(CATALOG, 'alphabet', '%s.json' % lang)['entries'] if e.get('name')]
    rows = []
    for entry in named:
        if sum(1 for other in named if other['glyph'] == entry['glyph']) > 1:
            print('  skip %r: named by more than one row' % entry['glyph'], file=sys.stderr)
        else:
            rows.append((entry['glyph'], entry.get('upper'), entry['name']))
    return rows


def titled_as(name, form):
    """True when [name]'s title ends in [form] cased as the catalog writes it, bar its first letter.

    Asked of an articled take only: an article opening a title-cased name is a proper name or a
    work title, not the noun — Lingua Libre's `La Paz` is a place, `Le Quartier` a title.
    """
    stem = unicodedata.normalize('NFC', name.rsplit('.', 1)[0].replace('_', ' '))
    form = unicodedata.normalize('NFC', form.strip())
    tail = stem[len(stem) - len(form):]
    return (len(stem) >= len(form) and tail[1:] == form[1:] and tail[:1].lower() == form[:1].lower()
            and stem[:len(stem) - len(form)][-1:] in ('', '-'))


def variants(text, lang):
    """Surface forms to try against the recording index, best first; a stressed form's own
    spelling first, then the same forms without the stress mark. A verb's citation prefix, a
    detachable particle or a reflexive ending (`bare_form`: sw `ku-`/`kw-`, en `to `, de `sich `,
    es `-se`) is one the recordings may lack, tried after the whole form, as is a Swahili adjective's leading `-`;
    Spanish opens exclamations with `¡`/`¿`, which nobody pronounces."""
    t = re.sub(r'[!?.,;:]+$', '', unicodedata.normalize('NFC', text.strip()))
    out = [t.lower()]
    stem = bare_form(t, language_info(lang))
    if stem:
        out.append(stem.lower())
    if lang == 'sw' and t.startswith('-'):
        out.append(t[1:].lower())
    if lang == 'es':
        out.append(t.lstrip('¡¿').lower())
    return list(dict.fromkeys(out + [stress_free(v) for v in out]))


_LANGUAGES = {}


def language_info(lang):
    if not _LANGUAGES:
        _LANGUAGES.update(read_json(CATALOG, 'languages.json'))
    return _LANGUAGES.get(lang, {})


def own_variants(form, lang):
    """[variants] a pack row may already be keyed by: a stressed form's plain spelling is a lookup
    key into Commons, never a row of its own."""
    found = variants(form, lang)
    return found if stress_free(found[0]) == found[0] else [v for v in found if stress_free(v) != v]


def contested_readings(forms, lang):
    """Plain spellings two catalog forms stress differently (`пі́вніч`/`півні́ч`): which one a take
    of the plain spelling says, no title tells, so neither may claim it."""
    readings = {}
    for form in forms:
        own = variants(form, lang)[0]
        if stress_free(own) != own:
            readings.setdefault(stress_free(own), set()).add(own)
    return {plain for plain, said in readings.items() if len(said) > 1}

"""Which forms the catalog shows a learner, per kind of recording — what a pack is resolved for
and what a shipped recording has to reach.
"""
import os

from audio_gates import speech_key, split_article, spoken_target_form, verb_stem
from audio_manifest import ROOT, read_json

CATALOG = os.path.join(ROOT, 'catalog')


def load_catalog():
    """({lang: {speech key of every form some card shows}},
    {lang: {speech key of an article + word some card shows: the word}}).

    Which concept shows a form is never asked: a recording is filed under the form it speaks,
    so a form moving between a concept's text, a synonym and a tagged form leaves audio alone.
    The second is what an ARTICLE recording is measured against: a realization's own
    `grammar.gender` in front of each form it shows, and each tagged form with the article
    it was authored with — only those show an article to say.
    """
    areas = [area['area'] for group in read_json(CATALOG, 'areas.json') for area in group['areas']]
    languages = read_json(CATALOG, 'languages.json')
    prefixes = {code: info.get('optionalVerbPrefixes', []) for code, info in languages.items()}
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
                # why: kern's lookup falls back to a verb's bare stem (`verbStem`), so a
                # recording of `piga simu` reaches a card showing `kupiga simu`.
                shown_here = shown_by + [
                    stem for stem in (verb_stem(form, prefixes.get(lang, [])) for form in shown_by) if stem]
                shown.setdefault(lang, set()).update(speech_key(form) for form in shown_here)
                gender = word.get('grammar', {}).get('gender')
                said = [(gender, form) for form in shown_by[:len(shown_by) - len(tagged)] if gender]
                said += [(article, form) for article, form in tagged if article]
                for article, form in said:
                    articled.setdefault(lang, {}).setdefault(
                        speech_key(spoken_target_form(article, form)), form)
    return shown, articled

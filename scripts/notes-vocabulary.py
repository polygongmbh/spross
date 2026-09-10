#!/usr/bin/env python3
"""Report own-language notes that lean on words the learner has not met yet.

A note keyed by its file's own language is the shared wording every reader falls back to
(`catalog/areas/README.md` § How a grammar rule gets taught), so it has to be readable from
the vocabulary already seeded when the card arrives. Two ways it fails:

  LATER   an example word that IS a card, but a later one — `chai na maziwa` on
          `connectors/and`, which is area 9, long before `chai` is taught. Swap it for a
          word already seeded.
  JARGON  a grammar term that is never a card at all — `pluriel`, `singular`, `adverbe`.
          The README already prefers showing the form over naming the rule.

A word the catalog never teaches AND never lists as jargon is left alone: that is a function
word or an inflected form, which a learner reads without having been dealt it as a card.

Reports only; it owns no verdict. Matching is surface-form and deliberately generous
(a token counts as met if it shares a 4-character stem with something seeded), so it
under-reports on the heavily inflected languages rather than crying wolf.

    scripts/notes-vocabulary.py                 # every area
    scripts/notes-vocabulary.py --until 12      # the first 12 areas, where it actually bites
    scripts/notes-vocabulary.py --lang sw --verbose
"""

import argparse
import json
import pathlib
import re
import sys
from collections import Counter, defaultdict

ROOT = pathlib.Path(__file__).resolve().parent.parent
AREAS = ROOT / "catalog" / "areas"
WORD = re.compile(r"[^\W\d_]+", re.UNICODE)
MIN_STEM = 4

# Grammar terms a catalog never teaches as cards. Reported as JARGON, never as LATER.
METALANGUAGE = {
    "de": {"plural", "singular", "adjektiv", "adverb", "verb", "substantiv", "artikel",
           "vorsilbe", "endung", "nomenklasse", "akkusativ", "dativ", "genitiv"},
    "en": {"plural", "singular", "adjective", "adverb", "verb", "noun", "article", "ending"},
    "eo": {"pluralo", "singularo", "adjektivo", "adverbo", "verbo", "substantivo", "artikolo",
           "finaĵo", "akuzativo", "nedifina", "difina"},
    "es": {"plural", "singular", "adjetivo", "adverbio", "verbo", "sustantivo", "femenino",
           "masculino", "terminación", "artículo"},
    "fr": {"pluriel", "singulier", "adjectif", "adverbe", "verbe", "nom", "féminin", "masculin",
           "terminaison", "article"},
    "it": {"plurale", "singolare", "aggettivo", "avverbio", "verbo", "sostantivo", "femminile",
           "maschile", "desinenza", "articolo"},
    "sw": {"kivumishi", "kitenzi", "nomino", "ngeli", "wingi", "umoja", "kiambishi"},
    "uk": {"множина", "однина", "прикметник", "прислівник", "дієслово", "іменник",
           "відмінок", "родовому", "знахідному", "давальному"},
}


def tokens(text):
    return [t.lower() for t in WORD.findall(text)]


def related(token, word):
    """Same word up to inflection — a shared stem that is most of BOTH sides.

    Bare containment is far too loose in practice: `mara` sits inside `marahaba` and `nada`
    inside `terminada`, neither of which is the card. Requiring the shorter form to be most
    of the longer keeps a prefixed form like `mzuri` against `zuri` and drops those two.

    Containment is the whole test, so a form that differs at the END (`amico`/`amici`) is
    not caught either way; the agreement forms that matter are listed as variants and match
    exactly.
    """
    if len(token) < MIN_STEM or len(word) < MIN_STEM:
        return False
    if token not in word and word not in token:
        return False
    return min(len(token), len(word)) / max(len(token), len(word)) >= 0.7


def surface_forms(realization):
    out = []
    for key in ("text", "synonyms", "variants"):
        value = realization.get(key)
        if isinstance(value, str):
            out.append(value)
        elif isinstance(value, list):
            out.extend(v for v in value if isinstance(v, str))
    return {t for form in out for t in tokens(form)}


def seed_order(until=None):
    """Slug -> position, following areas.json order then each area's concepts.json order."""
    groups = json.loads((ROOT / "catalog" / "areas.json").read_text())
    areas = [a["area"] for g in groups for a in g["areas"]]
    if until is not None:
        areas = areas[:until]
    position, order = {}, []
    for area in areas:
        concepts = AREAS / area / "concepts.json"
        if not concepts.exists():
            continue
        for concept in json.loads(concepts.read_text()):
            position[concept["slug"]] = len(order)
            order.append((area, concept["slug"]))
    return position


def audit(lang, position):
    """Return [(area, slug, note, later, jargon)] for this language's own-language notes."""
    introduced, notes = defaultdict(set), []
    for path in sorted(AREAS.glob(f"*/{lang}.json")):
        area = path.parent.name
        for slug, realization in (json.loads(path.read_text()).get("words") or {}).items():
            if not isinstance(realization, dict) or slug not in position:
                continue
            introduced[position[slug]] |= surface_forms(realization)
            note = (realization.get("notes") or {}).get(lang)
            if note:
                notes.append((position[slug], area, slug, note))

    running, met = set(), {}
    for at in sorted(introduced):
        running |= introduced[at]
        met[at] = set(running)
    marks = sorted(met)

    def seeded_by(at):
        found = set()
        for mark in marks:
            if mark > at:
                break
            found = met[mark]
        return found

    everything = set().union(*introduced.values()) if introduced else set()
    jargon = METALANGUAGE.get(lang, set())
    findings = []
    for at, area, slug, note in sorted(notes):
        known = seeded_by(at)
        later, meta = [], []
        for token in tokens(note):
            if len(token) < MIN_STEM or token in known:
                continue
            if any(related(token, k) for k in known):
                continue
            if token in jargon:
                meta.append(token)
            elif token in everything or any(related(token, w) for w in everything):
                # The catalog DOES teach this word, just not yet — swap it for one already seeded.
                later.append(token)
            # Anything else is a function word or an inflection the catalog never lists as a
            # card. A learner reads those; they are not what this report is looking for.
        if later or meta:
            findings.append((area, slug, note, later, meta))
    return len(notes), findings


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", action="append", help="limit to a language (repeatable)")
    parser.add_argument("--until", type=int, metavar="N",
                        help="only the first N areas in seed order")
    parser.add_argument("--verbose", action="store_true", help="print every flagged note")
    args = parser.parse_args()

    position = seed_order(args.until)
    langs = args.lang or ["de", "en", "eo", "es", "fr", "it", "sw", "uk"]
    scope = f"first {args.until} areas" if args.until else "every area"
    print(f"own-language notes, {scope}\n")
    print(f"{'lang':6}{'notes':>7}{'clean':>7}{'later':>8}{'jargon':>8}")

    flagged_total = 0
    for lang in langs:
        total, findings = audit(lang, position)
        later = sum(1 for *_, l, _m in findings if l)
        jargon = sum(1 for *_, _l, m in findings if m)
        flagged_total += len(findings)
        print(f"{lang:6}{total:>7}{total - len(findings):>7}{later:>8}{jargon:>8}")
        if args.verbose and findings:
            for area, slug, note, l, m in findings:
                tail = " ".join(filter(None, [
                    f"later={l}" if l else "", f"jargon={m}" if m else ""]))
                print(f"    {area}/{slug}: {note}\n      {tail}")
        elif findings:
            common = Counter(t for *_, l, _m in findings for t in l)
            if common:
                print("       most common later-card words: "
                      + ", ".join(f"{w}({n})" for w, n in common.most_common(8)))
    return 1 if flagged_total else 0


if __name__ == "__main__":
    sys.exit(main())

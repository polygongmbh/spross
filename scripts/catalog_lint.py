#!/usr/bin/env python3
"""The prompt-collision rules, swept over the whole catalog without a JVM.

`CatalogLintTest` owns these three rules and is the authority; this is the fast half,
so an authoring pass can fail inside its own loop instead of finding out from the gate
ninety seconds later. It reads the two allowlists OUT of the Kotlin rather than keeping
its own copy — one source of truth, and a pin added there is honoured here at once.

Same split as `audio_gates.py` beside `CatalogAudioLintTest`: the underscore name is the
importable module, the hyphenated scripts are the CLIs that use it.

  python3 scripts/catalog_lint.py --check
"""
import argparse, json, os, re, sys, unicodedata
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'catalog')
LINT_KT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       'kern/src/jvmTest/kotlin/net/spross/kern/catalog/CatalogLintTest.kt')


def prompt_forms(realization):
    """What a card may SHOW — text plus rotating synonyms, NFC-folded, as the lint reads them.

    Case-SENSITIVE on purpose: `Husten`/`husten` and `jua`/`kujua` are real visual
    distinctions that keep noun/verb homographs apart. `variants` are excluded — they
    are accept-only and never displayed, so they cannot collide on a prompt.
    """
    return [unicodedata.normalize('NFC', form).strip()
            for form in [realization['text']] + realization.get('synonyms', [])]


def load(catalog=ROOT):
    """(lang, form) -> ['area/slug', ...], keeping only the genuine collisions."""
    areas = [a['area'] for g in json.load(open(os.path.join(catalog, 'areas.json'), encoding='utf-8'))
             for a in g['areas']]
    langs = sorted(json.load(open(os.path.join(catalog, 'languages.json'), encoding='utf-8')))
    by_form = defaultdict(list)
    for area in areas:
        for lang in langs:
            path = os.path.join(catalog, area, '%s.json' % lang)
            if not os.path.exists(path):
                continue
            for slug, realization in json.load(open(path, encoding='utf-8'))['words'].items():
                for form in prompt_forms(realization):
                    by_form[(lang, form)].append('%s/%s' % (area, slug))
    return {k: v for k, v in by_form.items() if len(v) > 1}


def _kotlin_block(source, after, opener):
    """The string literals of the `opener(...)` call that follows `after`, parens balanced."""
    start = source.index(opener, source.index(after)) + len(opener)
    depth, i = 1, start
    while depth:
        depth += (source[i] == '(') - (source[i] == ')')
        i += 1
    body = source[start:i - 1]
    body = re.sub(r'//[^\n]*', '', body)          # the reviewed comments are prose, not data
    return body


def pinned_cross_area(lint_kt=LINT_KT):
    """The `crossAreaPromptCollisionsAreKnown` allowlist, read from the Kotlin."""
    src = open(lint_kt, encoding='utf-8').read()
    return set(re.findall(r'"([^"]+)"', _kotlin_block(src, 'fun crossAreaPromptCollisionsAreKnown', 'sortedSetOf(')))


def reviewed_pairs(lint_kt=LINT_KT):
    """`noConceptPairCollidesInTwoLanguages`'s reviewed merges: (a, b) -> {langs}."""
    src = open(lint_kt, encoding='utf-8').read()
    body = _kotlin_block(src, 'fun noConceptPairCollidesInTwoLanguages', 'mapOf(')
    out = {}
    for a, b, langs in re.findall(r'\("([^"]+)"\s+to\s+"([^"]+)"\)\s+to\s+setOf\(([^)]*)\)', body):
        out[tuple(sorted((a, b)))] = set(re.findall(r'"([^"]+)"', langs))
    return out


def failures(catalog=ROOT, lint_kt=LINT_KT):
    """Every violation, worst first. Empty means the three rules hold."""
    clusters, out = load(catalog), []

    # Unfixable at runtime: the engine's disambiguator IS the area label, identical on both.
    for (lang, form), ids in sorted(clusters.items()):
        for area, group in sorted(defaultdict(list, {a: [i for i in ids if i.startswith(a + '/')]
                                                     for a in {i.split('/')[0] for i in ids}}).items()):
            if len(group) > 1:
                out.append('same-area: %s "%s" is the prompt for %s — repick one'
                           % (lang, form, ' and '.join(sorted(group))))

    # One PAIR colliding in two languages is one meaning authored twice — unify it.
    langs_by_pair = defaultdict(set)
    for (lang, _), ids in clusters.items():
        ordered = sorted(set(ids))
        for i in range(len(ordered)):
            for j in range(i + 1, len(ordered)):
                langs_by_pair[(ordered[i], ordered[j])].add(lang)
    reviewed = reviewed_pairs(lint_kt)
    for pair, langs in sorted(langs_by_pair.items()):
        rest = langs - reviewed.get(tuple(sorted(pair)), set())
        if len(rest) > 1:
            out.append('two-language: %s and %s both collide in %s — unify the meaning, or '
                       'add the pair to reviewedPairs with a dated reason'
                       % (pair[0], pair[1], ', '.join(sorted(rest))))

    # Cross-area, single-language merges are legitimate, but each is a deliberate decision.
    actual = {'%s %s: %s' % (lang, form, ', '.join(sorted(set(ids))))
              for (lang, form), ids in clusters.items()}
    pinned = pinned_cross_area(lint_kt)
    for new in sorted(actual - pinned):
        out.append('unpinned cross-area: %s — pin it in crossAreaPromptCollisionsAreKnown '
                   'with a `// Reviewed <date>:` reason, or repick the word' % new)
    for stale in sorted(pinned - actual):
        out.append('stale pin: %s no longer collides — drop the line' % stale)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--check', action='store_true', help='name every violation, exit 1')
    ap.add_argument('--catalog', default=ROOT)
    args = ap.parse_args()
    found = failures(args.catalog)
    for line in found:
        print('error: %s' % line, file=sys.stderr)
    if found:
        print('\n%d prompt-collision violation(s). CatalogLintTest is the authority; '
              'this is the same three rules without the JVM.' % len(found), file=sys.stderr)
        return 1
    print('catalog prompt collisions clean')
    return 0


if __name__ == '__main__':
    sys.exit(main())

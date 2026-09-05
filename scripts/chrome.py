#!/usr/bin/env python3
"""Write the Android chrome tables from the iOS String Catalog.

One surface, one sentence: `Localizable.xcstrings` holds every chrome string the
product says, and `ChromeDe.kt`/`ChromeEn.kt` are generated from it. The two tables
used to be kept in step by hand and had drifted on some fifty strings — the same
button reading "Sprecher & Lizenzen" on one phone and "Impressum & Lizenzen" on the
other — which is exactly what a generator cannot let happen.

No flag reports drift and exits non-zero on it; --fix rewrites both tables, and --check
is the same report under the name the pre-commit hook calls it by.

The catalog is the source in both directions of work: a new Android string is added
there (and named in ANDROID_ONLY in scripts/strings.py, so the iOS drift check knows
no Swift will ever ask for it), then this regenerates the tables.

Every catalog key is either read by a field below or named in [IOS_ONLY] / [ANDROID_TODO],
which the no-flag run checks — so a string written for one phone is classified as it lands,
and what Android still owes is a list rather than a silence.

A field's NAME is its key: `box.card.due` is read by `boxCardDue`, the key's segments
camelCased. So the two are one fact, not a table to keep in step, and a field cannot end up
naming a key that says something else. [FAMILIES] holds the few fields that read SEVERAL
keys — a List<String> in the order the reader indexes them, or a Map<String, String>.

A counted key holds plural forms rather than one string, and which form a field reads is
its own NAME's to say: `<field>One` takes the `one` form, every other name the general
`other` one.

Placeholders are rewritten on the way out — iOS writes %@ and %lld, java.lang.String
wants %s and %d — so a call site's `.format(...)` keeps working unchanged.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'App/Sources/Resources/Localizable.xcstrings')
ANDROID = os.path.join(ROOT, 'android/src/main/kotlin/net/spross/app')
STRUCT = os.path.join(ANDROID, 'Chrome.kt')
TABLES = {'de': os.path.join(ANDROID, 'ChromeDe.kt'),
          'en': os.path.join(ANDROID, 'ChromeEn.kt')}
WIDTH = 96

HEADER = '''package net.spross.app

/**
 * The %(name)s chrome table.%(fallback)s
 *
 * GENERATED from App/Sources/Resources/Localizable.xcstrings by scripts/chrome.py —
 * do not edit. Change the wording in the String Catalog, run `scripts/chrome.py --fix`,
 * and both phones say the same sentence by construction.
 */
internal object Chrome%(code)s : Chrome {
'''
FALLBACK = ('\n *\n * Also the one every source without chrome of its own falls back to'
            ' ([Chrome.forSource]).')

class Series:
    """Every `<prefix>.<n>` key the catalog holds, in index order.

    How MANY a set holds is kern's to say — `HeadlineKind` sizes each headline set by how
    often a learner meets the kind — so writing the count here as well would be one fact in
    two homes, with nothing to stop one of them going quietly stale. Reading whatever the
    catalog holds leaves adding or dropping a phrasing a catalog edit and nothing else; the
    Android test `everyKindAndVariantResolvesToAPhrasing` is what fails if the two disagree.
    """

    def __init__(self, prefix):
        self.prefix = prefix

    def keys(self, strings):
        found = [(int(tail), k) for k, (head, _, tail)
                 in ((k, k.rpartition('.')) for k in strings)
                 if head == self.prefix and tail.isdigit()]
        return [k for _, k in sorted(found)]


FAMILIES = {

    # Families — one key per entry, in the order the reader indexes them.
    'countrySprosseHints': ['countries.sprosse.%d.hint' % i for i in range(1, 10)],
    'countrySprossen': ['countries.sprosse.%d' % i for i in range(1, 10)],
    'countryTiers': ['countries.tier.%d' % i for i in range(1, 5)],
    'dateSprosseHints': ['dates.sprosse.%d.hint' % i for i in range(1, 7)],
    'dateSprossen': ['dates.sprosse.%d' % i for i in range(1, 7)],
    'greetDay': ['home.greeting.day.%d %%@' % i for i in range(2)],
    'greetEvening': ['home.greeting.evening.%d %%@' % i for i in range(2)],
    'greetMorning': ['home.greeting.morning.%d %%@' % i for i in range(2)]
                    + ['home.greeting.morning.epithet %@'],
    'greetNight': ['home.greeting.night.%d %%@' % i for i in range(2)]
                  + ['home.greeting.night.epithet %@'],
    'growthBlooming': ['session.done.growth.blooming.%d' % i for i in range(3)],
    'growthGrown': ['session.done.growth.grown.%d' % i for i in range(3)],
    'growthSown': ['session.done.growth.sown.%d' % i for i in range(3)],
    'headlineFreshSet': Series('home.offer.headline.freshSet'),
    'headlineReviews': Series('home.offer.headline.reviews'),
    'headlineStreak': Series('home.offer.headline.streakReminder'),
    'headlineWarmUp': Series('home.offer.headline.warmUp'),

    'numberSections': {section: 'numbers.section.%s' % section for section in
                       ('base', 'tens', 'irregulars', 'compounds',
                        'hundreds', 'places', 'forms')},
}

# Catalog keys no chrome field reads, each with the reason it does not. Every other key is
# read by the field its name camelCases to — `main` checks that — so a string written for
# one phone is classified the day it lands, and the two kinds of not-read stay apart:
# what Android has no use for, and what it still owes.
IOS_ONLY = {
    # The voice-download banner walks the reader to an iOS Settings path; which voices an
    # Android device has is its TTS engine's own affair. `common.dismiss` is that banner's ✕.
    'common.dismiss', 'home.voiceUpgrade.path', 'home.voiceUpgrade.title %@',
    'settings.audio.voiceUpgrade %@',
    # Xcode canvas scaffolding, inside `#Preview("Palette")` in Design/Theme.swift.
    'preview.skip', 'preview.tokens',
    # A Swift switch owes every case a branch, including two kern never hands out: new is
    # the absence of a standing, and Years folds into the Numbers variant.
    'box.phase.new', 'trainer.variant.years',
    # The same control, named by another key on Android: box search heads with
    # `box.search.button`, and the session ✕ reads `common.done`.
    'a11y.action.endSession', 'box.search.title',
}
# Surfaces iOS ships that Android owes. A key leaves this set by being claimed above,
# which is what finishing the Android side looks like — and empty is what caught up looks
# like, which is where Android stands. The next one-sided surface is named here.
ANDROID_TODO = set()


def camel(key):
    """The field that reads [key] — its segments, camelCased, placeholder dropped."""
    segments = re.sub(r'\s*%(\d+\$)?(lld|@)', '', key).split('.')
    return segments[0] + ''.join(s[:1].upper() + s[1:] for s in segments[1:])


def claimed(strings):
    """field → key for every key Android reads. A counted key also answers to a
    `<field>One` sibling, where a screen has a wording for exactly one."""
    spoken = set(strings) - IOS_ONLY - ANDROID_TODO - family_keys(strings)
    table = {}
    for key in sorted(spoken):
        name = camel(key)
        table[name] = key
        if 'stringUnit' not in strings[key]['localizations']['en']:
            table[name + 'One'] = key
    return table


def family_keys(strings):
    keys = set()
    for spec in FAMILIES.values():
        if isinstance(spec, Series):
            keys.update(spec.keys(strings))
        elif isinstance(spec, dict):
            keys.update(spec.values())
        else:
            keys.update(spec)
    return keys


def catalog():
    return json.load(open(CATALOG))['strings']


def placeholders(text):
    """iOS argument syntax → java.lang.String.format's."""
    text = re.sub(r'%(\d+\$)?lld', lambda m: '%' + (m.group(1) or '') + 'd', text)
    return re.sub(r'%(\d+\$)?@', lambda m: '%' + (m.group(1) or '') + 's', text)


def value(strings, key, lang, field=''):
    """What [field] reads off [key] — the string, or the plural form its name asks for."""
    entry = strings.get(key)
    if entry is None:
        raise SystemExit('%s: not in the catalog — add it there first' % key)
    local = entry.get('localizations', {}).get(lang)
    if local is None:
        raise SystemExit('%s: no %s translation' % (key, lang))
    if 'stringUnit' in local:
        return placeholders(local['stringUnit']['value'])
    category = 'one' if field.endswith('One') else 'other'
    forms = local.get('variations', {}).get('plural', {})
    if category not in forms:
        raise SystemExit('%s (%s): no "%s" plural form' % (key, lang, category))
    return placeholders(forms[category]['stringUnit']['value'])


def literal(text, opening=0):
    """One Kotlin string literal, wrapped where the line it opens would run long.

    [opening] is what already stands on that first line (`    field = `), which is
    what makes the difference between a tidy table and one that runs off the page.
    Continuations are indented 8, so they get their own budget.
    """
    body = (text.replace('\\', '\\\\').replace('"', '\\"')
                .replace('$', '\\$').replace('\n', '\\n'))
    if opening + len(body) + 3 <= WIDTH:
        return '"%s"' % body
    lines, current, budget = [], '', WIDTH - opening - 5   # 5: quotes, space, plus
    for word in body.split(' '):
        candidate = (current + ' ' + word) if current else word
        if current and len(candidate) > budget:
            lines.append(current + ' ')
            current, budget = word, WIDTH - 8 - 5
        else:
            current = candidate
    lines.append(current)
    return ' +\n        '.join('"%s"' % line for line in lines)


def fields():
    """Field names in the order Chrome.kt declares them — which is the order the
    generated tables answer in, so the struct and its two tables read alike."""
    return re.findall(r'^    val (\w+):', open(STRUCT).read(), re.M)


def render(lang, code, name, strings):
    body = [HEADER % {'name': name, 'code': code,
                      'fallback': FALLBACK if lang == 'en' else ''}]
    table = claimed(strings)
    for field in fields():
        spec = FAMILIES.get(field) or table.get(field)
        if isinstance(spec, Series):
            spec = spec.keys(strings)
        if spec is None:
            raise SystemExit('Chrome.%s: names no catalog key — a field is its key '
                             'camelCased' % field)
        opening = len('    override val %s = ' % field)
        if isinstance(spec, list):
            entries = ''.join('        %s,\n' % literal(value(strings, k, lang, field), 8)
                              for k in spec)
            body.append('    override val %s = listOf(\n%s    )\n' % (field, entries))
        elif isinstance(spec, dict):
            entries = ''.join('        "%s" to %s,\n'
                              % (i, literal(value(strings, k, lang, field), 12 + len(i)))
                              for i, k in spec.items())
            body.append('    override val %s = mapOf(\n%s    )\n' % (field, entries))
        else:
            body.append('    override val %s = %s\n'
                        % (field, literal(value(strings, spec, lang, field), opening)))
    return ''.join(body) + '}\n'


def unclassified(strings, declared_fields):
    """Where a key, the two sets, and Chrome.kt's field list disagree."""
    table, both = claimed(strings), IOS_ONLY & ANDROID_TODO
    return ['%s: in IOS_ONLY and ANDROID_TODO at once' % k for k in sorted(both)] + \
           ['%s: declared unclaimed but not in the catalog' % k
            for k in sorted((IOS_ONLY | ANDROID_TODO) - set(strings))] + \
           ['%s: no field reads it — declare `val %s` in Chrome.kt, or name it in '
            'IOS_ONLY (Android has no use for it) or ANDROID_TODO (Android owes it)'
            % (k, camel(k))
            for f, k in sorted(table.items()) if not f.endswith('One')
            and f not in declared_fields]


def main():
    strings = catalog()
    declared = set(fields())
    orphan = sorted(set(FAMILIES) - declared)
    if orphan:
        print('FAMILIES names fields Chrome.kt does not declare: %s' % ', '.join(orphan))
        return 1

    gaps = unclassified(strings, declared)
    if gaps:
        print('\n'.join(gaps), file=sys.stderr)
        return 1

    # --check is the bare report under the name the pre-commit hook calls it by.
    fix = '--fix' in sys.argv
    drifted = []
    for lang, path in TABLES.items():
        code, name = lang.capitalize(), {'de': 'German', 'en': 'English'}[lang]
        wanted = render(lang, code, name, strings)
        if open(path).read() == wanted:
            continue
        drifted.append(os.path.relpath(path, ROOT))
        if fix:
            open(path, 'w').write(wanted)

    if drifted and not fix:
        print('%s: not what the catalog says — run `scripts/chrome.py --fix`'
              % ', '.join(drifted), file=sys.stderr)
        return 1
    print('%d chrome fields%s; %d keys Android owes' %
          (len(fields()),
           ' — rewrote %s' % ', '.join(drifted) if drifted else ' — in step with the catalog',
           len(ANDROID_TODO)))
    return 0


if __name__ == '__main__':
    sys.exit(main())

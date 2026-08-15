#!/usr/bin/env python3
"""Write the Android chrome tables from the iOS String Catalog.

One surface, one sentence: `Localizable.xcstrings` holds every chrome string the
product says, and `ChromeDe.kt`/`ChromeEn.kt` are generated from it. The two tables
used to be kept in step by hand and had drifted on some fifty strings — the same
button reading "Sprecher & Lizenzen" on one phone and "Impressum & Lizenzen" on the
other — which is exactly what a generator cannot let happen.

    scripts/chrome.py            # report drift
    scripts/chrome.py --fix      # rewrite both tables
    scripts/chrome.py --check    # exit 1 if they are not what the catalog says

The catalog is the source in both directions of work: a new Android string is added
there (and named in ANDROID_ONLY in scripts/strings.py, so the iOS drift check knows
no Swift will ever ask for it), then this regenerates the tables.

[MAPPING] is field → key. A field may also take:
  ('key %lld', 'one')   one plural category, where Android reads a String per form
  ['key.0', 'key.1']    a List<String>, in order
  {'id': 'key'}         a Map<String, String>

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
internal val Chrome%(code)s = Chrome(
'''
FALLBACK = ('\n *\n * Also the one every source without chrome of its own falls back to'
            ' ([Chrome.forSource]).')

MAPPING = {
    'aboutButton': 'settings.about',
    'almostHeard': 'session.almost.heard',
    'almostTypo': 'session.almost.typo',
    'alphabetSpeakExample': 'alphabet.speakExample',
    'alphabetSpeakName': 'alphabet.speakName',
    'alphabetTitle': 'trainer.alphabet',
    'also': 'grammar.also %@',
    'answerCorrect': 'a11y.correct',
    'answerDigits': 'trainer.answer.digits',
    'answerPlaceholder': 'session.answer.placeholder %@',
    'answerWrong': 'a11y.wrong',
    'audioOff': 'audio.off',
    'audioToggle': 'settings.audio.title',
    'audioToggleHint': 'settings.audio.hint',
    'bestStreak': 'trainer.bestStreak %@',
    'boxNav': 'nav.box',
    'boxSubtitle': 'box.cardsInProgress %@ %@',
    'boxTitle': 'box.title',
    'cancel': 'common.cancel',
    'caughtUpTitle': 'heute.caughtUp.title',
    'check': 'common.check',
    'chooseTitle': 'onboarding.welcome',
    'close': 'common.close',
    'combineLocked': 'numbers.combine.locked',
    'consolidatedLabel': 'box.consolidated',
    'copyMismatch': 'session.copyMismatch',
    'copyPrompt': 'session.copy.placeholder %@',
    'countriesBest': 'countries.best %@',
    'countriesFastHint': 'countries.fast.hint',
    'countriesPace': 'countries.pace',
    'countriesPage': 'countries.title %@',
    'countriesReference': 'countries.reference',
    'countriesReverseHint': 'countries.reverse.hint %@ %@',
    'countriesTitle': 'trainer.countries',
    'countryAskCountry': 'countries.ask.country',
    'countryAskFlag': 'countries.ask.flag',
    'countryAskLanguage': 'countries.ask.language',
    'countryAskNationality': 'countries.ask.nationality',
    'countryAskSpokenIn': 'countries.ask.spokenIn',
    'countryAskSpokenWhere': 'countries.ask.spokenWhere',
    'creditsCommons': 'credits.commonsNote',
    'creditsTitle': 'credits.title',
    'creditsUnmodified': 'credits.unmodified',
    'dayConsolidated': 'heute.done.consolidated %@',
    'dayMany': 'common.dayMany',
    'dayOne': 'common.dayOne',
    'doneToday': 'heute.done.title',
    'dueLabel': 'box.due',
    'emptyBoxAction': 'heute.empty.action',
    'emptyBoxMessage': 'heute.empty.message',
    'emptyBoxTitle': 'heute.empty.title',
    'enableSound': 'audio.enable',
    'errorCatalogMissing': 'error.catalogMissing',
    'errorContentUnavailable': 'error.contentUnavailable %@',
    'errorResetFailed': 'error.resetFailed %@',
    'errorTitle': 'error.title',
    'errorUnknownProfile': 'error.unknownProfile %@ %@',
    'extraRound': 'heute.done.extraRound',
    'finish': 'common.done',
    'freshLabel': 'box.fresh',
    'good': 'rating.good',
    'growthGrew': 'session.finished.grew',
    'growthOpened': 'session.finished.growth.opened',
    'hard': 'rating.hard',
    'heuteTitle': 'heute.title',
    'iLearn': 'settings.target.title',
    'iSpeak': 'settings.source.title',
    'keepPracticing': 'session.finished.keepPracticing',
    'last14Days': 'progress.last14Days',
    'letsGo': 'onboarding.start',
    'letterChoice': 'a11y.letterChoice %@',
    'lettersDictation': 'letters.dictation',
    'lettersHear': 'letters.hear',
    'lettersPage': 'letters.title %@',
    'lettersSpell': 'letters.spell',
    'lettersTitle': 'trainer.letters',
    'lettersUnavailable': 'letters.unavailable',
    'level': 'trainer.level %@',
    'lookUp': 'trainer.lookup',
    'modifierFast': 'trainer.modifier.fast',
    'modifierFastHint': 'trainer.modifier.fast.hint',
    'modifierMix': 'trainer.modifier.mix',
    'modifierMixHint': 'trainer.modifier.mix.hint',
    'modifierReverse': 'trainer.modifier.reverse',
    'modifierReverseHint': 'trainer.modifier.reverse.hint',
    'newLabel': 'box.new',
    'newPlace': 'trainer.newPlace %@',
    'newRecord': 'trainer.newRecord',
    'next': 'common.next',
    'numbersNotes': 'numbers.notes',
    'numbersPage': 'numbers.title %@',
    'numbersReference': 'numbers.reference',
    'numbersTitle': 'trainer.numbers',
    'otherWordNote': 'session.otherWord %@ %@',
    'overviewPractice': 'overview.practice',
    'overviewStart': 'overview.start',
    'ownWordAdd': 'box.ownWords.add',
    'ownWordInLanguage': 'box.ownWords.inLanguage %@',
    'ownWordPicture': 'box.ownWords.picture',
    'ownWordRemove': 'box.ownWords.remove',
    'ownWordTitle': 'box.ownWords.title',
    'ownWordsExplainer': 'box.ownWords.explainer',
    'ownWordsTitle': 'box.ownWords',
    'packArea': 'box.enqueue %@',
    'packDone': 'box.enqueueDone',
    'packWord': 'box.packWord',
    'packedWord': 'box.packedWord',
    'phaseLearning': 'phase.learning',
    'phaseRelearning': 'phase.relearning',
    'phaseReview': 'phase.review',
    'pluralEquals': 'grammar.plural.equals',
    'pluralForm': 'grammar.plural %@',
    'pluralOnly': 'grammar.plural.only',
    'practice': 'overview.practice',
    'profileHint': 'settings.profile.hint',
    'progressFresh': 'progress.freshCount %@',
    'progressTitle': 'progress.title',
    'promptInLanguage': 'trainer.promptInLanguage %@',
    'pronounce': 'a11y.pronounce',
    'readAloud': 'a11y.readAloud',
    'record': 'trainer.record %@',
    'recordSpoken': 'a11y.recordSuffix %@',
    'replayPrompt': 'a11y.replayPrompt',
    'reset': 'common.reset',
    'resetButton': 'settings.reset.button',
    'resetConfirm': 'settings.reset.confirm %@',
    'resetHint': 'settings.reset.hint',
    'restHint': 'session.finished.restHint',
    'reveal': 'session.reveal',
    'roundAllDone': 'session.summary.allDone',
    'roundConsolidated': 'session.summary.consolidated %@',
    'roundNew': 'session.summary.new %@',
    'roundReviewed': 'session.summary.reviewed %@',
    'search': 'box.search',
    'searchAreas': 'box.search.areas',
    'searchClear': 'search.clear',
    'searchHint': 'box.search.hint',
    'searchNothing': 'box.search.nothing %@',
    'searchPlaceholder': 'box.search.placeholder',
    'searchWords': 'box.search.words',
    'searchWriteOwn': 'box.search.writeOwn %@',
    'sessionDone': 'session.finished.title',
    'sessionSomeCards': 'heute.session.someCards',
    'sessionStart': 'heute.session.start',
    'settingsTitle': 'settings.title',
    'skipStep': 'session.skipCopy',
    'stageChoiceConfusable': 'letters.stage.choiceConfusable',
    'stageChoiceConfusableHint': 'letters.stage.choiceConfusable.hint',
    'stageChoiceEasy': 'letters.stage.choiceEasy',
    'stageChoiceEasyHint': 'letters.stage.choiceEasy.hint',
    'stageDictation': 'letters.stage.dictation',
    'stageDictationHint': 'letters.stage.dictation.hint',
    'stageDictationLocked': 'letters.stage.dictation.locked',
    'stageEntry': 'letters.entry',
    'stageTyped': 'letters.stage.typed',
    'stageTypedHint': 'letters.stage.typed.hint',
    'stateCollapsed': 'a11y.collapsed',
    'stateExpanded': 'a11y.expanded',
    'stateOff': 'a11y.off',
    'stateOn': 'a11y.on',
    'streak': 'trainer.streak %@',
    'streakRecord': 'session.finished.streakRecord',
    'streakSpoken': 'a11y.streakInARow %@',
    'suspended': 'box.suspended',
    'tapToHear': 'reference.tapToHear',
    'tomorrowFresh': 'heute.done.tomorrowFresh',
    'tomorrowPacked': 'heute.done.packed',
    'trainingSubtitle': 'trainer.subtitle',
    'trainingTitle': 'trainer.title',
    'typoNote': 'session.typoNote',
    'unknown': 'rating.unknown',
    'updateButton': 'settings.update.button',
    'updateDownload': 'settings.update.download',
    'updateOfferBody': 'settings.update.offer',
    'updateOfferTitle': 'settings.update.title',
    'updateViaObtainium': 'settings.update.obtainium',
    'unlockPrefix': 'numbers.unlock',
    'variantClock': 'trainer.clock',
    'variantForms': 'trainer.forms',
    'variantPhrases': 'trainer.phrases',
    'wake': 'box.wake',

    # One plural category of a counted key — Android reads a String per form,
    # and `countLine` formats whichever of the two it picked.
    'activityDays': ('a11y.activity14Days %lld', 'other'),
    'creditsRecordings': ('credits.recordings %lld', 'other'),
    'dayAhead': ('heute.session.ahead %lld', 'other'),
    'dayAheadOne': ('heute.session.ahead %lld', 'one'),
    'dayNewCards': ('heute.session.newCards %lld', 'other'),
    'dayNewCardsOne': ('heute.session.newCards %lld', 'one'),
    'dayReviews': ('heute.session.reviews %lld', 'other'),
    'dayReviewsOne': ('heute.session.reviews %lld', 'one'),
    'digitsMany': ('trainer.digits %lld', 'other'),
    'digitsOne': ('trainer.digits %lld', 'one'),
    'phrasesLocked': ('box.phrasesLockedShort %lld', 'other'),
    'phrasesLockedSpoken': ('box.phrasesLocked %lld', 'other'),
    'sessionHeldBack': ('heute.session.heldBack %lld', 'other'),
    'streakDays': ('a11y.streakDays %lld', 'other'),
    'streakDaysOne': ('a11y.streakDays %lld', 'one'),
    'tasksDone': ('trainer.tasksDone %lld', 'other'),
    'tasksDoneOne': ('trainer.tasksDone %lld', 'one'),
    'tomorrowDue': ('heute.done.tomorrowDue %lld', 'other'),

    # Families — one key per entry, in the order the reader indexes them.
    'countryRungHints': ['countries.rung.%d.hint' % i for i in range(1, 10)],
    'countryRungs': ['countries.rung.%d' % i for i in range(1, 10)],
    'countryTiers': ['countries.tier.%d' % i for i in range(1, 5)],
    'growthBlooming': ['session.finished.growth.blooming.%d' % i for i in range(3)],
    'growthGrown': ['session.finished.growth.grown.%d' % i for i in range(3)],
    'growthSown': ['session.finished.growth.sown.%d' % i for i in range(3)],
    'headlineFreshSet': ['heute.session.freshSet.%d' % i for i in range(3)],
    'headlineReviews': ['heute.session.reviews.%d' % i for i in range(3)],
    'headlineWarmUp': ['heute.session.warmUp.%d' % i for i in range(3)],

    'numberSections': {section: 'numbers.section.%s' % section for section in
                       ('base', 'tens', 'irregulars', 'compounds',
                        'hundreds', 'places', 'forms')},
}


def catalog():
    return json.load(open(CATALOG))['strings']


def placeholders(text):
    """iOS argument syntax → java.lang.String.format's."""
    text = re.sub(r'%(\d+\$)?lld', lambda m: '%' + (m.group(1) or '') + 'd', text)
    return re.sub(r'%(\d+\$)?@', lambda m: '%' + (m.group(1) or '') + 's', text)


def value(strings, key, lang, category=None):
    entry = strings.get(key)
    if entry is None:
        raise SystemExit('%s: not in the catalog — add it there first' % key)
    local = entry.get('localizations', {}).get(lang)
    if local is None:
        raise SystemExit('%s: no %s translation' % (key, lang))
    if category is None:
        if 'stringUnit' not in local:
            raise SystemExit('%s: counted key needs a plural category in MAPPING' % key)
        return placeholders(local['stringUnit']['value'])
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
    for field in fields():
        spec = MAPPING.get(field)
        if spec is None:
            raise SystemExit('Chrome.%s: no key in scripts/chrome.py MAPPING' % field)
        opening = len('    %s = ' % field)
        if isinstance(spec, tuple):
            body.append('    %s = %s,\n'
                        % (field, literal(value(strings, spec[0], lang, spec[1]), opening)))
        elif isinstance(spec, list):
            entries = ''.join('        %s,\n' % literal(value(strings, k, lang), 8) for k in spec)
            body.append('    %s = listOf(\n%s    ),\n' % (field, entries))
        elif isinstance(spec, dict):
            entries = ''.join('        "%s" to %s,\n'
                              % (i, literal(value(strings, k, lang), 12 + len(i)))
                              for i, k in spec.items())
            body.append('    %s = mapOf(\n%s    ),\n' % (field, entries))
        else:
            body.append('    %s = %s,\n' % (field, literal(value(strings, spec, lang), opening)))
    return ''.join(body) + ')\n'


def main():
    strings = catalog()
    unused = sorted(set(MAPPING) - set(fields()))
    if unused:
        print('MAPPING names fields Chrome.kt does not declare: %s' % ', '.join(unused))
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
    print('%d chrome fields%s' % (len(MAPPING),
                                  ' — rewrote %s' % ', '.join(drifted) if drifted else
                                  ' — in step with the catalog'))
    return 0


if __name__ == '__main__':
    sys.exit(main())

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

[MAPPING] is field → key. A field may also take:
  ['key.0', 'key.1']    a List<String>, in order
  {'id': 'key'}         a Map<String, String>

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

MAPPING = {
    'aboutButton': 'settings.about',
    'activityDays': 'a11y.count.activity14Days %lld',
    'almostHeard': 'session.almost.heard',
    'almostTypo': 'session.almost.typo',
    'alphabetSpeakExample': 'alphabet.speakExample',
    'alphabetSpeakName': 'alphabet.speakName',
    'alphabetTitle': 'trainer.alphabet',
    'also': 'grammar.also %@',
    'answerAlmost': 'a11y.verdict.almost',
    'answerCorrect': 'a11y.verdict.correct',
    'answerDigits': 'trainer.answer.digits',
    'answerPlaceholder': 'session.answer.placeholder %@',
    'answerWrong': 'a11y.verdict.wrong',
    'audioHintOff': 'settings.audio.hint.off',
    'audioHintRecordings': 'settings.audio.hint.recordings',
    'audioHintTts': 'settings.audio.hint.tts',
    'audioOff': 'audio.off',
    'audioOptionOff': 'settings.audio.option.off',
    'audioOptionRecordings': 'settings.audio.option.recordings',
    'audioOptionTts': 'settings.audio.option.tts',
    'audioToggle': 'settings.audio.title',
    'back': 'common.back',
    'bestStreak': 'trainer.bestStreak %@',
    'boxNav': 'nav.box',
    'boxNoAudio': 'box.noAudio',
    'boxSubtitle': 'box.cardsInProgress %@ %@',
    'boxTapToHear': 'box.tapToHear',
    'boxTitle': 'box.title',
    'cancel': 'common.cancel',
    'cantListen': 'session.hear.cantListen',
    'cardActions': 'box.card.actions',
    'cardForget': 'box.card.forget',
    'cardOwnFrom': 'box.card.ownFrom',
    'cardPosition': 'session.cardPosition %@ %@',
    'caughtUpTitle': 'heute.caughtUp.title',
    'check': 'common.check',
    'chooseTitle': 'onboarding.welcome',
    'close': 'common.close',
    'coachGrade': 'session.coach.grade',
    'coachRecognize': 'session.coach.recognize',
    'coachWrite': 'session.coach.write',
    'combineLocked': 'numbers.combine.locked',
    'copyMismatch': 'session.copyMismatch',
    'copyPrompt': 'session.copy.placeholder %@',
    'countriesBest': 'countries.best %@',
    'countriesFastHint': 'countries.fast.hint',
    'countriesPace': 'countries.pace',
    'countriesPage': 'countries.title %@',
    'countriesReference': 'countries.reference',
    'countriesReverseHint': 'countries.reverse.hint %@ %@',
    'countryAskCountry': 'countries.ask.country',
    'countryAskFlag': 'countries.ask.flag',
    'countryAskLanguage': 'countries.ask.language',
    'countryAskNationality': 'countries.ask.nationality',
    'countryAskSpokenIn': 'countries.ask.spokenIn',
    'countryAskSpokenWhere': 'countries.ask.spokenWhere',
    'creditsCommons': 'credits.commonsNote',
    'creditsRecordings': 'credits.recordings %lld',
    'creditsTitle': 'credits.title',
    'creditsUnmodified': 'credits.unmodified',
    'dayAhead': 'heute.session.ahead %lld',
    'dayAheadOne': 'heute.session.ahead %lld',
    'dayConsolidated': 'heute.done.consolidated %@',
    'dayMany': 'common.dayMany',
    'dayNewCards': 'heute.session.newCards %lld',
    'dayNewCardsOne': 'heute.session.newCards %lld',
    'dayNewWordsOnly': 'heute.session.newWordsOnly %@',
    'dayOne': 'common.dayOne',
    'dayReviews': 'heute.session.reviews %lld',
    'dayReviewsOne': 'heute.session.reviews %lld',
    'dequeueArea': 'box.dequeue %@',
    'digits': 'trainer.digits %lld',
    'digitsOne': 'trainer.digits %lld',
    'doneToday': 'heute.done.title',
    'dueLabel': 'box.due',
    'enableSound': 'audio.enable',
    'errorCatalogMissing': 'error.catalogMissing',
    'errorContentUnavailable': 'error.contentUnavailable %@',
    'errorResetFailed': 'error.resetFailed %@',
    'errorTitle': 'error.title',
    'errorUnknownProfile': 'error.unknownProfile %@ %@',
    'extraRound': 'heute.done.extraRound',
    'feedbackCopy': 'feedback.copy',
    'feedbackMail': 'settings.feedback',
    'feedbackNeedsTranslation': 'feedback.needsTranslation',
    'feedbackScopeAll': 'feedback.scope.all',
    'feedbackScopeNew': 'feedback.scope.new',
    'feedbackSend': 'feedback.send',
    'feminineForm': 'a11y.glyph.feminineForm',
    'finish': 'common.done',
    'firstRoundGrade': 'onboarding.firstRound.grade',
    'firstRoundRecognize': 'onboarding.firstRound.recognize',
    'firstRoundTitle': 'onboarding.firstRound.title',
    'firstRoundWrite': 'onboarding.firstRound.write',
    'good': 'rating.good',
    'growthGrew': 'session.finished.grew',
    'growthOpened': 'session.finished.growth.opened',
    'hard': 'rating.hard',
    'heuteTitle': 'heute.title',
    'iLearn': 'settings.target.title',
    'iSpeak': 'settings.source.title',
    'keepPracticing': 'session.finished.keepPracticing',
    'last14Days': 'progress.last14Days',
    'learnerNameHint': 'settings.name.hint',
    'learnerNamePlaceholder': 'settings.name.placeholder',
    'learnerNameQuestion': 'onboarding.name.question',
    'learnerNameTitle': 'settings.name.title',
    'legalAddressValue': 'legal.address.value',
    'legalCompany': 'legal.company',
    'legalContactLabel': 'legal.contact.label',
    'legalDirectorLabel': 'legal.director.label',
    'legalDirectorValue': 'legal.director.value',
    'legalPrivacy': 'legal.privacy',
    'legalRegisterLabel': 'legal.register.label',
    'legalRegisterValue': 'legal.register.value',
    'legalTitle': 'legal.title',
    'legalVatLabel': 'legal.vat.label',
    'legalVatValue': 'legal.vat.value',
    'letsGo': 'onboarding.start',
    'letterChoice': 'a11y.glyph.letter %@',
    'lettersDictation': 'letters.dictation',
    'lettersHear': 'letters.hear',
    'lettersPage': 'letters.title %@',
    'lettersSpell': 'letters.spell',
    'lettersUnavailable': 'letters.unavailable',
    'level': 'trainer.level %@',
    'listenMinutesLeft': 'listen.minutesLeft %lld',
    'listenPause': 'listen.pause',
    'listenRepeat': 'listen.repeat',
    'listenResume': 'listen.resume',
    'listenSkip': 'listen.skip',
    'listenSubtitle': 'listen.subtitle',
    'listenTimer': 'listen.timer',
    'listenTitle': 'listen.title',
    'lookUp': 'trainer.lookup',
    'modifierFast': 'trainer.modifier.fast',
    'modifierFastHint': 'trainer.modifier.fast.hint',
    'modifierMix': 'trainer.modifier.mix',
    'modifierMixHint': 'trainer.modifier.mix.hint',
    'modifierReverse': 'trainer.modifier.reverse',
    'modifierReverseHint': 'trainer.modifier.reverse.hint',
    'newPlace': 'trainer.newPlace %@',
    'newRecord': 'trainer.newRecord',
    'next': 'common.next',
    'notAnswered': 'a11y.verdict.notAnswered',
    'numbersNotes': 'numbers.notes',
    'numbersPage': 'numbers.title %@',
    'numbersReference': 'numbers.reference',
    'otherWordNote': 'session.otherWord %@ %@',
    'overviewPractice': 'overview.practice',
    'overviewStart': 'overview.start',
    'ownContentReported': 'box.own.reported',
    'ownContentTitle': 'box.own.title',
    'ownWordAdd': 'box.ownWords.add',
    'ownWordAddAction': 'box.ownWords.addAction',
    'ownWordEdit': 'box.ownWords.edit',
    'ownWordInLanguage': 'box.ownWords.inLanguage %@',
    'ownWordPicture': 'box.ownWords.picture',
    'ownWordRemove': 'box.ownWords.remove',
    'ownWordSave': 'box.ownWords.save',
    'ownWordSuggestion': 'box.ownWords.explainer.suggestion',
    'ownWordSwap': 'box.ownWords.swap',
    'ownWordTitle': 'box.ownWords.title',
    'ownWordsExplainer': 'box.ownWords.explainer',
    'ownWordsTitle': 'box.ownWords',
    'packArea': 'box.enqueue %@',
    'packDone': 'box.enqueueDone',
    'packWord': 'box.packWord',
    'phaseConsolidated': 'phase.consolidated',
    'phaseLearning': 'phase.learning',
    'phaseRelearning': 'phase.relearning',
    'phaseSettled': 'phase.settled',
    'phrasesLockedShort': 'box.phrasesLockedShort %lld',
    'phrasesLockedSpoken': 'box.phrasesLocked %lld',
    'pluralEquals': 'grammar.plural.equals',
    'pluralForm': 'grammar.plural %@',
    'pluralOnly': 'grammar.plural.only',
    'practiceSuffix': 'a11y.suffix.practice %@',
    'profileHint': 'settings.profile.hint',
    'progressConsolidated': 'progress.consolidatedCount %@',
    'progressLearning': 'progress.learningCount %@',
    'promptInLanguage': 'trainer.promptInLanguage %@',
    'pronounce': 'a11y.action.pronounce',
    'queuedWord': 'box.queuedWord',
    'ratingQuestion': 'rating.question',
    'readAloud': 'a11y.action.readAloud',
    'record': 'trainer.record %@',
    'recordSpoken': 'a11y.suffix.record %@',
    'replayPrompt': 'a11y.action.replayPrompt',
    'reportAction': 'report.action',
    'reportComment': 'report.comment',
    'reportDismiss': 'report.dismiss',
    'reportEdit': 'report.edit',
    'reportExplainer': 'report.explainer',
    'reportSend': 'report.send',
    'reportTitle': 'report.title',
    'reportTyped': 'report.typed',
    'reported': 'report.reported',
    'reset': 'common.reset',
    'resetButton': 'settings.reset.button %@',
    'resetConfirm': 'settings.reset.confirm %@',
    'resetHint': 'settings.reset.hint',
    'restHint': 'session.finished.restHint',
    'restartTutorial': 'settings.restartTutorial.button',
    'restartTutorialHint': 'settings.restartTutorial.hint',
    'reveal': 'session.reveal',
    'roundAllDone': 'session.summary.allDone',
    'roundConsolidated': 'session.summary.consolidated %@',
    'roundNew': 'session.summary.new %@',
    'roundNewOnly': 'session.summary.newOnly %@',
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
    'sessionHeldBack': 'heute.session.heldBack %lld',
    'sessionShortRound': 'heute.session.shortRound',
    'sessionSomeCards': 'heute.session.someCards',
    'sessionStart': 'heute.session.start',
    'sessionTally': 'a11y.count.sessionTally %@ %@ %@',
    'settingsTitle': 'settings.title',
    'skipStep': 'session.skipCopy',
    'sleep': 'box.sleep',
    'sourceQuestion': 'onboarding.source.question',
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
    'stateCollapsed': 'a11y.state.collapsed',
    'stateExpanded': 'a11y.state.expanded',
    'stateOff': 'a11y.state.off',
    'stateOn': 'a11y.state.on',
    'streak': 'trainer.streak %@',
    'streakDays': 'a11y.count.streakDays %lld',
    'streakDaysOne': 'a11y.count.streakDays %lld',
    'streakRecord': 'session.finished.streakRecord',
    'streakSpoken': 'a11y.count.streakInARow %@',
    'suspended': 'box.suspended',
    'tapToHear': 'reference.tapToHear',
    'targetQuestion': 'onboarding.target.question',
    'tasksDone': 'trainer.tasksDone %lld',
    'tasksDoneOne': 'trainer.tasksDone %lld',
    'tomorrowDue': 'heute.done.tomorrowDue %lld',
    'tomorrowFresh': 'heute.done.tomorrowFresh',
    'tomorrowPacked': 'heute.done.packed',
    'trainerCountries': 'trainer.countries',
    'trainerLetters': 'trainer.letters',
    'trainerNumbers': 'trainer.numbers',
    'trainingSubtitle': 'trainer.subtitle',
    'trainingTitle': 'trainer.title',
    'unknown': 'rating.unknown',
    'unlockPrefix': 'numbers.unlock',
    'unpackWord': 'box.unpackWord',
    'updateButton': 'settings.update.button',
    'updateDownload': 'settings.update.download',
    'updateOfferBody': 'settings.update.offer',
    'updateOfferTitle': 'settings.update.title',
    'updateViaObtainium': 'settings.update.obtainium',
    'variantClock': 'trainer.clock',
    'variantForms': 'trainer.forms',
    'variantPhrases': 'trainer.phrases',
    'wake': 'box.wake',
    'whyBreadthBody': 'onboarding.why.breadth.body',
    'whyBreadthTitle': 'onboarding.why.breadth.title',
    'whyCompanionBody': 'onboarding.why.companion.body',
    'whyCompanionTitle': 'onboarding.why.companion.title',
    'whyGrammarBody': 'onboarding.why.grammar.body',
    'whyGrammarTitle': 'onboarding.why.grammar.title',
    'whyTitle': 'onboarding.why.title',
    'widgetAwaitingBody': 'widget.awaiting.body',
    'widgetAwaitingTitle': 'widget.awaiting.title',

    # Families — one key per entry, in the order the reader indexes them.
    'countryRungHints': ['countries.rung.%d.hint' % i for i in range(1, 10)],
    'countryRungs': ['countries.rung.%d' % i for i in range(1, 10)],
    'countryTiers': ['countries.tier.%d' % i for i in range(1, 5)],
    'greetDay': ['heute.greeting.day.%d %%@' % i for i in range(2)],
    'greetMorningAddressee': 'heute.greeting.morning.addressee',
    'greetNightAddressee': 'heute.greeting.night.addressee',
    'greetEvening': ['heute.greeting.evening.%d %%@' % i for i in range(2)],
    'greetMorning': ['heute.greeting.morning.%d %%@' % i for i in range(2)]
                    + ['heute.greeting.morning.epithet %@'],
    'greetNight': ['heute.greeting.night.%d %%@' % i for i in range(2)]
                  + ['heute.greeting.night.epithet %@'],
    'growthBlooming': ['session.finished.growth.blooming.%d' % i for i in range(3)],
    'growthGrown': ['session.finished.growth.grown.%d' % i for i in range(3)],
    'growthSown': ['session.finished.growth.sown.%d' % i for i in range(3)],
    'headlineFreshSet': ['heute.session.freshSet.%d' % i for i in range(3)],
    'headlineReviews': ['heute.session.reviews.%d' % i for i in range(3)],
    'headlineStreak': ['heute.session.streakReminder.%d' % i for i in range(3)],
    'headlineWarmUp': ['heute.session.warmUp.%d' % i for i in range(3)],

    'numberSections': {section: 'numbers.section.%s' % section for section in
                       ('base', 'tens', 'irregulars', 'compounds',
                        'hundreds', 'places', 'forms')},
}

# Catalog keys no chrome field claims, each with the reason it does not. Together with
# [MAPPING] these account for every key in the catalog — `main` checks that — so a string
# written for one phone is classified the day it lands, and the two kinds of not-claimed
# stay apart: what Android has no use for, and what it still owes.
IOS_ONLY = {
    # The voice-download banner walks the reader to an iOS Settings path; which voices an
    # Android device has is its TTS engine's own affair. `common.dismiss` is that banner's ✕.
    'common.dismiss', 'heute.voiceUpgrade.path', 'heute.voiceUpgrade.title %@',
    'settings.audio.voiceUpgrade %@',
    # Xcode canvas scaffolding, inside `#Preview("Palette")` in Design/Theme.swift.
    'preview.skip', 'preview.tokens',
    # A Swift switch owes every case a branch, including two kern never hands out: new is
    # the absence of a standing, and Years folds into the Numbers variant.
    'phase.new', 'trainer.years',
    # The same control, named by another key on Android: box search heads with `box.search`,
    # the credits door is `settings.about`, and the session ✕ reads `common.done`.
    'a11y.action.endSession', 'box.search.title', 'settings.credits',
}
# Surfaces iOS ships that Android owes. A key leaves this set by being claimed above,
# which is what finishing the Android side looks like — and empty is what caught up looks
# like, which is where Android stands. The next one-sided surface is named here.
ANDROID_TODO = set()


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
    for field in fields():
        spec = MAPPING.get(field)
        if spec is None:
            raise SystemExit('Chrome.%s: no key in scripts/chrome.py MAPPING' % field)
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


def claimed():
    """Every catalog key some chrome field reads."""
    keys = set()
    for spec in MAPPING.values():
        keys.update(spec.values() if isinstance(spec, dict) else
                    spec if isinstance(spec, list) else [spec])
    return keys


def unclassified(strings):
    """Where a key and the three sets that account for it disagree."""
    mine, declared = claimed(), IOS_ONLY | ANDROID_TODO
    return ['%s: claimed in MAPPING and declared unclaimed' % k
            for k in sorted(mine & declared)] + \
           ['%s: declared unclaimed but not in the catalog' % k
            for k in sorted(declared - set(strings))] + \
           ['%s: no chrome field reads it — give one its key, or name it in IOS_ONLY '
            '(Android has no use for it) or ANDROID_TODO (Android owes it)' % k
            for k in sorted(set(strings) - mine - declared)]


def main():
    strings = catalog()
    unused = sorted(set(MAPPING) - set(fields()))
    if unused:
        print('MAPPING names fields Chrome.kt does not declare: %s' % ', '.join(unused))
        return 1

    gaps = unclassified(strings)
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
          (len(MAPPING),
           ' — rewrote %s' % ', '.join(drifted) if drifted else ' — in step with the catalog',
           len(ANDROID_TODO)))
    return 0


if __name__ == '__main__':
    sys.exit(main())

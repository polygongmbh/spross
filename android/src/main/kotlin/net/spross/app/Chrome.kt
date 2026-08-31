package net.spross.app

import androidx.compose.runtime.Stable
import net.spross.kern.catalog.LanguageChoices

/**
 * UI chrome strings, rendered in the KNOWN language when chrome exists
 * (de/en today), otherwise en — design.md "Profile & onboarding".
 *
 * The two tables live beside this file ([ChromeDe], [ChromeEn]) and are GENERATED from
 * the iOS String Catalog by `scripts/chrome.py` — the words themselves are never written
 * here. A new field is declared below, given its key in that script's MAPPING, and worded
 * in `App/Sources/Resources/Localizable.xcstrings`; a pre-commit check keeps the three
 * in step, so the same surface cannot read differently on the two phones.
 * Placeholders are java-format, rendered with `.format(...)`.
 *
 * What a string MEANS is its catalog entry's `comment`, where the other phone and whoever
 * translates it read the same sentence. What a field says here is what only Kotlin can:
 * the shape a list or map takes, the kern enum it is indexed by, and the placeholders owed.
 *
 * An INTERFACE, and it cannot go back to being a class: a constructor holds one parameter per
 * string, and the JVM caps a method descriptor at 255 slots including `this` — a `data class`
 * spends eight more on `copy$default`'s bitmasks — so past ~250 strings the table died at class
 * LOAD with a `ClassFormatError` that named no field and took every chrome test with it.
 * Two generated objects implement this instead, which removes the ceiling rather than raising
 * it and turns a string one table is short of into a named "does not implement abstract member"
 * at the table that is short of it. `ChromeTableShapeTest` holds the rule.
 *
 * `@Stable` is load-bearing, not decoration: an interface has no fields for the compiler to
 * infer stability from, so without it a `chrome: Chrome` parameter reads as unstable. With it,
 * Compose compares the two singletons by `==` — identity here, which is exactly right.
 */
@Stable
interface Chrome {
    /**
     * The line over the day's card, naming the language the profile is learning (%s), in
     * the words that fit the hour: one list per [net.spross.kern.box.DayPart], indexed by
     * [net.spross.kern.box.partVariant]. Every line here NAMES the language and asks rather
     * than states — the register that SPEAKS it comes from the catalog instead, and the two
     * hours that lend an epithet carry a third line for it. [heuteTitle] stands in only
     * where no profile names a language yet.
     */
    val greetMorning: List<String>
    val greetDay: List<String>
    val greetEvening: List<String>
    val greetNight: List<String>
    val greetMorningAddressee: String
    val greetNightAddressee: String
    val heuteTitle: String
    val extraRound: String
    val doneToday: String
    val dueLabel: String
    val chooseTitle: String
    val sourceQuestion: String
    val targetQuestion: String
    val iSpeak: String
    val iLearn: String
    val learnerNameQuestion: String
    val letsGo: String
    val back: String
    val whyTitle: String
    val whyBreadthTitle: String
    val whyBreadthBody: String
    val whyCompanionTitle: String
    val whyCompanionBody: String
    val whyGrammarTitle: String
    val whyGrammarBody: String
    val firstRoundTitle: String
    val firstRoundRecognize: String
    val firstRoundGrade: String
    val firstRoundWrite: String
    val check: String
    val reveal: String
    val next: String
    val also: String              // %s = the forms a card also answers to
    val otherWordNote: String     // %1$s = the word typed, %2$s = what it means
    val answerPlaceholder: String // %s = target language name
    val ratingQuestion: String
    /** The three the FIRST round teaches itself with, one per moment ([SessionCoach]). */
    val coachRecognize: String
    val coachGrade: String
    val coachWrite: String
    val hard: String
    val good: String
    val unknown: String           // the third verdict — a judgment, not an instruction
    val sessionDone: String
    val keepPracticing: String
    val finish: String
    val pluralEquals: String
    val pluralOnly: String
    val pluralForm: String        // %s = the plural, where a card carries one
    val feminineForm: String
    val readAloud: String         // the switch's stable a11y label — never flips
    val stateOn: String
    val stateOff: String
    val pronounce: String         // "say it again" action on a word
    val aboutButton: String
    val feedbackMail: String
    val updateButton: String
    val updateOfferTitle: String
    val updateOfferBody: String   // what Obtainium does, and what going without costs
    val updateViaObtainium: String
    val updateDownload: String
    val audioToggle: String
    val audioOptionOff: String      // the box's three-way audio preference
    val audioOptionRecordings: String
    val audioOptionTts: String
    val audioHintOff: String        // the hint under the picker, per selection
    val audioHintRecordings: String
    val audioHintTts: String
    val creditsTitle: String
    val creditsRecordings: String // %d = how many files the speaker contributed
    val creditsUnmodified: String
    val creditsCommons: String
    /** The address the notice answers on is kern's ([net.spross.kern.Legal]). */
    val legalTitle: String
    val legalCompany: String
    val legalAddressValue: String
    val legalDirectorLabel: String
    val legalDirectorValue: String
    val legalRegisterLabel: String
    val legalRegisterValue: String
    val legalVatLabel: String
    val legalVatValue: String
    val legalContactLabel: String
    val legalPrivacy: String
    val trainingTitle: String
    val trainingSubtitle: String  // what free practice is, under its name
    val practiceSuffix: String
    val trainerLetters: String
    val lettersHear: String       // the question a letter-name prompt asks
    val lettersSpell: String      // …and the one a gap word asks
    val lettersDictation: String
    val letterChoice: String      // %s = the glyph — a tile's spoken name
    val replayPrompt: String      // the replay button's name
    val promptInLanguage: String  // %s = target language name
    val level: String             // %d — the rung a run stands on
    val streak: String            // %d — answers in a row, never days
    // The two captions an amber hold wears; the form itself follows, composed by
    // the reader, so the words stay one string and the layout stays each phone's.
    val almostTypo: String
    val almostHeard: String
    val audioOff: String
    val enableSound: String
    val tasksDoneOne: String      // the count-of-one form, which carries the number too
    val tasksDone: String         // %d
    val bestStreak: String        // %d
    val answerCorrect: String     // an answered tile's state, never color alone
    val answerAlmost: String      // the near miss's own — amber is not a state a reader hears
    val answerWrong: String
    val notAnswered: String

    // ── The three overview pages ────────────────────────────────────────────────
    val close: String
    val trainerNumbers: String    // the hub entry, and the variant's own name
    val numbersPage: String       // %s = the language being learnt
    val lettersPage: String       // %s
    val overviewPractice: String  // the heading the picks stand under
    val overviewStart: String     // the button both pages open a run with
    val tapToHear: String         // the gesture a reference page discloses once, under its heading
    val boxTapToHear: String
    val boxNoAudio: String
    val numbersReference: String
    val numbersNotes: String
    /**
     * Kern's band key → the heading it takes. A map, because the bands are kern's to
     * grow: one it has no wording for still gets its rows rather than printing a key.
     */
    val numberSections: Map<String, String>
    val variantClock: String
    val variantPhrases: String
    val variantForms: String
    val modifierReverse: String
    val modifierReverseHint: String
    val modifierFast: String
    val modifierFastHint: String
    val modifierMix: String
    val modifierMixHint: String
    val combineLocked: String
    val unlockPrefix: String

    // ── Inside a run ────────────────────────────────────────────────────────────
    val digitsOne: String         // the numbers rung, which counts digits
    val digits: String            // %d
    val record: String            // %d — the standing streak the run has not beaten yet
    val streakSpoken: String      // %d — the score line as a screen reader hears it
    val recordSpoken: String      // %d — appended to it
    val answerDigits: String      // a reversed task owes digits, never a language
    val newPlace: String          // %s = the place word, the first time a length appears
    val lookUp: String            // the "?" that raises the numbers page mid-run
    val newRecord: String

    // ── The letter drill's stages, as the overview lists them ───────────────────
    val stageChoiceEasy: String
    val stageChoiceEasyHint: String
    val stageChoiceConfusable: String
    val stageChoiceConfusableHint: String
    val stageTyped: String
    val stageTypedHint: String
    val stageDictation: String
    val stageDictationHint: String
    val stageDictationLocked: String
    val stageEntry: String
    val lettersUnavailable: String
    val alphabetTitle: String
    val alphabetSpeakName: String
    val alphabetSpeakExample: String

    // ── The atlas: the Länder page and its run ──────────────────────────────────
    val trainerCountries: String  // the hub entry, and what the result tile says was drilled
    val countriesPage: String     // %s = the language being learnt
    val countriesReference: String
    val countriesPace: String
    val countriesBest: String     // %d = the furthest rung any run reached
    val countriesFastHint: String // this ladder costs THREE clean wins, so it prices its own
    val countriesReverseHint: String // %1$s = the side asked in, %2$s = the side owed
    /**
     * The rungs, in the order they are climbed — one entry per rung of kern's own ladder
     * ([net.spross.kern.trainer.CountryDrill.MAX_LEVEL]), read through [countryRung].
     */
    val countryRungs: List<String>
    val countryRungHints: List<String>
    /** How far from home a reference group sits, innermost first — read through [countryTier]. */
    val countryTiers: List<String>
    val countryAskCountry: String
    val countryAskFlag: String
    val countryAskLanguage: String
    val countryAskNationality: String
    val countryAskSpokenIn: String
    val countryAskSpokenWhere: String

    // ── Box browse ──────────────────────────────────────────────────────────────
    val boxTitle: String
    val boxNav: String
    val boxSubtitle: String       // %1$d active of %2$d held
    val ownWordsTitle: String
    val ownWordsExplainer: String
    val packArea: String          // %d = what packing this shelf would add
    val packDone: String
    val packWord: String          // the single-word offer a search hit carries
    val unpackWord: String
    val dequeueArea: String
    val queuedWord: String
    val suspended: String         // the sleeping mark's name
    val wake: String
    val sleep: String
    /**
     * Dropping ONE word's progress: it goes back to new and may be offered again.
     * The card stays, and so does anything filed against it — forgetting the answers
     * does not make the translation right.
     */
    val cardActions: String
    val cardForget: String
    val cardOwnFrom: String
    val reported: String
    val progressConsolidated: String // %d
    val progressLearning: String  // %d
    val phrasesLockedShort: String // %d = phrases still waiting on their components
    val phrasesLockedSpoken: String // %d
    val stateExpanded: String
    val stateCollapsed: String
    // A card with nothing behind it has NO phase word: new is the absence of a badge. Past
    // that, a row reads one of four: [phaseLearning] while walking the learning steps,
    // [phaseRelearning] the same rung after a lapse (same color/icon, its own word),
    // [phaseSettled] once in Review but short of the consolidated bar, and
    // [phaseConsolidated] once a card has cleared it — the shelf's own count stays the
    // two-way consolidated/learning split it has always been (`AreaStatistics.learning`).
    val phaseLearning: String
    val phaseRelearning: String
    val phaseSettled: String
    val phaseConsolidated: String

    // ── Box search ──────────────────────────────────────────────────────────────
    val search: String
    val searchPlaceholder: String
    val searchHint: String        // what the field will look through, before anything is typed
    val searchAreas: String
    val searchWords: String
    val searchNothing: String     // %s = the query
    val searchWriteOwn: String    // %s = the query — the one door to writing a word
    val searchClear: String

    // ── Own-word form ───────────────────────────────────────────────────────────
    val ownWordTitle: String
    val ownWordInLanguage: String // %s = language name — both fields ask it
    val ownWordPicture: String
    val ownWordAdd: String
    val ownWordRemove: String     // the app's only deletion; catalog words sleep instead
    val ownWordEdit: String
    val ownWordSave: String
    val ownWordSwap: String
    val ownWordSuggestion: String

    // ── Reporting a problem ─────────────────────────────────────────────────────
    val reportAction: String
    val reportEdit: String
    val reportDismiss: String
    val reportTitle: String
    val reportSend: String
    val reportComment: String     // optional, and the label says so
    val reportTyped: String
    val reportExplainer: String   // who reads it, and that the word's schedule is untouched

    // ── Own content ─────────────────────────────────────────────────────────────
    val ownContentTitle: String
    val ownContentReported: String
    val ownWordAddAction: String

    // ── Feedback to the catalog ─────────────────────────────────────────────────
    val feedbackNeedsTranslation: String
    val feedbackCopy: String
    val feedbackSend: String
    val feedbackScopeNew: String
    val feedbackScopeAll: String

    // ── Box settings ────────────────────────────────────────────────────────────
    val settingsTitle: String
    val learnerNameTitle: String
    val learnerNamePlaceholder: String
    val learnerNameHint: String
    val profileHint: String
    val restartTutorial: String
    val restartTutorialHint: String
    val resetButton: String       // %s = the language being learnt, in its own name
    val resetHint: String
    val resetConfirm: String      // %s = the language being learnt, in its own name
    val cancel: String
    val reset: String

    // ── Session turn ────────────────────────────────────────────────────────────
    val copyPrompt: String        // %s = target language name — the write-it-out field
    val copyMismatch: String      // the copy was another word: the card still holds the answer
    val skipStep: String
    val cantListen: String
    val cardPosition: String
    val sessionTally: String

    // ── The day's standing (Heute) ──────────────────────────────────────────────
    val caughtUpTitle: String
    val dayReviews: String        // %d
    val dayReviewsOne: String     // the count-of-one line, verbatim
    val dayNewCards: String       // %d
    val dayNewCardsOne: String
    val dayNewWordsOnly: String    // %d
    val dayConsolidated: String   // %d — "gefestigt" needs no declining
    /** Which of the two a round names is [net.spross.kern.session.SessionOffer.summaryParts]'. */
    val dayAhead: String          // %d
    val dayAheadOne: String
    val tomorrowPacked: String
    val tomorrowFresh: String
    val tomorrowDue: String       // %d

    // ── The session offer (Heute's one card) ────────────────────────────────────
    /**
     * The phrasings each offer kind carries, indexed by
     * [net.spross.kern.session.SessionHeadline.variant] — kern picks which, from the
     * round's shape, so the same round headlines the same on both platforms.
     * Each list holds [net.spross.kern.session.SessionOffer.HEADLINE_VARIANTS] entries.
     */
    val headlineReviews: List<String>
    val headlineWarmUp: List<String>
    val headlineFreshSet: List<String>
    /** What the card says instead once a standing run is still owed today's work. */
    val headlineStreak: List<String>
    val sessionSomeCards: String
    val sessionHeldBack: String   // %d
    val sessionStart: String
    val sessionShortRound: String

    // ── Listening (Heute's one row, and the run it opens) ───────────────────────
    val listenTitle: String
    val listenSubtitle: String
    val listenPause: String
    val listenResume: String
    val listenSkip: String
    val listenRepeat: String
    val listenTimer: String
    val listenMinutesLeft: String   // %d

    // ── Load failures ───────────────────────────────────────────────────────────
    val errorTitle: String
    val errorCatalogMissing: String
    val errorContentUnavailable: String // %s = what the system said
    val errorUnknownProfile: String     // %1$s = known, %2$s = learnt
    val errorResetFailed: String        // %s

    // ── Round completion ────────────────────────────────────────────────────────
    val roundNew: String          // %d
    val roundNewOnly: String      // %d
    val roundConsolidated: String // %d
    val roundReviewed: String     // %d
    val roundAllDone: String      // the round had nothing nameable in it
    val restHint: String          // today's recall is strained; more reps buy little
    val streakRecord: String

    val growthGrew: String
    val growthOpened: String
    val growthBlooming: List<String>
    val growthSown: List<String>
    val growthGrown: List<String>

    // ── Activity strip ──────────────────────────────────────────────────────────
    val last14Days: String
    val activityDays: String      // %d = days worked inside the window
    val streakDays: String        // %d — days in a row, the strip's own label
    val streakDaysOne: String
    val dayOne: String            // the badge's unit word, by count
    val dayMany: String

    // ── Home-screen widget ──────────────────────────────────────────────────────
    val widgetAwaitingTitle: String
    val widgetAwaitingBody: String
    companion object {
        /** Which language carries the chrome — and the en fallback — is kern's rule; only the table map is ours. */
        fun forSource(source: String): Chrome =
            if (LanguageChoices.chromeLanguage(source) == "de") ChromeDe else ChromeEn
    }
}

/**
 * The declining count line: [one] at exactly 1, else [many] — and the count goes into
 * whichever was picked. Both forms carry the number, because both are one plural category
 * of the same counted key in the catalog ("%d Wiederholung" / "%d Wiederholungen").
 */
fun countLine(one: String, many: String, count: Int): String =
    (if (count == 1) one else many).format(count)

/**
 * An amber hold's line: the caption, then the form it owes. The caption alone is what the
 * catalog holds — iOS stacks the two, Android sets them on one line — so the words stay
 * shared and the layout stays each phone's own.
 */
fun almostLine(caption: String, form: String): String = "$caption: $form"

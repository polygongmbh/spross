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
    /**
     * Who a spoken line is addressed to when no name is known: the hour lends the word, and
     * it goes INSIDE the target-language sentence ("Tayari kujifunza, Nachteule?"), so it is
     * never rendered on its own. Morning and night only — nobody is a night owl at two in
     * the afternoon.
     */
    val greetMorningAddressee: String
    val greetNightAddressee: String
    val heuteTitle: String
    val practice: String
    val extraRound: String
    val doneToday: String
    val dueLabel: String
    val newLabel: String
    val consolidatedLabel: String
    val freshLabel: String
    val chooseTitle: String
    val chooseSubtitle: String    // what the first page asks for, under the welcome
    val iSpeak: String
    val iLearn: String
    /** The third question of the first page. */
    val learnerNameQuestion: String
    val letsGo: String
    /** The way back out of a page, wherever a flow has one behind it. */
    val back: String
    /** What Spross is for, said once before the first round asks anything of you. */
    val whyTitle: String
    val whyBreadthTitle: String
    val whyBreadthBody: String
    val whyCompanionTitle: String
    val whyCompanionBody: String
    val whyGrammarTitle: String
    val whyGrammarBody: String
    /** What a round asks of you, before the first one runs. */
    val firstRoundTitle: String
    val firstRoundRecognize: String
    val firstRoundGrade: String
    val firstRoundWrite: String
    val check: String
    val reveal: String
    val next: String
    val also: String              // %s = the forms a card also answers to
    val typoNote: String
    val otherWordNote: String     // %1$s = the word typed, %2$s = what it means
    val answerPlaceholder: String // %s = target language name
    /** The question the three verdicts answer, standing under them. */
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
    val readAloud: String         // the switch's stable a11y label — never flips
    val stateOn: String
    val stateOff: String
    val pronounce: String         // "say it again" action on a word
    val aboutButton: String
    /** The footer's door to newer builds — a noun, not an errand. */
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
    /** The free-practice card's own name — the ladder both drills climb, not a workshop. */
    val trainingTitle: String
    val trainingSubtitle: String  // what free practice is, under its name
    val lettersTitle: String
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

    // ── The three overview pages ────────────────────────────────────────────────
    /** The ✕'s name — the corner every page and every run wears on the left. */
    val close: String
    val numbersTitle: String      // the hub entry, and the variant's own name
    val numbersPage: String       // %s = the language being learnt
    val lettersPage: String       // %s
    val overviewPractice: String  // the heading the picks stand under
    val overviewStart: String     // the button both pages open a run with
    val tapToHear: String         // the gesture a reference page discloses once, under its heading
    /** The box names its rows "words", not a table's rows, so it discloses the tap in its own words. */
    val boxTapToHear: String
    /** Beside a word neither a recording nor the device's voice can say — the tap that does nothing. */
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
    /** Why the picks are a radio: mixing several exercises into one run is itself earned. */
    val combineLocked: String
    /** What a locked row costs, before kern's table words the rungs themselves. */
    val unlockPrefix: String

    // ── Inside a run ────────────────────────────────────────────────────────────
    val digitsOne: String         // the numbers rung, which counts digits
    val digitsMany: String        // %d
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
    /** The one stage row that says where THIS learner's run opens. */
    val stageEntry: String
    val lettersUnavailable: String
    val alphabetTitle: String
    val alphabetSpeakName: String
    val alphabetSpeakExample: String

    // ── The atlas: the Länder page and its run ──────────────────────────────────
    val countriesTitle: String    // the hub entry, and what the result tile says was drilled
    val countriesPage: String     // %s = the language being learnt
    val countriesReference: String
    /** How the ladder is walked, said once instead of marked on every rung row. */
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
    /**
     * What a question ASKS. None of them names a language: the field's placeholder says
     * which side is owed, and saying it here too would be the third telling.
     */
    val countryAskCountry: String
    val countryAskFlag: String
    val countryAskLanguage: String
    val countryAskNationality: String
    val countryAskSpokenIn: String
    val countryAskSpokenWhere: String

    // ── Box browse ──────────────────────────────────────────────────────────────
    val boxTitle: String
    /** The door to the box, wherever a screen puts one — a name for an icon that has none. */
    val boxNav: String
    val boxSubtitle: String       // %1$d active of %2$d held
    /**
     * The learner's own shelf. Kern hands back the area KEY for it
     * (`OwnWords.AREA`, in no group) and leaves the naming to the reader's chrome;
     * catalog shelves name themselves, down to `BoxBrowser.sections`' own id fallback.
     */
    val ownWordsTitle: String
    val ownWordsExplainer: String
    val packArea: String          // %d = what packing this shelf would add
    val packDone: String
    val packWord: String          // the single-word offer a search hit carries
    /** The queued mark's own control: tapping it takes the word back out. */
    val unpackWord: String
    /** A shelf's own control, taking its whole queue back out. %d = what it would remove. */
    val dequeueArea: String
    /** A queued row's mark where no per-word control is offered — the shelf's own does it. */
    val queuedWord: String
    val suspended: String         // the sleeping mark's name
    val wake: String
    /** Putting a word to sleep from the card in front of you; [wake] is the way back. */
    val sleep: String
    /**
     * The flag a reported word wears.
     * Its own mark, said apart from [suspended] — a report says nothing about where the
     * word stands, and a reported word keeps whatever badge it had.
     */
    val reported: String
    /**
     * An area's row of counts: the consolidated half beside the seal, the learning half
     * beside the leaf. [dayConsolidated] is the SEPARATE wording for the day's own tally on
     * Heute — a different screen's sentence, not this one read twice.
     */
    val progressConsolidated: String // %d
    val progressLearning: String  // %d
    val phrasesLocked: String     // %d = phrases still waiting on their components
    /**
     * The same count spelled out for a screen reader.
     * [phrasesLocked] sits beside a padlock that carries the "locked"; spoken, the padlock is gone.
     */
    val phrasesLockedSpoken: String // %d
    /** A fold's state, never its label — the heading stays the heading whichever way it points. */
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
    /**
     * What the form says while only ONE side is written.
     * The word is kept as a suggestion and never asked until the other half arrives.
     */
    val ownWordSuggestion: String

    // ── Reporting a problem ─────────────────────────────────────────────────────
    /** The menu entry a word grows, and the way back out of a report already filed. */
    val reportAction: String
    val reportDismiss: String
    val reportTitle: String
    val reportSend: String
    val reportComment: String     // optional, and the label says so
    /** What they had typed, shown rather than asked about — the rejected answer IS the report. */
    val reportTyped: String
    val reportExplainer: String   // who reads it, and that the word's schedule is untouched

    // ── Feedback to the catalog ─────────────────────────────────────────────────
    val feedbackTitle: String
    /** The words written with one side only; they join no card, so this is where they show. */
    val feedbackSuggestions: String
    val feedbackNeedsTranslation: String
    val feedbackCopy: String
    val feedbackSend: String
    /** The two scopes each action offers once a copy has ever been taken. */
    val feedbackScopeNew: String
    val feedbackScopeAll: String

    // ── Box settings ────────────────────────────────────────────────────────────
    val settingsTitle: String
    /** The name the greeting uses — cleared here as well as given here. */
    val learnerNameTitle: String
    val learnerNamePlaceholder: String
    val learnerNameHint: String
    val profileHint: String
    val resetButton: String       // %s = the language being learnt, in its own name
    val resetHint: String
    val resetConfirm: String      // %s = the language being learnt, in its own name
    val cancel: String
    val reset: String

    // ── Session turn ────────────────────────────────────────────────────────────
    val copyPrompt: String        // %s = target language name — the write-it-out field
    val copyMismatch: String      // the copy was another word: the card still holds the answer
    /**
     * Leaving a step that has already decided its rating: the write-out's skip, and
     * giving up on an open retry. One word for both, as on iOS (`session.skipCopy`) —
     * a step you cannot leave is a trap, and neither leaving costs the schedule anything.
     */
    val skipStep: String
    /** The way out of a card asked by ear: the word goes on screen instead of in the air. */
    val cantListen: String

    // ── The day's standing (Heute) ──────────────────────────────────────────────
    /** Nothing due, and nothing done yet — never [doneToday], which the day must earn. */
    val caughtUpTitle: String
    val dayReviews: String        // %d
    val dayReviewsOne: String     // the count-of-one line, verbatim
    val dayNewCards: String       // %d
    val dayNewCardsOne: String
    /**
     * Fresh cards spelled out with their noun, for the one shape [dayNewCards] cannot
     * cover on its own: the round names nothing else, so a bare count needs the word
     * it is counting ("neue Wörter") rather than a nominalized adjective standing alone.
     * No count-of-one form — a round with only fresh cards and only one is not a real shape.
     */
    val dayNewWordsOnly: String    // %d
    val dayConsolidated: String   // %d — "gefestigt" needs no declining
    /**
     * Pull-aheads carrying the round on their own — the freshening-up.
     * Named only in that case: everywhere else they count into [dayReviews]
     * ([net.spross.kern.session.SessionOffer.summaryParts] decides which).
     */
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
    /** What a round with no nameable parts says instead of printing zeros. */
    val sessionSomeCards: String
    /** The cap is a promise, not a loss: what it holds back is named. */
    val sessionHeldBack: String   // %d
    val sessionStart: String
    /** The quiet way in, up while the round is long enough to be worth halving. */
    val sessionShortRound: String

    // ── Listening (Heute's one row, and the run it opens) ───────────────────────
    val listenTitle: String
    /** The row's second line: which words it leans on, and that it needs no hands. */
    val listenSubtitle: String
    val listenPause: String
    val listenResume: String
    val listenSkip: String
    val listenRepeat: String
    /** The bedtime control, off — and its screen-reader label in both states. */
    val listenTimer: String
    /** The bedtime control while it runs; minutes, never m:ss — a ticking clock is watched. */
    val listenMinutesLeft: String   // %d

    // ── Load failures ───────────────────────────────────────────────────────────
    val errorTitle: String
    val errorCatalogMissing: String
    val errorContentUnavailable: String // %s = what the system said
    val errorUnknownProfile: String     // %1$s = known, %2$s = learnt
    val errorResetFailed: String        // %s

    // ── Round completion ────────────────────────────────────────────────────────
    val roundNew: String          // %d
    /** [roundNew] spelled out with its noun, for a round that named nothing else. */
    val roundNewOnly: String      // %d
    val roundConsolidated: String // %d
    val roundReviewed: String     // %d
    val roundAllDone: String      // the round had nothing nameable in it
    val restHint: String          // today's recall is strained; more reps buy little
    val streakRecord: String

    /**
     * What the round's area GREW, read off the tree's own delta rather than the round's tallies.
     * [growthGrew] is the one line a strained day gets — no growth claim on a bad day —
     * and [growthOpened] the first ground broken in an area; the three lists are the
     * variants a stable seeded pick chooses from.
     */
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
    /** The two lines a tile with no readable snapshot stands on, beside the sprout. */
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

package net.spross.app

import androidx.compose.runtime.Stable
import net.spross.kern.catalog.LanguageChoices

/**
 * UI chrome strings, rendered in the KNOWN language when chrome exists
 * (de/en today), otherwise en — design.md "Profile & onboarding".
 *
 * The two tables live beside this file ([ChromeDe], [ChromeEn]) and are GENERATED from
 * the iOS String Catalog by `scripts/chrome.py` — the words themselves are never written
 * here. A new field is worded in `App/Sources/Resources/Localizable.xcstrings` and declared
 * below under the name its key camelCases to (`box.card.due` → `boxCardDue`), which is the
 * whole of the binding; a pre-commit check keeps the two in step, so the same surface
 * cannot read differently on the two phones.
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
     * hours that lend an epithet carry a third line for it.
     */
    val greetMorning: List<String>
    val greetDay: List<String>
    val greetEvening: List<String>
    val greetNight: List<String>
    val homeGreetingMorningAddressee: String
    val homeGreetingNightAddressee: String
    val homeDoneExtraRound: String
    val homeDoneTitle: String
    val boxCardDue: String
    val onboardingWelcome: String
    val onboardingKnownQuestion: String
    val onboardingLearningQuestion: String
    val settingsKnownTitle: String
    val settingsLearningTitle: String
    val onboardingNameQuestion: String
    val onboardingStart: String
    val commonBack: String
    val onboardingWhyTitle: String
    val onboardingWhyBreadthTitle: String
    val onboardingWhyBreadthBody: String
    val onboardingWhyCompanionTitle: String
    val onboardingWhyCompanionBody: String
    val onboardingWhyGrammarTitle: String
    val onboardingWhyGrammarBody: String
    val onboardingFirstRoundTitle: String
    val onboardingFirstRoundRecognize: String
    val onboardingFirstRoundGrade: String
    val onboardingFirstRoundWrite: String
    val commonCheck: String
    val sessionReveal: String
    val commonNext: String
    val sessionGrammarAlso: String              // %s
    val sessionOtherWord: String     // %1$s %2$s
    val sessionAnswerPlaceholder: String // %s
    val sessionRatingQuestion: String
    /** The three the FIRST round teaches itself with, one per moment ([SessionCoach]). */
    val sessionCoachRecognize: String
    val sessionCoachGrade: String
    val sessionCoachWrite: String
    val sessionRatingHard: String
    val sessionRatingGood: String
    val sessionRatingUnknown: String
    val sessionDoneTitle: String
    val sessionDoneKeepPracticing: String
    val commonDone: String
    val sessionGrammarPluralEquals: String
    val sessionGrammarPluralOnly: String
    val sessionGrammarPlural: String        // %s
    val a11yGlyphFeminineForm: String
    val a11yActionReadAloud: String
    val a11yStateOn: String
    val a11yStateOff: String
    val a11yActionPronounce: String
    val settingsAbout: String
    val settingsFeedback: String
    val settingsUpdateButton: String
    val settingsUpdateTitle: String
    val settingsUpdateOffer: String
    val settingsUpdateObtainium: String
    val settingsUpdateDownload: String
    val settingsAudioTitle: String
    val settingsAudioOptionOff: String
    val settingsAudioOptionRecordings: String
    val settingsAudioOptionTts: String
    val settingsAudioHintOff: String
    val settingsAudioHintRecordings: String
    val settingsAudioHintTts: String
    val creditsTitle: String
    val creditsRecordings: String // %d
    val creditsUnmodified: String
    val creditsCommonsNote: String
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
    val trainerHubTitle: String
    val trainerHubSubtitle: String
    val a11ySuffixPractice: String
    val trainerSkillLetters: String
    val lettersAskHear: String
    val lettersAskSpell: String
    val lettersAskDictation: String
    val a11yGlyphLetter: String      // %s
    val a11yActionReplayPrompt: String
    val lettersPromptInLanguage: String  // %s
    val trainerRung: String             // %d
    val trainerRunStreak: String            // %d
    // The two captions an amber hold wears; the form itself follows, composed by
    // the reader, so the words stay one string and the layout stays each phone's.
    val sessionAlmostTypo: String
    val sessionAlmostHeard: String
    val lettersMutedTitle: String
    val lettersMutedEnable: String
    val trainerResultTasksDoneOne: String
    val trainerResultTasksDone: String         // %d
    val trainerResultBestStreak: String        // %d
    val a11yVerdictCorrect: String
    val a11yVerdictAlmost: String
    val a11yVerdictWrong: String
    val a11yVerdictNotAnswered: String

    // ── The three overview pages ────────────────────────────────────────────────
    val commonClose: String
    val trainerSkillNumbers: String
    val numbersTitle: String       // %s
    val lettersTitle: String       // %s
    val trainerOverviewPractice: String
    val trainerOverviewStart: String
    val trainerReferenceTapToHear: String
    val boxTapToHear: String
    val boxCardNoAudio: String
    val numbersReference: String
    val numbersNotes: String
    /**
     * Kern's band key → the heading it takes. A map, because the bands are kern's to
     * grow: one it has no wording for still gets its rows rather than printing a key.
     */
    val numberSections: Map<String, String>
    val trainerVariantClock: String
    val trainerVariantPhrases: String
    val trainerVariantForms: String
    val trainerModifierReverse: String
    val trainerModifierReverseHint: String
    val trainerModifierFast: String
    val trainerModifierFastHint: String
    val trainerModifierMix: String
    val trainerModifierMixHint: String
    val numbersCombineLocked: String
    val numbersUnlock: String

    // ── Inside a run ────────────────────────────────────────────────────────────
    val numbersRungOne: String
    val numbersRung: String            // %d
    val trainerRunRecord: String            // %d
    val a11yCountStreakInARow: String      // %d
    val a11ySuffixRecord: String      // %d
    val numbersAnswerPlaceholder: String
    val numbersNewPlace: String          // %s
    val numbersLookup: String
    val trainerResultNewRecord: String

    // ── The letter drill's stages, as the overview lists them ───────────────────
    val lettersStageChoiceEasy: String
    val lettersStageChoiceEasyHint: String
    val lettersStageChoiceConfusable: String
    val lettersStageChoiceConfusableHint: String
    val lettersStageTyped: String
    val lettersStageTypedHint: String
    val lettersStageDictation: String
    val lettersStageDictationHint: String
    val lettersStageDictationLocked: String
    val lettersStageEntry: String
    val lettersUnavailable: String
    val lettersAlphabetTitle: String
    val lettersAlphabetSpeakName: String
    val lettersAlphabetSpeakExample: String

    // ── The atlas: the Länder page and its run ──────────────────────────────────
    val trainerSkillCountries: String
    val countriesTitle: String     // %s
    val countriesReference: String
    val countriesPace: String
    val countriesBest: String     // %d
    val countriesFastHint: String
    val countriesReverseHint: String // %1$s %2$s
    /**
     * The rungs, in the order they are climbed — one entry per rung of kern's own ladder
     * ([net.spross.kern.trainer.CountryDrill.MAX_LEVEL]), read through [countryRung].
     */
    val countryRungs: List<String>
    val countryRungHints: List<String>
    /** How far from home a reference group sits, innermost first — read through [countryTier]. */
    val countryTiers: List<String>
    val countriesAskCountry: String
    val countriesAskFlag: String
    val countriesAskLanguage: String
    val countriesAskNationality: String
    val countriesAskSpokenIn: String
    val countriesAskSpokenWhere: String

    // ── Box browse ──────────────────────────────────────────────────────────────
    val boxTitle: String
    val boxDoor: String
    val boxSubtitle: String       // %1$d %2$d
    val boxOwnShelf: String
    val boxOwnWordExplainer: String
    val boxShelfPack: String          // %d
    val boxShelfPacked: String
    val boxCardPack: String
    val boxCardUnpack: String
    val boxShelfUnpack: String
    val boxCardQueued: String
    val boxCardSuspended: String
    val boxCardWake: String
    val boxCardSleep: String
    /**
     * Dropping ONE word's progress: it goes back to new and may be offered again.
     * The card stays, and so does anything filed against it — forgetting the answers
     * does not make the translation right.
     */
    val boxCardActions: String
    val boxCardForget: String
    val boxCardOwnFrom: String
    val reportReported: String
    val progressConsolidatedCount: String // %d
    val progressLearningCount: String  // %d
    val boxAreaPhrasesLockedShort: String // %d
    val boxAreaPhrasesLocked: String // %d
    val a11yStateExpanded: String
    val a11yStateCollapsed: String
    // A card with nothing behind it has NO phase word: new is the absence of a badge. Past
    // that, a row reads one of four: [boxPhaseLearning] while walking the learning steps,
    // [boxPhaseRelearning] the same rung after a lapse (same color/icon, its own word),
    // [boxPhaseSettled] once in Review but short of the consolidated bar, and
    // [boxPhaseConsolidated] once a card has cleared it — the shelf's own count stays the
    // two-way consolidated/learning split it has always been (`AreaStatistics.learning`).
    val boxPhaseLearning: String
    val boxPhaseRelearning: String
    val boxPhaseSettled: String
    val boxPhaseConsolidated: String

    // ── Box search ──────────────────────────────────────────────────────────────
    val boxSearchButton: String
    val boxSearchPlaceholder: String
    val boxSearchHint: String
    val boxSearchAreas: String
    val boxSearchWords: String
    val boxSearchNothing: String     // %s
    val boxSearchWriteOwn: String    // %s
    val boxSearchClear: String

    // ── Own-word form ───────────────────────────────────────────────────────────
    val boxOwnWordTitle: String
    val boxOwnWordInLanguage: String // %s
    val boxOwnWordPicture: String
    val boxOwnWordAdd: String
    val boxOwnWordRemove: String
    val boxOwnWordEdit: String
    val boxOwnWordSave: String
    val boxOwnWordSwap: String
    val boxOwnWordExplainerSuggestion: String

    // ── Reporting a problem ─────────────────────────────────────────────────────
    val reportAction: String
    val reportEdit: String
    val reportDismiss: String
    val reportTitle: String
    val reportSend: String
    val reportComment: String
    val reportTyped: String
    val reportExplainer: String

    // ── Own content ─────────────────────────────────────────────────────────────
    val boxOwnTitle: String
    val boxOwnReported: String
    val boxOwnWordAddAction: String

    // ── Feedback to the catalog ─────────────────────────────────────────────────
    val boxOwnWordNeedsTranslation: String
    val reportExportCopy: String
    val reportExportSend: String
    val reportExportScopeNew: String
    val reportExportScopeAll: String

    // ── Box settings ────────────────────────────────────────────────────────────
    val settingsTitle: String
    val settingsNameTitle: String
    val settingsNamePlaceholder: String
    val settingsNameHint: String
    val settingsProfileHint: String
    val settingsRestartTutorialButton: String
    val settingsRestartTutorialHint: String
    val settingsResetButton: String       // %s
    val settingsResetHint: String
    val settingsResetConfirm: String      // %s
    val commonCancel: String
    val commonReset: String

    // ── Session turn ────────────────────────────────────────────────────────────
    val sessionCopyPlaceholder: String        // %s
    val sessionCopyMismatch: String
    val sessionSkip: String
    val sessionHearCantListen: String
    val sessionCardPosition: String
    val a11yCountSessionTally: String

    // ── The day's standing (Home) ──────────────────────────────────────────────
    val homeDoneCaughtUp: String
    val homeTallyReviews: String        // %d
    val homeTallyReviewsOne: String
    val homeTallyNewCards: String       // %d
    val homeTallyNewCardsOne: String
    val homeTallyNewWordsOnly: String    // %d
    val homeTallyConsolidated: String   // %d
    /** Which of the two a round names is [net.spross.kern.session.SessionOffer.summaryParts]'. */
    val homeTallyAhead: String          // %d
    val homeTallyAheadOne: String
    val homeDonePacked: String
    val homeDoneTomorrowFresh: String
    val homeDoneTomorrowDue: String       // %d

    // ── The session offer (Home's one card) ────────────────────────────────────
    /**
     * The phrasings each offer kind carries, indexed by
     * [net.spross.kern.session.SessionHeadline.variant] — kern picks which, from the
     * round's shape, so the same round headlines the same on both platforms.
     * How many each list holds is kern's, sized per kind by how often a learner meets it —
     * see [net.spross.kern.session.HeadlineKind]; the tables are generated to match.
     */
    val headlineReviews: List<String>
    val headlineWarmUp: List<String>
    val headlineFreshSet: List<String>
    /** What the card says instead once a standing run is still owed today's work. */
    val headlineStreak: List<String>
    val homeTallySomeCards: String
    val homeOfferHeldBack: String   // %d
    val homeOfferStart: String
    val homeOfferShortRound: String

    // ── Listening (Home's one row, and the run it opens) ───────────────────────
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
    val errorContentUnavailable: String // %s
    val errorUnknownProfile: String     // %1$s %2$s
    val errorResetFailed: String        // %s

    // ── Round completion ────────────────────────────────────────────────────────
    val sessionDoneTallyNew: String          // %d
    val sessionDoneTallyNewOnly: String      // %d
    val sessionDoneTallyConsolidated: String // %d
    val sessionDoneTallyReviewed: String     // %d
    val sessionDoneTallyAllDone: String
    val sessionDoneRestHint: String
    val sessionDoneStreakRecord: String

    val sessionDoneGrowthGrew: String
    val sessionDoneGrowthOpened: String
    val growthBlooming: List<String>
    val growthSown: List<String>
    val growthGrown: List<String>

    // ── Activity strip ──────────────────────────────────────────────────────────
    val progressLast14Days: String
    val a11yCountActivity14Days: String      // %d
    val a11yCountStreakDays: String        // %d
    val a11yCountStreakDaysOne: String
    val commonDayOne: String
    val commonDayOther: String

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

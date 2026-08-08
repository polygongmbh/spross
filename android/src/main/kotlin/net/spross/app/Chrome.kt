package net.spross.app

/**
 * UI chrome strings, rendered in the KNOWN language when chrome exists
 * (de/en today), otherwise en — design.md "Profile & onboarding".
 *
 * The two tables live beside this file ([ChromeDe], [ChromeEn]); the data class is the
 * contract, and adding a field here is what forces both of them to answer for it.
 * Placeholders are java-format, rendered with `.format(...)`.
 */
data class Chrome(
    val heuteTitle: String,
    val practice: String,
    val extraRound: String,
    val doneToday: String,
    val emptyState: String,
    val dueLabel: String,
    val newLabel: String,
    val consolidatedLabel: String,
    val freshLabel: String,
    val changeLanguages: String,
    val chooseTitle: String,
    val iSpeak: String,
    val iLearn: String,
    val conceptsSuffix: String,
    val letsGo: String,
    val backLabel: String,
    val check: String,
    val reveal: String,
    val next: String,
    val alsoPrefix: String,
    val typoNote: String,
    val otherWordNote: String,     // %1$s = the word typed, %2$s = what it means
    val answerPlaceholder: String, // %s = target language name
    val again: String,
    val hard: String,
    val good: String,
    val easy: String,
    val sessionDone: String,
    val summaryLine: String,       // %d neu · %d gefestigt · %d wiederholt
    val keepPracticing: String,
    val finish: String,
    val pluralEquals: String,
    val pluralOnly: String,
    val pluralPrefix: String,
    val readAloud: String,         // the switch's stable a11y label — never flips
    val stateOn: String,
    val stateOff: String,
    val pronounce: String,         // "say it again" action on a word
    val aboutButton: String,
    val audioToggle: String,
    val audioToggleHint: String,
    val creditsTitle: String,
    val creditsRecordings: String, // %d = how many files the speaker contributed
    val creditsUnmodified: String,
    val creditsCommons: String,
    val trainingTitle: String,
    val lettersTitle: String,
    val lettersHear: String,       // the question a letter-name prompt asks
    val lettersSpell: String,      // …and the one a gap word asks
    val lettersDictation: String,
    val letterChoice: String,      // %s = the glyph — a tile's spoken name
    val replayPrompt: String,      // the replay button's name
    val promptInLanguage: String,  // %s = target language name
    val level: String,             // %d
    val streak: String,            // %d — answers in a row, never days
    val typoCorrection: String,    // %s = the spelling the learner missed
    val heardInstead: String,      // %s = the form that actually played
    val audioOff: String,
    val enableSound: String,
    val tasksDone: String,         // %d
    val bestStreak: String,        // %d
    val answerCorrect: String,     // an answered tile's state, never colour alone
    val answerWrong: String,
    val correctAnswer: String,     // %s = what it was, on a miss or a reveal

    // ── Box browse ──────────────────────────────────────────────────────────────
    val boxTitle: String,
    /** The door to the box, wherever a screen puts one — a name for an icon that has none. */
    val boxNav: String,
    val boxSubtitle: String,       // %1$d active of %2$d held
    /**
     * The learner's own shelf. Kern hands back the area KEY for it
     * (`OwnWords.AREA`, in no group) and leaves the naming to the reader's chrome;
     * catalog shelves name themselves, down to `BoxBrowser.sections`' own id fallback.
     */
    val ownWordsTitle: String,
    val ownWordsExplainer: String,
    val packArea: String,          // %d = what packing this shelf would add
    val packDone: String,
    val packWord: String,          // the single-word offer a search hit carries
    val packedWord: String,
    val suspended: String,         // the sleeping mark's name
    val wake: String,
    val phrasesLocked: String,     // %d = phrases still waiting on their components
    // A card with nothing behind it has NO phase word: new is the absence of a badge.
    val phaseLearning: String,
    val phaseReview: String,
    val phaseRelearning: String,

    // ── Box search ──────────────────────────────────────────────────────────────
    val search: String,
    val searchPlaceholder: String,
    val searchHint: String,        // what the field will look through, before anything is typed
    val searchAreas: String,
    val searchWords: String,
    val searchNothing: String,     // %s = the query
    val searchWriteOwn: String,    // %s = the query — the one door to writing a word
    val searchClear: String,

    // ── Own-word form ───────────────────────────────────────────────────────────
    val ownWordTitle: String,
    val ownWordInLanguage: String, // %s = language name — both fields ask it
    val ownWordPicture: String,
    val ownWordAdd: String,
    val ownWordRemove: String,     // the app's only deletion; catalog words sleep instead

    // ── Box settings ────────────────────────────────────────────────────────────
    val settingsTitle: String,
    val profileHint: String,
    val resetButton: String,
    val resetHint: String,
    val resetConfirm: String,      // %s = the language being learnt, in its own name
    val cancel: String,
    val reset: String,

    // ── Session turn ────────────────────────────────────────────────────────────
    val copyPrompt: String,        // %s = target language name — the write-it-out field
    val copyMismatch: String,      // the copy was another word: the card still holds the answer
    /**
     * Leaving a step that has already decided its rating: the write-out's skip, and
     * giving up on an open retry. One word for both, as on iOS (`session.skipCopy`) —
     * a step you cannot leave is a trap, and neither leaving costs the schedule anything.
     */
    val skipStep: String,

    // ── The day's standing (Heute) ──────────────────────────────────────────────
    /** Nothing due, and nothing done yet — never [doneToday], which the day must earn. */
    val caughtUpTitle: String,
    val dayReviews: String,        // %d
    val dayNewCards: String,       // %d
    val dayConsolidated: String,   // %d
    val tomorrowPacked: String,
    val tomorrowFresh: String,
    val tomorrowDue: String,       // %d

    // ── Round completion ────────────────────────────────────────────────────────
    val roundNew: String,          // %d
    val roundConsolidated: String, // %d
    val roundReviewed: String,     // %d
    val roundAllDone: String,      // the round had nothing nameable in it
    val restHint: String,          // today's recall is strained; more reps buy little
    val streakRecord: String,

    // ── Activity strip ──────────────────────────────────────────────────────────
    val progressTitle: String,
    val last14Days: String,
    val activityDays: String,      // %d = days worked inside the window
    val streakDays: String,        // %d — days in a row, the strip's own label
    val dayOne: String,            // the badge's unit word, by count
    val dayMany: String,
) {
    companion object {
        fun forSource(source: String): Chrome = if (source == "de") ChromeDe else ChromeEn
    }
}

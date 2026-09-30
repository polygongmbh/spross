package net.spross.kern.session

import net.spross.kern.model.Rating

/** Grading verdict for a typed produce answer. */
sealed interface Match {
    data object Exact : Match

    /**
     * Accepted with a small slip; carries the accepted form as authored in the
     * catalog (proper spelling for the UI's correction display, not the
     * lowercased/stripped comparison form).
     */
    data class Typo(val corrected: String) : Match

    /**
     * The typed answer IS a different concept's word in the answer language —
     * a miss, but a nameable one: [word] as the catalog spells it, [meanings]
     * the source-side words of every concept that owns it (seed order).
     * Only [CatalogAnswerGrader] produces this; a bare normalizer sees one card.
     */
    data class OtherWord(val word: String, val meanings: List<String>) : Match

    data object Wrong : Match

    /**
     * The FSRS rating this match earns on its own, before any reveal/retry
     * step takes over. [Exact] came back clean (Good); a [Typo] came back
     * readable but imperfect — the same Hard a finished retype after a reveal
     * earns, because neither came back on the first, unaided try. One rule
     * here rather than in each platform's UI, so a produce screen never
     * re-derives it and drifts from another (iOS graded a typo Good until
     * 2026-08-07; Android never did).
     * Null for [OtherWord] and [Wrong]: neither has a rating of its own —
     * both route through reveal, where the eventual retype (Hard) or
     * give-up (Again) decides it.
     */
    fun producedRating(): Rating? = when (this) {
        Exact -> Rating.Good
        is Typo -> Rating.Hard
        is OtherWord, Wrong -> null
    }
}

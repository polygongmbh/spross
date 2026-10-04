package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * An unlock is marked ONCE: the first time an overview page shows a row open that it last
 * showed padlocked, that row is marked, and from then on the page files what it shows
 * padlocked now.
 *
 * The record is the rows a page last SHOWED padlocked, never the ones it showed open: a row
 * that was open from the start, or joined the page already open, has no padlock to lose. A
 * first visit has no record to compare with, so it marks nothing. A row marked on the way back
 * from the run that opened it is marked there, and never again.
 *
 * Which rows a page can padlock, and what they are called, is kern's ([row]); the platform
 * files the set under [key] beside the Sprosse records, and draws and announces the mark.
 */
object DrillUnlockMark {

    /** Store prefix of the rows a page last showed padlocked — the full key is [key]. */
    const val LOCKED_PREFIX: String = "trainer.locked."

    fun key(page: String): String = LOCKED_PREFIX + page

    /** The numbers page's identity: one page per learned language, like its ladder. */
    fun numbersPage(language: Language): String = "numbers.$language"

    fun row(exercise: NumbersExercise): String = "exercise.${exercise.name}"

    fun row(modifier: DrillModifier): String = "modifier.${modifier.name}"

    fun row(format: LetterFormat): String = "stage.${format.name}"

    /** The atlas' and the calendar's one padlocked row: Fast, earned on the top Sprosse. */
    val typedDrillFast: String get() = row(DrillModifier.Fast)

    /** The rows to mark: padlocked when the page last showed them, and open now. */
    fun marked(lastLocked: Set<String>, open: Set<String>): Set<String> = lastLocked intersect open
}

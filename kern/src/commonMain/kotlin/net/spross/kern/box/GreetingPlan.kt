package net.spross.kern.box

import net.spross.kern.model.Language

/** Whom the language's own greeting lines address; the platform resolves the word. */
enum class Addressee {
    /** The learner, by the name they gave. */
    Learner,

    /** No name known: the word the morning lends. */
    MorningWord,

    /** No name known: the word the night lends. */
    NightWord,

    /** No name known, and the hour lends none. */
    Nobody,
}

/** One line of the Home greeting: the [index]th of the language's own lines, or of the chrome's for [GreetingPlan.chromePart]. */
data class GreetingLine(val spoken: Boolean, val index: Int)

/**
 * The line over Home's day card: two registers, the target language speaking for itself
 * ([net.spross.kern.catalog.Catalog.spokenLines] at [targetPart]) or the known language asking about it
 * (the chrome's lines for [chromePart]), the spoken lines first — they both greet and teach,
 * and only they address the learner ([address]).
 * The words are the catalog's and each app's string table; which one is shown is [pick].
 */
class GreetingPlan(
    private val nowEpochMillis: Long,
    private val tzId: String,
    private val target: Language?,
    learnerNamed: Boolean,
) {
    /** The target's own hours for its own lines ([dayPart]). */
    val targetPart: DayPart = dayPart(nowEpochMillis, tzId, target)

    /** Chrome's fixed schedule for the chrome lines ([chromePart]). */
    val chromePart: DayPart = chromePart(nowEpochMillis, tzId)

    val address: Addressee = when {
        learnerNamed -> Addressee.Learner
        chromePart == DayPart.Morning -> Addressee.MorningWord
        chromePart == DayPart.Night -> Addressee.NightWord
        else -> Addressee.Nobody
    }

    /** The line taken out of [spoken] spoken lines followed by [chrome] chrome lines, held through the stretch ([partVariant]). */
    fun pick(spoken: Int, chrome: Int): GreetingLine {
        val index = partVariant(nowEpochMillis, tzId, target, spoken + chrome)
        return if (index < spoken) GreetingLine(true, index) else GreetingLine(false, index - spoken)
    }
}

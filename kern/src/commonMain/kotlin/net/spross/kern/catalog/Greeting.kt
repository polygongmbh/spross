package net.spross.kern.catalog

import net.spross.kern.box.DayPart

/**
 * What a language says at one stretch of the day, and how a learner's name joins it.
 *
 * Which concept a stretch reaches for is the engine's rule, not a surface's: both phones
 * ask the catalog the same question and get the same answer, and a language with no such
 * greeting says so by realizing none — see [Catalog.greeting].
 */
object Greetings {

    /** Sentence-final marks an address has to move in front of. */
    private const val SENTENCE_MARKS = "!?."

    /** The concept [part] is greeted with. */
    fun slug(part: DayPart): String = when (part) {
        DayPart.Morning -> "good-morning"
        DayPart.Day -> "good-day"
        DayPart.Evening -> "good-evening"
        DayPart.Night -> "good-night"
    }

    /**
     * [greeting] said to [name]: the address goes INSIDE the sentence, so the closing mark
     * travels behind it — "Habari za asubuhi!" becomes "Habari za asubuhi, Tim!" rather
     * than stranding the mark mid-sentence.
     *
     * Whatever space stood before that mark is kept as authored, because French sets one
     * there and German does not; a greeting carrying no final mark simply ends on the name,
     * and a blank name returns the greeting untouched.
     */
    fun addressed(greeting: String, name: String?): String {
        val addressee = name?.trim().orEmpty()
        if (addressee.isEmpty()) return greeting
        val marks = greeting.takeLastWhile { it in SENTENCE_MARKS }
        val head = greeting.dropLast(marks.length)
        val gap = head.takeLastWhile { it.isWhitespace() }
        return "${head.trimEnd()}, $addressee$gap$marks"
    }
}

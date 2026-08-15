package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the reveal may say, in every authored language at every one of the 1440 times.
 *
 * The reveal shows the canonical reading and, under it, the gloss naming the
 * alternatives. A gloss quoting something the drill would then MARK WRONG teaches a
 * wrong answer, so every alternative it names has to be an accepted reading — and the
 * canonical reading has to be one too, since the same string is what a learner is asked
 * to reproduce next time.
 */
class ClockRevealTests {

    /**
     * The gloss lists alternatives after this marker, in the language being ANSWERED in.
     * A language must appear here or in [ruleHintGlosses], and the test says so out loud:
     * a marker that matches nothing skips its 1440 rows in silence, which is the whole
     * defect this file exists to catch.
     */
    private val alternativeMarkers = mapOf(
        "de" to "auch: ", "en" to "also: ", "es" to "también: ", "it" to "anche: ",
        "uk" to "також: ", "fr" to "aussi : ", "eo" to "ankaŭ: ",
    )

    /**
     * Languages whose gloss is a rule hint ("Saa ± 6h"), not a list of readings — there is
     * nothing in it to hold to the accepted set. Declaring them is what keeps a language
     * missing from [alternativeMarkers] from passing quietly.
     */
    private val ruleHintGlosses = setOf("sw")

    /** Every separator a gloss joins its alternatives with, across the languages. */
    private val separators = arrayOf(", ", " oder ", " or ", " · ")

    @Test
    fun everyAlternativeTheGlossNamesIsAcceptedAndNotTheDisplay() {
        for (language in Trainer.languages) {
            val marker = alternativeMarkers[language]
            assertTrue(
                marker != null || language in ruleHintGlosses,
                "$language: no gloss lead-in registered, and not declared a rule-hint gloss",
            )
            for (hour in 0..23) {
                for (minute in 0..59) {
                    val task = Trainer.clock(hour, minute, language)
                    val where = "$language ${task.prompt}"
                    assertTrue(task.display in task.accepted, "$where: display not accepted")
                    if (marker == null) continue
                    val listed = task.gloss?.substringAfter(marker, "").orEmpty()
                    if (listed.isEmpty()) continue
                    for (alternative in listed.split(*separators)) {
                        assertTrue(alternative in task.accepted, "$where: gloss names \"$alternative\"")
                        assertTrue(alternative != task.display, "$where: gloss repeats the display")
                    }
                }
            }
        }
    }

    /**
     * The accepted set is generous on purpose, but it is also crossed with every
     * authored frame in `PhraseSlots.compose` — a set that grows without a ceiling
     * turns one sentence drill into hundreds of strings to grade against.
     */
    @Test
    fun everyReadingSetStaysWithinItsCapAndCarriesNoDuplicates() {
        val caps = mapOf("de" to 14, "en" to 27, "es" to 42, "fr" to 28, "it" to 38, "sw" to 22, "uk" to 24, "eo" to 22)
        for (language in Trainer.languages) {
            var widest = 0
            var widestAt = ""
            for (hour in 0..23) {
                for (minute in 0..59) {
                    val task = Trainer.clock(hour, minute, language)
                    assertEquals(
                        task.accepted.size, task.accepted.toSet().size,
                        "$language ${task.prompt}: duplicate readings",
                    )
                    if (task.accepted.size > widest) {
                        widest = task.accepted.size
                        widestAt = task.prompt
                    }
                }
            }
            assertTrue(widest <= caps.getValue(language), "$language: $widest readings at $widestAt")
        }
    }
}

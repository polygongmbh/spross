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

    /** The gloss lists alternatives after this marker, one per language. */
    private val alternativeMarkers = mapOf(
        "de" to "auch: ", "en" to "also: ", "es" to "también: ",
        "sw" to "pia: ", "uk" to "також: ",
    )

    @Test
    fun everyAlternativeTheGlossNamesIsAcceptedAndNotTheDisplay() {
        for (language in Trainer.languages) {
            val marker = alternativeMarkers.getValue(language)
            for (hour in 0..23) {
                for (minute in 0..59) {
                    val task = Trainer.clock(hour, minute, language)
                    val where = "$language ${task.prompt}"
                    assertTrue(task.display in task.accepted, "$where: display not accepted")
                    val listed = task.gloss?.substringAfter(marker, "").orEmpty()
                    if (listed.isEmpty()) continue
                    for (alternative in listed.split(", ", " oder ")) {
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
        val caps = mapOf("de" to 14, "en" to 26, "es" to 28, "sw" to 16, "uk" to 24)
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

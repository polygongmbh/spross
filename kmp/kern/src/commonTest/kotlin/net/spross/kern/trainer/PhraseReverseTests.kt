package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reverse phrase-slot drills (v1 ReversePhraseTests): the target sentence in
 * words is the prompt, the typed answer is the SOURCE sentence with digits.
 */
class PhraseReverseTests {

    private fun template(id: String): PhraseTemplate =
        PhraseTemplates.all.first { it.id == id }

    @Test
    fun reverseNumberShowsTargetAsksSourceDigits() {
        val task = PhraseSlots.reverseInstantiate(template("uk-num-hefte"), value = 21L)
        assertEquals("У мене є двадцять один зошит.", task.prompt)
        assertEquals("Ich habe 21 Hefte.", task.display)
        assertEquals(listOf("Ich habe 21 Hefte."), task.accepted)
        assertEquals("de", task.language)
    }

    @Test
    fun reverseClockAcceptsPaddedAndBareHour() {
        val task = PhraseSlots.reverseInstantiate(template("sw-clock-zug"), hour = 8, minute = 5)
        assertEquals("Treni inaondoka saa mbili na dakika tano asubuhi.", task.prompt)
        assertTrue("Der Zug fährt um 08:05 Uhr ab." in task.accepted)
        assertTrue("Der Zug fährt um 8:05 Uhr ab." in task.accepted)
    }

    @Test
    fun reverseSampleMatchesForwardSample() {
        val a = Random(7)
        val b = Random(7)
        for (template in PhraseTemplates.all) {
            val sampled = PhraseSlots.reverseSample(template, a)
            val forward = PhraseSlots.sample(template, b)
            assertEquals(forward.display, sampled.prompt, template.id)
            assertEquals("de", sampled.language, template.id)
        }
    }
}

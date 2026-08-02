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

    private fun frame(target: String, slug: String) = RealFrames.frame(target, slug)

    @Test
    fun reverseNumberShowsTargetAsksSourceDigits() {
        val task = PhraseSlots.reverseInstantiate(frame("uk", "i-have-n-notebooks"), value = 21L)
        assertEquals("У мене є двадцять один зошит.", task.prompt)
        assertEquals("Ich habe 21 Hefte.", task.display)
        // Digits stay canonical; the written-out German cardinal is accepted too.
        assertEquals(listOf("Ich habe 21 Hefte.", "Ich habe einundzwanzig Hefte."), task.accepted)
        assertEquals("de", task.language)
    }

    @Test
    fun reverseClockAcceptsPaddedAndBareHour() {
        val task = PhraseSlots.reverseInstantiate(frame("sw", "train-departs-at"), hour = 8, minute = 5)
        assertEquals("Treni inaondoka saa mbili na dakika tano asubuhi.", task.prompt)
        assertTrue("Der Zug fährt um 08:05 Uhr ab." in task.accepted)
        assertTrue("Der Zug fährt um 8:05 Uhr ab." in task.accepted)
    }

    /** Reverse works for any joined pair now, not only the ones answering in German. */
    @Test
    fun reverseAsksTheSourceLanguageOfEveryPair() {
        val task = PhraseSlots.reverseInstantiate(RealFrames.frame("uk", "write-please", source = "sw"), value = 5L)
        assertEquals("Напиши, будь ласка: п'ять.", task.prompt)
        assertEquals("Andika, tafadhali: 5.", task.display)
        assertTrue("Andika, tafadhali: tano." in task.accepted)
        assertEquals("sw", task.language)
    }

    @Test
    fun reverseSampleMatchesForwardSample() {
        val a = Random(7)
        val b = Random(7)
        for (template in RealFrames.all) {
            val sampled = PhraseSlots.reverseSample(template, a)
            val forward = PhraseSlots.sample(template, b)
            assertEquals(forward.display, sampled.prompt, template.id)
            assertEquals(template.source, sampled.language, template.id)
        }
    }
}

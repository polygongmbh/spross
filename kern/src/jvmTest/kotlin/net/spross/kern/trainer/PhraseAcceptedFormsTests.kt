package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Accepted-form assembly for phrase-slot drills: every rendering of the slot
 * value — digits AND written-out generator variants — yields an accepted
 * sentence in every authored frame (user report: written-out numbers and
 * times typed mid-sentence were rejected).
 */
class PhraseAcceptedFormsTests {

    private fun frame(target: String, slug: String) = RealFrames.frame(target, slug)

    // Forward: the target-language answer accepts the digit form too

    @Test
    fun forwardClockAcceptsPaddedAndBareDigits() {
        val task = PhraseSlots.instantiate(frame("sw", "train-departs-at"), hour = 8, minute = 5)
        assertTrue("Treni inaondoka 08:05." in task.accepted)
        assertTrue("Treni inaondoka 8:05." in task.accepted)
        // The canonical word reading stays the display.
        assertEquals("Treni inaondoka saa mbili na dakika tano asubuhi.", task.display)
    }

    @Test
    fun forwardNumberAcceptsDigitsWithCountAgreement() {
        val task = PhraseSlots.instantiate(frame("uk", "i-have-n-notebooks"), value = 22L)
        assertTrue("У мене є 22 зошити." in task.accepted)
        assertEquals("У мене є двадцять два зошити.", task.display)
    }

    // German as the ANSWER side: a written-out clock reading rewrites the frame

    /**
     * The reading is the whole time expression, so the frame's own " Uhr" gives way to it —
     * while the digital rendering keeps it ("Es ist jetzt 18:35 Uhr." is right).
     */
    @Test
    fun germanAnswerAbsorbsTheFramesUhrForWordReadingsOnly() {
        val task = PhraseSlots.instantiate(RealFrames.frame("de", "it-is-now", source = "uk"), 18, 35)
        assertEquals("Es ist jetzt fünf nach halb sieben.", task.display)
        assertTrue("Es ist jetzt achtzehn Uhr fünfunddreißig." in task.accepted)
        assertTrue("Es ist jetzt 18:35 Uhr." in task.accepted)
        assertFalse(task.accepted.any { "sieben Uhr." in it }, task.accepted.toString())
    }

    @Test
    fun germanAnswerComposesColloquialUmOnlyAfterUm() {
        val train = PhraseSlots.instantiate(RealFrames.frame("de", "train-departs-at", source = "en"), 20, 0)
        // "um acht" reading + frame "… um {slot} Uhr …" merge without doubling "um".
        assertTrue("Der Zug fährt um acht ab." in train.accepted)
        assertTrue("Der Zug fährt um acht Uhr ab." in train.accepted)
        assertTrue("Der Zug fährt um zwanzig Uhr ab." in train.accepted)
        // No "um" in the frame → the adverbial "um acht" reading is skipped.
        val now = PhraseSlots.instantiate(RealFrames.frame("de", "it-is-now", source = "uk"), 20, 0)
        assertFalse(now.accepted.any { "um acht" in it }, now.accepted.toString())
        assertTrue("Es ist jetzt acht Uhr." in now.accepted)
        assertTrue("Es ist jetzt zwanzig Uhr." in now.accepted)
    }

    @Test
    fun germanAnswerOneOClockComposesApocopatedEinUhr() {
        val train = PhraseSlots.instantiate(RealFrames.frame("de", "train-departs-at", source = "en"), 13, 0)
        assertTrue("Der Zug fährt um ein Uhr ab." in train.accepted)
        assertTrue("Der Zug fährt um eins ab." in train.accepted)
        assertTrue("Der Zug fährt um dreizehn Uhr ab." in train.accepted)
        assertFalse(train.accepted.any { "eins Uhr" in it }, "wrong 'eins Uhr' in ${train.accepted}")
    }

    // masculineNumeral filter holds across the widened accepted assembly

    @Test
    fun masculineNumeralNeverAcceptsFilteredFeminineForms() {
        val feminineBeforeNoun = Regex("(одна|дві) (євро|зошит)")
        for (slug in listOf("it-costs-n-euros", "i-have-n-notebooks")) {
            for (value in listOf(1L, 2L, 21L, 22L, 101L, 102L, 1001L, 1002L, 2022L)) {
                val template = frame("uk", slug)
                for (sentence in PhraseSlots.instantiate(template, value).accepted) {
                    assertFalse(feminineBeforeNoun.containsMatchIn(sentence), "$slug/$value: $sentence")
                }
            }
        }
    }

    // Determinism with a seeded RNG

    @Test
    fun sampledAcceptedSetsAreDeterministic() {
        for (template in RealFrames.all) {
            assertEquals(
                PhraseSlots.sample(template, Random(43)),
                PhraseSlots.sample(template, Random(43)),
                template.id,
            )
        }
    }
}

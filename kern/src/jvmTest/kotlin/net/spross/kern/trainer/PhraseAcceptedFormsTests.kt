package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Accepted-form assembly for phrase-slot drills: every rendering of the slot
 * value — digits AND written-out generator variants — yields an accepted
 * sentence in every authored frame, in both drill directions (user report:
 * written-out numbers and times typed mid-sentence were rejected).
 */
class PhraseAcceptedFormsTests {

    private fun frame(target: String, slug: String) = RealFrames.frame(target, slug)

    // Reverse: the German answer accepts written-out readings alongside digits

    @Test
    fun reverseClockAcceptsWrittenOutAndDigitalTime() {
        val task = PhraseSlots.reverseInstantiate(frame("uk", "it-is-now"), hour = 18, minute = 35)
        assertEquals("Es ist jetzt 18:35 Uhr.", task.display)
        assertTrue("Es ist jetzt 18:35 Uhr." in task.accepted)
        // The 24-hour reading absorbs the frame's literal "Uhr".
        assertTrue("Es ist jetzt achtzehn Uhr fünfunddreißig." in task.accepted)
        // Conversational 12-hour reading.
        assertTrue("Es ist jetzt fünf nach halb sieben." in task.accepted)
        val train = PhraseSlots.reverseInstantiate(frame("sw", "train-departs-at"), hour = 18, minute = 5)
        assertTrue("Der Zug fährt um 18:05 Uhr ab." in train.accepted)
        assertTrue("Der Zug fährt um achtzehn Uhr fünf ab." in train.accepted)
        assertTrue("Der Zug fährt um fünf nach sechs ab." in train.accepted)
    }

    @Test
    fun reverseClockComposesColloquialUmOnlyAfterUm() {
        val train = PhraseSlots.reverseInstantiate(frame("sw", "train-departs-at"), hour = 20, minute = 0)
        // "um acht" reading + frame "… um {slot} Uhr …" merge without doubling "um".
        assertTrue("Der Zug fährt um acht ab." in train.accepted)
        assertTrue("Der Zug fährt um acht Uhr ab." in train.accepted)
        assertTrue("Der Zug fährt um punkt acht ab." in train.accepted)
        assertTrue("Der Zug fährt um zwanzig Uhr ab." in train.accepted)
        val now = PhraseSlots.reverseInstantiate(frame("uk", "it-is-now"), hour = 20, minute = 0)
        // No "um" in the frame → the adverbial "um acht" reading is skipped.
        assertFalse(now.accepted.any { "um acht" in it })
        assertTrue("Es ist jetzt acht Uhr." in now.accepted)
        assertTrue("Es ist jetzt zwanzig Uhr." in now.accepted)
    }

    @Test
    fun reverseClockOneOClockComposesApocopatedEinUhr() {
        val train = PhraseSlots.reverseInstantiate(frame("sw", "train-departs-at"), hour = 13, minute = 0)
        assertTrue("Der Zug fährt um ein Uhr ab." in train.accepted)
        assertTrue("Der Zug fährt um eins ab." in train.accepted)
        assertTrue("Der Zug fährt um dreizehn Uhr ab." in train.accepted)
        assertFalse(train.accepted.any { "eins Uhr" in it }, "wrong 'eins Uhr' in ${train.accepted}")
    }

    @Test
    fun reverseNumberAndYearAcceptWrittenForms() {
        val price = PhraseSlots.reverseInstantiate(frame("uk", "it-costs-n-euros"), value = 21L)
        assertEquals(
            listOf("Das kostet 21 Euro.", "Das kostet einundzwanzig Euro."),
            price.accepted,
        )
        val year = PhraseSlots.reverseInstantiate(frame("uk", "repeat-the-year"), value = 1978L)
        assertTrue("Wiederholen Sie bitte die Jahreszahl: 1978." in year.accepted)
        assertTrue("Wiederholen Sie bitte die Jahreszahl: neunzehnhundertachtundsiebzig." in year.accepted)
        assertTrue("Wiederholen Sie bitte die Jahreszahl: eintausendneunhundertachtundsiebzig." in year.accepted)
    }

    /** The prompt realization's variant frames grade too — the du/Sie register split. */
    @Test
    fun reverseAcceptsEverySourceVariantFrame() {
        val task = PhraseSlots.reverseInstantiate(frame("uk", "repeat-please"), value = 7L)
        assertEquals("Wiederholen Sie bitte: 7.", task.display)
        assertEquals(
            listOf(
                "Wiederholen Sie bitte: 7.",
                "Wiederhole bitte: 7.",
                "Wiederholen Sie bitte: sieben.",
                "Wiederhole bitte: sieben.",
            ),
            task.accepted,
        )
    }

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

    // masculineNumeral filter holds across the widened accepted assembly

    @Test
    fun masculineNumeralNeverAcceptsFilteredFeminineForms() {
        val feminineBeforeNoun = Regex("(одна|дві) (євро|зошит)")
        for (slug in listOf("it-costs-n-euros", "i-have-n-notebooks")) {
            for (value in listOf(1L, 2L, 21L, 22L, 101L, 102L, 1001L, 1002L, 2022L)) {
                val template = frame("uk", slug)
                val forward = PhraseSlots.instantiate(template, value)
                val reverse = PhraseSlots.reverseInstantiate(template, value)
                for (sentence in forward.accepted + reverse.accepted) {
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
                PhraseSlots.reverseSample(template, Random(42)),
                PhraseSlots.reverseSample(template, Random(42)),
                template.id,
            )
            assertEquals(
                PhraseSlots.sample(template, Random(43)),
                PhraseSlots.sample(template, Random(43)),
                template.id,
            )
        }
    }
}

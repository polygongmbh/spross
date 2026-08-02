package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Accepted-form assembly for phrase-slot drills: every rendering of the slot
 * value — digits AND written-out generator variants — yields an accepted
 * sentence, in both drill directions (user report: written-out numbers and
 * times typed mid-sentence were rejected).
 */
class PhraseAcceptedFormsTests {

    private fun template(id: String): PhraseTemplate =
        PhraseTemplates.all.first { it.id == id }

    // Reverse: the German answer accepts written-out readings alongside digits

    @Test
    fun reverseClockAcceptsWrittenOutAndDigitalTime() {
        val task = PhraseSlots.reverseInstantiate(template("uk-clock-jetzt"), hour = 18, minute = 35)
        assertEquals("Es ist jetzt 18:35 Uhr.", task.display)
        assertTrue("Es ist jetzt 18:35 Uhr." in task.accepted)
        // The 24-hour reading absorbs the frame's literal "Uhr".
        assertTrue("Es ist jetzt achtzehn Uhr fünfunddreißig." in task.accepted)
        // Conversational 12-hour reading.
        assertTrue("Es ist jetzt fünf nach halb sieben." in task.accepted)
        // Swahili templates embed only minutes 0..30 — same assembly there.
        val zug = PhraseSlots.reverseInstantiate(template("sw-clock-zug"), hour = 18, minute = 5)
        assertTrue("Der Zug fährt um 18:05 Uhr ab." in zug.accepted)
        assertTrue("Der Zug fährt um achtzehn Uhr fünf ab." in zug.accepted)
        assertTrue("Der Zug fährt um fünf nach sechs ab." in zug.accepted)
    }

    @Test
    fun reverseClockComposesColloquialUmOnlyAfterUm() {
        val zug = PhraseSlots.reverseInstantiate(template("sw-clock-zug"), hour = 20, minute = 0)
        // "um acht" reading + frame "… um {slot} Uhr …" merge without doubling "um".
        assertTrue("Der Zug fährt um acht ab." in zug.accepted)
        assertTrue("Der Zug fährt um acht Uhr ab." in zug.accepted)
        assertTrue("Der Zug fährt um punkt acht ab." in zug.accepted)
        assertTrue("Der Zug fährt um zwanzig Uhr ab." in zug.accepted)
        val jetzt = PhraseSlots.reverseInstantiate(template("uk-clock-jetzt"), hour = 20, minute = 0)
        // No "um" in the frame → the adverbial "um acht" reading is skipped.
        assertFalse(jetzt.accepted.any { "um acht" in it })
        assertTrue("Es ist jetzt acht Uhr." in jetzt.accepted)
        assertTrue("Es ist jetzt zwanzig Uhr." in jetzt.accepted)
    }

    @Test
    fun reverseClockOneOClockComposesApocopatedEinUhr() {
        val zug = PhraseSlots.reverseInstantiate(template("sw-clock-zug"), hour = 13, minute = 0)
        assertTrue("Der Zug fährt um ein Uhr ab." in zug.accepted)
        assertTrue("Der Zug fährt um eins ab." in zug.accepted)
        assertTrue("Der Zug fährt um dreizehn Uhr ab." in zug.accepted)
        assertFalse(zug.accepted.any { "eins Uhr" in it }, "wrong 'eins Uhr' in ${zug.accepted}")
    }

    @Test
    fun reverseNumberAndYearAcceptWrittenForms() {
        val price = PhraseSlots.reverseInstantiate(template("uk-num-preis"), value = 21L)
        assertEquals(
            listOf("Das kostet 21 Euro.", "Das kostet einundzwanzig Euro."),
            price.accepted,
        )
        val year = PhraseSlots.reverseInstantiate(template("uk-year-wiederholen"), value = 1978L)
        assertTrue("Wiederholen Sie bitte die Jahreszahl: 1978." in year.accepted)
        assertTrue("Wiederholen Sie bitte die Jahreszahl: neunzehnhundertachtundsiebzig." in year.accepted)
        assertTrue("Wiederholen Sie bitte die Jahreszahl: eintausendneunhundertachtundsiebzig." in year.accepted)
    }

    // Forward: the target-language answer accepts the digit form too

    @Test
    fun forwardClockAcceptsPaddedAndBareDigits() {
        val task = PhraseSlots.instantiate(template("sw-clock-zug"), hour = 8, minute = 5)
        assertTrue("Treni inaondoka 08:05." in task.accepted)
        assertTrue("Treni inaondoka 8:05." in task.accepted)
        // The canonical word reading stays the display.
        assertEquals("Treni inaondoka saa mbili na dakika tano asubuhi.", task.display)
    }

    @Test
    fun forwardNumberAcceptsDigitsWithCountAgreement() {
        val task = PhraseSlots.instantiate(template("uk-num-hefte"), value = 22L)
        assertTrue("У мене є 22 зошити." in task.accepted)
        assertEquals("У мене є двадцять два зошити.", task.display)
    }

    // masculineSlot filter holds across the widened accepted assembly

    @Test
    fun masculineSlotNeverAcceptsFilteredFeminineForms() {
        val feminineBeforeNoun = Regex("(одна|дві) (євро|зошит)")
        for (id in listOf("uk-num-preis", "uk-num-hefte")) {
            for (value in listOf(1L, 2L, 21L, 22L, 101L, 102L, 1001L, 1002L, 2022L)) {
                val forward = PhraseSlots.instantiate(template(id), value)
                val reverse = PhraseSlots.reverseInstantiate(template(id), value)
                for (sentence in forward.accepted + reverse.accepted) {
                    assertFalse(feminineBeforeNoun.containsMatchIn(sentence), "$id/$value: $sentence")
                }
            }
        }
    }

    // Determinism with a seeded RNG

    @Test
    fun sampledAcceptedSetsAreDeterministic() {
        for (template in PhraseTemplates.all) {
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

package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Esperanto pack boundaries: the exceptionless composition, the welded tens and hundreds
 * against the spaced rest, the x-system twins, the scale NOUNS, years as plain cardinals,
 * and the clock's ordinal hour with its countdown past the half.
 */
class TrainerEsperantoTests {

    private fun display(n: Long) = Trainer.number(n, "eo").display
    private fun accepted(n: Long) = Trainer.number(n, "eo").accepted

    @Test
    fun cardinalsCoverTheBoundaries() {
        assertEquals("nulo", display(0))
        assertEquals("dek", display(10))
        assertEquals("dek kvin", display(15))
        assertEquals("dek ses", display(16))
        assertEquals("dek naŭ", display(19))
        assertEquals("dudek", display(20))
        assertEquals("dudek unu", display(21))
        assertEquals("tridek", display(30))
        assertEquals("tridek unu", display(31))
        assertEquals("naŭdek naŭ", display(99))
        assertEquals("cent", display(100))
        assertEquals("cent unu", display(101))
        assertEquals("ducent", display(200))
        assertEquals("kvincent", display(500))
        assertEquals("tricent kvardek sep", display(347))
    }

    /** The tens and the hundreds weld; every other seam is a space. */
    @Test
    fun thousandsAndMillionsCountWithTheScaleNouns() {
        assertEquals("mil", display(1000))
        assertEquals("mil naŭcent sepdek ok", display(1978))
        assertEquals("du mil", display(2000))
        assertEquals("dudek unu mil", display(21_000))
        assertEquals("cent mil", display(100_000))
        assertEquals("cent unu mil", display(101_000))
        // miliono and miliardo are nouns: a numeral counts them and they pluralize.
        assertEquals("unu miliono", display(1_000_000))
        assertEquals("du milionoj", display(2_000_000))
        assertEquals("dudek unu milionoj", display(21_000_000))
        assertEquals("unu miliardo", display(1_000_000_000))
        assertEquals(
            "naŭ miliardoj naŭcent naŭdek naŭ milionoj " +
                "naŭcent naŭdek naŭ mil naŭcent naŭdek naŭ",
            display(9_999_999_999),
        )
        assertEquals("10000000000", display(10_000_000_000))
    }

    /** A keyboard without ŭ writes the x-system, which is two edits away and must be listed. */
    @Test
    fun theXSystemSpellingIsAcceptedAndNeverShown() {
        assertEquals(listOf("naŭ", "naux"), accepted(9))
        assertEquals(listOf("naŭdek naŭ", "nauxdek naux"), accepted(99))
        assertEquals(listOf("naŭcent naŭdek naŭ", "nauxcent nauxdek naux"), accepted(999))
        assertTrue(accepted(1978).let { it[0] == "mil naŭcent sepdek ok" && "mil nauxcent sepdek ok" in it })
    }

    /**
     * Only the tens and the hundreds close up: what is spoken as separate words is written
     * as separate words, so a welded thousand is corrected rather than graded.
     */
    @Test
    fun onlyTheTensAndHundredsWeld() {
        assertEquals(listOf("du mil"), accepted(2000))
        assertEquals(listOf("cent mil"), accepted(100_000))
        assertEquals(listOf("dek kvin"), accepted(15))
        assertEquals(listOf("nulo", "nul"), accepted(0))
    }

    @Test
    fun yearsAreReadAsPlainCardinals() {
        val y1978 = Trainer.year(1978, "eo")
        assertEquals("mil naŭcent sepdek ok", y1978.display)
        assertTrue("mil nauxcent sepdek ok" in y1978.accepted)
        assertEquals("du mil kvin", Trainer.year(2005, "eo").display)
        assertEquals("du mil", Trainer.year(2000, "eo").display)
        assertEquals("mil naŭcent", Trainer.year(1900, "eo").display)
    }

    /** The hour is an ordinal with `horo` elided, and it carries its own article. */
    @Test
    fun theClockNamesTheHourAsAnOrdinal() {
        assertEquals("la dua posttagmeze", Trainer.clock(14, 0, "eo").display)
        assertEquals("la unua posttagmeze", Trainer.clock(13, 0, "eo").display)
        assertEquals("la dua kaj duono posttagmeze", Trainer.clock(14, 30, "eo").display)
        assertEquals("la unua kaj kvarono posttagmeze", Trainer.clock(13, 15, "eo").display)
        assertEquals("la dua kaj dek sep posttagmeze", Trainer.clock(14, 17, "eo").display)
        assertTrue("la dua horo" in Trainer.clock(14, 0, "eo").accepted)
    }

    @Test
    fun clockAcceptsTheTypedVariants() {
        val half = Trainer.clock(14, 30, "eo").accepted
        assertTrue("la dua kaj duono" in half)
        assertTrue("la dua kaj tridek" in half)
        assertTrue("duono post la dua" in half)
        assertTrue("la dua kaj tridek minutoj" in half)

        val quarter = Trainer.clock(14, 15, "eo").accepted
        assertTrue("la dua kaj dek kvin" in quarter)
        assertTrue("kvarono post la dua" in quarter)
        // The joiner is optional in a counted reading.
        assertTrue("la dua dek kvin" in quarter)

        // A compound hour closes up with a hyphen; the spaced spelling is not a reading.
        val eleven = Trainer.clock(11, 0, "eo").accepted
        assertEquals("la dek-unua antaŭtagmeze", Trainer.clock(11, 0, "eo").display)
        assertTrue("la dek-unua" in eleven)
        assertTrue(eleven.none { "dek unua" in it })
    }

    /** Past the half the coming hour is counted down to; `minus` is the quarter's alone. */
    @Test
    fun theCountdownNamesTheComingHour() {
        assertEquals("kvarono antaŭ la tria posttagmeze", Trainer.clock(14, 45, "eo").display)
        assertTrue("la tria minus kvarono" in Trainer.clock(14, 45, "eo").accepted)
        assertTrue("la dua kaj kvardek kvin" in Trainer.clock(14, 45, "eo").accepted)
        assertEquals("dek minutoj antaŭ la kvara nokte", Trainer.clock(3, 50, "eo").display)
        assertTrue("dek minutoj antaŭ la kvara matene" in Trainer.clock(3, 50, "eo").accepted)
        assertEquals("unu minuto antaŭ la tria posttagmeze", Trainer.clock(14, 59, "eo").display)
        assertTrue(Trainer.clock(14, 35, "eo").accepted.none { " minus " in it })
    }

    /** The part of the day belongs to the hour the reading NAMES, and stays optional. */
    @Test
    fun theDayPartFollowsTheNamedHour() {
        assertEquals("la kvina matene", Trainer.clock(5, 0, "eo").display)
        assertEquals("la kvina posttagmeze", Trainer.clock(17, 0, "eo").display)
        // 19:45 reads as eight in the evening while the clock still says seven.
        assertEquals("kvarono antaŭ la oka vespere", Trainer.clock(19, 45, "eo").display)
        // Counting DOWN to noon is still morning.
        assertTrue("kvarono antaŭ la dek-dua antaŭtagmeze" in Trainer.clock(11, 45, "eo").accepted)
        assertTrue(Trainer.clock(11, 45, "eo").accepted.none { "posttagmeze" in it })
        for (bare in listOf("kvarono antaŭ la kvina", "la kvara kaj kvardek kvin")) {
            assertTrue(bare in Trainer.clock(16, 45, "eo").accepted, bare)
        }
    }

    /** Timetables run 0–23 and name no part of the day. */
    @Test
    fun theTimetableRegisterIsAccepted() {
        assertTrue("la dek-kvara horo kaj tridek minutoj" in Trainer.clock(14, 30, "eo").accepted)
        assertTrue("la dek-kvara kaj tridek" in Trainer.clock(14, 30, "eo").accepted)
        assertTrue("la dek-kvara tridek" in Trainer.clock(14, 30, "eo").accepted)
        assertTrue("la dudek-unua horo" in Trainer.clock(21, 0, "eo").accepted)
        assertTrue("la nula horo kaj tridek minutoj" in Trainer.clock(0, 30, "eo").accepted)
        // Below thirteen its ordinal IS the conversational one, so only the spelled form is new.
        assertTrue("la tria horo kaj dek sep minutoj" in Trainer.clock(3, 17, "eo").accepted)
        assertTrue(Trainer.clock(14, 30, "eo").accepted.none { it.endsWith("posttagmeze") && "horo" in it })
    }

    /** One minute is counted with its noun, never with a bare numeral. */
    @Test
    fun oneMinuteIsCountedWithTheNoun() {
        assertEquals("la dua kaj unu minuto posttagmeze", Trainer.clock(14, 1, "eo").display)
        assertTrue("la dua kaj unu" in Trainer.clock(14, 1, "eo").accepted)
        assertTrue(Trainer.clock(14, 1, "eo").accepted.none { "unu minutoj" in it })
        assertTrue(Trainer.clock(14, 59, "eo").accepted.none { "unu minutoj" in it })
    }

    @Test
    fun middayAndMidnightAreNamed() {
        val midnight = Trainer.clock(0, 0, "eo")
        assertEquals("noktomezo", midnight.display)
        assertTrue("meznokto" in midnight.accepted)
        assertTrue("la nula horo" in midnight.accepted)
        assertTrue("la dek-dua nokte" in midnight.accepted)
        val noon = Trainer.clock(12, 0, "eo")
        assertEquals("tagmezo", noon.display)
        assertTrue("la dek-dua tagmeze" in noon.accepted)
        assertTrue("la dek-dua horo" in noon.accepted)
        // Only the exact hour is named; half past midnight reads as a time.
        assertEquals("la dek-dua kaj duono nokte", Trainer.clock(0, 30, "eo").display)
    }
}

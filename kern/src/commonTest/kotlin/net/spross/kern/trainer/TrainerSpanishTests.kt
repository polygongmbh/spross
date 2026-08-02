package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spanish pack boundaries: the twenties written as one word, the cien/ciento
 * split, the -cientos agreement, the bare "mil", the apocopated multipliers,
 * years as plain cardinals, and the clock's singular hour plus the countdown
 * past the half hour.
 */
class TrainerSpanishTests {

    private fun display(n: Long) = Trainer.number(n, "es").display
    private fun accepted(n: Long) = Trainer.number(n, "es").accepted

    @Test
    fun cardinalsCoverTheBoundaries() {
        assertEquals("cero", display(0))
        assertEquals("diez", display(10))
        assertEquals("quince", display(15))
        assertEquals("dieciséis", display(16))
        assertEquals("diecinueve", display(19))
        assertEquals("veinte", display(20))
        assertEquals("veintiuno", display(21))
        assertEquals("veintidós", display(22))
        assertEquals("treinta", display(30))
        assertEquals("treinta y uno", display(31))
        assertEquals("noventa y nueve", display(99))
        assertEquals("cien", display(100))
        assertEquals("ciento uno", display(101))
        assertEquals("doscientos", display(200))
        assertEquals("quinientos", display(500))
        assertEquals("trescientos cuarenta y siete", display(347))
    }

    @Test
    fun thousandsAndMillionsCountCorrectly() {
        assertEquals("mil", display(1000))
        assertEquals("mil novecientos setenta y ocho", display(1978))
        assertEquals("dos mil", display(2000))
        assertEquals("veintiún mil", display(21_000))
        assertEquals("cien mil", display(100_000))
        assertEquals("ciento un mil", display(101_000))
        assertEquals("un millón", display(1_000_000))
        assertEquals("dos millones", display(2_000_000))
        assertEquals("veintiún millones", display(21_000_000))
        // No short-scale billion — 10^9 is "mil millones".
        assertEquals("mil millones", display(1_000_000_000))
        assertEquals(
            "nueve mil novecientos noventa y nueve millones " +
                "novecientos noventa y nueve mil novecientos noventa y nueve",
            display(9_999_999_999),
        )
        assertEquals("10000000000", display(10_000_000_000))
    }

    @Test
    fun agreementFormsAreAccepted() {
        assertEquals(listOf("uno", "un", "una"), accepted(1))
        assertEquals(listOf("veintiuno", "veintiún", "veintiuna"), accepted(21))
        assertEquals(listOf("treinta y uno", "treinta y un", "treinta y una"), accepted(31))
        assertEquals(listOf("doscientos", "doscientas"), accepted(200))
        assertTrue("quinientas treinta y una" in accepted(531))
        // Nothing to agree with: one form only.
        assertEquals(listOf("cuarenta y siete"), accepted(47))
    }

    @Test
    fun yearsAreReadAsPlainCardinals() {
        val y1978 = Trainer.year(1978, "es")
        assertEquals("mil novecientos setenta y ocho", y1978.display)
        assertEquals(listOf("mil novecientos setenta y ocho"), y1978.accepted)
        assertEquals("dos mil cinco", Trainer.year(2005, "es").display)
        assertEquals("dos mil", Trainer.year(2000, "es").display)
        assertEquals("mil novecientos", Trainer.year(1900, "es").display)
    }

    @Test
    fun clockAgreesWithTheNamedHour() {
        assertEquals("son las dos", Trainer.clock(14, 0, "es").display)
        assertEquals("es la una", Trainer.clock(13, 0, "es").display)
        assertEquals("son las dos y media", Trainer.clock(14, 30, "es").display)
        assertEquals("es la una y cuarto", Trainer.clock(13, 15, "es").display)
        assertEquals("son las tres menos cuarto", Trainer.clock(14, 45, "es").display)
        // The copula follows the hour the reading names, not the clock's hour.
        assertEquals("es la una menos cuarto", Trainer.clock(12, 45, "es").display)
        assertEquals("son las tres menos veinticinco", Trainer.clock(14, 35, "es").display)
        assertEquals("son las dos y diecisiete", Trainer.clock(14, 17, "es").display)
    }

    @Test
    fun clockAcceptsTheTypedVariants() {
        val half = Trainer.clock(14, 30, "es").accepted
        assertTrue("son las dos y treinta" in half)
        assertTrue("dos y media" in half)

        val quarter = Trainer.clock(14, 15, "es").accepted
        assertTrue("son las dos y quince" in quarter)

        val toThree = Trainer.clock(14, 45, "es").accepted
        assertTrue("son las dos y cuarenta y cinco" in toThree)
        assertTrue("son las tres menos quince" in toThree)

        val two = Trainer.clock(14, 0, "es").accepted
        assertTrue("son las dos en punto" in two)
        assertTrue("dos" in two)
    }

    @Test
    fun middayAndMidnightAreAccepted() {
        val midnight = Trainer.clock(0, 0, "es")
        assertEquals("son las doce", midnight.display)
        assertTrue("es medianoche" in midnight.accepted)
        val noon = Trainer.clock(12, 0, "es")
        assertTrue("es mediodía" in noon.accepted)
        assertEquals("son las doce y media", Trainer.clock(0, 30, "es").display)
    }
}

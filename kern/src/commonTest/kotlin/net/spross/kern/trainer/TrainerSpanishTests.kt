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
        assertEquals("son las dos de la tarde", Trainer.clock(14, 0, "es").display)
        assertEquals("es la una de la tarde", Trainer.clock(13, 0, "es").display)
        assertEquals("son las dos y media de la tarde", Trainer.clock(14, 30, "es").display)
        assertEquals("es la una y cuarto de la tarde", Trainer.clock(13, 15, "es").display)
        assertEquals("son las tres menos cuarto de la tarde", Trainer.clock(14, 45, "es").display)
        // The copula follows the hour the reading names, not the clock's hour.
        assertEquals("es la una menos cuarto de la tarde", Trainer.clock(12, 45, "es").display)
        assertEquals("son las tres menos veinticinco de la tarde", Trainer.clock(14, 35, "es").display)
        assertEquals("son las dos y diecisiete de la tarde", Trainer.clock(14, 17, "es").display)
    }

    @Test
    fun clockAcceptsTheTypedVariants() {
        val half = Trainer.clock(14, 30, "es").accepted
        assertTrue("son las dos y treinta" in half)
        assertTrue("dos y media" in half)
        assertTrue("las dos y media" in half)
        assertTrue("son las dos treinta" in half)

        val quarter = Trainer.clock(14, 15, "es").accepted
        assertTrue("son las dos y quince" in quarter)
        assertTrue("son las dos y quince minutos" in quarter)

        val toThree = Trainer.clock(14, 45, "es").accepted
        assertTrue("son las dos y cuarenta y cinco" in toThree)
        assertTrue("son las tres menos quince" in toThree)

        val two = Trainer.clock(14, 0, "es").accepted
        assertTrue("son las dos en punto" in two)
        assertTrue("dos" in two)
    }

    /** The part of the day belongs to the hour the reading NAMES. */
    @Test
    fun theDayPartFollowsTheNamedHourAndStaysOptional() {
        assertEquals("son las cinco de la madrugada", Trainer.clock(5, 0, "es").display)
        assertEquals("son las cinco de la tarde", Trainer.clock(17, 0, "es").display)
        // 19:45 reads as eight o'clock, and eight is de la noche.
        assertEquals("son las ocho menos cuarto de la noche", Trainer.clock(19, 45, "es").display)
        assertTrue("son las ocho menos cuarto de la tarde" in Trainer.clock(19, 45, "es").accepted)
        // Counting DOWN to noon is still morning: never "del día".
        assertEquals("son las doce menos cuarto de la mañana", Trainer.clock(11, 45, "es").display)
        // Every reading is accepted bare, so nothing that graded right stops.
        for (bare in listOf("son las cinco menos cuarto", "cinco menos cuarto", "las cinco menos cuarto")) {
            assertTrue(bare in Trainer.clock(16, 45, "es").accepted, bare)
        }
    }

    /** "veinte para las tres" — the countdown as America says it. */
    @Test
    fun theAmericanCountdownIsAccepted() {
        val toThree = Trainer.clock(14, 40, "es").accepted
        assertTrue("veinte para las tres" in toThree)
        assertTrue("veinte minutos para las tres" in toThree)
        assertTrue("faltan veinte para las tres" in toThree)
        assertTrue("faltan veinte minutos para las tres" in toThree)
        assertTrue("son veinte para las tres" in toThree)
        val toOne = Trainer.clock(12, 45, "es").accepted
        assertTrue("cuarto para la una" in toOne)
        assertTrue("falta un cuarto para la una" in toOne)
        assertTrue("quince minutos para la una" in toOne)
        // "a" never replaces "para" — the DPD says so outright.
        assertTrue(Trainer.clock(14, 40, "es").accepted.none { " a las " in it })
    }

    /** Timetables, news and announcements run 0–23 and name no part of the day. */
    @Test
    fun theTimetableRegisterIsAccepted() {
        assertTrue("son las catorce treinta" in Trainer.clock(14, 30, "es").accepted)
        assertTrue("son las catorce horas treinta minutos" in Trainer.clock(14, 30, "es").accepted)
        assertTrue("son las dieciocho treinta y cinco" in Trainer.clock(18, 35, "es").accepted)
        assertTrue("son las veintiuna horas" in Trainer.clock(21, 0, "es").accepted)
        // One o'clock stays singular even here.
        assertTrue("es la una treinta" in Trainer.clock(1, 30, "es").accepted)
        assertTrue(Trainer.clock(1, 30, "es").accepted.none { it.startsWith("son las una") })
    }

    /** A minute is counted with its noun, never with a bare "uno". */
    @Test
    fun oneMinuteIsCountedWithTheNoun() {
        assertEquals("son las dos y un minuto de la tarde", Trainer.clock(14, 1, "es").display)
        assertEquals("son las tres menos un minuto de la tarde", Trainer.clock(14, 59, "es").display)
        assertTrue("un minuto para las tres" in Trainer.clock(14, 59, "es").accepted)
        // An apocopating count keeps its noun apocopated too.
        assertTrue("son las dos y veintiún minutos" in Trainer.clock(14, 21, "es").accepted)
        for (task in listOf(Trainer.clock(14, 1, "es"), Trainer.clock(14, 21, "es"), Trainer.clock(14, 59, "es"))) {
            assertTrue(task.accepted.none { "uno minutos" in it || "un minutos" in it }, task.accepted.toString())
        }
    }

    @Test
    fun middayAndMidnightAreNamed() {
        val midnight = Trainer.clock(0, 0, "es")
        assertEquals("son las doce de la noche", midnight.display)
        assertTrue("es medianoche" in midnight.accepted)
        assertTrue("es la medianoche" in midnight.accepted)
        assertTrue("son las cero horas" in midnight.accepted)
        val noon = Trainer.clock(12, 0, "es")
        assertEquals("son las doce del mediodía", noon.display)
        assertTrue("es mediodía" in noon.accepted)
        assertTrue("son las doce del día" in noon.accepted)
        // Only the exact hour is named; half past midnight reads as a time.
        assertEquals("son las doce y media de la noche", Trainer.clock(0, 30, "es").display)
    }
}

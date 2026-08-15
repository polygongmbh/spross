package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Italian pack boundaries: the elisions at the tens and at cento, the accent on a compound
 * tre, everything below a million welded into one word, years as plain cardinals, and the
 * clock's copula agreeing with the hour a reading names.
 */
class TrainerItalianTests {

    private fun display(n: Long) = Trainer.number(n, "it").display
    private fun accepted(n: Long) = Trainer.number(n, "it").accepted

    @Test
    fun cardinalsCoverTheBoundaries() {
        assertEquals("zero", display(0))
        assertEquals("dieci", display(10))
        assertEquals("quindici", display(15))
        assertEquals("sedici", display(16))
        assertEquals("diciannove", display(19))
        assertEquals("venti", display(20))
        assertEquals("ventuno", display(21))
        assertEquals("ventidue", display(22))
        assertEquals("trenta", display(30))
        assertEquals("trentuno", display(31))
        assertEquals("novantanove", display(99))
        assertEquals("cento", display(100))
        assertEquals("centouno", display(101))
        assertEquals("duecento", display(200))
        assertEquals("cinquecento", display(500))
        assertEquals("trecentoquarantasette", display(347))
    }

    /** The ten gives its vowel up to uno and otto, and cento gives its own to any o. */
    @Test
    fun theSeamsElideExactlyWhereItalianElides() {
        assertEquals("ventotto", display(28))
        assertEquals("quarantuno", display(41))
        assertEquals("quarantotto", display(48))
        assertEquals("novantotto", display(98))
        assertEquals("centotto", display(108))
        assertEquals("centottanta", display(180))
        assertEquals("centottantotto", display(188))
        assertEquals("duecentottanta", display(280))
        // Before u nothing elides, and mille keeps its e against everything.
        assertEquals("centoundici", display(111))
        assertEquals("milleuno", display(1001))
        assertEquals("milleotto", display(1008))
        assertEquals("duemilaotto", display(2008))
    }

    /** A compound ending in tre carries the stress, and therefore the accent. */
    @Test
    fun aCompoundEndingInTreTakesTheAcuteAccent() {
        assertEquals("tre", display(3))
        assertEquals("tredici", display(13))
        assertEquals("ventitré", display(23))
        assertEquals("novantatré", display(93))
        assertEquals("centotré", display(103))
        assertEquals("milletré", display(1003))
        assertEquals("duemilatré", display(2003))
    }

    @Test
    fun thousandsAndMillionsCountCorrectly() {
        assertEquals("mille", display(1000))
        assertEquals("millenovecentosettantotto", display(1978))
        assertEquals("duemila", display(2000))
        assertEquals("ventunomila", display(21_000))
        assertEquals("centomila", display(100_000))
        assertEquals("centounomila", display(101_000))
        assertEquals("novecentonovantanovemilanovecentonovantanove", display(999_999))
        assertEquals("un milione", display(1_000_000))
        assertEquals("due milioni", display(2_000_000))
        assertEquals("un miliardo", display(1_000_000_000))
        assertEquals(
            "nove miliardi novecentonovantanove milioni " +
                "novecentonovantanovemilanovecentonovantanove",
            display(9_999_999_999),
        )
        assertEquals("10000000000", display(10_000_000_000))
    }

    @Test
    fun theRecordedAlternateSpellingsAreAccepted() {
        // The hiatus spelling of the cento/otto seam grades beside the contracted one.
        assertEquals(listOf("centotto", "centootto"), accepted(108))
        assertEquals(listOf("centottomila", "centoottomila"), accepted(108_000))
        // Where a scale word leaves a tail, the e speakers put before it grades too —
        // in front of the whole last group, not in front of its scale word.
        assertEquals(
            listOf("un milione cinquecento", "un milione e cinquecento"),
            accepted(1_000_500),
        )
        assertTrue("un miliardo e un milione" in accepted(1_001_000_000))
        assertEquals(listOf("due milioni"), accepted(2_000_000))
        // "centuno" is one substitution from "ventuno" and is left out for that reason.
        assertEquals(listOf("centouno"), accepted(101))
        // Nothing to record: one spelling only.
        assertEquals(listOf("quarantasette"), accepted(47))
    }

    @Test
    fun yearsAreReadAsPlainCardinals() {
        val y1978 = Trainer.year(1978, "it")
        assertEquals("millenovecentosettantotto", y1978.display)
        assertEquals(listOf("millenovecentosettantotto"), y1978.accepted)
        assertEquals("duemilacinque", Trainer.year(2005, "it").display)
        assertEquals("duemila", Trainer.year(2000, "it").display)
        assertEquals("millenovecento", Trainer.year(1900, "it").display)
    }

    @Test
    fun clockAgreesWithTheNamedHour() {
        assertEquals("sono le due di pomeriggio", Trainer.clock(14, 0, "it").display)
        assertEquals("è l'una di pomeriggio", Trainer.clock(13, 0, "it").display)
        assertEquals("sono le due e mezza di pomeriggio", Trainer.clock(14, 30, "it").display)
        assertEquals("è l'una e un quarto di pomeriggio", Trainer.clock(13, 15, "it").display)
        assertEquals("sono le tre meno un quarto di pomeriggio", Trainer.clock(14, 45, "it").display)
        // The copula follows the hour the reading names, not the clock's hour.
        assertEquals("è l'una meno un quarto di pomeriggio", Trainer.clock(12, 45, "it").display)
        assertEquals("sono le tre meno venticinque di pomeriggio", Trainer.clock(14, 35, "it").display)
        assertEquals("sono le due e diciassette di pomeriggio", Trainer.clock(14, 17, "it").display)
    }

    @Test
    fun clockAcceptsTheTypedVariants() {
        val half = Trainer.clock(14, 30, "it").accepted
        assertTrue("sono le due e trenta" in half)
        assertTrue("sono le due e mezzo" in half)
        assertTrue("le due e mezza" in half)
        assertTrue("due e mezza" in half)

        val quarter = Trainer.clock(14, 15, "it").accepted
        assertTrue("sono le due e quindici" in quarter)
        assertTrue("sono le due e quindici minuti" in quarter)

        val toThree = Trainer.clock(14, 45, "it").accepted
        assertTrue("sono le due e tre quarti" in toThree)
        assertTrue("sono le due e quarantacinque" in toThree)
        assertTrue("sono le tre meno quindici" in toThree)

        val two = Trainer.clock(14, 0, "it").accepted
        assertTrue("due" in two)
    }

    /** The part of the day belongs to the hour the reading NAMES. */
    @Test
    fun theDayPartFollowsTheNamedHourAndStaysOptional() {
        assertEquals("sono le cinque di mattina", Trainer.clock(5, 0, "it").display)
        assertTrue("sono le cinque del mattino" in Trainer.clock(5, 0, "it").accepted)
        assertEquals("sono le cinque di pomeriggio", Trainer.clock(17, 0, "it").display)
        // 19:45 reads as eight o'clock, and eight is di sera.
        assertEquals("sono le otto meno un quarto di sera", Trainer.clock(19, 45, "it").display)
        // Twelve takes no part of the day: mezzogiorno and mezzanotte are its words.
        assertEquals("sono le dodici meno un quarto", Trainer.clock(11, 45, "it").display)
        assertTrue(Trainer.clock(12, 30, "it").accepted.none { " di " in it })
        // Every reading is accepted bare, so nothing that graded right stops.
        for (bare in listOf("sono le cinque meno un quarto", "cinque meno un quarto", "le cinque meno un quarto")) {
            assertTrue(bare in Trainer.clock(16, 45, "it").accepted, bare)
        }
    }

    /** "mancano venti minuti alle tre" — what is still missing before the coming hour. */
    @Test
    fun theMissingMinutesReadingIsAccepted() {
        val toThree = Trainer.clock(14, 40, "it").accepted
        assertTrue("mancano venti minuti alle tre" in toThree)
        assertTrue("mancano venti minuti alle tre di pomeriggio" in toThree)
        val toOne = Trainer.clock(12, 45, "it").accepted
        assertTrue("manca un quarto all'una" in toOne)
        assertTrue("mancano quindici minuti all'una" in toOne)
        // The verb agrees with what is missing, so one of anything takes manca.
        assertTrue("manca un minuto alle tre" in Trainer.clock(14, 59, "it").accepted)
        // A count that apocopates does so before the noun too.
        assertTrue("mancano ventun minuti alle tre" in Trainer.clock(14, 39, "it").accepted)
    }

    /** Timetables, news and announcements run 13–23 and name no part of the day. */
    @Test
    fun theTimetableRegisterIsAccepted() {
        assertTrue("sono le quattordici e trenta" in Trainer.clock(14, 30, "it").accepted)
        assertTrue("sono le quattordici e trenta minuti" in Trainer.clock(14, 30, "it").accepted)
        assertTrue("sono le diciotto e trentacinque" in Trainer.clock(18, 35, "it").accepted)
        assertTrue("sono le ventuno" in Trainer.clock(21, 0, "it").accepted)
        assertTrue("sono le ventitré e quarantacinque" in Trainer.clock(23, 45, "it").accepted)
        // Below thirteen it would only repeat the 12-hour reading, so it is not offered —
        // and with no second construction left the reveal goes bare.
        assertNull(Trainer.clock(11, 0, "it").gloss)
        assertEquals("anche: sono le ventitré", Trainer.clock(23, 0, "it").gloss)
        // One o'clock stays singular: every reading of it takes "è l'", never "sono le".
        assertTrue(Trainer.clock(1, 30, "it").accepted.none { it.startsWith("sono le") })
    }

    /** A minute is counted with its noun, never with a bare "uno". */
    @Test
    fun oneMinuteIsCountedWithTheNoun() {
        assertEquals("sono le due e un minuto di pomeriggio", Trainer.clock(14, 1, "it").display)
        assertTrue("sono le quattordici e un minuto" in Trainer.clock(14, 1, "it").accepted)
        assertTrue("sono le tre meno un minuto" in Trainer.clock(14, 59, "it").accepted)
        // An apocopating count keeps its noun apocopated too.
        assertTrue("sono le due e ventun minuti" in Trainer.clock(14, 21, "it").accepted)
        assertTrue("sono le due e ventuno" in Trainer.clock(14, 21, "it").accepted)
        for (task in listOf(Trainer.clock(14, 1, "it"), Trainer.clock(14, 59, "it"))) {
            assertTrue(task.accepted.none { "uno minuti" in it || "un minuti" in it }, task.accepted.toString())
        }
    }

    @Test
    fun middayAndMidnightAreNamed() {
        val midnight = Trainer.clock(0, 0, "it")
        assertEquals("è mezzanotte", midnight.display)
        assertTrue("mezzanotte" in midnight.accepted)
        assertTrue("sono le dodici di notte" in midnight.accepted)
        assertTrue("sono le ventiquattro" in midnight.accepted)
        val noon = Trainer.clock(12, 0, "it")
        assertEquals("è mezzogiorno", noon.display)
        assertTrue("mezzogiorno" in noon.accepted)
        assertTrue("sono le dodici" in noon.accepted)
        // Only the exact hour is named; half past midnight reads as a time.
        assertEquals("sono le dodici e mezza di notte", Trainer.clock(0, 30, "it").display)
    }
}

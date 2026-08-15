package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * French pack boundaries: the vigesimal seventies and eighties, the `et` that stops at 71,
 * the plural -s a following numeral takes off `vingts`/`cents`, the regional decades,
 * years as cardinals with the hundred-counting and `mil` spellings beside them, and the
 * clock's bare reading with its copula, its countdown and its timetable register.
 */
class TrainerFrenchTests {

    private fun display(n: Long) = Trainer.number(n, "fr").display
    private fun accepted(n: Long) = Trainer.number(n, "fr").accepted

    @Test
    fun cardinalsCoverTheBoundaries() {
        assertEquals("zéro", display(0))
        assertEquals("dix", display(10))
        assertEquals("quinze", display(15))
        assertEquals("seize", display(16))
        assertEquals("dix-sept", display(17))
        assertEquals("dix-neuf", display(19))
        assertEquals("vingt", display(20))
        assertEquals("vingt et un", display(21))
        assertEquals("vingt-deux", display(22))
        assertEquals("trente", display(30))
        assertEquals("trente et un", display(31))
        assertEquals("quatre-vingt-dix-neuf", display(99))
        assertEquals("cent", display(100))
        assertEquals("cent un", display(101))
        assertEquals("deux cents", display(200))
        assertEquals("trois cent quarante-sept", display(347))
    }

    /** 70/80/90 are counted on twenty, and `et` reaches 71 and stops. */
    @Test
    fun theVigesimalDecadesReadAsStandardFrenchWritesThem() {
        assertEquals("soixante-dix", display(70))
        assertEquals("soixante et onze", display(71))
        assertEquals("soixante-douze", display(72))
        assertEquals("soixante-dix-neuf", display(79))
        assertEquals("quatre-vingts", display(80))
        assertEquals("quatre-vingt-un", display(81))
        assertEquals("quatre-vingt-dix", display(90))
        assertEquals("quatre-vingt-onze", display(91))
    }

    /** A following NUMERAL takes the plural mark off; a following scale noun leaves it. */
    @Test
    fun theAgreementOfVingtsAndCentsFollowsWhatComesAfterThem() {
        assertEquals("cent quatre-vingts", display(180))
        assertEquals("quatre-vingt mille", display(80_000))
        assertEquals("quatre-vingts millions", display(80_000_000))
        assertEquals("deux cent mille", display(200_000))
        assertEquals("deux cents millions", display(200_000_000))
        assertEquals("deux cent un", display(201))
    }

    @Test
    fun thousandsAndMillionsCountCorrectly() {
        assertEquals("mille", display(1000))
        assertEquals("mille neuf cent soixante-dix-huit", display(1978))
        assertEquals("deux mille", display(2000))
        assertEquals("vingt et un mille", display(21_000))
        assertEquals("cent mille", display(100_000))
        assertEquals("un million", display(1_000_000))
        assertEquals("un milliard", display(1_000_000_000))
        assertEquals(
            "neuf milliards neuf cent quatre-vingt-dix-neuf millions " +
                "neuf cent quatre-vingt-dix-neuf mille neuf cent quatre-vingt-dix-neuf",
            display(9_999_999_999),
        )
        assertEquals("10000000000", display(10_000_000_000))
    }

    /** Three spellings of one number, and the regional decades beside them. */
    @Test
    fun theSpellingsAndTheRegionalDecadesAreAccepted() {
        assertEquals(listOf("vingt et un", "vingt-et-un"), accepted(21))
        assertEquals(listOf("soixante-dix", "soixante dix", "septante"), accepted(70))
        assertEquals(listOf("quatre-vingts", "quatre vingts", "huitante"), accepted(80))
        assertEquals(listOf("quatre-vingt-dix", "quatre vingt dix", "nonante"), accepted(90))
        assertTrue("septante et un" in accepted(71))
        assertTrue("nonante-neuf" in accepted(99))
        assertTrue("huitante et un" in accepted(81))
        assertTrue("mille-neuf-cent-soixante-dix-huit" in accepted(1978))
        assertTrue("mille neuf cent soixante dix huit" in accepted(1978))
        // Regionally near-extinct: an accepted-but-dead form teaches a register nobody uses.
        assertTrue(accepted(80).none { "octante" in it })
        // Nothing to vary: one spelling only.
        assertEquals(listOf("quarante-cinq", "quarante cinq"), accepted(45))
    }

    @Test
    fun yearsTakeTheDateAndHundredCountingSpellings() {
        val y1978 = Trainer.year(1978, "fr")
        assertEquals("mille neuf cent soixante-dix-huit", y1978.display)
        assertTrue("mil neuf cent soixante-dix-huit" in y1978.accepted)
        assertTrue("dix-neuf cent soixante-dix-huit" in y1978.accepted)
        assertEquals("mille neuf cents", Trainer.year(1900, "fr").display)
        assertTrue("dix-neuf cents" in Trainer.year(1900, "fr").accepted)
        assertEquals("deux mille cinq", Trainer.year(2005, "fr").display)
        // Past the thousand nobody counts hundreds, and "mil" is a date spelling only.
        assertTrue(Trainer.year(2005, "fr").accepted.none { "cent" in it || it.startsWith("mil ") })
    }

    @Test
    fun theClockReadsBareAndAgreesWithItsHour() {
        assertEquals("deux heures", Trainer.clock(14, 0, "fr").display)
        assertEquals("une heure", Trainer.clock(13, 0, "fr").display)
        assertEquals("deux heures et quart", Trainer.clock(14, 15, "fr").display)
        assertEquals("deux heures et demie", Trainer.clock(14, 30, "fr").display)
        assertEquals("trois heures moins le quart", Trainer.clock(14, 45, "fr").display)
        assertEquals("trois heures moins vingt", Trainer.clock(14, 40, "fr").display)
        assertEquals("deux heures dix", Trainer.clock(14, 10, "fr").display)
        // Off the five-minute grid the minute is read out, never counted back.
        assertEquals("deux heures trente-sept", Trainer.clock(14, 37, "fr").display)
        // The minute is feminine, so a count ending in one agrees with it.
        assertEquals("deux heures une", Trainer.clock(14, 1, "fr").display)
        assertEquals("deux heures vingt et une", Trainer.clock(14, 21, "fr").display)
        assertEquals("trois heures moins une", Trainer.clock(14, 59, "fr").display)
    }

    @Test
    fun theClockAcceptsTheTypedVariants() {
        val quarter = Trainer.clock(14, 15, "fr").accepted
        assertTrue("il est deux heures et quart" in quarter)
        assertTrue("deux heures quinze" in quarter)
        assertTrue("quatorze heures quinze" in quarter)

        val toThree = Trainer.clock(14, 45, "fr").accepted
        assertTrue("trois heures moins un quart" in toThree)
        assertTrue("deux heures quarante-cinq" in toThree)
        assertTrue("deux heures quarante cinq" in toThree)
        assertTrue("quatorze heures quarante-cinq" in toThree)

        assertTrue("deux heures trente" in Trainer.clock(14, 30, "fr").accepted)
        assertTrue("il est deux heures" in Trainer.clock(14, 0, "fr").accepted)
    }

    /** The part of the day belongs to the hour the reading NAMES, and is optional. */
    @Test
    fun theDayPartFollowsTheNamedHour() {
        assertTrue("deux heures de l'après-midi" in Trainer.clock(14, 0, "fr").accepted)
        assertTrue("deux heures du matin" in Trainer.clock(2, 0, "fr").accepted)
        assertTrue("deux heures de la nuit" in Trainer.clock(2, 0, "fr").accepted)
        assertTrue("onze heures du soir" in Trainer.clock(23, 0, "fr").accepted)
        // 19:45 names eight in the evening, while the clock still says seven.
        assertTrue("huit heures moins le quart du soir" in Trainer.clock(19, 45, "fr").accepted)
        // Nothing is compulsory: every reading grades bare too.
        assertTrue("huit heures moins le quart" in Trainer.clock(19, 45, "fr").accepted)
        // Noon and midnight ARE the half of the day, so no part is hung on them.
        assertTrue(Trainer.clock(12, 0, "fr").accepted.none { "matin" in it || "après" in it })
    }

    /** Timetables, news and announcements run 0–23 and name no part of the day. */
    @Test
    fun theTimetableRegisterIsAcceptedAndGlossed() {
        assertEquals("aussi : quatorze heures trente", Trainer.clock(14, 30, "fr").gloss)
        assertTrue("il est quatorze heures trente" in Trainer.clock(14, 30, "fr").accepted)
        assertTrue("zéro heure trente" in Trainer.clock(0, 30, "fr").accepted)
        assertTrue("vingt et une heures" in Trainer.clock(21, 0, "fr").accepted)
        assertTrue("dix-huit heures" in Trainer.clock(18, 0, "fr").accepted)
        // Below thirteen the two registers coincide, so the reveal has nothing to add.
        assertEquals(null, Trainer.clock(3, 5, "fr").gloss)
    }

    /** midi and minuit take no "heures", and being masculine they take "et demi". */
    @Test
    fun noonAndMidnightAreNamedAndTakeTheMasculineHalf() {
        assertEquals("midi", Trainer.clock(12, 0, "fr").display)
        assertEquals("minuit", Trainer.clock(0, 0, "fr").display)
        assertEquals("midi et demi", Trainer.clock(12, 30, "fr").display)
        assertEquals("minuit et demi", Trainer.clock(0, 30, "fr").display)
        assertEquals("midi moins le quart", Trainer.clock(11, 45, "fr").display)
        assertEquals("minuit moins le quart", Trainer.clock(23, 45, "fr").display)
        assertTrue("il est midi" in Trainer.clock(12, 0, "fr").accepted)
        assertTrue("zéro heure" in Trainer.clock(0, 0, "fr").accepted)
        assertTrue("douze heures" in Trainer.clock(12, 0, "fr").accepted)
        for (task in listOf(Trainer.clock(12, 30, "fr"), Trainer.clock(0, 30, "fr"))) {
            assertTrue(task.accepted.none { "et demie" in it }, task.accepted.toString())
            assertTrue(task.accepted.none { "midi heures" in it || "minuit heures" in it })
        }
    }
}

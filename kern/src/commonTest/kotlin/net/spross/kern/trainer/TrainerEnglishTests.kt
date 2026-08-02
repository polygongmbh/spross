package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * English pack boundaries: the teens, the hundreds join, the thousands join,
 * zero, the "and"/hyphen spelling axes, the pair-vs-cardinal year split, and
 * the clock's o'clock/quarter/half/to forms including midnight and noon.
 */
class TrainerEnglishTests {

    private fun display(n: Long) = Trainer.number(n, "en").display
    private fun accepted(n: Long) = Trainer.number(n, "en").accepted

    @Test
    fun cardinalsCoverTheBoundaries() {
        assertEquals("zero", display(0))
        assertEquals("ten", display(10))
        assertEquals("twelve", display(12))
        assertEquals("thirteen", display(13))
        assertEquals("nineteen", display(19))
        assertEquals("twenty", display(20))
        assertEquals("twenty-one", display(21))
        assertEquals("forty", display(40))
        assertEquals("ninety-nine", display(99))
        assertEquals("one hundred", display(100))
        assertEquals("three hundred forty-seven", display(347))
        assertEquals("one thousand", display(1000))
        assertEquals("one thousand five", display(1005))
        assertEquals("two thousand three hundred forty-five", display(2345))
        assertEquals("ten thousand", display(10_000))
    }

    @Test
    fun millionsAndBillionsStayInLongArithmetic() {
        assertEquals("one million", display(1_000_000))
        assertEquals("two million", display(2_000_000))
        assertEquals("one billion", display(1_000_000_000))
        assertEquals(
            "nine billion nine hundred ninety-nine million " +
                "nine hundred ninety-nine thousand nine hundred ninety-nine",
            display(9_999_999_999),
        )
        assertEquals("10000000000", display(10_000_000_000))
    }

    @Test
    fun bothSpellingAxesAreAccepted() {
        val forms = accepted(347)
        assertTrue("three hundred forty-seven" in forms)
        assertTrue("three hundred and forty-seven" in forms)
        assertTrue("three hundred forty seven" in forms)
        assertTrue("three hundred and forty seven" in forms)
        assertTrue("one thousand and five" in accepted(1005))
        // A number with nothing to hyphenate or bridge has exactly one form.
        assertEquals(listOf("seven"), accepted(7))
        assertEquals(listOf("one hundred"), accepted(100))
    }

    @Test
    fun yearsSplitPairReadingFromFullCardinal() {
        val y1978 = Trainer.year(1978, "en")
        assertEquals("nineteen seventy-eight", y1978.display)
        assertTrue("nineteen seventy eight" in y1978.accepted)
        assertTrue("one thousand nine hundred seventy-eight" in y1978.accepted)

        val y2005 = Trainer.year(2005, "en")
        assertEquals("two thousand five", y2005.display)
        assertTrue("two thousand and five" in y2005.accepted)
        assertTrue("twenty oh five" in y2005.accepted)

        assertEquals("twenty nineteen", Trainer.year(2019, "en").display)
        assertEquals("nineteen hundred", Trainer.year(1900, "en").display)
        assertEquals("two thousand", Trainer.year(2000, "en").display)

        val y1905 = Trainer.year(1905, "en")
        assertEquals("nineteen oh five", y1905.display)
        assertTrue("nineteen hundred and five" in y1905.accepted)
    }

    @Test
    fun clockReadsPastAndTo() {
        assertEquals("two o'clock", Trainer.clock(14, 0, "en").display)
        assertEquals("quarter past two", Trainer.clock(14, 15, "en").display)
        assertEquals("half past two", Trainer.clock(14, 30, "en").display)
        assertEquals("quarter to three", Trainer.clock(14, 45, "en").display)
        assertEquals("twenty-five to three", Trainer.clock(14, 35, "en").display)
        assertEquals("five past two", Trainer.clock(14, 5, "en").display)
        assertEquals("seventeen past two", Trainer.clock(14, 17, "en").display)
        // The prompt is the only place digits appear.
        assertEquals("14:35", Trainer.clock(14, 35, "en").prompt)
    }

    @Test
    fun clockAcceptsTheTypedVariants() {
        val quarter = Trainer.clock(14, 15, "en").accepted
        assertTrue("a quarter past two" in quarter)
        assertTrue("quarter after two" in quarter)
        assertTrue("fifteen past two" in quarter)
        assertTrue("two fifteen" in quarter)

        val half = Trainer.clock(14, 30, "en").accepted
        assertTrue("two thirty" in half)
        assertTrue("half two" in half)

        val toThree = Trainer.clock(14, 45, "en").accepted
        assertTrue("a quarter to three" in toThree)
        assertTrue("two forty-five" in toThree)
        assertTrue("two forty five" in toThree)

        assertTrue("two oh five" in Trainer.clock(14, 5, "en").accepted)
        assertTrue("two" in Trainer.clock(14, 0, "en").accepted)
    }

    @Test
    fun midnightAndNoonAreNamed() {
        val midnight = Trainer.clock(0, 0, "en")
        assertEquals("midnight", midnight.display)
        assertTrue("twelve o'clock" in midnight.accepted)
        val noon = Trainer.clock(12, 0, "en")
        assertEquals("noon", noon.display)
        assertTrue("midday" in noon.accepted)
        // Only the exact hour is named; 00:30 reads as a time.
        assertEquals("half past twelve", Trainer.clock(0, 30, "en").display)
    }
}

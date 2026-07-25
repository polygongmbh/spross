package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hand-picked assertions for millions and billions (beyond the golden
 * fixture, which stops at 999 999). Canonical forms verified against
 * standard readings in each language; 10-digit values prove the Long
 * arithmetic (Kotlin Int is 32-bit on every platform).
 */
class TrainerLargeNumberTests {

    private fun display(n: Long, lang: String): String = Trainer.number(n, lang).display

    @Test
    fun germanMillionsAndBillions() {
        assertEquals("eine Million", display(1_000_000, "de"))
        assertEquals("zwei Millionen", display(2_000_000, "de"))
        assertEquals("einundzwanzig Millionen", display(21_000_000, "de"))
        assertEquals("eine Milliarde", display(1_000_000_000, "de"))
        assertEquals("zwei Milliarden", display(2_000_000_000, "de"))
        assertEquals("eine Million eintausend", display(1_001_000, "de"))
        assertEquals(
            "eine Million zweihundertvierunddreißigtausendfünfhundertsiebenundsechzig",
            display(1_234_567, "de"),
        )
    }

    @Test
    fun swahiliMillionsAndBillions() {
        assertEquals("milioni moja", display(1_000_000, "sw"))
        assertEquals("milioni mbili", display(2_000_000, "sw"))
        assertEquals("bilioni moja", display(1_000_000_000, "sw"))
        assertEquals("milioni tano", display(5_000_000, "sw"))
        assertEquals("milioni moja na mia tano", display(1_000_500, "sw"))
    }

    @Test
    fun ukrainianMillionsAndBillions() {
        assertEquals("один мільйон", display(1_000_000, "uk"))
        assertEquals("два мільйони", display(2_000_000, "uk"))
        assertEquals("п'ять мільйонів", display(5_000_000, "uk"))
        assertEquals("двадцять один мільйон", display(21_000_000, "uk"))
        assertEquals("один мільярд", display(1_000_000_000, "uk"))
        assertEquals("три мільярди", display(3_000_000_000L, "uk"))
    }

    /**
     * Every value 0…9_999_999_999 sampled sparsely stays well-formed
     * (non-empty, no digit fallback for in-range values).
     */
    @Test
    fun largeGeneratorsNeverFallBackToDigits() {
        for (lang in Trainer.languages) {
            for (n in listOf(1_000_000L, 9_999_999L, 10_000_000L, 123_456_789L,
                             1_000_000_000L, 9_999_999_999L)) {
                val word = display(n, lang)
                assertTrue(word != n.toString(), "n=$n $lang fell back to digits")
                assertTrue(word.isNotEmpty())
            }
        }
    }
}

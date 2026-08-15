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

    /** The scale words are nouns, so a numeral counts them and they take the plural -j. */
    @Test
    fun esperantoMillionsAndBillions() {
        assertEquals("unu miliono", display(1_000_000, "eo"))
        assertEquals("du milionoj", display(2_000_000, "eo"))
        assertEquals("kvin milionoj", display(5_000_000, "eo"))
        assertEquals("dudek unu milionoj", display(21_000_000, "eo"))
        assertEquals("unu miliardo", display(1_000_000_000, "eo"))
        assertEquals("tri miliardoj", display(3_000_000_000L, "eo"))
        assertEquals("unu miliono mil", display(1_001_000, "eo"))
        assertEquals(
            "unu miliono ducent tridek kvar mil kvincent sesdek sep",
            display(1_234_567, "eo"),
        )
    }

    @Test
    fun frenchMillionsAndBillions() {
        assertEquals("un million", display(1_000_000, "fr"))
        assertEquals("deux millions", display(2_000_000, "fr"))
        assertEquals("vingt et un millions", display(21_000_000, "fr"))
        // A multiplied "vingt"/"cent" keeps its -s before a scale NOUN and loses it
        // before another numeral — "quatre-vingts millions" against "quatre-vingt mille".
        assertEquals("quatre-vingts millions", display(80_000_000, "fr"))
        assertEquals("quatre-vingt mille", display(80_000, "fr"))
        assertEquals("un milliard", display(1_000_000_000, "fr"))
        assertEquals("trois milliards", display(3_000_000_000L, "fr"))
        assertEquals(
            "un million deux cent trente-quatre mille cinq cent soixante-sept",
            display(1_234_567, "fr"),
        )
    }

    @Test
    fun italianMillionsAndBillions() {
        assertEquals("un milione", display(1_000_000, "it"))
        assertEquals("due milioni", display(2_000_000, "it"))
        assertEquals("cinque milioni", display(5_000_000, "it"))
        assertEquals("ventuno milioni", display(21_000_000, "it"))
        // miliardo IS 10^9: Italian is short-scale up to the drill's ceiling.
        assertEquals("un miliardo", display(1_000_000_000, "it"))
        assertEquals("tre miliardi", display(3_000_000_000L, "it"))
        assertEquals(
            "un milione duecentotrentaquattromilacinquecentosessantasette",
            display(1_234_567, "it"),
        )
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

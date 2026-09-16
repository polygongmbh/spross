package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumbersHintsTests {

    @Test
    fun placeValueHintsCoverEveryDigitLength() {
        // 1 digit has no place word; 2…10 all do, for every authored language.
        for (lang in Numbers.languages) {
            assertNull(Numbers.placeValueHint(1, lang))
            for (digits in 2..Numbers.maxLevel(TrainerKind.Numbers)) {
                assertNotNull(Numbers.placeValueHint(digits, lang), "$lang missing place hint for $digits digits")
            }
            assertNull(Numbers.placeValueHint(11, lang))
        }
    }

    @Test
    fun placeValueHintsAreTheExpectedWords() {
        assertEquals("hundert", Numbers.placeValueHint(3, "de"))
        assertEquals("elfu", Numbers.placeValueHint(4, "sw"))
        assertEquals("milioni", Numbers.placeValueHint(7, "sw"))
        assertEquals("Milliarde", Numbers.placeValueHint(10, "de"))
        assertEquals("thousand", Numbers.placeValueHint(4, "en"))
        assertEquals("billion", Numbers.placeValueHint(10, "en"))
        // Spanish counts 10^9 as "mil millones" — no short-scale billion.
        assertEquals("mil millones", Numbers.placeValueHint(10, "es"))
        // Esperanto's scale words are nouns, so the place hint is the counted plural.
        assertEquals("cent", Numbers.placeValueHint(3, "eo"))
        assertEquals("dek milionoj", Numbers.placeValueHint(8, "eo"))
        assertEquals("miliardo", Numbers.placeValueHint(10, "eo"))
        // French has the milliard, so 10^9 is one word again.
        assertEquals("milliard", Numbers.placeValueHint(10, "fr"))
        // Italian does have one, and calls it miliardo.
        assertEquals("miliardo", Numbers.placeValueHint(10, "it"))
        assertEquals("diecimila", Numbers.placeValueHint(5, "it"))
    }

    @Test
    fun unauthoredLanguagesHaveNoHintsAndNoTrainer() {
        assertNull(Numbers.placeValueHint(3, "pt"))
        assertTrue(!Numbers.supports("pt"))
        assertEquals(listOf("de", "en", "es", "sw", "uk", "eo", "fr", "it"), Numbers.languages)
    }
}

package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainerHintsTests {

    @Test
    fun placeValueHintsCoverEveryDigitLength() {
        // 1 digit has no place word; 2…10 all do, for every authored language.
        for (lang in Trainer.languages) {
            assertNull(Trainer.placeValueHint(1, lang))
            for (digits in 2..Trainer.maxLevel(TrainerKind.Numbers)) {
                assertNotNull(Trainer.placeValueHint(digits, lang), "$lang missing place hint for $digits digits")
            }
            assertNull(Trainer.placeValueHint(11, lang))
        }
    }

    @Test
    fun placeValueHintsAreTheExpectedWords() {
        assertEquals("hundert", Trainer.placeValueHint(3, "de"))
        assertEquals("elfu", Trainer.placeValueHint(4, "sw"))
        assertEquals("milioni", Trainer.placeValueHint(7, "sw"))
        assertEquals("Milliarde", Trainer.placeValueHint(10, "de"))
        assertEquals("thousand", Trainer.placeValueHint(4, "en"))
        assertEquals("billion", Trainer.placeValueHint(10, "en"))
        // Spanish counts 10^9 as "mil millones" — no short-scale billion.
        assertEquals("mil millones", Trainer.placeValueHint(10, "es"))
    }

    @Test
    fun unauthoredLanguagesHaveNoHintsAndNoTrainer() {
        assertNull(Trainer.placeValueHint(3, "fr"))
        assertTrue(!Trainer.supports("fr"))
        assertEquals(listOf("de", "en", "es", "sw", "uk"), Trainer.languages)
    }
}

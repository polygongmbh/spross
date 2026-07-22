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
    }

    @Test
    fun tensReferenceIsSwahiliOnly() {
        assertNull(Trainer.tensReference("de"))
        assertNull(Trainer.tensReference("uk"))
        val sw = assertNotNull(Trainer.tensReference("sw"))
        assertEquals(9, sw.size)
        assertEquals("10 kumi", sw.first())
        assertTrue("30 thelathini" in sw)
        assertEquals("90 tisini", sw.last())
    }

    @Test
    fun unauthoredLanguagesHaveNoHintsAndNoTrainer() {
        assertNull(Trainer.placeValueHint(3, "en"))
        assertNull(Trainer.tensReference("en"))
        assertTrue(!Trainer.supports("en"))
        assertEquals(listOf("de", "sw", "uk"), Trainer.languages)
    }
}

package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.trainer.DrillModifier
import net.spross.kern.trainer.DrillUnlocks
import net.spross.kern.trainer.NumbersExercise

/**
 * What a locked row SAYS it costs. The ladder itself is kern's and is tested there; what is
 * asserted here is that the price is read off that table rather than authored beside it,
 * and that the numbers Sprosse is worded as the length it is.
 */
class UnlockPriceTest {

    private val chrome = Chrome.forSource("en")

    @Test
    fun theNumbersSprosseIsPricedAsDigitsAndTheOthersNameThemselves() {
        val clockCost = DrillUnlocks.requirements(NumbersExercise.Clock)
        val digits = clockCost.getValue(NumbersExercise.Counting)
        val clock = chrome.unlockPrice(clockCost)
        assertTrue(clock.contains(countLine(chrome.numbersSprosseOne, chrome.numbersSprosse, digits)), clock)
        assertFalse(clock.contains(chrome.badge(NumbersExercise.Counting)), clock)

        val phrasesCost = DrillUnlocks.requirements(NumbersExercise.Phrases)
        val (exercise, sprosse) = phrasesCost.entries.single { it.key != NumbersExercise.Counting }
        val phrases = chrome.unlockPrice(phrasesCost)
        assertTrue(phrases.contains("${chrome.badge(exercise)} ${chrome.trainerSprosse.format(sprosse)}"), phrases)
    }

    @Test
    fun aModifierPricesWhateverItsTableAsks() {
        val cost = DrillUnlocks.requirements(DrillModifier.Mix)
        val mix = chrome.unlockPrice(cost)
        for ((exercise, sprosse) in cost) {
            assertTrue(mix.contains(chrome.badge(exercise)), mix)
            assertTrue(mix.contains(chrome.trainerSprosse.format(sprosse)), mix)
        }
    }

    /**
     * On a map built by hand: no Sprosse in kern's table costs two exercises at once today,
     * so reading one out of it would assert nothing about naming both.
     */
    @Test
    fun aPriceOfTwoExercisesNamesBoth() {
        val both = chrome.unlockPrice(mapOf(NumbersExercise.Counting to 10, NumbersExercise.Forms to 5))
        assertTrue(both.contains(countLine(chrome.numbersSprosseOne, chrome.numbersSprosse, 10)), both)
        assertTrue(both.contains("${chrome.badge(NumbersExercise.Forms)} ${chrome.trainerSprosse.format(5)}"), both)
    }

    /** Nothing to buy, nothing to say — the prefix stands alone. */
    @Test
    fun anAlwaysOpenRowPricesNothing() {
        assertEquals(chrome.numbersUnlock, chrome.unlockPrice(emptyMap()))
    }
}

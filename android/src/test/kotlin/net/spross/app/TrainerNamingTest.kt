package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.trainer.DrillModifier
import net.spross.kern.trainer.DrillUnlocks
import net.spross.kern.trainer.DrillVariant

/**
 * What a locked row SAYS it costs. The ladder itself is kern's and is tested there; what is
 * asserted here is that the price is read off that table rather than authored beside it,
 * and that the numbers rung is worded as the length it is.
 */
class TrainerNamingTest {

    @Test
    fun theNumbersRungIsPricedAsDigitsAndTheOthersNameThemselves() {
        val clock = ChromeDe.unlockPrice(DrillUnlocks.requirements(DrillVariant.Clock))
        // Clock is bought with the numbers ladder, which counts digits and wears 🔢 itself.
        assertEquals("Freischalten: 🔢 4 Stellen", clock)

        val phrases = ChromeDe.unlockPrice(DrillUnlocks.requirements(DrillVariant.Phrases))
        assertTrue(phrases.startsWith("Freischalten: 🕐 Uhrzeit Sprosse "), phrases)
    }

    @Test
    fun aModifierPricesWhateverItsTableAsks() {
        val mix = ChromeEn.unlockPrice(DrillUnlocks.requirements(DrillModifier.Mix))
        assertEquals("Unlocks at: ➗ Forms Rung 5", mix)
    }

    /**
     * The join, on a map built by hand: no rung in kern's table costs two variants at
     * once today, so reading one out of it would assert nothing about the separator —
     * and the day one does, this is the line that already covers it.
     */
    @Test
    fun aPriceOfTwoVariantsNamesBothAroundOneSeparator() {
        val both = ChromeEn.unlockPrice(
            mapOf(DrillVariant.Numbers to 10, DrillVariant.Forms to 5),
        )
        assertEquals("Unlocks at: 🔢 10 digits · ➗ Forms Rung 5", both)
    }

    /** Nothing to buy, nothing to say — the prefix stands alone rather than trailing a gap. */
    @Test
    fun anAlwaysOpenRowPricesNothing() {
        assertEquals("Unlocks at:", ChromeEn.unlockPrice(emptyMap()))
    }
}

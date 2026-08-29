package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AnswerNormalizer

/**
 * The index resolves a reading to the value it names — pinned on the shapes the
 * collision check depends on: a bare twin, a welded compound, a whole-answer
 * form twin, and the ordinal/cardinal identity split.
 */
class NumberReadingIndexTests {

    @Test
    fun spanishCardinalResolvesToItsValue() {
        val index = index("es")
        assertEquals(setOf<NumberIdentity>(NumberIdentity.Cardinal(70)), lookup(index, "setenta"))
        assertEquals("70", lookup(index, "setenta").single().display)
    }

    @Test
    fun frenchWeldedCompoundResolvesWhole() {
        // The hyphen is deleted by the comparison pipeline, so the welded spelling
        // and the hyphenated one are one key.
        assertEquals(
            setOf<NumberIdentity>(NumberIdentity.Cardinal(70)),
            lookup(index("fr"), "soixante-dix"),
        )
    }

    @Test
    fun ordinalAndCardinalAreDistinctIdentities() {
        val index = index("es")
        assertEquals(
            setOf<NumberIdentity>(NumberIdentity.Cardinal(4)),
            lookup(index, "cuatro"),
        )
        val cuarto = lookup(index, "cuarto")
        assertTrue(cuarto.isNotEmpty(), "cuarto should be indexed as a form reading")
        assertTrue(cuarto.none { it is NumberIdentity.Cardinal }, "cuarto is no cardinal: $cuarto")
    }

    @Test
    fun spanishFractionAndOrdinalTwinsSplitApart() {
        val index = index("es")
        val fraction = lookup(index, "un décimo")
        val ordinal = lookup(index, "undécimo")
        assertTrue(fraction.isNotEmpty() && ordinal.isNotEmpty())
        assertTrue((fraction intersect ordinal).isEmpty(), "1/10 and 11th must be disjoint")
    }

    @Test
    fun esperantoWeldedOrdinalIsAWholeReading() {
        val values = lookup(index("eo"), "sesdek-sesa")
        assertEquals(setOf(NumberValue.Ordinal(66)), values.map { (it as NumberIdentity.Form).value }.toSet())
    }

    @Test
    fun aWordThatNamesNoValueResolvesEmpty() {
        assertEquals(emptySet(), lookup(index("es"), "menos"))
        assertEquals(emptySet(), lookup(index("es"), "setnta"))
    }

    private fun lookup(index: NumberReadingIndex, raw: String): Set<NumberIdentity> =
        index.values(index.normalizer.comparisonForms(raw, verbLeniency = false).first())

    private fun index(language: String): NumberReadingIndex {
        val info = Fixture.catalog().languages[language]
            ?: LanguageInfo(language, language, language, "🏳️")
        return NumberReadingIndex(language, AnswerNormalizer.drill(info))
    }
}

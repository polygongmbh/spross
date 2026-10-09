package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which bands of the counting table stand in two columns. */
class ReferenceColumnsTest {

    /** What a 360-point phone leaves inside the band panel: a pair has to hold there or nowhere. */
    private val phone = 296.0

    private fun band(language: String, key: String) =
        Numbers.reference(language).first { it.key == key }.entries

    @Test
    fun theBandsReadAtAGlanceStandInTwoColumns() {
        assertEquals(2, ReferenceColumns.count(band("de", "base"), largeText = false, width = phone))
        assertEquals(2, ReferenceColumns.count(band("de", "tens"), largeText = false, width = phone))
    }

    /**
     * Ukrainian pairs nowhere:
     * "п'ятнадцять" and "вісімдесят" outrun a half-width column on a 360-point phone,
     * and a paired band that wraps mid-word is worse than a single-column one.
     */
    @Test
    fun ukrainianKeepsTheWholeWidthInEveryBand() {
        val paired = Numbers.reference("uk")
            .filter { ReferenceColumns.count(it.entries, largeText = false, width = phone) == 2 }
            .map { it.key }

        assertEquals(emptyList(), paired, "Cyrillic readings need the whole width")
    }

    @Test
    fun largeTextOrANarrowPanelKeepsOneColumn() {
        assertEquals(1, ReferenceColumns.count(band("de", "base"), largeText = true, width = phone))
        assertEquals(1, ReferenceColumns.count(band("de", "base"), largeText = false, width = 240.0))
    }

    /** A letter written with a combining accent is one letter, as a reader counts it. */
    @Test
    fun aCombiningAccentAddsNoLetter() {
        val composed = List(6) { ReferenceEntry("1", "dieciséis") }
        val decomposed = List(6) { ReferenceEntry("1", "dieciséis".replace("é", "e\u0301")) }
        assertEquals(
            ReferenceColumns.count(composed, largeText = false),
            ReferenceColumns.count(decomposed, largeText = false),
        )
    }
}

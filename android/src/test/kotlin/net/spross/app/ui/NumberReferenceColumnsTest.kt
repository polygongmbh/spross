package net.spross.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.trainer.Numbers

/**
 * Which bands of the counting table stand in two columns.
 * The rows themselves are kern's — `:kern:jvmTest` pins the readings —
 * and what is checked here is the cut this platform makes on them.
 */
class NumberReferenceColumnsTest {

    /** What a 360 dp phone leaves inside the band panel: a pair has to hold there or nowhere. */
    private val PHONE = 296.dp

    private fun band(key: String) =
        Numbers.reference("de").first { it.key == key }.entries

    @Test
    fun theBandsReadAtAGlanceStandInTwoColumns() {
        assertEquals(2, columnCount(band("base"), fontScale = 1f, width = PHONE))
        assertEquals(2, columnCount(band("tens"), fontScale = 1f, width = PHONE))
    }

    /**
     * Ukrainian pairs nowhere:
     * "п'ятнадцять" and "вісімдесят" outrun a half-width column on a 360 dp phone,
     * and a paired band that wraps mid-word is worse than a single-column one.
     */
    @Test
    fun ukrainianKeepsTheWholeWidthInEveryBand() {
        val paired = Numbers.reference("uk")
            .filter { columnCount(it.entries, fontScale = 1f, width = PHONE) == 2 }
            .map { it.key }

        assertEquals(emptyList<String>(), paired, "Cyrillic readings need the whole width")
    }
}

package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.app.Chrome
import net.spross.kern.box.GrowthClaim
import net.spross.kern.box.GrowthHeadline

/** Kern's growth claim, in the table's words: which lines each claim may read. */
class GrowthLineTest {
    private val chrome = Chrome.forSource("en")

    @Test
    fun aRoundThatAddedNothingNeverSaysTheWordsGrew() {
        val lines = (0..20).map { growthLine(chrome, GrowthHeadline(GrowthClaim.Held, it)) }.toSet()
        assertTrue(chrome.growthGrown.first() !in lines)
        assertEquals(chrome.growthGrown.drop(1).toSet(), lines)
    }
}

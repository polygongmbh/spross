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

    @Test
    fun everyClaimReadsFromItsOwnLines() {
        assertEquals(chrome.sessionDoneGrowthGrew, growthLine(chrome, GrowthHeadline(GrowthClaim.Unclaimed, 7)))
        assertEquals(chrome.sessionDoneGrowthOpened, growthLine(chrome, GrowthHeadline(GrowthClaim.Opened, 7)))
        assertTrue(growthLine(chrome, GrowthHeadline(GrowthClaim.Settled, 7)) in chrome.growthBlooming)
        assertTrue(growthLine(chrome, GrowthHeadline(GrowthClaim.Met, 7)) in chrome.growthSown)
    }
}

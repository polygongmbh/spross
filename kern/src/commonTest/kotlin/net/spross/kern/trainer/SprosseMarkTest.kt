package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a Sprosse circle says of the record. */
class SprosseMarkTest {

    /** A Sprosse can be answered out on a run that opened above the best filed — cleared still wins. */
    @Test
    fun aClearedSprosseReadsClearedEvenPastTheBestReached() {
        assertEquals(SprosseMark.Cleared, SprosseMark.of(sprosse = 5, cleared = setOf(5), bestSprosse = 2))
    }

    @Test
    fun aSprosseStoodOnButNotAnsweredOutReadsReached() {
        assertEquals(SprosseMark.Reached, SprosseMark.of(sprosse = 2, cleared = setOf(5), bestSprosse = 2))
        assertEquals(SprosseMark.Untouched, SprosseMark.of(sprosse = 3, cleared = setOf(5), bestSprosse = 2))
    }
}

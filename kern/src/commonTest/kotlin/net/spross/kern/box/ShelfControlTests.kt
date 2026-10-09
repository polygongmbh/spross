package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfControlTests {

    private val settled = AreaStatistics(name = "kitchen", total = 4, stages = StageCounts(settled = 4))

    @Test
    fun aShelfWithAnythingLeftOffersToQueueIt() {
        assertEquals(ShelfControl.Queue, ShelfControl.of(ShelfCounts(queueable = 2, queued = 9), settled))
    }

    @Test
    fun theBulkUnqueueOnlyStandsForMoreThanACoupleOfQueuedWords() {
        assertEquals(ShelfControl.Unqueue, ShelfControl.of(ShelfCounts(queueable = 0, queued = 5), settled))
        assertEquals(ShelfControl.AllQueued, ShelfControl.of(ShelfCounts(queueable = 0, queued = 1), settled))
    }

    @Test
    fun onlyAShelfWithNothingQueuedAndEverythingSettledWearsTheSettledMark() {
        assertEquals(ShelfControl.Settled, ShelfControl.of(null, settled))
        val growing = settled.copy(stages = StageCounts(growing = 1, settled = 3))
        assertEquals(ShelfControl.AllQueued, ShelfControl.of(null, growing))
    }
}

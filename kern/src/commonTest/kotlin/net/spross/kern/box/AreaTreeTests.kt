package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardPhase

/** One tree per area: which word stands in which tier, and what a round moved. */
class AreaTreeTests {
    private val now = Box.day1
    private val future = Box.plusDays(now, 5.0)

    private fun trees(state: BoxState) = areaTrees(state, BoxEngine.growth(state, now, Box.TZ))

    private fun tree(
        met: Int = 0, growing: Int = 0, matured: Int = 0, longHeld: Int = 0, queued: Int = 0,
    ) = AreaTree("a", met, growing, matured, longHeld, queued, 0, 0.0, false, emptyList())

    @Test
    fun everyMetWordStandsInExactlyOneTierAndOnlyMetWordsDo() {
        var state = Box.state((1..7).map { Box.word(it) } + Box.word(8, area = "other"))
        state = BoxEngine.enqueue(state, listOf("w01"))
        state = Box.inject(state, Box.sched("w02", phase = CardPhase.Learning, stability = 0.5, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w03", stability = 9.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w04", stability = MATURED_STABILITY, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w05", stability = FRUIT_STABILITY, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w06", phase = CardPhase.Relearning, stability = 4.0, dueMillis = future, lastReviewMillis = now))

        val area = trees(state).getValue("area1")
        assertEquals(listOf(1, 1, 1, 1, 1, 1), listOf(area.arriving, area.growing, area.matured, area.longHeld, area.queued, area.lapsed))
        assertEquals(4, area.met)
        assertEquals(area.reaches.sortedDescending(), area.reaches, "most-grown first")
        assertTrue(area.answeredToday)
        assertTrue(trees(state).getValue("other").isBare, "an area never opened")
    }

    @Test
    fun aRoundThatAddsWordsChangesOnlyTheNewRanks() {
        val move = TreeTransition(tree(met = 2, growing = 3), tree(met = 4, growing = 3))
        assertEquals(5, move.settledCount)
        assertEquals(listOf(5, 6), move.changedRanks)
    }

    @Test
    fun aWordMaturingChangesInPlaceAndTheRanksAroundItStandStill() {
        val move = TreeTransition(tree(growing = 3, matured = 1), tree(growing = 2, matured = 2))
        assertTrue(1 in move.changedRanks)
        assertTrue(0 !in move.changedRanks && 2 !in move.changedRanks)
    }

    @Test
    fun aLapseTakesNothingOffInFrontOfTheLearner() {
        val move = TreeTransition(tree(growing = 4), tree(growing = 3))
        assertEquals(emptyList(), move.changedRanks)
    }

    @Test
    fun theRoundsAreaIsTheOneItAnsweredMostTiesToTheFirstInOrder() {
        val before = Box.state(listOf(Box.word(1, area = "x"), Box.word(2, area = "y"), Box.word(3, area = "y")))
        var after = before
        for (id in listOf("w01", "w02", "w03")) {
            after = Box.inject(after, Box.sched(id, phase = CardPhase.Learning, stability = 0.5, dueMillis = future, lastReviewMillis = now))
        }
        fun grown(ids: List<String>, order: List<String>) = grownArea(before, after, ids, order, now, Box.TZ)

        val most = grown(listOf("w01", "w02", "w03"), listOf("x", "y"))
        assertEquals("y", most?.after?.area)
        assertEquals(2, most?.after?.met)
        val tie = grown(listOf("w02", "w01"), listOf("y", "x"))
        assertEquals("y", tie?.after?.area)
        assertTrue(tie!!.before.isBare, "an area the round opened stands on bare ground before")
        assertNull(grown(emptyList(), listOf("x", "y")))
    }

    private fun claim(before: AreaTree, after: AreaTree, rest: Boolean = false) =
        growthHeadline(TreeTransition(before, after), rest, 1, 0, 3, 2)?.claim

    @Test
    fun theHeadlineClaimsOnlyWhatTheTreeGained() {
        val worked = tree(met = 2, growing = 4, matured = 1)
        assertEquals(GrowthClaim.Opened, claim(tree(), worked))
        assertEquals(GrowthClaim.Matured, claim(worked, worked.copy(matured = 2, growing = 3)))
        assertEquals(GrowthClaim.Met, claim(worked, worked.copy(arriving = 4)))
        assertEquals(GrowthClaim.Grew, claim(worked, worked.copy(arriving = 1, growing = 6)))
        assertEquals(GrowthClaim.Held, claim(worked, worked))
        assertEquals(GrowthClaim.Unclaimed, claim(worked, worked.copy(matured = 3), rest = true))
        assertNull(growthHeadline(TreeTransition(tree(), tree(queued = 0)), false, 1, 0, 0, 0))
        assertNull(growthHeadline(null, false, 1, 0, 0, 0))
    }

    @Test
    fun theHeadlinesPickIsStableWithinARoundAndMovesWithTheStreak() {
        val move = TreeTransition(tree(met = 1), tree(met = 3))
        val picks = (1..12).map { growthHeadline(move, false, 3, 1, 9, it)!!.pick }
        assertEquals(picks, (1..12).map { growthHeadline(move, false, 3, 1, 9, it)!!.pick })
        assertTrue(picks.all { it >= 0 })
        assertTrue(picks.map { it % 3 }.toSet().size > 1, "a steady habit still reads more than one line")
    }
}

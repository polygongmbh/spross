package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/** What a listening draw is willing to say twice — one ladder in stability. */
class ListeningWeightTests {

    private fun candidate(
        stability: Double,
        suspended: Boolean,
        scheduled: Boolean,
    ): ListeningCandidate = ListeningCandidate(
        card = Box.word(1),
        stability = stability,
        suspended = suspended,
        scheduled = scheduled,
    )

    /**
     * RULE: a settled word keeps the draw floor.
     * WHY: the floor is what makes this a playlist and not a filter — a word that has landed
     * is not excluded from an hour of exposure, only pushed to the end of the draw by the
     * ones that have not.
     */
    @Test
    fun aSettledWordKeepsTheDrawFloor() {
        assertEquals(1, listeningWeight(candidate(30.0, suspended = false, scheduled = true)))
    }

    /**
     * RULE: higher stability means lower priority, one point per step of the ladder.
     * WHY: the whole draw is one figure — a just-learned word leads, and every two days of
     * stability drops it a rung, so the not-quite-settled sit in the middle and the
     * consolidated ones are pushed to the end.
     */
    @Test
    fun higherStabilityMeansLowerPriority() {
        assertEquals(5, listeningWeight(candidate(0.0, suspended = false, scheduled = true)))
        assertEquals(4, listeningWeight(candidate(2.0, suspended = false, scheduled = true)))
        assertEquals(3, listeningWeight(candidate(4.0, suspended = false, scheduled = true)))
        assertEquals(2, listeningWeight(candidate(6.0, suspended = false, scheduled = true)))
        // The floor, however settled: ten days or a hundred are the same rung.
        assertEquals(1, listeningWeight(candidate(8.0, suspended = false, scheduled = true)))
        assertEquals(1, listeningWeight(candidate(40.0, suspended = false, scheduled = true)))
    }

    /**
     * RULE: a suspended word keeps the bare floor however low its stability.
     * WHY: the leech rule suspends at two lapses, so a suspended card is exactly the sort the
     * ladder would otherwise put at the top — but the box has already decided that word is
     * being pushed outward. It stays worth hearing; it is not what the hour is for.
     */
    @Test
    fun aSuspendedWordEarnsNoBoost() {
        assertEquals(1, listeningWeight(candidate(0.0, suspended = true, scheduled = true)))
    }

    /**
     * RULE: an unscheduled word takes the fixed new weight, not the floor.
     * WHY: it has no stability to ladder on — there is no history to read — so its value is
     * set, a focus tier on its own: a first hearing is the mode's cheapest breadth, and new
     * words are met alongside the ones that are not sticking.
     */
    @Test
    fun anUnseenWordTakesTheNewWeight() {
        assertEquals(LISTENING_NEW_WEIGHT, listeningWeight(candidate(0.0, suspended = false, scheduled = false)))
    }

    /**
     * RULE: new and just-learned words lead, settling words rotate in the middle, and
     * consolidated ones are pushed to the end.
     * WHY: that is the hour's whole shape — the words that have not landed are what listening
     * is for, and the ones that have are background.
     */
    @Test
    fun unsettledAndNewLeadOverConsolidated() {
        val fresh = listeningWeight(candidate(0.0, suspended = false, scheduled = true))
        val new = listeningWeight(candidate(0.0, suspended = false, scheduled = false))
        val settling = listeningWeight(candidate(4.0, suspended = false, scheduled = true))
        val consolidated = listeningWeight(candidate(20.0, suspended = false, scheduled = true))
        assertTrue(fresh >= new && new >= settling && settling > consolidated)
    }

    /**
     * RULE: the recall gap is the long one for a word already answered, the short one for an
     * unseen word.
     * WHY: it is the only beat that teaches — a held word needs room to be remembered before
     * its meaning arrives, and an unseen word has nothing to remember, so a pause there would
     * only feel like a test the learner is failing.
     */
    @Test
    fun theRecallGapIsLongForAHeldWordAndShortForAnUnseenOne() {
        assertEquals(RECALL_GAP_HELD_MS, recallGap(candidate(5.0, suspended = false, scheduled = true)))
        assertEquals(RECALL_GAP_FRESH_MS, recallGap(candidate(0.0, suspended = false, scheduled = false)))
        // Suspended is still a word the learner has answered — the gap follows the history,
        // not the box's decision about it.
        assertEquals(RECALL_GAP_HELD_MS, recallGap(candidate(9.0, suspended = true, scheduled = true)))
    }
}

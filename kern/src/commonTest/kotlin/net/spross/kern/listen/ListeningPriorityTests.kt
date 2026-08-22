package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/** Where a word stands on the listening draw — one ladder in stability. */
class ListeningPriorityTests {

    private fun candidate(
        stability: Double,
        suspended: Boolean,
        scheduled: Boolean,
        queued: Boolean = false,
    ): ListeningCandidate = ListeningCandidate(
        card = Box.word(1),
        stability = stability,
        suspended = suspended,
        scheduled = scheduled,
        queued = queued,
    )

    /**
     * RULE: a settled word keeps the draw floor.
     * WHY: the floor is what makes this a playlist and not a filter — a word that has landed
     * is not excluded from an hour of exposure, only pushed to the end of the draw by the
     * ones that have not.
     */
    @Test
    fun aSettledWordKeepsTheDrawFloor() {
        assertEquals(1, listeningPriority(candidate(30.0, suspended = false, scheduled = true)))
    }

    /**
     * RULE: higher stability means lower priority, one point per step of the ladder.
     * WHY: the whole draw is one figure — a just-learned word leads, and every two days of
     * stability drops it a rung, so the not-quite-settled sit in the middle and the
     * consolidated ones are pushed to the end.
     */
    @Test
    fun higherStabilityMeansLowerPriority() {
        assertEquals(6, listeningPriority(candidate(0.0, suspended = false, scheduled = true)))
        assertEquals(5, listeningPriority(candidate(2.0, suspended = false, scheduled = true)))
        assertEquals(4, listeningPriority(candidate(4.0, suspended = false, scheduled = true)))
        assertEquals(3, listeningPriority(candidate(6.0, suspended = false, scheduled = true)))
        assertEquals(2, listeningPriority(candidate(8.0, suspended = false, scheduled = true)))
        // The floor, however settled: ten days or a hundred are the same rung.
        assertEquals(1, listeningPriority(candidate(10.0, suspended = false, scheduled = true)))
        assertEquals(1, listeningPriority(candidate(40.0, suspended = false, scheduled = true)))
    }

    /**
     * RULE: a suspended word keeps its stability's rung less the toll — it is NOT sent to the
     * floor, and a shaky leech still comes in early.
     * WHY: the pool holds leeches precisely because they are what an hour of listening is for;
     * the leech rule takes a word out of the box's rotation and this is the surface that can
     * still reach it. Two rungs are enough that it does not lead the hour.
     */
    @Test
    fun aSuspendedWordPaysATollRatherThanTakingTheFloor() {
        val shaky = listeningPriority(candidate(0.0, suspended = true, scheduled = true))
        assertEquals(LISTENING_MAX_STABILITY_PRIORITY - LISTENING_SUSPENDED_PENALTY, shaky)
        assertTrue(shaky > 1, "a shaky leech is not at the floor")
        assertEquals(3, listeningPriority(candidate(2.0, suspended = true, scheduled = true)))
        // Nothing leads a suspended word past a word of the same stability that is not one.
        assertTrue(shaky < listeningPriority(candidate(0.0, suspended = false, scheduled = true)))
    }

    /**
     * RULE: a settled leech still bottoms out at the floor.
     * WHY: the toll is on top of the stability ladder, not instead of it — a word that both
     * sat well and was suspended has no claim on the hour, and the floor keeps it audible
     * without making it a subject.
     */
    @Test
    fun aSettledLeechStillBottomsOut() {
        assertEquals(1, listeningPriority(candidate(30.0, suspended = true, scheduled = true)))
    }

    /**
     * RULE: an unscheduled word takes the fixed new priority, not the floor.
     * WHY: it has no stability to ladder on — there is no history to read — so its value is
     * set, a focus tier on its own: a first hearing is the mode's cheapest breadth, and new
     * words are met alongside the ones that are not sticking.
     */
    @Test
    fun anUnseenWordTakesTheNewPriority() {
        assertEquals(
            LISTENING_NEW_PRIORITY,
            listeningPriority(candidate(0.0, suspended = false, scheduled = false)),
        )
    }

    /**
     * RULE: a packed word outranks a plain unseen one, and is outranked by a very shaky one.
     * WHY: packing is the learner saying *these words next*, which every other surface honors,
     * so the ear must honor it too — but one rung is the whole of the ask. A word that is
     * actively falling out of the box still leads, because that is what the hour is for.
     */
    @Test
    fun aPackedWordLeadsTheOtherUnseenOnesAndTrailsAShakyOne() {
        val packed = listeningPriority(candidate(0.0, suspended = false, scheduled = false, queued = true))
        val unseen = listeningPriority(candidate(0.0, suspended = false, scheduled = false))
        val shaky = listeningPriority(candidate(0.0, suspended = false, scheduled = true))

        assertEquals(LISTENING_QUEUED_PRIORITY, packed)
        assertTrue(shaky > packed && packed > unseen)
    }

    /**
     * RULE: new and just-learned words lead, settling words rotate in the middle, and
     * consolidated ones are pushed to the end.
     * WHY: that is the hour's whole shape — the words that have not landed are what listening
     * is for, and the ones that have are background.
     */
    @Test
    fun unsettledAndNewLeadOverConsolidated() {
        val fresh = listeningPriority(candidate(0.0, suspended = false, scheduled = true))
        val new = listeningPriority(candidate(0.0, suspended = false, scheduled = false))
        val settling = listeningPriority(candidate(4.0, suspended = false, scheduled = true))
        val consolidated = listeningPriority(candidate(20.0, suspended = false, scheduled = true))
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

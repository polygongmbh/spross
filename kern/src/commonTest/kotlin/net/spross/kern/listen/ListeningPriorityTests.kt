package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/** Where a held word stands on the listening ladder — two Sprossen off the box's own bar. */
class ListeningPriorityTests {

    private fun candidate(
        growing: Boolean,
        suspended: Boolean,
        scheduled: Boolean,
    ): ListeningCandidate = ListeningCandidate(
        card = Box.word(1),
        growing = growing,
        suspended = suspended,
        scheduled = scheduled,
        queued = false,
        packedRank = 0,
    )

    /**
     * RULE: a word short of the growing bar leads, and one past it takes the floor.
     * WHY: that is the hour's whole shape — the words that have not landed are what listening
     * is for, and the ones that have are background. The bar is the box's own, not a second
     * one invented for the ear.
     */
    @Test
    fun aShakyWordLeadsAGrowingOne() {
        val shaky = listeningPriority(growing = false, suspended = false)
        val growing = listeningPriority(growing = true, suspended = false)
        assertEquals(LISTENING_SHAKY_PRIORITY, shaky)
        assertEquals(LISTENING_GROWING_PRIORITY, growing)
        assertTrue(shaky > growing)
    }

    /**
     * RULE: a suspended word takes the floor whatever its bar — it comes in, it does not lead.
     * WHY: the pool holds leeches precisely because they are what an hour of listening is for;
     * suspension takes a word out of the box's rotation and this is the surface that can
     * still reach it. But a word the box gave up on does not lead the ones it is still working on.
     */
    @Test
    fun aSuspendedWordComesInButDoesNotLead() {
        assertEquals(LISTENING_GROWING_PRIORITY, listeningPriority(growing = false, suspended = true))
        assertEquals(LISTENING_GROWING_PRIORITY, listeningPriority(growing = true, suspended = true))
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
        assertEquals(RECALL_GAP_HELD_MS, recallGap(candidate(growing = false, suspended = false, scheduled = true)))
        assertEquals(RECALL_GAP_FRESH_MS, recallGap(candidate(growing = false, suspended = false, scheduled = false)))
        // Suspended is still a word the learner has answered — the gap follows the history,
        // not the box's decision about it.
        assertEquals(RECALL_GAP_HELD_MS, recallGap(candidate(growing = true, suspended = true, scheduled = true)))
    }
}

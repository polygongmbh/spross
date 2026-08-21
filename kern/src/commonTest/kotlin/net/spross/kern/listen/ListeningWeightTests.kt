package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.box.Box

/** What a listening draw is willing to say twice, and what it holds to the floor. */
class ListeningWeightTests {

    private fun candidate(
        difficulty: Double,
        lapses: Int,
        suspended: Boolean,
        scheduled: Boolean,
    ): ListeningCandidate = ListeningCandidate(
        card = Box.word(1),
        difficulty = difficulty,
        lapses = lapses,
        suspended = suspended,
        scheduled = scheduled,
    )

    /**
     * RULE: a clean, easy, held word still weighs 1.
     * WHY: the floor is what makes this a playlist and not a filter — a word the learner has
     * mastered is not excluded from an hour of exposure, only out-drawn by shakier ones.
     */
    @Test
    fun everyWordKeepsTheDrawFloor() {
        assertEquals(1, listeningWeight(candidate(3.0, 0, suspended = false, scheduled = true)))
    }

    /**
     * RULE: lapses and above-midpoint difficulty add to the floor, each capped.
     * WHY: the hour is for the words that are not sticking — but a single leech that could
     * outweigh a dozen clean words would take the whole hour over.
     */
    @Test
    fun lapsesAndDifficultyAddUpToTheirCaps() {
        assertEquals(3, listeningWeight(candidate(3.0, 2, suspended = false, scheduled = true)))
        assertEquals(3, listeningWeight(candidate(9.0, 0, suspended = false, scheduled = true)))
        // Both maxed: 1 + LAPSE_CAP 3 + DIFFICULTY_CAP 2, whatever the raw figures say.
        assertEquals(6, listeningWeight(candidate(10.0, 99, suspended = false, scheduled = true)))
    }

    /**
     * RULE: a suspended word keeps the bare floor however bad its figures are.
     * WHY: the leech rule suspends at two lapses, so a suspended card's lapses and difficulty
     * are exactly the ones that would win every draw — but the box has already decided that
     * word is being pushed outward. It stays worth hearing; it is not what the hour is for.
     */
    @Test
    fun aSuspendedWordEarnsNoBoost() {
        assertEquals(1, listeningWeight(candidate(10.0, 5, suspended = true, scheduled = true)))
    }

    /**
     * RULE: an unscheduled word keeps the bare floor too.
     * WHY: it has no history to weigh, and its 0.0 difficulty is an absence rather than a
     * measurement — read as a figure it would say "very easy" about a word never once met.
     */
    @Test
    fun anUnseenWordEarnsNoBoost() {
        assertEquals(1, listeningWeight(candidate(0.0, 0, suspended = false, scheduled = false)))
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
        assertEquals(RECALL_GAP_HELD_MS, recallGap(candidate(5.0, 0, suspended = false, scheduled = true)))
        assertEquals(RECALL_GAP_FRESH_MS, recallGap(candidate(0.0, 0, suspended = false, scheduled = false)))
        // Suspended is still a word the learner has answered — the gap follows the history,
        // not the box's decision about it.
        assertEquals(RECALL_GAP_HELD_MS, recallGap(candidate(9.0, 3, suspended = true, scheduled = true)))
    }
}

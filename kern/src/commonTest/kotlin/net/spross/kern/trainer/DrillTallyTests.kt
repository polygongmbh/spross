package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.session.AnswerOutcome

/**
 * The counter a run carries, pinned once where the rule lives — the three run states each
 * expose the same one-line [DrillTally.of] delegate, so the mapping is not re-derived per run.
 */
class DrillTallyTests {

    @Test
    fun cleanWinsJudgedEitherWayAndAlmostCountsInNeitherHalf() {
        assertEquals(
            DrillTally(clean = 1, judged = 2),
            DrillTally.of(listOf(AnswerOutcome.Right, AnswerOutcome.Almost, AnswerOutcome.Wrong)),
        )
        assertEquals(
            DrillTally(clean = 0, judged = 0),
            DrillTally.of(listOf(AnswerOutcome.Almost, AnswerOutcome.Almost)),
        )
    }
}

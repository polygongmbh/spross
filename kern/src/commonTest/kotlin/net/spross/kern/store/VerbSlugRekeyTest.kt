package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.box.ReportedIssue

/** rekeyingPrefixedVerbs: the bare-verb-slug migration (delete at 7.0+). */
class VerbSlugRekeyTest {
    private val now = Box.day1

    private fun stateWith(vararg cardIds: String): BoxState =
        Box.state(cardIds.mapIndexed { i, id -> Box.word(i + 1).copy(id = id) })

    private fun issue(cardId: String) =
        ReportedIssue(cardId = cardId, comment = "wrong", learnerInput = null, reportedAt = Box.instant(now))

    @Test
    fun movesAnOrphanedBareSlugOntoItsPrefixedCard() {
        var state = stateWith("to-study", "w02")
        state = Box.inject(state, Box.sched("study", dueMillis = now, lastReviewMillis = now))
        state = state.copy(enqueued = listOf("study", "w02"), reportedIssues = mapOf("study" to issue("study")))

        val moved = state.rekeyingPrefixedVerbs()

        assertNull(moved.scheduling["study"])
        assertEquals("to-study", moved.scheduling.getValue("to-study").cardId)
        assertEquals(listOf("to-study", "w02"), moved.enqueued)
        assertEquals("to-study", moved.reportedIssues.getValue("to-study").cardId)
    }

    @Test
    fun leavesABareSlugThatIsStillACardAlone() {
        var state = stateWith("help", "to-help")
        state = Box.inject(state, Box.sched("help", dueMillis = now, lastReviewMillis = now))

        assertEquals(state, state.rekeyingPrefixedVerbs())
    }

    @Test
    fun leavesAnOrphanWithoutAPrefixedCardOrphaned() {
        var state = stateWith("w01")
        state = Box.inject(state, Box.sched("retired", dueMillis = now, lastReviewMillis = now))

        assertEquals(state, state.rekeyingPrefixedVerbs())
    }

    @Test
    fun keepsTheProgressAlreadyStoredUnderThePrefixedSlug() {
        var state = stateWith("to-study")
        state = Box.inject(state, Box.sched("study", stability = 3.0, dueMillis = now, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("to-study", stability = 42.0, dueMillis = now, lastReviewMillis = now))

        val moved = state.rekeyingPrefixedVerbs()

        assertEquals(42.0, moved.scheduling.getValue("to-study").memory?.stability)
        assertTrue("study" in moved.scheduling)
    }

    @Test
    fun returnsABoxWithNothingToMoveUnchanged() {
        var state = stateWith("w01", "w02")
        state = Box.inject(state, Box.sched("w01", dueMillis = now, lastReviewMillis = now))

        assertEquals(state, state.rekeyingPrefixedVerbs())
    }
}

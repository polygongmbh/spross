package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase

/**
 * `BoxEngine.consolidatedCardIds` — the drill's word supply: what the shelf holds,
 * what falls off it, and the order it is handed over in.
 */
class ConsolidatedCardsTests {
    private val now = Box.day1

    /** A consolidated schedule at the default `consolidatedStability` of 6.0 days. */
    private fun consolidated(cardId: String, stability: Double = 7.0, suspended: Boolean = false) =
        Box.sched(
            cardId,
            stability = stability,
            dueMillis = Box.plusDays(now, 3.0),
            lastReviewMillis = now,
            suspended = suspended,
        )

    private fun named(id: String, seedIndex: Int): Card =
        Box.word(seedIndex).copy(id = id, seedIndex = seedIndex)

    @Test
    fun anEmptyBoxHandsOverNothing() {
        assertEquals(emptyList(), BoxEngine.consolidatedCardIds(Box.state(emptyList())))
        // Cards without a schedule have not been seen at all, let alone consolidated.
        assertEquals(emptyList(), BoxEngine.consolidatedCardIds(Box.state((1..3).map { Box.word(it) })))
    }

    @Test
    fun onlyReviewPhaseAtOrAboveTheConsolidatedBar() {
        var state = Box.state((1..5).map { Box.word(it) })
        state = Box.inject(state, consolidated("w01", stability = 6.0)) // exactly the bar
        state = Box.inject(state, consolidated("w02", stability = 5.9)) // just under it
        state = Box.inject(
            state,
            // Stable enough, but still stepping through Learning.
            Box.sched("w03", phase = CardPhase.Learning, stability = 9.0, dueMillis = now, lastReviewMillis = now),
        )
        state = Box.inject(
            state,
            // Lapsed: back in Relearning, so it stops counting as known — the point of the phase.
            Box.sched(
                "w04",
                phase = CardPhase.Relearning,
                stability = 9.0,
                dueMillis = now,
                lastReviewMillis = now,
                lapses = 1,
            ),
        )
        state = Box.inject(state, consolidated("w05", stability = 40.0))

        assertEquals(listOf("w01", "w05"), BoxEngine.consolidatedCardIds(state))
    }

    @Test
    fun suspendedAndNonJoiningSchedulesAreNeverOffered() {
        var state = Box.state((1..2).map { Box.word(it) })
        state = Box.inject(state, consolidated("w01"))
        state = Box.inject(state, consolidated("w02", suspended = true))
        // Inert schedule from another join — kept in the map, invisible to inventory reads.
        state = Box.inject(state, consolidated("w99"))

        assertEquals(listOf("w01"), BoxEngine.consolidatedCardIds(state))
        // Reviving the suspended card puts it straight back on the shelf.
        val revived = BoxEngine.setSuspended(state, "w02", suspended = false)
        assertEquals(listOf("w01", "w02"), BoxEngine.consolidatedCardIds(revived))
    }

    @Test
    fun seedOrderRatherThanIdOrScheduleOrder() {
        var state = Box.state(
            listOf(named("zulu", 1), named("alpha", 2), named("mike", 3), named("bravo", 3)),
        )
        for (id in listOf("mike", "bravo", "alpha", "zulu")) state = Box.inject(state, consolidated(id))

        // Catalog position decides; the id only breaks a seedIndex tie, so the order is total.
        assertEquals(listOf("zulu", "alpha", "bravo", "mike"), BoxEngine.consolidatedCardIds(state))
    }
}

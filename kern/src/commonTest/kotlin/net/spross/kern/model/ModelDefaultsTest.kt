package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ModelDefaultsTest {
    @Test
    fun boxConfigProductDefaults() {
        // The factory IS the defaults — the platforms call it because Kotlin default
        // arguments do not cross the ObjC boundary, not to state a second calibration.
        val config = BoxConfig.product()
        assertEquals(BoxConfig(), config)
        assertEquals(25, config.sessionCap)
        assertEquals(0.8, config.desiredRetention)
        assertEquals(365, config.maximumIntervalDays)
        assertEquals(6.0, config.consolidatedStability)
        // FSRS-6 reference step defaults — no in-session lapse retry.
        assertEquals(listOf(120L), config.learningStepsSeconds)
        assertEquals(listOf(600L), config.relearningStepsSeconds)
    }

    @Test
    fun dayStatsDefaultsToZero() {
        assertEquals(DayStats(0, 0, 0), DayStats())
    }

    @Test
    fun ratingValuesMatchFsrs() {
        assertEquals(listOf(1, 2, 3, 4), Rating.entries.map { it.value })
    }

    @Test
    fun newSchedulingDefaultsHoldInvariant() {
        val s = CardScheduling(cardId = "a/b", addedAt = Instant.fromEpochMilliseconds(0))
        assertEquals(CardPhase.New, s.phase)
        assertNull(s.memory)
        assertNull(s.due)
        assertEquals(0, s.lapses)
        assertFalse(s.suspended)
        assertTrue(s.log.isEmpty())
        assertEquals(0, s.reviewCount)
    }

    @Test
    fun schedulingRejectsInvalidCardIds() {
        val addedAt = Instant.fromEpochMilliseconds(0)
        assertFailsWith<IllegalArgumentException> { CardScheduling(cardId = "", addedAt = addedAt) }
        // Card ids never contain '|' (v1 reserved it for scheduling keys).
        assertFailsWith<IllegalArgumentException> {
            CardScheduling(cardId = "a|produce", addedAt = addedAt)
        }
    }

    @Test
    fun sessionPlanIsEmpty() {
        val stamp = JoinStamp("de", "sw", "0")
        val none = SessionPlan(emptyList(), emptyList(), emptyList(), emptyList(), stamp)
        assertTrue(none.isEmpty)
        assertTrue(none.queue.isEmpty())
        assertFalse(none.copy(reviews = listOf("a/b")).isEmpty)
        assertFalse(none.copy(ahead = listOf("a/b")).isEmpty)
        assertFalse(none.copy(unlockedPhrases = listOf("a/b")).isEmpty)
        assertFalse(none.copy(newCards = listOf("a/b")).isEmpty)
    }

    /** The queue IS the run: due work leads, warm-ups follow, unseen words land last. */
    @Test
    fun sessionPlanQueueOrder() {
        val plan = SessionPlan(
            reviews = listOf("r"),
            ahead = listOf("a"),
            unlockedPhrases = listOf("p"),
            newCards = listOf("n"),
            joinStamp = JoinStamp("de", "sw", "0"),
        )
        assertEquals(listOf("r", "a", "p", "n"), plan.queue)
        assertEquals(4, plan.cardCount)
        assertEquals(2, plan.freshCount)
    }
}

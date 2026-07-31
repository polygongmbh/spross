package net.spross.kern.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import net.spross.kern.box.Box
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.DayStats

class WidgetSnapshotBuilderTests {

    private val fem = Snap.card(
        "wf", 1, emoji = "👩", sourceText = "Kellner", targetText = "ofisantka",
        feminineMarker = true,
    )
    private val gendered = Snap.card(
        "wg", 2, emoji = "🧊", sourceText = "Kühlschrank", targetText = "friji", gender = "der",
    )
    private val verb = Snap.card("wv", 3, kind = CardKind.Verb, targetText = "kupika")

    private fun scheduledState() = listOf(fem, gendered, verb).fold(Snap.state(listOf(fem, gendered, verb))) { s, card ->
        Box.inject(s, Box.sched(card.id, dueMillis = Box.plusDays(Box.day1, 1.0), lastReviewMillis = Box.day1))
    }

    @Test
    fun entriesRenderTargetSideWithTintAndMarker() {
        val doc = WidgetSnapshotBuilder.doc(scheduledState(), Box.day1, exposureLimit = 10)
        val byCard = doc.entries.associateBy { it.cardId }

        val femEntry = byCard.getValue("wf")
        assertEquals("ofisantka", femEntry.text)
        assertEquals("Kellner ♀", femEntry.sourceText)
        assertEquals("👩", femEntry.emoji)
        assertNull(femEntry.articleTint)

        assertEquals("der", byCard.getValue("wg").articleTint)
        assertEquals("Kühlschrank", byCard.getValue("wg").sourceText)
        assertNull(byCard.getValue("wv").articleTint)
        assertNull(byCard.getValue("wv").emoji)
    }

    @Test
    fun cardsCarryDueMillisAndTheSettledCountIsResolvedPhoneSide() {
        val due = Box.plusDays(Box.day1, 2.0)
        val lastReview = Box.plusSeconds(Box.day1, -3600)
        var state = Snap.state(listOf(fem, gendered))
        state = Box.inject(state, Box.sched("wf", stability = 6.5, dueMillis = due, lastReviewMillis = lastReview))
        state = Box.inject(
            state,
            Box.sched(
                "wg", phase = CardPhase.Learning, stability = 1.0,
                dueMillis = Box.day1, lastReviewMillis = Box.day1,
            ),
        )
        val doc = WidgetSnapshotBuilder.doc(state, Box.day1, exposureLimit = 10)
        val byCard = doc.cards.associateBy { it.cardId }

        assertEquals(due, byCard.getValue("wf").due)
        assertEquals(Box.day1, byCard.getValue("wg").due)
        assertEquals(1, doc.settledCount) // wg has not settled
    }

    @Test
    fun suspendedAndNonJoiningCardsAreExcluded() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched("wf", suspended = true, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        state = Box.inject(state, Box.sched("zz", dueMillis = Box.day1, lastReviewMillis = Box.day1))
        val doc = WidgetSnapshotBuilder.doc(state, Box.day1, exposureLimit = 10)
        assertTrue(doc.cards.isEmpty())
    }

    @Test
    fun dailyStatsKeepOnlyTheTrailing70Days() {
        val start = LocalDate(2026, 1, 1)
        val stats = (0 until 80).associate {
            start.plus(it, DateTimeUnit.DAY).toString() to DayStats(reviews = it)
        }
        val state = Snap.state(emptyList()).copy(dailyStats = stats)
        val doc = WidgetSnapshotBuilder.doc(state, Box.day1, exposureLimit = 5)

        assertEquals(70, doc.dailyStats.size)
        assertFalse("2026-01-01" in doc.dailyStats)
        assertFalse(start.plus(9, DateTimeUnit.DAY).toString() in doc.dailyStats)
        assertTrue(start.plus(10, DateTimeUnit.DAY).toString() in doc.dailyStats)
        assertEquals(79, doc.dailyStats.getValue(start.plus(79, DateTimeUnit.DAY).toString()).reviews)
    }

    @Test
    fun schemaVersionIsPinned() {
        assertEquals(2, WidgetSnapshotBuilder.doc(Snap.state(emptyList()), Box.day1, 5).schemaVersion)
    }

    @Test
    fun buildEmitsDeterministicJson() {
        val state = scheduledState()
        val reversed = state.copy(
            scheduling = state.scheduling.entries.reversed().associate { it.key to it.value },
        )
        assertEquals(
            WidgetSnapshotBuilder.build(state, Box.day1),
            WidgetSnapshotBuilder.build(reversed, Box.day1),
        )
        assertTrue(WidgetSnapshotBuilder.build(state, Box.day1).startsWith("{\"cards\":"))
    }
}

package net.spross.kern.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import net.spross.kern.box.Box
import net.spross.kern.box.Statistics
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.streakWindow
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
    fun phrasesTooLongForARowNeverReachTheWidget() {
        val longTarget = Snap.card("wl", 4, targetText = "a".repeat(WidgetSnapshotBuilder.MAX_TEXT_CHARS + 1))
        val longSource = Snap.card(
            "ws", 5,
            sourceText = "b".repeat(WidgetSnapshotBuilder.MAX_TEXT_CHARS), feminineMarker = true,
        )
        val fits = Snap.card("wk", 6, sourceText = "c".repeat(WidgetSnapshotBuilder.MAX_TEXT_CHARS))
        val cards = listOf(longTarget, longSource, fits)
        val state = cards.fold(Snap.state(cards)) { s, card ->
            Box.inject(s, Box.sched(card.id, dueMillis = Box.plusDays(Box.day1, 1.0), lastReviewMillis = Box.day1))
        }

        val ids = WidgetSnapshotBuilder.doc(state, Box.day1, exposureLimit = 10).entries.map { it.cardId }
        assertEquals(listOf("wk"), ids) // the ♀ marker pushes "ws" over the limit
    }

    @Test
    fun cardsCarryDueMillisAndTheConsolidatedCountIsResolvedPhoneSide() {
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
        assertEquals(1, doc.consolidatedCount) // wg has not consolidated
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
    fun dailyStatsMergeInOtherTargetLanguagesReviews() {
        val state = Snap.state(emptyList()).copy(dailyStats = mapOf("2026-01-01" to DayStats(reviews = 2)))
        val sibling = mapOf("2026-01-01" to DayStats(reviews = 3), "2026-01-02" to DayStats(reviews = 1))

        val doc = WidgetSnapshotBuilder.doc(
            state, Box.day1, exposureLimit = 5,
            otherLanguagesDailyStats = listOf(sibling),
        )

        assertEquals(5, doc.dailyStats.getValue("2026-01-01").reviews)
        assertEquals(1, doc.dailyStats.getValue("2026-01-02").reviews)
    }

    @Test
    fun schemaVersionIsPinned() {
        assertEquals(2, WidgetSnapshotBuilder.doc(Snap.state(emptyList()), Box.day1, 5).schemaVersion)
    }

    @Test
    fun decodedDerivationsAnswerWhatTheEngineAnswers() {
        // Yesterday earned, today still empty — the run is bridgeable, not yet earned.
        val dailyStats = mapOf(
            "2026-06-29" to DayStats(reviews = 4),
            "2026-06-30" to DayStats(reviews = 6),
        )
        val state = scheduledState().copy(dailyStats = dailyStats)
        val view = assertNotNull(WidgetSnapshotBuilder.decode(WidgetSnapshotBuilder.build(state, Box.day1)))

        val doc = WidgetSnapshotBuilder.doc(state, Box.day1, WidgetSnapshotBuilder.DEFAULT_EXPOSURE_LIMIT)
        assertEquals(doc.entries.map { it.cardId }, view.entries.map { it.cardId })
        assertEquals("Kellner ♀", view.entries.first { it.cardId == "wf" }.sourceText)
        assertEquals("der", view.entries.first { it.cardId == "wg" }.articleTint)
        assertEquals(doc.consolidatedCount, view.consolidatedCount)

        // Every card is due tomorrow, so only a later clock counts them.
        assertEquals(0, view.dueCount(Box.day1))
        assertEquals(3, view.dueCount(Box.plusDays(Box.day1, 1.0)))

        assertEquals(Statistics.streak(dailyStats, Box.day1, Box.TZ), view.streak(Box.day1, Box.TZ))
        assertEquals(2, view.streak(Box.day1, Box.TZ))
        assertEquals(StreakHealth.Bridgeable, view.streakHealth(Box.day1, Box.TZ))
        assertEquals(
            streakWindow(dailyStats, days = 4, nowEpochMillis = Box.day1, tzId = Box.TZ),
            view.activityWindow(days = 4, nowEpochMillis = Box.day1, tzId = Box.TZ),
        )
    }

    @Test
    fun decodeRejectsWhatItCannotDraw() {
        assertNull(WidgetSnapshotBuilder.decode("not json at all"))
        assertNull(WidgetSnapshotBuilder.decode("{}")) // schemaVersion missing
        val current = WidgetSnapshotBuilder.build(scheduledState(), Box.day1)
        assertNull(WidgetSnapshotBuilder.decode(current.replace("\"schemaVersion\":2", "\"schemaVersion\":3")))
        assertNotNull(WidgetSnapshotBuilder.decode(current))
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

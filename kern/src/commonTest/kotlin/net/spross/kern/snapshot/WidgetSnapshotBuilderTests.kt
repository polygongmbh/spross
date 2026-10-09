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
import net.spross.kern.box.ACTIVITY_WINDOW_DAYS
import net.spross.kern.box.Box
import net.spross.kern.box.Statistics
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.answerDays
import net.spross.kern.box.mergeAnswerDays
import net.spross.kern.box.streakWindow
import net.spross.kern.design.ActivityBars
import net.spross.kern.design.ActivityScale
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Gender

class WidgetSnapshotBuilderTests {

    private val fem = Snap.card(
        "wf", 1, emoji = "👩", sourceText = "Kellner", targetText = "ofisantka",
    )
    private val gendered = Snap.card(
        "wg", 2, emoji = "🧊", sourceText = "Kühlschrank", targetText = "friji", gender = "der",
    )
    private val verb = Snap.card("wv", 3, kind = CardKind.Verb, targetText = "kupika")

    private fun scheduledState(lastReviewMillis: Long = Box.day1) =
        listOf(fem, gendered, verb).fold(Snap.state(listOf(fem, gendered, verb))) { s, card ->
            Box.inject(
                s,
                Box.sched(card.id, dueMillis = Box.plusDays(Box.day1, 1.0), lastReviewMillis = lastReviewMillis),
            )
        }

    @Test
    fun entriesRenderTargetSideWithTint() {
        val doc = WidgetSnapshotBuilder.doc(scheduledState(), Box.day1, Box.TZ, exposureLimit = 10)
        val byCard = doc.entries.associateBy { it.cardId }

        val femEntry = byCard.getValue("wf")
        assertEquals("ofisantka", femEntry.text)
        assertEquals("Kellner", femEntry.sourceText)
        assertEquals("👩", femEntry.emoji)
        assertNull(femEntry.article)
        assertNull(femEntry.gender)

        assertEquals("der", byCard.getValue("wg").article)
        assertEquals("masculine", byCard.getValue("wg").gender)
        assertEquals("Kühlschrank", byCard.getValue("wg").sourceText)
        assertNull(byCard.getValue("wv").article)
        assertNull(byCard.getValue("wv").emoji)
    }

    @Test
    fun theGenderIsTheOneTheArticleMarksInTheTargetLanguage() {
        val french = Snap.card("fr", 7, targetText = "pain", gender = "le", targetLang = "fr")
        val italian = Snap.card("it", 8, targetText = "mele", gender = "le", targetLang = "it")
        val cards = listOf(french, italian)
        val state = cards.fold(Snap.state(cards)) { s, card ->
            Box.inject(s, Box.sched(card.id, dueMillis = Box.plusDays(Box.day1, 1.0), lastReviewMillis = Box.day1))
        }

        val byCard = WidgetSnapshotBuilder.doc(state, Box.day1, Box.TZ, exposureLimit = 10).entries.associateBy { it.cardId }
        assertEquals("masculine", byCard.getValue("fr").gender)
        assertEquals("feminine", byCard.getValue("it").gender)
    }

    @Test
    fun chromeFollowsTheKnownLanguageAndFallsBackWhereThereIsNone() {
        assertEquals("de", WidgetSnapshotBuilder.doc(Snap.state(emptyList()), Box.day1, Box.TZ, 5).chromeLanguage)
        val swahili = Snap.state(emptyList(), source = "sw")
        assertEquals("en", WidgetSnapshotBuilder.doc(swahili, Box.day1, Box.TZ, 5).chromeLanguage)
        assertEquals("en", WatchSnapshotBuilder.doc(swahili, Box.day1).chromeLanguage)
    }

    @Test
    fun phrasesTooLongForARowNeverReachTheWidget() {
        val longTarget = Snap.card("wl", 4, targetText = "a".repeat(WidgetSnapshotBuilder.MAX_TEXT_CHARS + 1))
        val longSource = Snap.card(
            "ws", 5,
            sourceText = "b".repeat(WidgetSnapshotBuilder.MAX_TEXT_CHARS + 1),
        )
        val fits = Snap.card("wk", 6, sourceText = "c".repeat(WidgetSnapshotBuilder.MAX_TEXT_CHARS))
        val cards = listOf(longTarget, longSource, fits)
        val state = cards.fold(Snap.state(cards)) { s, card ->
            Box.inject(s, Box.sched(card.id, dueMillis = Box.plusDays(Box.day1, 1.0), lastReviewMillis = Box.day1))
        }

        val ids = WidgetSnapshotBuilder.doc(state, Box.day1, Box.TZ, exposureLimit = 10).entries.map { it.cardId }
        assertEquals(listOf("wk"), ids)
    }

    @Test
    fun cardsCarryDueMillisAndTheSettledCountIsResolvedPhoneSide() {
        val due = Box.plusDays(Box.day1, 2.0)
        val lastReview = Box.plusSeconds(Box.day1, -3600)
        var state = Snap.state(listOf(fem, gendered))
        state = Box.inject(state, Box.sched("wf", stability = 35.0, dueMillis = due, lastReviewMillis = lastReview))
        state = Box.inject(
            state,
            Box.sched(
                "wg", phase = CardPhase.Learning, stability = 1.0,
                dueMillis = Box.day1, lastReviewMillis = Box.day1,
            ),
        )
        val doc = WidgetSnapshotBuilder.doc(state, Box.day1, Box.TZ, exposureLimit = 10)
        val byCard = doc.cards.associateBy { it.cardId }

        assertEquals(due, byCard.getValue("wf").due)
        assertEquals(Box.day1, byCard.getValue("wg").due)
        assertEquals(1, doc.allSettledCount) // wg has not settled
    }

    @Test
    fun suspendedAndNonJoiningCardsAreExcluded() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched("wf", suspended = true, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        state = Box.inject(state, Box.sched("zz", dueMillis = Box.day1, lastReviewMillis = Box.day1))
        val doc = WidgetSnapshotBuilder.doc(state, Box.day1, Box.TZ, exposureLimit = 10)
        assertTrue(doc.cards.isEmpty())
    }

    @Test
    fun theStripEmptiesOnRenderDaysAfterTheBuild() {
        val days = mapOf("2026-06-30" to 4, "2026-07-01" to 6)
        val view = assertNotNull(
            WidgetSnapshotBuilder.decode(
                WidgetSnapshotBuilder.build(Snap.state(emptyList()), Box.day1, Box.TZ, otherLanguagesAnswerDays = days),
            ),
        )

        assertEquals(listOf(4, 6), view.activityBars(Box.day1, Box.TZ).takeLast(2).map { it.reviews })
        assertEquals(6, view.activityBars(Box.plusDays(Box.day1, 2.0), Box.TZ).dropLast(2).last().reviews)
        assertTrue(view.activityBars(Box.plusDays(Box.day1, 40.0), Box.TZ).none { it.worked })
    }

    @Test
    fun theStripMergesInOtherTargetLanguagesReviews() {
        val state = Box.inject(
            Snap.state(emptyList()),
            Box.sched("zz", dueMillis = Box.day1, lastReviewMillis = Box.millis(2026, 6, 30), logCount = 2),
        )
        val sibling = mapOf("2026-06-30" to 3, "2026-07-01" to 1)

        val view = assertNotNull(
            WidgetSnapshotBuilder.decode(
                WidgetSnapshotBuilder.build(state, Box.day1, Box.TZ, otherLanguagesAnswerDays = sibling),
            ),
        )

        assertEquals(listOf(5, 1), view.activityBars(Box.day1, Box.TZ).takeLast(2).map { it.reviews })
    }

    @Test
    fun aStreakLongerThanTheDayTailReachesTheWidget() {
        val start = LocalDate(2026, 7, 1)
        val days = (0 until 100).associate { start.plus(-it, DateTimeUnit.DAY).toString() to 1 }
        val view = assertNotNull(
            WidgetSnapshotBuilder.decode(
                WidgetSnapshotBuilder.build(Snap.state(emptyList()), Box.day1, Box.TZ, otherLanguagesAnswerDays = days),
            ),
        )

        assertEquals(100, view.streak(Box.day1, Box.TZ))
        assertEquals(StreakHealth.Earned, view.streakHealth(Box.day1, Box.TZ))
    }

    @Test
    fun theStreakAgesOnRenderDaysAfterTheBuild() {
        val days = mapOf("2026-06-30" to 4, "2026-07-01" to 6)
        val view = assertNotNull(
            WidgetSnapshotBuilder.decode(
                WidgetSnapshotBuilder.build(Snap.state(emptyList()), Box.day1, Box.TZ, otherLanguagesAnswerDays = days),
            ),
        )
        val twoDaysOn = Box.plusDays(Box.day1, 2.0)
        val weekOn = Box.plusDays(Box.day1, 7.0)

        assertEquals(StreakHealth.Ending, view.streakHealth(twoDaysOn, Box.TZ))
        assertEquals(2, view.streak(twoDaysOn, Box.TZ))
        assertEquals(StreakHealth.NoRun, view.streakHealth(weekOn, Box.TZ))
        assertEquals(0, view.streak(weekOn, Box.TZ))
    }

    @Test
    fun decodedDerivationsAnswerWhatTheEngineAnswers() {
        // Yesterday earned, today still empty — the run is bridgeable, not yet earned.
        // This box's own answers sit days back, so they add no day to the run.
        val dailyStats = mapOf("2026-06-29" to 4, "2026-06-30" to 6)
        val state = scheduledState(lastReviewMillis = Box.plusDays(Box.day1, -5.0))
        val view = assertNotNull(
            WidgetSnapshotBuilder.decode(
                WidgetSnapshotBuilder.build(
                    state, Box.day1, Box.TZ,
                    otherLanguagesAnswerDays = dailyStats,
                ),
            ),
        )

        val doc = WidgetSnapshotBuilder.doc(
            state, Box.day1, Box.TZ, WidgetSnapshotBuilder.DEFAULT_EXPOSURE_LIMIT,
            otherLanguagesAnswerDays = dailyStats,
        )
        assertEquals(doc.entries.map { it.cardId }, view.entries.map { it.cardId })
        assertEquals("Kellner", view.entries.first { it.cardId == "wf" }.sourceText)
        assertEquals("der", view.entries.first { it.cardId == "wg" }.article)
        assertEquals(Gender.Masculine, view.entries.first { it.cardId == "wg" }.gender)
        assertEquals(doc.allSettledCount, view.allSettledCount)

        // Every card is due tomorrow, so only a later clock counts them.
        assertEquals(0, view.dueCount(Box.day1))
        assertEquals(3, view.dueCount(Box.plusDays(Box.day1, 1.0)))

        assertEquals(Statistics.streak(dailyStats, Box.day1, Box.TZ), view.streak(Box.day1, Box.TZ))
        assertEquals(StreakHealth.Bridgeable, view.streakHealth(Box.day1, Box.TZ))
        val window = streakWindow(
            mergeAnswerDays(listOf(dailyStats, answerDays(state.scheduling, Box.TZ))),
            ACTIVITY_WINDOW_DAYS, nowEpochMillis = Box.day1, tzId = Box.TZ,
        )
        assertEquals(
            ActivityBars.of(window, ActivityScale.widget).map { it.height },
            view.activityBars(nowEpochMillis = Box.day1, tzId = Box.TZ).map { it.height },
        )
    }

    @Test
    fun decodeRejectsWhatItCannotDraw() {
        assertNull(WidgetSnapshotBuilder.decode("not json at all"))
        assertNull(WidgetSnapshotBuilder.decode("{}")) // schemaVersion missing
        val current = WidgetSnapshotBuilder.build(scheduledState(), Box.day1, Box.TZ)
        assertNull(WidgetSnapshotBuilder.decode(current.replace("\"schemaVersion\":${WidgetSnapshotBuilder.SCHEMA_VERSION}", "\"schemaVersion\":0")))
        assertNotNull(WidgetSnapshotBuilder.decode(current))
    }

    @Test
    fun buildEmitsDeterministicJson() {
        val state = scheduledState()
        val reversed = state.copy(
            scheduling = state.scheduling.entries.reversed().associate { it.key to it.value },
        )
        assertEquals(
            WidgetSnapshotBuilder.build(state, Box.day1, Box.TZ),
            WidgetSnapshotBuilder.build(reversed, Box.day1, Box.TZ),
        )
    }
}

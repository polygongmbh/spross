package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.AnswerStatus
import net.spross.kern.box.BoxEngine
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.model.presentationRole
import net.spross.kern.session.SessionComposer

class SessionFlowTest {

    private val now = 1_753_000_000_000L
    private val tz = "Europe/Berlin"

    private fun card(id: String, index: Int) = Card(
        id = "test/$id",
        kind = CardKind.Noun,
        area = "test",
        emoji = "🧪",
        seedIndex = index,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = "Wort $index"),
        target = Realization(lang = "sw", text = "neno $index"),
        promptFeminineMarker = false,
    )

    private fun freshFlow(): SessionFlow {
        val cards = listOf(card("a", 0), card("b", 1), card("c", 2))
        val state = BoxEngine.bootstrap(cards, BoxConfig(), JoinStamp("de", "sw", "fp"))
        val plan = SessionComposer.composeSession(state, now)
        return SessionFlow(state, plan)
    }

    @Test
    fun dayOneSessionIntroducesAndDrains() {
        val flow = freshFlow()
        assertFalse(flow.isFinished)

        val first = flow.currentCardId
        assertNotNull(first)
        assertEquals(0, flow.reviewCount(first))
        assertEquals(PresentationRole.Recognize, presentationRole(first, flow.reviewCount(first)))

        assertEquals(AnswerStatus.Applied, flow.answer(Rating.Good, now, tz))
        assertEquals(1, flow.answered)
        assertEquals(1, flow.introduced)
        assertEquals(1, flow.strengthened)
        assertEquals(listOf(AnswerTone.Right), flow.segments)

        assertEquals(AnswerStatus.Applied, flow.answer(Rating.Again, now, tz))
        assertEquals(1, flow.strengthened)
        assertEquals(listOf(AnswerTone.Right, AnswerTone.Wrong), flow.segments)

        assertEquals(AnswerStatus.Applied, flow.answer(Rating.Good, now, tz))
        // learning-step cards are due minutes later, not at `now` — the drain ends.
        assertTrue(flow.isFinished)
        assertNull(flow.currentCardId)
        assertEquals(3, flow.introduced)

        val ended = flow.finish(now, tz)
        assertEquals(3, ended.newIntroduced.values.sum())
    }

    @Test
    fun endlessWithNothingAvailableStaysFinished() {
        // 3 cards all sitting in learning steps: nothing due, no growth budget left.
        val flow = freshFlow()
        while (!flow.isFinished) flow.answer(Rating.Good, now, tz)

        flow.continueEndless(now)
        assertTrue(flow.isFinished)
        assertNull(flow.currentCardId)
    }

    @Test
    fun endlessPullsLearningCardsOnceDue() {
        val flow = freshFlow()
        while (!flow.isFinished) flow.answer(Rating.Good, now, tz)

        // learning step [1m, 10m]: quarter of an hour later everything is due again
        flow.continueEndless(now + 15 * 60_000)
        assertFalse(flow.isFinished)
        assertNotNull(flow.currentCardId)
    }

    @Test
    fun progressGrowsWithAnswers() {
        val flow = freshFlow()
        assertEquals(0f, flow.progress())
        flow.answer(Rating.Good, now, tz)
        assertEquals(1f / 3f, flow.progress())
    }
}

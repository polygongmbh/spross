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
    fun dayOneSessionIntroducesAndFinishes() {
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
        // the plan is the whole run — three cards in, three cards out.
        assertTrue(flow.isFinished)
        assertNull(flow.currentCardId)
        assertEquals(3, flow.introduced)

        val ended = flow.finish(now, tz)
        assertEquals(3, ended.newIntroduced.values.sum())
    }

    // The count on screen is a promise: a word whose learning step matures while the
    // learner is still sitting there does NOT join the run and push the finish line
    // back. It waits for the summary, where "Weiter üben" takes it on purpose.
    @Test
    fun aCardComingDueMidRunDoesNotJoinIt() {
        val flow = freshFlow()
        flow.answer(Rating.Again, now, tz) // due again 2 minutes on

        val late = now + 3 * 60_000 // by here that step has long matured
        flow.answer(Rating.Good, late, tz)
        flow.answer(Rating.Good, late, tz)

        assertTrue(flow.isFinished)
        assertEquals(3, flow.answered)

        // …and it is genuinely there for the asking, not lost.
        flow.continueEndless(late)
        assertFalse(flow.isFinished)
        assertNotNull(flow.currentCardId)
    }

    @Test
    fun endlessWithNothingAvailableStaysFinished() {
        // 3 cards, all answered on sight → day-scale intervals, nothing left today.
        val flow = freshFlow()
        while (!flow.isFinished) flow.answer(Rating.Good, now, tz)

        flow.continueEndless(now)
        assertTrue(flow.isFinished)
        assertNull(flow.currentCardId)
    }

    @Test
    fun endlessPullsMissedCardsBackOnceTheStepMatures() {
        val flow = freshFlow()
        // Missed words take the one 2-minute step; words answered on sight go to
        // day scale and do not come back at all.
        while (!flow.isFinished) flow.answer(Rating.Again, now, tz)

        flow.continueEndless(now + 60_000)
        assertTrue(flow.isFinished) // 1 min in: the step has not matured

        flow.continueEndless(now + 3 * 60_000)
        assertFalse(flow.isFinished)
        assertNotNull(flow.currentCardId)
    }

    @Test
    fun extraRoundPrefersEndlessComposition() {
        val cards = listOf(card("a", 0), card("b", 1), card("c", 2))
        val state = BoxEngine.bootstrap(cards, BoxConfig(), JoinStamp("de", "sw", "fp"))
        // Fresh box: endless offers new vocab within the pool budget, while the
        // review-ahead extra round would be empty (no active cards yet).
        val plan = extraSessionPlan(state, now)
        assertFalse(plan.isEmpty)
        assertTrue(plan.newCards.isNotEmpty())
    }

    @Test
    fun extraRoundFallsBackToReviewAheadWhenEndlessIsEmpty() {
        // Drain day one: all cards sit in learning steps (due minutes later),
        // pool budget spent — endless is legitimately empty right now.
        val flow = freshFlow()
        while (!flow.isFinished) flow.answer(Rating.Good, now, tz)
        val ended = flow.finish(now, tz)
        assertTrue(SessionComposer.composeEndless(ended, now).isEmpty)

        val plan = extraSessionPlan(ended, now)
        assertFalse(plan.isEmpty)
        assertTrue(plan.reviews.isNotEmpty()) // review-ahead: soonest-due first
    }

    @Test
    fun progressGrowsWithAnswers() {
        val flow = freshFlow()
        assertEquals(0f, flow.progress())
        flow.answer(Rating.Good, now, tz)
        assertEquals(1f / 3f, flow.progress())
    }
}

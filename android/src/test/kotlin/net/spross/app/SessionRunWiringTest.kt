package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.TallyPart
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.answerDays
import net.spross.kern.box.dayKey
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization
import net.spross.kern.session.SessionEffect
import net.spross.kern.session.SessionIntent
import net.spross.kern.session.SessionRun
import net.spross.kern.session.SessionRunState
import net.spross.kern.store.SaveScope

/**
 * What the app DOES with kern's session run — the intents each affordance sends, and the
 * effects the model owes an answer to. The rules themselves (queue, buckets, endless
 * refill, delta fold) belong to `:kern:jvmTest`; nothing here re-tests them.
 *
 * The harness is `AppModel.dispatch` with the platform stripped out: reduce, keep the
 * state, record what the reduction asked for.
 */
class SessionRunWiringTest {

    private val now = 1_753_000_000_000L
    private val tz = "Europe/Berlin"

    private fun card(id: String, index: Int) = Card(
        id = "test/$id",
        kind = CardKind.Noun,
        area = "test",
        emoji = "🧪",
        seedIndex = index,
        components = emptyList(),
        source = Realization(lang = "de", text = "Wort $index"),
        target = Realization(lang = "sw", text = "neno $index"),
    )

    private class Model(box: BoxState) {
        var state: SessionRunState = SessionRun.idle(box)

        /** The scope of every save asked for, in order. */
        val saves = mutableListOf<SaveScope>()
        var daysBooked = 0

        fun dispatch(intent: SessionIntent, nowEpochMillis: Long, tzId: String): SessionRunState {
            val reduction = SessionRun.reduce(state, intent, nowEpochMillis, tzId)
            state = reduction.state
            for (effect in reduction.effects) {
                when (effect) {
                    is SessionEffect.Save -> saves += effect.scope
                    SessionEffect.DayBooked -> daysBooked += 1
                }
            }
            return state
        }
    }

    private fun freshModel(): Model {
        val cards = listOf(card("a", 0), card("b", 1), card("c", 2))
        return Model(BoxEngine.bootstrap(cards, BoxConfig.product(), JoinStamp("de", "sw", "fp")))
    }

    private fun Model.reviewsBooked(): Int =
        answerDays(state.box.scheduling, tz)[dayKey(now, tz)] ?: 0

    /** The screen it navigates to hangs on this: a round with no card took nobody anywhere. */
    @Test
    fun aStartedRoundOffersItsFirstCard() {
        val model = freshModel()
        assertNotNull(model.dispatch(SessionIntent.Start, now, tz).currentCardId)
    }

    @Test
    fun everyAnswerAsksToBeWrittenOut() {
        val model = freshModel()
        model.dispatch(SessionIntent.Start, now, tz)
        model.dispatch(SessionIntent.Answer(Rating.Good), now, tz)
        assertEquals(listOf(SaveScope.BOX), model.saves)
    }

    /**
     * The three numbers the summary line formats, straight off the run's buckets — a first
     * meeting is "neu" and nothing else, whatever its rating: never "gefestigt", never a
     * repetition.
     */
    @Test
    fun theSummaryLineReadsFirstMeetingsAsNewAndNothingElse() {
        val model = freshModel()
        model.dispatch(SessionIntent.Start, now, tz)
        model.dispatch(SessionIntent.Answer(Rating.Good), now, tz)
        model.dispatch(SessionIntent.Answer(Rating.Again), now, tz)
        val run = model.dispatch(SessionIntent.Answer(Rating.Easy), now, tz)

        assertEquals(listOf(TallyPart(TallyPartKind.Introduced, 3)), run.tally.parts())
    }

    /** The progress bar's denominator is the promise on screen, not what is left of it. */
    @Test
    fun theCountOnScreenStaysTheOneThatWasPromised() {
        val model = freshModel()
        val started = model.dispatch(SessionIntent.Start, now, tz)
        assertEquals(3, started.total)
        model.dispatch(SessionIntent.Answer(Rating.Good), now, tz)
        assertEquals(3, model.state.total)
        assertEquals(1, model.state.segments.size)
        assertEquals(2, model.state.remaining)
    }

    /**
     * `SprossActivity.onStop` writes the box, and the day is already
     * in it — every answer lands in the box and asks for a save as it does, so an evicted
     * app loses nothing the learner demonstrated.
     */
    @Test
    fun everyAnswerIsInTheBoxAndAsksForASave() {
        val model = freshModel()
        model.dispatch(SessionIntent.Start, now, tz)
        model.dispatch(SessionIntent.Answer(Rating.Good), now, tz)
        model.dispatch(SessionIntent.Answer(Rating.Good), now, tz)

        assertEquals(2, model.reviewsBooked())
        assertTrue(model.saves.isNotEmpty())

        model.dispatch(SessionIntent.Answer(Rating.Good), now, tz) // drains → finishes
        model.dispatch(SessionIntent.Close, now, tz)
        assertEquals(3, model.reviewsBooked())
        assertEquals(SaveScope.BOX_AND_SNAPSHOTS, model.saves.last())
        assertTrue(model.daysBooked > 0) // finishing and closing both tell the surfaces to re-read
    }

    /**
     * The extra round: the app hands kern the intent and never picks the composition
     * itself. On a drained box that means the MIXING round — every card in it has been
     * answered before — not an endless draw of first sights.
     */
    @Test
    fun theExtraRoundPullsWorkForwardRatherThanOpeningNewWords() {
        val model = freshModel()
        model.dispatch(SessionIntent.Start, now, tz)
        while (model.state.currentCardId != null) {
            model.dispatch(SessionIntent.Answer(Rating.Good), now, tz)
        }
        model.dispatch(SessionIntent.Close, now, tz)

        val extra = model.dispatch(SessionIntent.StartExtra, now, tz)
        assertNotNull(extra.currentCardId)
        val seen = extra.queue.all { (extra.box.scheduling[it]?.reviewCount ?: 0) > 0 }
        assertTrue(seen, "the extra round opened first sights instead of mixing recall in")
    }

    /** Nothing to pull, nothing to open: the tap leaves the learner on Home. */
    @Test
    fun anExtraRoundWithNothingBehindItGoesNowhere() {
        val model = Model(BoxEngine.bootstrap(emptyList(), BoxConfig.product(), JoinStamp("de", "sw", "fp")))
        assertNull(model.dispatch(SessionIntent.StartExtra, now, tz).currentCardId)
        assertTrue(model.saves.isEmpty())
    }
}

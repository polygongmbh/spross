package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.Saying
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.CountryRunFixture.content
import net.spross.kern.trainer.CountryRunFixture.open

/**
 * The pair drill run both the atlas and the calendar step through ([PairDrillRun]): what an
 * intent does, what the ramp does with an answer, and what a close leaves behind — driven on
 * the one-country atlas, whose Sprosse 1 holds a single question.
 *
 * The verdicts are [TypedDrillVerdictsTest]'s and the booking [LadderStandingTest]'s; what each
 * drill grades and asks is its own test's.
 */
class PairDrillRunTest {

    private val saysUjerumani = Saying("Ujerumani", "sw")

    private fun PairDrillRunState<CountryDrillTask>.reduce(intent: PairDrillIntent) =
        CountryDrillRun.reduce(this, intent, Random(7))

    /** The run stepped by one whole answer, the way the platform steps it. */
    private fun PairDrillRunState<CountryDrillTask>.answered(text: String) =
        reduce(PairDrillIntent.Submit(text)).state.reduce(PairDrillIntent.ConfirmPending).state

    private fun PairDrillRunState<CountryDrillTask>.missed() =
        reduce(PairDrillIntent.Reveal).state.reduce(PairDrillIntent.ConfirmPending).state

    private fun PairDrillRunState<CountryDrillTask>.closed() = CountryDrillRun.close(this, standingRecord = 0)

    // MARK: - The shared pricing

    /** Fast is earned by having STOOD on the top Sprosse: the app keeps the highest any run reached. */
    @Test
    fun fastIsOfferedOnlyOnceTheTopSprosseHasBeenReached() {
        assertFalse(PairDrill.fastUnlocked(bestSprosse = 3, top = 6))
        assertTrue(PairDrill.fastUnlocked(bestSprosse = 6, top = 6))
        assertTrue(PairDrill.fastUnlocked(bestSprosse = 9, top = 6), "a stored best above the top")
    }

    // MARK: - Where a run opens

    @Test
    fun aRunOpensOnTheFirstSprosseWithAQuestionStanding() {
        val run = CountryDrillRun.open(CountryRunFixture.config(), Random(7))
        assertEquals(1, run.sprosse)
        assertEquals("Ujerumani", run.task.display)
        assertTrue(run.owesAnswer)
        assertEquals(0, run.done)
    }

    /** The forced Sprosse is for tests and screenshot drivers; kern clamps it to the ladder. */
    @Test
    fun aForcedSprosseIsClampedToTheLadder() {
        assertEquals(CountryDrill.MAX_SPROSSE, open(sprosse = 99).sprosse)
        assertEquals(1, open(sprosse = 0).sprosse)
    }

    /** Which side prompts and which is owed is the run's, not a screen's. */
    @Test
    fun theDirectionSettlesWhichLanguageIsPromptedAndWhichIsOwed() {
        assertEquals("sw", open().answerLanguage)
        assertEquals("de", open().promptLanguage)
        assertEquals("de", open(reverse = true).answerLanguage)
        assertEquals("sw", open(reverse = true).promptLanguage)
    }

    // MARK: - What a typed answer earns

    /** A keystroke reaches the live approve, and an approved answer is said. */
    @Test
    fun aKeystrokeIsGradedForTheLiveApprove() {
        val finished = open().reduce(PairDrillIntent.InputChanged("Ujerumani"))
        assertEquals(TurnFeedback.Correct, finished.state.feedback)
        assertEquals(saysUjerumani, finished.state.reading.answer)

        val slipped = open().reduce(PairDrillIntent.InputChanged("Ujerumami"))
        assertEquals(TurnFeedback.Neutral, slipped.state.feedback, "a slip mid-word is not an answer yet")
    }

    /** A check reaches the verdict ladder with the drill's own grade: a slip holds on the proper spelling. */
    @Test
    fun aCheckIsGradedByTheDrill() {
        val exact = open().reduce(PairDrillIntent.Submit("Ujerumani"))
        assertEquals(TypedDrillVerdicts.submit(Match.Exact).effects, exact.effects)

        val slip = open().reduce(PairDrillIntent.Submit("Ujerumami"))
        assertEquals(TurnFeedback.Almost("Ujerumani", AlmostReason.Typo), slip.state.feedback)
    }

    /**
     * A reversed run asks in the language being learned, so its prompt is said as it goes up;
     * it answers in the learner's own, which no verdict reads out.
     */
    @Test
    fun aReversedRunSaysItsPromptAndNotItsVerdict() {
        val reversed = open(reverse = true)
        assertEquals(Saying(assertNotNull(reversed.task.promptText), "sw"), reversed.reading.prompt)
        assertNull(reversed.reduce(PairDrillIntent.Submit(reversed.task.display)).state.reading.answer)
        assertNull(open().reading.prompt)
    }

    /** An empty field reveals and books a miss; Check with nothing typed is the same ask. */
    @Test
    fun aBlankSubmitRevealsAndTheRevealBooksAMiss() {
        val run = open()
        val asked = run.reduce(PairDrillIntent.Reveal)
        assertEquals(asked, run.reduce(PairDrillIntent.Submit("   ")))
        assertTrue(asked.state.reduce(PairDrillIntent.Submit("   ")).effects.isEmpty(), "nothing is owed once revealed")

        val booked = asked.state.reduce(PairDrillIntent.ConfirmPending).state
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
    }

    // MARK: - The ramp

    /** Three clean wins a Sprosse, and the question moves on with the booking; Fast spends one. */
    @Test
    fun cleanWinsCarryTheRunAndFastSpendsOne() {
        var run = open(sprosse = 5)
        repeat(PairDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        assertEquals(6, run.sprosse)
        assertEquals(3, run.index)
        assertTrue(run.owesAnswer, "the next question is up, not the last one's verdict")

        val fast = open(fast = true, sprosse = 5)
        assertEquals(6, fast.answered(fast.task.display).sprosse)
    }

    /** A miss drops the Sprosse, and the close reports the Sprosse the run REACHED. */
    @Test
    fun theCloseReportsTheSprosseTheRunReached() {
        var run = open(sprosse = 5)
        repeat(PairDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        run = run.missed()
        assertEquals(5, run.sprosse)
        assertEquals(6, run.closed().bestSprosse)
    }

    // MARK: - Asking each question once

    /**
     * The Sprosse the fixture opens on holds ONE question, so answering it right leaves the
     * Sprosse with nothing: the run climbs past it rather than asking it again.
     */
    @Test
    fun aSprosseWithNothingLeftToAskIsClimbedPast() {
        val run = open().answered("Ujerumani")
        assertEquals(2, run.sprosse)
        assertEquals(0, run.winsAtSprosse, "the wins stay behind with the Sprosse that earned them")
        assertFalse(run.task.kind == CountryTaskKind.CountryName && run.task.id == "germany")
    }

    /** A slip and a miss both leave the question in the pool — only a clean answer retires it. */
    @Test
    fun onlyACleanAnswerRetiresAQuestion() {
        assertEquals(1, open().answered("Ujerumami").sprosse)
        assertEquals(1, open().missed().sprosse)
    }

    /** A whole ladder answered out ends the run on its summary, cleared to the top. */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        var run = open(sprosse = CountryDrill.MAX_SPROSSE)
        val asked = mutableSetOf<String>()
        while (!run.finished) {
            asked += DrillSolved.key(run.task)
            run = run.answered(run.task.display)
        }
        assertEquals(CountryDrill.tasks(content, CountryDrill.MAX_SPROSSE).size, asked.size)
        assertEquals(asked.size, run.done)
        assertEquals((1..CountryDrill.MAX_SPROSSE).toSet(), run.closed().clearedSprossen)
    }

    /** Answering a Sprosse out clears it for the next run, unless the run slipped first. */
    @Test
    fun aSprosseAnsweredOutBeforeTheFirstSlipIsCleared() {
        val opened = open()
        assertEquals(emptySet(), opened.closed().clearedSprossen)
        assertEquals(setOf(1), opened.answered(opened.task.display).closed().clearedSprossen)

        val slipped = open().missed()
        assertEquals(emptySet(), slipped.answered(slipped.task.display).closed().clearedSprossen)
    }

    /** Answering out a Sprosse the store did not hold is what a pause for improving names. */
    @Test
    fun aSprosseAnsweredOutForTheFirstTimePausesAsImproved() {
        fun stretched(cleared: Set<Int>) = open(cleared = cleared)
            .let { it.copy(core = DrillRunCore(done = DrillPacing.IMPROVED_AFTER - 1)) }
            .answered("Ujerumani")
        assertEquals(DrillPauseReason.Improved, stretched(cleared = emptySet()).pause)
        assertNull(stretched(cleared = setOf(1)).pause)
    }

    // MARK: - Leaving

    @Test
    fun anUntouchedRunReportsNothingButStillNamesItsSprosse() {
        val closed = open().closed()
        assertNull(closed.summary)
        assertEquals(1, closed.bestSprosse)
        assertTrue(closed.state.finished)
        assertEquals(LadderStanding.EFFECTS, closed.effects)
    }

    /** Closing books a pending answer as the tap would, against the record the store holds. */
    @Test
    fun aPendingAnswerBooksOnTheWayOutAgainstTheStandingRecord() {
        val summary = assertNotNull(open().reduce(PairDrillIntent.Submit("Ujerumani")).state.closed().summary)
        assertEquals(1, summary.done)
        assertTrue(summary.newRecord, "a first streak beats a standing record of none")
    }

    /** What a close files is keyed by the drill and the pair, in the direction it ran. */
    @Test
    fun aCloseFilesUnderTheDrillsOwnPairKey() {
        val bookings = open().answered("Ujerumani").closed().bookings()
        val key = CountryDrill.storageKey("de", "sw")
        assertEquals(mapOf(key to 2), bookings.sprossen)
        assertEquals(setOf(NumbersMode.clearedKey(key, false)), bookings.cleared.keys)
    }
}

package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The dates run: what a typed reading earns, which beat it arms, what the ramp does with
 * it, and what a close leaves behind. The ladder, the draw and the task shapes are
 * [DateDrill]'s and pinned in [DateDrillTests]; what stands here is the run that steps
 * through them.
 */
class DateDrillRunTest {

    private fun config(
        reverse: Boolean = false,
        fast: Boolean = false,
        graded: Boolean = true,
    ) = DateDrillRunConfig(
        content = DateDrillFixture.germanContent,
        reverse = reverse,
        fast = fast,
        normalizer = when {
            !graded -> null
            reverse -> AnswerNormalizer.drill(DateDrillFixture.english)
            else -> AnswerNormalizer.drill(DateDrillFixture.german)
        },
    )

    private fun open(
        reverse: Boolean = false,
        fast: Boolean = false,
        graded: Boolean = true,
        level: Int = 1,
    ) = DateDrillRun.openAt(config(reverse, fast, graded), level, Random(7))

    private fun DateDrillRunState.reduce(intent: DateDrillIntent) =
        DateDrillRun.reduce(this, intent, Random(7))

    /** The run stepped by one whole answer, the way the platform steps it. */
    private fun DateDrillRunState.answered(text: String): DateDrillRunState =
        reduce(DateDrillIntent.Submit(text)).state.reduce(DateDrillIntent.ConfirmPending).state

    private fun DateDrillRunState.missed(): DateDrillRunState =
        reduce(DateDrillIntent.Reveal).state.reduce(DateDrillIntent.ConfirmPending).state

    /** The standing task's reading with its last letter fumbled — a slip, never another name. */
    private fun DateDrillRunState.slipped(): String = task.display.dropLast(1) + "x"

    // MARK: - Where a run opens

    @Test
    fun aRunOpensOnTheFirstSprosseWithAQuestionStanding() {
        val run = DateDrillRun.open(config(), Random(7))
        assertEquals(1, run.level)
        assertEquals(1, run.bestLevel)
        assertEquals(DateTaskKind.NameChoice, run.task.kind, "every ladder opens on the tiles")
        assertTrue(run.owesAnswer)
        assertFalse(run.showsAnswer)
        assertEquals(0, run.done)
    }

    /** The forced Sprosse is for tests and screenshot drivers; kern clamps it to THIS ladder. */
    @Test
    fun aForcedSprosseIsClampedToTheLadder() {
        assertEquals(7, open(level = 99).level)
        assertEquals(1, open(level = 0).level)
        assertEquals(3, open(reverse = true, level = 99).level)
    }

    @Test
    fun theDirectionSettlesWhichLanguageIsPromptedAndWhichIsOwed() {
        val forward = open()
        assertEquals("de", forward.answerLanguage)
        assertEquals("en", forward.promptLanguage)

        val reversed = open(reverse = true)
        assertEquals("en", reversed.answerLanguage)
        assertEquals("de", reversed.promptLanguage)
    }

    // MARK: - What a typed reading earns

    /** Finishing the reading IS the answer — exact only, a typo budget would fire early. */
    @Test
    fun finishingTheReadingArmsTheLiveBeatWithoutACheckTap() {
        val run = open()
        val reduction = run.reduce(DateDrillIntent.InputChanged(run.task.display))
        assertEquals(TurnFeedback.Correct, reduction.state.feedback)
        assertEquals(
            listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ArmAdvance(AdvanceTier.Live)),
            reduction.effects,
        )
    }

    @Test
    fun aSlipNeverApprovesLive() {
        val run = open()
        val reduction = run.reduce(DateDrillIntent.InputChanged(run.slipped()))
        assertEquals(TurnFeedback.Neutral, reduction.state.feedback)
        assertEquals(listOf(DrillEffect.CancelAdvance), reduction.effects)
    }

    @Test
    fun backingOutOfAFinishedReadingWithdrawsTheApproval() {
        val run = open()
        val approved = run.reduce(DateDrillIntent.InputChanged(run.task.display)).state
        val reduction = approved.reduce(DateDrillIntent.InputChanged(run.task.display + "x"))
        assertEquals(TurnFeedback.Neutral, reduction.state.feedback)
        assertEquals(listOf(DrillEffect.CancelAdvance), reduction.effects)
    }

    @Test
    fun anExplicitCheckOnTheRightReadingArmsTheLongerBeat() {
        val run = open()
        val reduction = run.reduce(DateDrillIntent.Submit(run.task.display))
        assertEquals(TurnFeedback.Correct, reduction.state.feedback)
        assertEquals(
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ArmAdvance(AdvanceTier.Explicit),
            ),
            reduction.effects,
        )
    }

    @Test
    fun aSlipOnACheckHoldsAlmostAndGivesTheKeyboardBack() {
        val run = open()
        val reduction = run.reduce(DateDrillIntent.Submit(run.slipped()))
        val hold = assertIs<TurnFeedback.Almost>(reduction.state.feedback)
        assertEquals(run.task.display, hold.correctForm)
        assertEquals(AlmostReason.Typo, hold.reason)
        assertEquals(
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ReleaseFocus,
            ),
            reduction.effects,
        )
    }

    /**
     * The numeral Sprossen carry the numbers drill's value check: a day that names another
     * day is refused and named, while a genuine fumble stays the forgiven slip it was.
     */
    @Test
    fun aDayThatNamesAnotherDayIsRefusedNotForgiven() {
        val task = DateDrillTasks.day(DateDrillFixture.germanContent, 3)
        val match = DateDrillRun.grade("vierte", task, config())
        assertIs<Match.OtherWord>(match)
        assertEquals("vierte", match.word)
        assertIs<Match.Typo>(DateDrillRun.grade("drittte", task, config()))
    }

    /** No language info (a preview): a plain case- and punctuation-insensitive comparison. */
    @Test
    fun aPreviewWithNoLanguageInfoStillGradesPlainly() {
        val task = DateDrillTasks.day(DateDrillFixture.germanContent, 3)
        assertEquals(Match.Exact, DateDrillRun.grade("  dritte!  ", task, config(graded = false)))
        assertEquals(Match.Wrong, DateDrillRun.grade("drittex", task, config(graded = false)))
    }

    @Test
    fun revealingOpensTheCardAndBooksAMiss() {
        val revealed = open().reduce(DateDrillIntent.Reveal)
        assertEquals(TurnFeedback.Revealed, revealed.state.feedback)
        assertEquals(listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)), revealed.effects)

        val booked = revealed.state.reduce(DateDrillIntent.ConfirmPending).state
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
        assertEquals(0, booked.streak)
    }

    @Test
    fun aBlankSubmitIsInert() {
        val run = open()
        val reduction = run.reduce(DateDrillIntent.Submit("   "))
        assertEquals(run, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun anElapsedBeatBooksOnlyACleanAnswer() {
        val held = open().run { reduce(DateDrillIntent.Submit(slipped())).state }
        assertEquals(held, held.reduce(DateDrillIntent.AdvanceElapsed).state)

        val clean = open().run { reduce(DateDrillIntent.Submit(task.display)).state }
        assertEquals(1, clean.reduce(DateDrillIntent.AdvanceElapsed).state.done)
    }

    // MARK: - The ramp

    /** Three clean wins a Sprosse — Sprosse 3 has 31 questions, so the wins carry the climb. */
    @Test
    fun threeCleanWinsCarryTheRun() {
        var run = open(level = 3)
        repeat(DateDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        assertEquals(4, run.level)
        assertEquals(4, run.bestLevel)
        assertEquals(3, run.done)
        assertEquals(3, run.streak)
        assertEquals(3, run.index)
        assertTrue(run.owesAnswer, "the next question is up, not the last one's verdict")
    }

    @Test
    fun fastSpendsOneWinASprosse() {
        val run = open(fast = true, level = 3)
        assertEquals(4, run.answered(run.task.display).level)
    }

    @Test
    fun theAlmostHoldBanksNoWin() {
        val opened = open()
        val run = opened.answered(opened.slipped())
        assertEquals(1, run.level)
        assertEquals(0, run.winsAtLevel)
        assertEquals(listOf(AnswerOutcome.Almost), run.outcomes)
        assertEquals(1, run.streak, "a slip is still an answer the learner got")
    }

    @Test
    fun aMissDropsTheSprosseButNotTheOneTheRunReached() {
        var run = open(level = 5)
        repeat(DateDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        assertEquals(6, run.level)
        run = run.missed()
        assertEquals(5, run.level)
        assertEquals(6, run.bestLevel)
    }

    // MARK: - Asking each question once

    /** A slip and a miss both leave the question in the pool — only a clean answer retires it. */
    @Test
    fun onlyACleanAnswerRetiresAQuestion() {
        val opened = open()
        val slipped = opened.answered(opened.slipped())
        assertTrue(DrillSolved.key(opened.task) !in slipped.solved)

        val clean = opened.answered(opened.task.display)
        assertTrue(DrillSolved.key(opened.task) in clean.solved)
    }

    /**
     * The reversed ladder is finite — nineteen names and their mix — so answering it out
     * ends the run on its summary, every question asked exactly once.
     */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        var run = open(reverse = true, level = 3)
        val asked = mutableSetOf<String>()
        while (!run.finished) {
            asked += DrillSolved.key(run.task)
            run = run.answered(run.task.display)
        }
        assertEquals(19, asked.size, "every name was asked exactly once before the run ran out")
        assertEquals(asked.size, run.done)
    }

    @Test
    fun theWayOutIsOfferedOnTheSecondMissInARow() {
        val first = open().reduce(DateDrillIntent.Reveal).state
        assertFalse(first.offersFinish, "one miss is not yet a run worth leaving")

        val second = first.reduce(DateDrillIntent.ConfirmPending).state
            .reduce(DateDrillIntent.Reveal).state
        assertTrue(second.offersFinish)
    }

    // MARK: - Leaving

    @Test
    fun anUntouchedRunReportsNothingButStillNamesItsSprosse() {
        val closed = DateDrillRun.close(open(), standingRecord = 0)
        assertNull(closed.summary)
        assertEquals(1, closed.bestLevel)
        assertTrue(closed.state.finished)
        assertEquals(listOf(DrillEffect.CancelAdvance, DrillEffect.Silence), closed.effects)
    }

    /** Closing may neither lose a pending answer nor upgrade it. */
    @Test
    fun aPendingAnswerBooksOnTheWayOutExactlyAsTheTapWould() {
        val opened = open()
        val clean = opened.reduce(DateDrillIntent.Submit(opened.task.display)).state
        val closedClean = DateDrillRun.close(clean, standingRecord = 0)
        val summary = assertNotNull(closedClean.summary)
        assertEquals(1, summary.done)
        assertEquals(1, summary.bestStreak)
        assertTrue(summary.newRecord, "a first streak beats a standing record of none")

        val held = opened.reduce(DateDrillIntent.Submit(opened.slipped())).state
        assertEquals(listOf(AnswerOutcome.Almost), DateDrillRun.close(held, standingRecord = 0).state.outcomes)

        val revealed = opened.reduce(DateDrillIntent.Reveal).state
        assertNull(
            DateDrillRun.close(revealed, standingRecord = 0).summary,
            "a revealed answer nobody confirmed books nothing",
        )
    }

    /** The close reports the Sprosse the run REACHED, which is what the page files. */
    @Test
    fun theCloseReportsTheSprosseTheRunStoodOn() {
        var run = open(level = 3)
        repeat(DateDrill.WINS_TO_ADVANCE) { run = run.answered(run.task.display) }
        run = run.missed()
        assertEquals(4, DateDrillRun.close(run, standingRecord = 0).bestLevel)
    }

    /** The record write is strictly greater, so a run that only equaled it claims nothing. */
    @Test
    fun aStandingRecordIsOnlyBeatenStrictly() {
        val opened = open()
        val run = opened.answered(opened.task.display)
        assertFalse(assertNotNull(DateDrillRun.close(run, standingRecord = 1).summary).newRecord)
        assertTrue(assertNotNull(DateDrillRun.close(run, standingRecord = 0).summary).newRecord)
    }
}

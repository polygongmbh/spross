package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The verdict ladder every typed drill climbs, pinned once here;
 * the run tests check only that their intents reach it.
 */
class TypedDrillVerdictsTest {

    private val almost = TurnFeedback.Almost("Ujerumani", AlmostReason.Typo)

    // MARK: - A keystroke

    /** Finishing the answer IS the answer: it approves itself on the live beat, chiming once. */
    @Test
    fun anExactAnswerApprovesLive() {
        val verdict = assertNotNull(TypedDrillVerdicts.typed(TurnFeedback.Neutral) { true })
        assertEquals(TurnFeedback.Correct, verdict.feedback)
        assertEquals(
            listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ArmAdvance(AdvanceBeat.Live)),
            verdict.effects,
        )
    }

    /** A keystroke inside an approved answer keeps the beat but does not re-chime. */
    @Test
    fun aKeystrokeInsideAnApprovedAnswerDoesNotReChime() {
        val verdict = assertNotNull(TypedDrillVerdicts.typed(TurnFeedback.Correct) { true })
        assertEquals(TurnFeedback.Correct, verdict.feedback)
        assertEquals(listOf(DrillEffect.ArmAdvance(AdvanceBeat.Live)), verdict.effects)
    }

    /** Typing past a finished answer takes the approval back, so it is never booked. */
    @Test
    fun anUnfinishedAnswerWithdrawsTheApproval() {
        val verdict = assertNotNull(TypedDrillVerdicts.typed(TurnFeedback.Correct) { false })
        assertEquals(TurnFeedback.Neutral, verdict.feedback)
        assertEquals(listOf(DrillEffect.CancelAdvance), verdict.effects)
    }

    /** A showing pause is the learner's to confirm, so typing on neither grades nor overwrites it. */
    @Test
    fun aPauseIgnoresKeystrokesWithoutGrading() {
        var graded = false
        assertNull(TypedDrillVerdicts.typed(almost) { graded = true; true })
        assertNull(TypedDrillVerdicts.typed(TurnFeedback.Revealed) { graded = true; true })
        assertFalse(graded)
    }

    // MARK: - A submit and a look-up

    @Test
    fun anExactSubmitArmsTheExplicitBeat() {
        val verdict = TypedDrillVerdicts.submit(Match.Exact)
        assertEquals(TurnFeedback.Correct, verdict.feedback)
        assertEquals(DrillEffect.ArmAdvance(AdvanceBeat.Explicit), verdict.effects.last())
        assertTrue(DrillEffect.Tone(ToneKind.Correct) in verdict.effects)
    }

    /** A slip holds on its spelling and gives the keyboard back for the tap it waits on. */
    @Test
    fun aSlipHoldsAlmostWithoutABeat() {
        val verdict = TypedDrillVerdicts.submit(Match.Typo("Ujerumani"))
        assertEquals(almost, verdict.feedback)
        assertTrue(DrillEffect.ReleaseFocus in verdict.effects)
        assertTrue(DrillEffect.Tone(ToneKind.Almost) in verdict.effects)
        assertTrue(verdict.effects.none { it is DrillEffect.ArmAdvance })
    }

    /** A refusal reveals, and carries the entry the answer really named where there is one. */
    @Test
    fun aRefusalRevealsAndNamesWhatTheAnswerWas() {
        val other = Match.OtherWord("Uhispania", listOf("Spanien"))
        val named = TypedDrillVerdicts.submit(other)
        assertEquals(TurnFeedback.Revealed, named.feedback)
        assertEquals(other, named.otherWord)
        assertTrue(DrillEffect.Tone(ToneKind.Wrong) in named.effects)
        assertNull(TypedDrillVerdicts.submit(Match.Wrong).otherWord)
    }

    @Test
    fun aLookUpRevealsWithItsOwnTone() {
        val verdict = TypedDrillVerdicts.reveal()
        assertEquals(TurnFeedback.Revealed, verdict.feedback)
        assertTrue(DrillEffect.Tone(ToneKind.Reveal) in verdict.effects)
    }

    /** A verdict silences what is playing, unless the run keeps its answers unsaid anyway. */
    @Test
    fun aVerdictSilencesOnlyWhereTheRunAsksIt() {
        assertTrue(DrillEffect.Silence in TypedDrillVerdicts.submit(Match.Exact).effects)
        assertTrue(DrillEffect.Silence in TypedDrillVerdicts.reveal().effects)
        assertFalse(DrillEffect.Silence in TypedDrillVerdicts.submit(Match.Exact, silence = false).effects)
        assertFalse(DrillEffect.Silence in TypedDrillVerdicts.reveal(silence = false).effects)
    }

    // MARK: - What the ramp books

    /** The confirming tap books a clean win, a held slip, or a reveal as a miss. */
    @Test
    fun theConfirmingTapBooksWhatTheTurnShows() {
        assertEquals(DrillBooking(correct = true, clean = true), TypedDrillVerdicts.confirmed(TurnFeedback.Correct))
        assertEquals(DrillBooking(correct = true, clean = false), TypedDrillVerdicts.confirmed(almost))
        assertEquals(DrillBooking(correct = false, clean = true), TypedDrillVerdicts.confirmed(TurnFeedback.Revealed))
        assertNull(TypedDrillVerdicts.confirmed(TurnFeedback.Neutral))
    }

    /** Closing books an accepted answer as the tap would, and never a reveal. */
    @Test
    fun aCloseBooksOnlyAnAcceptedAnswer() {
        assertEquals(TypedDrillVerdicts.confirmed(TurnFeedback.Correct), TypedDrillVerdicts.pending(TurnFeedback.Correct))
        assertEquals(TypedDrillVerdicts.confirmed(almost), TypedDrillVerdicts.pending(almost))
        assertNull(TypedDrillVerdicts.pending(TurnFeedback.Revealed))
    }

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    @Test
    fun anElapsedBeatBooksOnlyACleanAnswer() {
        assertEquals(DrillBooking(correct = true, clean = true), TypedDrillVerdicts.elapsed(TurnFeedback.Correct))
        assertNull(TypedDrillVerdicts.elapsed(almost))
        assertNull(TypedDrillVerdicts.elapsed(TurnFeedback.Revealed))
    }
}

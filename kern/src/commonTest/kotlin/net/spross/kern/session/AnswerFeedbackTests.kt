package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.catalog.Chime
import net.spross.kern.design.Palette

/**
 * What an answer's verdict looks, sounds and feels like, read alike by both phones.
 * That a near miss arms no beat is the machines' own (`TurnTest`, `TypedDrillVerdictsTest`).
 */
class AnswerFeedbackTests {

    @Test
    fun aRightAnswersFeedbackIsTheSubtlest() {
        val quietest = ToneKind.entries.minBy { it.chime.levelDb }
        assertEquals(ToneKind.Correct, quietest)
        assertTrue(ToneKind.Correct.chime.levelDb < ToneKind.Wrong.chime.levelDb)
        assertEquals(listOf(ToneKind.Wrong), ToneKind.entries.filter { it.taps })
    }

    @Test
    fun aNearMissSoundsItsOwnChime() {
        assertEquals(Chime.Almost, ToneKind.Almost.chime)
    }

    @Test
    fun aNearMissRunsAmberAndGreenIsTheCleanAnswersAlone() {
        for (reason in AlmostReason.entries) {
            val nearMiss = FieldVerdict.of(TurnFeedback.Almost("kisu", reason))
            assertEquals(Palette.amber, nearMiss.ink)
            assertTrue(nearMiss.check)
        }
        assertEquals(listOf(FieldVerdict.Clean), FieldVerdict.entries.filter { it.ink == Palette.success })
        assertEquals(listOf(AnswerOutcome.Right), AnswerOutcome.entries.filter { it.ink == Palette.success })
        assertEquals(Palette.amber, AnswerOutcome.Almost.ink)
    }

    /** Either the card opens onto the answer or the field marks it accepted — never both, never in the field. */
    @Test
    fun theAnswerIsNeverOnScreenTwice() {
        val asking = TurnFixture.produce(TurnFixture.knife)
        val clean = TurnFixture.state(asking, TurnIntent.Submit("kisu"))
        val slip = TurnFixture.state(asking, TurnIntent.Submit("kisuu"))
        val miss = TurnFixture.state(asking, TurnIntent.Submit("mbwa"))

        assertEquals(FieldVerdict.Clean, FieldVerdict.of(clean.controls.fieldFeedback))
        assertFalse(clean.question.opens)

        assertEquals(FieldVerdict.NearMiss, FieldVerdict.of(slip.controls.fieldFeedback))
        assertFalse(slip.question.opens)

        val shown = FieldVerdict.of(miss.controls.fieldFeedback)
        assertTrue(miss.question.opens)
        assertFalse(shown.check)
        val primed = TurnFixture.step(asking, TurnIntent.Submit("mbwa")).effects.filterIsInstance<TurnEffect.PrimeField>()
        assertTrue(primed.none { "kisu" in it.text })
    }
}

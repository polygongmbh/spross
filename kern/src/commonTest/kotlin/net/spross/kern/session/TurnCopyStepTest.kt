package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating

/**
 * The write-it-out step: which misses ask for it, that it holds the rating rather than
 * grading it, and that no miss ever pays for two write-outs.
 */
class TurnCopyStepTest {

    @Test
    fun aMissedWordIsWrittenOutBeforeTheTurnEnds() {
        val opened = TurnFixture.step(revealedLanguage(), TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown))
        assertEquals(CopyStep(Rating.Again, missed = false, written = false), opened.state.copyStep)
        // Nothing is booked yet — the buttons decided, the writing has not happened.
        assertTrue(opened.effects.isEmpty())

        val written = TurnFixture.step(opened.state, TurnIntent.InputChanged("lugha"))
        assertEquals(true, written.state.copyStep?.written)
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Live)),
            written.effects,
        )

        // The rating the buttons chose, applied unchanged: encoding, never a grade.
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Again)),
            TurnFixture.step(written.state, TurnIntent.AdvanceElapsed).effects,
        )
    }

    @Test
    fun enterForgivesASlipTheLiveWritingDoesNot() {
        val opened = openWriteOut()
        val short = TurnFixture.step(opened, TurnIntent.InputChanged("lugh"))
        // One edit short: inside the typo budget, so the live path must not fire on it.
        assertEquals(false, short.state.copyStep?.written)
        assertTrue(short.effects.isEmpty())

        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.Answer(Rating.Again)),
            TurnFixture.step(short.state, TurnIntent.CopySubmit("lugh")).effects,
        )
    }

    @Test
    fun backingOutOfAWrittenWordTakesTheGreenWithIt() {
        val written = TurnFixture.state(openWriteOut(), TurnIntent.InputChanged("lugha"))
        val backed = TurnFixture.step(written, TurnIntent.InputChanged("lugh"))
        assertEquals(false, backed.state.copyStep?.written)
        assertEquals(listOf(TurnEffect.CancelAdvance), backed.effects)
        assertTrue(TurnFixture.step(backed.state, TurnIntent.AdvanceElapsed).effects.isEmpty())
    }

    @Test
    fun aDifferentWordDoesNotPassForTheCopy() {
        val missed = TurnFixture.step(openWriteOut(), TurnIntent.CopySubmit("neno"))
        assertEquals(true, missed.state.copyStep?.missed)
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Wrong)), missed.effects)

        // Writing it properly clears the hint and arms the beat.
        val written = TurnFixture.step(missed.state, TurnIntent.InputChanged("lugha"))
        assertEquals(false, written.state.copyStep?.missed)
        assertEquals(true, written.state.copyStep?.written)
    }

    @Test
    fun theWriteOutCanAlwaysBeLeft() {
        // A step you cannot leave is a trap, and the rating is already decided.
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Again)),
            TurnFixture.step(openWriteOut(), TurnIntent.SkipCopy).effects,
        )
    }

    @Test
    fun aFirstExposureMissIsWrittenOutAsTheWordIsMet() {
        val revealed = TurnFixture.state(
            TurnFixture.recognize(TurnFixture.language, firstExposure = true),
            TurnIntent.Reveal, TurnFixture.T0 + 2_000,
        )
        val opened = TurnFixture.step(revealed, TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown))
        assertEquals(Rating.Again, opened.state.copyStep?.pendingRating)
        assertTrue(opened.effects.isEmpty())
    }

    @Test
    fun aLaterRecognitionMissBooksStraightAway() {
        // The target has stood in the prompt since the first frame: transcribing it teaches
        // nothing the reading did not, and the next review asks properly.
        val revealed = TurnFixture.state(
            TurnFixture.recognize(TurnFixture.language), TurnIntent.Reveal, TurnFixture.T0 + 2_000,
        )
        val graded = TurnFixture.step(revealed, TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown))
        assertNull(graded.state.copyStep)
        assertEquals(listOf(TurnEffect.Answer(Rating.Again)), graded.effects)
    }

    @Test
    fun aWordThatAlreadySticksIsNeverSlowedDown() {
        val revealed = TurnFixture.state(
            TurnFixture.produce(TurnFixture.language, settled = true),
            TurnIntent.Reveal, TurnFixture.T0 + 2_000,
        )
        val graded = TurnFixture.step(revealed, TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown))
        assertNull(graded.state.copyStep)
        assertEquals(listOf(TurnEffect.Answer(Rating.Again)), graded.effects)
    }

    @Test
    fun givingUpOnARetryIsOneWriteOutNotTwo() {
        // Production, unsettled, an honest Again — everything the write-out asks for,
        // except that the retry field already WAS the write-out.
        val missed = TurnFixture.state(TurnFixture.produce(TurnFixture.language), TurnIntent.Submit("neno"))
        val gone = TurnFixture.step(missed, TurnIntent.GiveUp)
        assertNull(gone.state.copyStep)
        assertEquals(listOf(TurnEffect.Answer(Rating.Again)), gone.effects)
    }

    @Test
    fun aSoundPromptedMissIsWrittenOutLikeAnyOther() {
        val revealed = TurnFixture.state(
            TurnFixture.produce(TurnFixture.car, prompt = ProducePrompt.Sound),
            TurnIntent.Reveal, TurnFixture.T0 + 2_000,
        )
        val opened = TurnFixture.step(revealed, TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown))
        assertEquals(Rating.Again, opened.state.copyStep?.pendingRating)
        // Only the target is ever copied, so the played form is what stands to be written.
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Live)),
            TurnFixture.step(opened.state, TurnIntent.InputChanged("gari")).effects,
        )
    }

    /** A produce card revealed without typing: the self-grade path, 3 s of recall spent. */
    private fun revealedLanguage(): TurnState = TurnFixture.state(
        TurnFixture.produce(TurnFixture.language), TurnIntent.Reveal, TurnFixture.T0 + 3_000,
    )

    private fun openWriteOut(): TurnState =
        TurnFixture.state(revealedLanguage(), TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown))
}

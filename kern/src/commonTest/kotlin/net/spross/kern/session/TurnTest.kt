package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating

/**
 * The turn: what each answer is worth, when a beat is armed, and what the reveal opens.
 * The write-out step it can divert into has its own suite (`TurnCopyStepTest`).
 */
class TurnTest {

    @Test
    fun finishingTheWordIsTheAnswer() {
        val typed = TurnFixture.step(TurnFixture.produce(TurnFixture.knife), TurnIntent.InputChanged("kisu"))
        assertEquals(TurnFeedback.Correct, typed.state.feedback)
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Live)),
            typed.effects,
        )
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Good)),
            TurnFixture.step(typed.state, TurnIntent.AdvanceElapsed).effects,
        )
    }

    @Test
    fun anExplicitCheckGetsTheLongerBeat() {
        val submitted = TurnFixture.step(TurnFixture.produce(TurnFixture.knife), TurnIntent.Submit("kisu"))
        assertEquals(TurnFeedback.Correct, submitted.state.feedback)
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Explicit)),
            submitted.effects,
        )
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Good)),
            TurnFixture.step(submitted.state, TurnIntent.AdvanceElapsed).effects,
        )
    }

    @Test
    fun backingOutOfAFinishedWordTakesTheGreenWithIt() {
        val green = TurnFixture.state(TurnFixture.produce(TurnFixture.knife), TurnIntent.InputChanged("kisu"))
        val backed = TurnFixture.step(green, TurnIntent.InputChanged("kis"))
        assertEquals(TurnFeedback.Neutral, backed.state.feedback)
        assertEquals(listOf(TurnEffect.CancelAdvance), backed.effects)
        // The beat it cancelled cannot book afterwards either.
        assertTrue(TurnFixture.step(backed.state, TurnIntent.AdvanceElapsed).effects.isEmpty())
    }

    @Test
    fun aTypoHoldsOnItsCorrectionAndBooksHard() {
        val held = TurnFixture.step(TurnFixture.produce(TurnFixture.knife), TurnIntent.Submit("kisuu"))
        assertEquals(TurnFeedback.Almost("kisu", AlmostReason.Typo), held.state.feedback)
        assertEquals(Rating.Hard, held.state.pendingRating)
        // No beat: the pause is the point, and it hands the keyboard back.
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ReleaseFocus), held.effects)

        val done = TurnFixture.step(held.state, TurnIntent.ConfirmPending)
        assertEquals(listOf(TurnEffect.Answer(Rating.Hard)), done.effects)
        // Hard is not Again, so nothing is written out.
        assertNull(done.state.copyStep)
    }

    @Test
    fun anotherConceptsWordRevealsAndKeepsTheWordsAlreadyRight() {
        val missed = TurnFixture.step(TurnFixture.produce(TurnFixture.open), TurnIntent.Submit("kufunga"))
        assertEquals(TurnFeedback.Revealed, missed.state.feedback)
        assertEquals(Match.OtherWord("kufunga", listOf("schließen")), missed.state.otherWord)
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Wrong), TurnEffect.PrimeField("kufunga ")),
            missed.effects,
        )
    }

    @Test
    fun aMissDropsTheWrongTailFromTheRetry() {
        val missed = TurnFixture.step(TurnFixture.produce(TurnFixture.language), TurnIntent.Submit("neno"))
        assertEquals(TurnFeedback.Revealed, missed.state.feedback)
        assertNull(missed.state.otherWord)
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Wrong), TurnEffect.PrimeField("")), missed.effects)
    }

    @Test
    fun finishingTheRetypeIsRecalledWithHelp() {
        val retyped = TurnFixture.step(missedLanguage(), TurnIntent.InputChanged("lugha"))
        assertTrue(retyped.state.retryApproved)
        // The card holds its reveal open while the field turns right.
        assertEquals(TurnFeedback.Revealed, retyped.state.feedback)
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Live)),
            retyped.effects,
        )

        val done = TurnFixture.step(retyped.state, TurnIntent.AdvanceElapsed)
        assertEquals(listOf(TurnEffect.Answer(Rating.Hard)), done.effects)
        assertNull(done.state.copyStep)
    }

    @Test
    fun backingOutOfAFinishedRetypeTakesItsParkedRatingWithIt() {
        val retyped = TurnFixture.state(missedLanguage(), TurnIntent.InputChanged("lugha"))
        val backed = TurnFixture.step(retyped, TurnIntent.InputChanged("lugh"))
        assertFalse(backed.state.retryApproved)
        assertEquals(listOf(TurnEffect.CancelAdvance), backed.effects)
        assertTrue(TurnFixture.step(backed.state, TurnIntent.AdvanceElapsed).effects.isEmpty())
    }

    @Test
    fun theExplicitButtonBooksWhatTheBeatWouldHave() {
        // Where a screen reader runs no timer ever arms, so the button that replaces it
        // must land on the same rating rather than a literal of its own.
        val green = TurnFixture.state(TurnFixture.produce(TurnFixture.knife), TurnIntent.InputChanged("kisu"))
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Good)),
            TurnFixture.step(green, TurnIntent.ConfirmPending).effects,
        )

        val retyped = TurnFixture.state(missedLanguage(), TurnIntent.InputChanged("lugha"))
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Hard)),
            TurnFixture.step(retyped, TurnIntent.ConfirmPending).effects,
        )
    }

    @Test
    fun theRecallClockRunsUntilTheAnswerIsAskedFor() {
        val start = TurnFixture.produce(TurnFixture.language)
        val revealed = TurnFixture.step(start, TurnIntent.Reveal, TurnFixture.T0 + 3_000)
        assertEquals(3_000L, revealed.state.recallMs)
        assertTrue(revealed.state.revealed)
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Reveal)), revealed.effects)

        // Asked once: a second ask neither restarts nor re-closes the span.
        val again = TurnFixture.step(revealed.state, TurnIntent.Reveal, TurnFixture.T0 + 9_000)
        assertEquals(3_000L, again.state.recallMs)
        assertTrue(again.effects.isEmpty())

        // Once the buttons are out, typing cannot take the turn back.
        assertTrue(TurnFixture.step(revealed.state, TurnIntent.InputChanged("lugha")).effects.isEmpty())

        // A typed answer never reaches the clock at all — it never reaches self-grading.
        assertEquals(0L, TurnFixture.state(start, TurnIntent.Submit("lugha"), TurnFixture.T0 + 3_000).recallMs)
    }

    @Test
    fun aWordThatCameInstantlyEarnsEasy() {
        // "kisu" is 4 chars, so the instant budget is 1500 + 4 × 40 = 1660 ms.
        val revealed = TurnFixture.state(
            TurnFixture.recognize(TurnFixture.knife), TurnIntent.Reveal, TurnFixture.T0 + 1_300,
        )
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Easy)),
            TurnFixture.step(revealed, TurnIntent.SelfGrade(SelfGrading.Verdict.Knew)).effects,
        )
    }

    @Test
    fun aSlowOrUnmeasuredRecallStaysGood() {
        val slow = TurnFixture.state(
            TurnFixture.recognize(TurnFixture.knife), TurnIntent.Reveal, TurnFixture.T0 + 5_000,
        )
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Good)),
            TurnFixture.step(slow, TurnIntent.SelfGrade(SelfGrading.Verdict.Knew)).effects,
        )

        val untimed = TurnFixture.state(TurnFixture.recognize(TurnFixture.knife), TurnIntent.Reveal)
        assertEquals(0L, untimed.recallMs)
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Good)),
            TurnFixture.step(untimed, TurnIntent.SelfGrade(SelfGrading.Verdict.Knew)).effects,
        )
    }

    @Test
    fun theClockOnlyEverUpgradesAKnew() {
        val instant = TurnFixture.state(
            TurnFixture.recognize(TurnFixture.knife), TurnIntent.Reveal, TurnFixture.T0 + 200,
        )
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Hard)),
            TurnFixture.step(instant, TurnIntent.SelfGrade(SelfGrading.Verdict.Tough)).effects,
        )
    }

    @Test
    fun aSynonymOfWhatPlayedIsAmberNeverWrong() {
        val heard = TurnFixture.step(byEar(), TurnIntent.Submit("motokaa"))
        assertEquals(TurnFeedback.Almost("gari", AlmostReason.Heard), heard.state.feedback)
        assertEquals(Rating.Hard, heard.state.pendingRating)
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ReleaseFocus), heard.effects)
        assertEquals(
            listOf(TurnEffect.Answer(Rating.Hard)),
            TurnFixture.step(heard.state, TurnIntent.ConfirmPending).effects,
        )
    }

    @Test
    fun theFormThatPlayedIsExactEvenWhereTheCardAlsoListsIt() {
        // "Gari" is one of the card's own variants, so the heard rule would have matched too —
        // being exactly what played wins.
        val exact = TurnFixture.step(byEar(), TurnIntent.Submit("gari"))
        assertEquals(TurnFeedback.Correct, exact.state.feedback)
        assertEquals(
            listOf(TurnEffect.Tone(ToneKind.Correct), TurnEffect.ArmAdvance(AdvanceTier.Explicit)),
            exact.effects,
        )
    }

    @Test
    fun aWordThatNeverPlayedStillRevealsAndPrimes() {
        val taken = TurnFixture.step(byEar(), TurnIntent.Submit("kisu"))
        assertEquals(TurnFeedback.Revealed, taken.state.feedback)
        assertEquals(Match.OtherWord("kisu", listOf("Messer")), taken.state.otherWord)
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Wrong), TurnEffect.PrimeField("")), taken.effects)

        val nowhere = TurnFixture.step(byEar(), TurnIntent.Submit("zzznope"))
        assertEquals(TurnFeedback.Revealed, nowhere.state.feedback)
        assertNull(nowhere.state.otherWord)
        assertEquals(listOf(TurnEffect.Tone(ToneKind.Wrong), TurnEffect.PrimeField("")), nowhere.effects)
    }

    @Test
    fun theEarComparesSpeechNotSpelling() {
        // The stem dash and the citation punctuation are spelling; what is said is the same word.
        val nice = TurnFixture.card("nice", "schön", "zuri", CardKind.Adjective, synonyms = listOf("-Zuri!"))
        assertTrue(alsoAccepts(nice, "zuri"))
        assertFalse(alsoAccepts(nice, "mbaya"))
    }

    @Test
    fun aSubmitOffAnOpenTurnBooksNothing() {
        val green = TurnFixture.state(TurnFixture.produce(TurnFixture.knife), TurnIntent.InputChanged("kisu"))
        val doubled = TurnFixture.step(green, TurnIntent.Submit("kisu"))
        assertEquals(green, doubled.state)
        assertTrue(doubled.effects.isEmpty())

        val held = TurnFixture.state(TurnFixture.produce(TurnFixture.knife), TurnIntent.Submit("kisuu"))
        assertTrue(TurnFixture.step(held, TurnIntent.Submit("kisu")).effects.isEmpty())

        val revealed = TurnFixture.state(TurnFixture.produce(TurnFixture.language), TurnIntent.Reveal)
        assertTrue(TurnFixture.step(revealed, TurnIntent.Submit("lugha")).effects.isEmpty())
    }

    @Test
    fun recognitionIsNeverGradedFromText() {
        // A comprehension check is self-graded, so no schedule is graded against a language
        // it was not learned with — text reaching this turn decides nothing.
        val start = TurnFixture.recognize(TurnFixture.knife)
        assertEquals(start, TurnFixture.state(start, TurnIntent.InputChanged("kisu")))
        assertEquals(start, TurnFixture.state(start, TurnIntent.Submit("kisu")))
    }

    /** A produce miss on a word not yet consolidated: the field stays open, the answer stands on the card. */
    private fun missedLanguage(): TurnState =
        TurnFixture.state(TurnFixture.produce(TurnFixture.language), TurnIntent.Submit("neno"))

    /** The sound-prompted produce turn: "gari" played, nothing on screen. */
    private fun byEar(): TurnState =
        TurnFixture.produce(TurnFixture.car, prompt = ProducePrompt.Sound)
}

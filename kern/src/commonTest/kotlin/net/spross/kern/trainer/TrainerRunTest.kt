package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * What an answer does to a slot run: how it is graded, what it moves on the ramp, what the
 * almost rules cost it, when the run offers its way out, and what a close leaves behind.
 *
 * The ladder itself (`DrillUnlocks`, `DrillRamp`) is pinned by `DrillProgressionTests` and the
 * selection by `TrainerModeTest`; what is asserted here is the RUN that steps through them.
 */
class TrainerRunTest {

    private val de = LanguageInfo(code = "de", name = "Deutsch", englishName = "German", flag = "🇩🇪")

    /** The strictness triple every drill grades with: no article leniency, one slip per word. */
    private val normalizer = AnswerNormalizer(de, articleLeniency = false, maxTyposPerWord = 1)

    private fun numbers(language: String = "de") = TrainerMode(DrillVariant.Numbers, language)

    private fun reduce(state: TrainerRunState, intent: TrainerIntent, rng: Random) =
        TrainerRun.reduce(state, intent, normalizer, rng)

    private fun answerRight(state: TrainerRunState, rng: Random): TrainerRunState {
        val answer = state.currentTask.accepted.first()
        val submitted = reduce(state, TrainerIntent.Submit(answer), rng).state
        return reduce(submitted, TrainerIntent.ConfirmPending, rng).state
    }

    private fun miss(state: TrainerRunState, rng: Random): TrainerRunState {
        val revealed = reduce(state, TrainerIntent.Reveal, rng).state
        return reduce(revealed, TrainerIntent.ConfirmPending, rng).state
    }

    // MARK: - The draw

    @Test
    fun everyRunStartsAtRungOneHoweverFarTheLearnerHasClimbed() {
        val mode = TrainerMode(listOf(DrillVariant.Numbers, DrillVariant.Clock), "de", emptySet())
        val state = TrainerRun.open(mode, Random(11))
        assertEquals(mapOf(DrillVariant.Numbers to 1, DrillVariant.Clock to 1), state.levels)
        assertEquals(0, state.done)
        assertEquals(TurnFeedback.Neutral, state.feedback)
    }

    /**
     * The variant pick, the direction flip and the value all spend ONE rng, so a seeded run is
     * reproducible end to end rather than three-quarters of the way.
     */
    @Test
    fun oneSeedDrawsOneRun() {
        val picks = listOf(DrillVariant.Numbers, DrillVariant.Clock)
        val mode = TrainerMode(picks, "de", setOf(DrillModifier.Mix))
        fun play(): List<DrawnTask> {
            val rng = Random(5)
            var state = TrainerRun.open(mode, rng)
            val drawn = mutableListOf(state.current)
            repeat(12) {
                state = answerRight(state, rng)
                drawn += state.current
            }
            return drawn
        }
        assertEquals(play(), play())
    }

    // MARK: - Grading

    /**
     * A reversed task reuses the forward path unchanged: the accepted set already carries the
     * notation twins, and a digit-bearing word grades exact-only, so no separator forgives a
     * different time.
     */
    @Test
    fun aReversedTaskGradesThroughTheSameNormalizer() {
        val clock = Trainer.reversed(Trainer.clock(18, 5, "de"))
        assertEquals(Match.Exact, TrainerRun.grade("18:05", clock, normalizer))
        assertEquals(Match.Wrong, TrainerRun.grade("18:06", clock, normalizer))

        val number = Trainer.reversed(Trainer.number(12345, "de"))
        assertEquals(Match.Exact, TrainerRun.grade("12345", number, normalizer))
        // The grouped form is written with a narrow no-break space; an ordinary one still matches.
        assertEquals(Match.Exact, TrainerRun.grade("12 345", number, normalizer))
    }

    /** Previews carry no language info; a run without a normalizer still grades exactly. */
    @Test
    fun aRunWithoutAGraderFallsBackToAPlainComparison() {
        val task = Trainer.number(7, "de")
        assertEquals(Match.Exact, TrainerRun.grade(task.display.uppercase(), task, null))
        assertEquals(Match.Wrong, TrainerRun.grade("acht", task, null))
    }

    /**
     * "son las nueve" is both a finished answer and the first half of a longer one — a field
     * that confirms itself on the shorter reading takes the fuller answer away.
     */
    @Test
    fun anAnswerStillGrowingIntoALongerOneDoesNotConfirmItself() {
        val task = TrainerTask(
            kind = TrainerKind.Clock,
            language = "es",
            prompt = "21:00",
            accepted = listOf("son las nueve de la noche", "son las nueve"),
            display = "son las nueve de la noche",
        )
        assertTrue(TrainerRun.stillGrowing("son las nueve", task))
        // The reading the reveal TEACHES always confirms on its own.
        assertFalse(TrainerRun.stillGrowing("son las nueve de la noche", task))
        assertFalse(TrainerRun.stillGrowing("", task))
    }

    @Test
    fun finishingTheWordIsTheAnswerAndEditingItBackWithdrawsTheApproval() {
        val rng = Random(31)
        val state = TrainerRun.open(numbers(), rng)
        val answer = state.currentTask.accepted.first()
        val live = reduce(state, TrainerIntent.InputChanged(answer), rng)
        assertEquals(TurnFeedback.Correct, live.state.feedback)
        assertTrue(DrillEffect.ArmAdvance(AdvanceTier.Live) in live.effects)

        val edited = reduce(live.state, TrainerIntent.InputChanged(answer.dropLast(1)), rng)
        assertEquals(TurnFeedback.Neutral, edited.state.feedback)
        assertTrue(DrillEffect.CancelAdvance in edited.effects)
    }

    // MARK: - The ramp inside a run

    @Test
    fun twoCleanWinsClimbTheAskingVariantAndAMissStepsItBack() {
        val rng = Random(7)
        var state = TrainerRun.open(numbers(), rng)
        state = answerRight(state, rng)
        assertEquals(1, state.currentLevel)
        assertEquals(1, state.winsAtLevel[DrillVariant.Numbers])
        assertEquals(listOf(AnswerOutcome.Right), state.outcomes)
        assertEquals(1, state.streak)

        state = answerRight(state, rng)
        assertEquals(2, state.currentLevel)
        assertEquals(2, state.done)

        state = miss(state, rng)
        assertEquals(1, state.currentLevel)
        assertEquals(0, state.streak)
        assertEquals(1, state.missRun)
        assertEquals(AnswerOutcome.Wrong, state.outcomes.last())
    }

    /**
     * A look-up while the answer is still owed books the task almost: the streak carries on, the
     * rung banks nothing. After the answer is in, nothing is owed and reading is free.
     */
    @Test
    fun readingTheReferenceWhileTheAnswerIsOwedCostsTheRung() {
        val rng = Random(13)
        var state = TrainerRun.open(numbers(), rng)
        assertTrue(state.offersLookUp)
        state = reduce(state, TrainerIntent.LookUp, rng).state
        assertTrue(state.hintUsed)

        val submitted = reduce(state, TrainerIntent.Submit(state.currentTask.accepted.first()), rng)
        assertEquals(TurnFeedback.Correct, submitted.state.feedback)
        val booked = reduce(submitted.state, TrainerIntent.ConfirmPending, rng).state
        assertEquals(listOf(AnswerOutcome.Almost), booked.outcomes)
        assertEquals(1, booked.currentLevel)
        assertEquals(0, booked.winsAtLevel[DrillVariant.Numbers])
        assertEquals(1, booked.streak, "almost extends the streak")
        assertFalse(booked.hintUsed, "the debt is cleared with the question")

        val revealed = reduce(TrainerRun.open(numbers(), rng), TrainerIntent.Reveal, rng).state
        assertFalse(reduce(revealed, TrainerIntent.LookUp, rng).state.hintUsed)
    }

    /** The same almost, taken through the live approve rather than the explicit check. */
    @Test
    fun aHintAssistedLiveApproveBooksAlmostToo() {
        val rng = Random(17)
        var state = TrainerRun.open(numbers(), rng)
        state = reduce(state, TrainerIntent.LookUp, rng).state
        state = reduce(state, TrainerIntent.InputChanged(state.currentTask.accepted.first()), rng).state
        state = reduce(state, TrainerIntent.AdvanceElapsed, rng).state
        assertEquals(listOf(AnswerOutcome.Almost), state.outcomes)
    }

    /** A slip pauses instead of moving on, and the tap that ends the pause books it almost. */
    @Test
    fun aSlipHoldsTheAnswerAndBooksAlmost() {
        val rng = Random(19)
        val state = TrainerRun.open(numbers(), rng)
        val held = state.copy(feedback = TurnFeedback.Almost("sieben", AlmostReason.Typo))
        assertTrue(held.answerAccepted)
        assertFalse(held.showsAnswer, "the correction box already spells it out")
        val booked = reduce(held, TrainerIntent.ConfirmPending, rng).state
        assertEquals(listOf(AnswerOutcome.Almost), booked.outcomes)
        assertEquals(1, booked.currentLevel)
    }

    /** A miss reveals; a drill has no "Wusste ich", so confirming it simply counts as a miss. */
    @Test
    fun aRevealedAnswerCarriesTheCardAndBooksAMiss() {
        val rng = Random(23)
        val state = TrainerRun.open(numbers(), rng)
        val revealed = reduce(state, TrainerIntent.Reveal, rng)
        assertTrue(revealed.state.showsAnswer)
        assertTrue(DrillEffect.Tone(ToneKind.Reveal) in revealed.effects)
        // The beat never arms on a reveal, so nothing may ride it.
        assertEquals(revealed.state, reduce(revealed.state, TrainerIntent.AdvanceElapsed, rng).state)
        val booked = reduce(revealed.state, TrainerIntent.ConfirmPending, rng).state
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
    }

    // MARK: - Asking each prompt once

    /** A prompt answered right is retired: a run never comes back round to it. */
    @Test
    fun aPromptAnsweredRightIsNeverAskedAgain() {
        val rng = Random(41)
        var state = TrainerRun.open(TrainerMode(DrillVariant.Clock, "de"), rng)
        val asked = mutableListOf<String>()
        repeat(20) {
            asked += state.currentTask.prompt
            state = answerRight(state, rng)
        }
        assertEquals(asked.size, asked.toSet().size, "a prompt came round twice: $asked")
    }

    /** A slip and a miss leave the prompt in the pool — only a clean answer retires it. */
    @Test
    fun onlyACleanAnswerRetiresAPrompt() {
        val rng = Random(43)
        val state = TrainerRun.open(numbers(), rng)
        assertEquals(1, answerRight(state, rng).solved.size)
        assertTrue(miss(state, rng).solved.isEmpty())

        val hinted = reduce(state, TrainerIntent.LookUp, rng).state
        assertTrue(answerRight(hinted, rng).solved.isEmpty(), "a look-up books almost, not right")
    }

    /**
     * Ten single digits is the whole of the first numbers rung, so a run that has answered
     * them all is carried past it rather than asked one of them again — and the rung it is
     * carried to is a rung it stood on.
     */
    @Test
    fun aRungWithNothingLeftToAskIsClimbedPast() {
        val rng = Random(47)
        val digits = (0L..9L).map { DrillSolved.key(DrillVariant.Numbers, Trainer.number(it, "de")) }
        val spent = TrainerRun.open(numbers(), rng).copy(solved = digits.toSet())

        val next = answerRight(spent, rng)
        assertEquals(2, next.currentLevel)
        assertEquals(2, next.bestLevels[DrillVariant.Numbers])
        assertEquals(0, next.winsAtLevel[DrillVariant.Numbers], "the wins stay behind with the rung")
        assertEquals(2, next.currentTask.prompt.length, "the second rung asks two digits")
        assertFalse(next.finished)
    }

    // MARK: - The place-value hint

    @Test
    fun eachNumberLengthIsIntroducedOnceAndNeverOnAReversedTask() {
        val rng = Random(29)
        val forward = TrainerRun.open(numbers("sw"), rng).copy(
            current = DrawnTask(DrillVariant.Numbers, Trainer.number(347, "sw"), reversed = false),
            levels = mapOf(DrillVariant.Numbers to 3),
        )
        assertEquals(3, forward.currentDigits)
        assertEquals(Trainer.placeValueHint(3, "sw"), forward.placeValueHint)
        assertNotNull(forward.placeValueHint)

        // Booked once, the length is introduced and never hinted again.
        val submitted = TrainerRun.reduce(
            forward,
            TrainerIntent.Submit(forward.currentTask.accepted.first()),
            null,
            rng,
        ).state
        val booked = TrainerRun.reduce(submitted, TrainerIntent.ConfirmPending, null, rng).state
        assertTrue(3 in booked.seenDigitCounts)
        assertNull(forward.copy(seenDigitCounts = setOf(3)).placeValueHint)

        // A reversed prompt IS the reading, which already names the place.
        val back = forward.copy(
            current = DrawnTask(
                DrillVariant.Numbers,
                Trainer.reversed(Trainer.number(347, "sw")),
                reversed = true,
            ),
        )
        assertNull(back.currentDigits)
        assertNull(back.placeValueHint)
    }

    // MARK: - The way out

    @Test
    fun theSecondMissInARowOffersTheWayOutAndACorrectAnswerTakesItBack() {
        val rng = Random(37)
        var state = TrainerRun.open(numbers(), rng)
        // One miss is what a drill is made of — the first reveal offers nothing.
        assertFalse(reduce(state, TrainerIntent.Reveal, rng).state.offersFinish)

        state = miss(state, rng)
        assertEquals(1, state.missRun)
        assertFalse(state.offersFinish, "the offer stands under a miss, not between questions")
        assertTrue(reduce(state, TrainerIntent.Reveal, rng).state.offersFinish)

        state = answerRight(state, rng)
        assertEquals(0, state.missRun)
        assertFalse(reduce(state, TrainerIntent.Reveal, rng).state.offersFinish)
    }
}

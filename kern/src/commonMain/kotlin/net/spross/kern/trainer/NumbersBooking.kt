package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/** How [NumbersRun] books an answer: the ramp, the tallies, and the next question at the Sprossen it left. */
internal object NumbersBooking {

    /**
     * A challenge books an answer the moment it is graded and asks the next question:
     * nothing is shown, so nothing waits — a shown answer would be a breather against the clock.
     */
    fun raced(state: NumbersRunState, tone: ToneKind, correct: Boolean, clean: Boolean, rng: Random): NumbersReduction =
        booked(state, correct, clean, rng).let { it.copy(effects = listOf(DrillEffect.Tone(tone)) + it.effects) }

    /** Book the answer, then put the next question up at the Sprossen the booking left. */
    fun booked(
        state: NumbersRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): NumbersReduction {
        val next = advanced(state, correct, clean)
        val draw = next.challenge?.drawAt(state.index + 1, next.sprossen)
            ?: next.mode.draw(next.sprossen, state.currentTask.prompt, next.solved, rng)
        return NumbersReduction(
            paced(climbed(next, draw).copy(
                // Nothing left to ask anywhere: end on the summary, never on a repeat.
                current = draw.drawn ?: next.current,
                finished = draw.drawn == null,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next prompt must
                // never render one frame carrying the last one's answer or its almost debt.
                feedback = TurnFeedback.Neutral,
                otherWord = null,
                hintUsed = false,
            )),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    /**
     * The pause a booked answer leaves due, if one is ([DrillPacing]).
     * A timed run ends on its clock and a challenge on its script, so neither pauses.
     */
    private fun paced(state: NumbersRunState): NumbersRunState = state.copy(
        core = state.core.paced(
            // why: a mixed run climbs one ladder per exercise, and no one of them is the run's.
            sprosse = state.mode.exercises.singleOrNull()?.let { state.sprossen[it] },
            newSprossen = state.newSprossen,
            endless = !state.finished && state.challenge == null,
        ),
    )

    /**
     * Adopt the Sprossen the draw climbed to. A Sprosse the run was carried past because its prompts
     * were answered out is a Sprosse it STOOD on, so the close books it like any other; the wins
     * banked on the one below stay behind with it.
     */
    private fun climbed(state: NumbersRunState, draw: NumbersDraw): NumbersRunState {
        val moved = draw.sprossen.filter { (exercise, sprosse) -> sprosse != state.sprossen[exercise] }
        if (moved.isEmpty()) return state
        return state.copy(
            sprossen = draw.sprossen,
            winsAtSprosse = state.winsAtSprosse + moved.map { (exercise, _) -> exercise to 0 },
            bestSprossen = state.bestSprossen + moved.map { (exercise, sprosse) ->
                exercise to maxOf(state.bestSprossen[exercise] ?: 1, sprosse)
            },
        )
    }

    /**
     * The booking itself: the ramp for the exercise that asked, the answer streak, the tallies. The other
     * exercises of a mixed run stand exactly where they were.
     */
    fun advanced(state: NumbersRunState, correct: Boolean, clean: Boolean): NumbersRunState {
        val exercise = state.currentExercise
        val step = DrillRamp.step(
            sprosse = state.currentSprosse,
            winsAtSprosse = state.winsAtSprosse[exercise] ?: 0,
            correct = correct,
            clean = clean,
            winsRequired = state.mode.winsToAdvance,
        )
        return state.copy(
            sprossen = state.sprossen + (exercise to step.sprosse),
            winsAtSprosse = state.winsAtSprosse + (exercise to step.winsAtSprosse),
            bestSprossen = state.bestSprossen + (exercise to maxOf(state.bestSprossen[exercise] ?: 1, step.sprosse)),
            seenDigitCounts = state.currentDigits
                ?.let { state.seenDigitCounts + it }
                ?: state.seenDigitCounts,
            seenFormKeys = state.currentFormKey
                ?.let { state.seenFormKeys + it }
                ?: state.seenFormKeys,
            core = state.core.book(correct, clean, DrillSolved.key(exercise, state.currentTask)),
            score = state.score + TimedRun.points(state.currentSprosse, correct, clean),
            earnedSeconds = state.earnedSeconds + TimedRun.bonusSeconds(state.currentTask, correct, clean),
        )
    }
}

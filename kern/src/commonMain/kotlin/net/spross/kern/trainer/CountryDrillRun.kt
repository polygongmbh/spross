package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerTone
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The atlas drill as pure state plus one reducer — the third sibling of [TrainerRun] and
 * [LetterDrillRun]. The run's shape is [CountryDrillRunState]; what it can ask is
 * [CountryDrill].
 *
 * It types like the slot run — writing the name out IS the answer — and climbs like the
 * letter run, one ladder for the whole run rather than one per variant. That, and the rung
 * it REACHED being what a close reports, is the whole of what it does not share with them;
 * the ramp, the effects and the summary are the same ones.
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed, so
 * none is taken. No default arguments: they do not cross the ObjC boundary.
 */
object CountryDrillRun {

    /** A fresh run. Every run opens at rung 1 however far the learner has climbed. */
    fun open(config: CountryDrillRunConfig, rng: Random): CountryDrillRunState =
        openAt(config, 1, rng)

    /**
     * The same, forced to one rung — what the record buys is the page and never a head
     * start, so this exists for tests and screenshot drivers, which have no other way to
     * reach the outer tiers.
     */
    fun openAt(config: CountryDrillRunConfig, level: Int, rng: Random): CountryDrillRunState {
        val start = level.coerceIn(1, CountryDrill.MAX_LEVEL)
        return CountryDrillRunState(
            config = config,
            task = CountryDrill.sample(config.content, start, config.reverse, null, rng),
            index = 0,
            level = start,
            bestLevel = start,
            winsAtLevel = 0,
            done = 0,
            streak = 0,
            bestStreak = 0,
            missRun = 0,
            outcomes = emptyList(),
            feedback = TurnFeedback.Neutral,
            finished = false,
        )
    }

    fun reduce(
        state: CountryDrillRunState,
        intent: CountryDrillIntent,
        rng: Random,
    ): CountryDrillReduction = when (intent) {
        is CountryDrillIntent.InputChanged -> typed(state, intent.text)
        is CountryDrillIntent.Submit -> submit(state, intent.text)
        CountryDrillIntent.Reveal -> reveal(state)
        CountryDrillIntent.ConfirmPending -> confirm(state, rng)
        CountryDrillIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * Grade [input] against every form the task accepts, the way a drill grades: word by
     * word, one slip per word, no article forgiven — the atlas authors "die Schweiz" and the
     * bare form beside it, so leniency would accept an article the learner never wrote.
     *
     * Never [Match.OtherWord]: the accepted set is wrapped as one synthetic card, so there
     * is no catalog for a neighboring country's name to come out of.
     */
    fun grade(
        input: String,
        task: CountryDrillTask,
        config: CountryDrillRunConfig,
    ): Match = gradeDrillAnswer(
        input = input,
        accepted = task.accepted,
        display = task.display,
        language = config.answerLanguage,
        cardId = "atlas",
        normalizer = config.normalizer,
    )

    /**
     * Leaving, from the corner or from "Fertig". A pending accepted answer books first,
     * exactly as the explicit tap would — closing may neither lose it nor upgrade it — and a
     * revealed answer nobody confirmed books nothing. An untouched run reports nothing at
     * all, though it still names the rung it opened on, which the page files either way.
     *
     * [standingRecord] is what the platform's store holds now; the write is strictly
     * greater, so re-closing a resumed run never double-claims.
     */
    fun close(state: CountryDrillRunState, standingRecord: Int): CountryDrillClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = when (state.feedback) {
            TurnFeedback.Correct -> advanced(state, correct = true, clean = true)
            is TurnFeedback.Almost -> advanced(state, correct = true, clean = false)
            else -> state
        }
        val ended = pending.copy(feedback = TurnFeedback.Neutral, finished = true)
        val summary = if (ended.done == 0) {
            null
        } else {
            DrillRunSummary(ended.done, ended.bestStreak, ended.bestStreak > standingRecord)
        }
        return CountryDrillClose(ended, summary, ended.bestLevel, effects)
    }

    // MARK: - Intents

    /**
     * "Finishing the name IS the answer" — the live approve, the review loop's rule and the
     * one a learner arrives here already knowing.
     *
     * EXACT only, where an explicit check still forgives a slip: the typo budget would fire
     * a letter early and grade the name before it was finished, and a real slip has to pause
     * on its correction anyway. Backing out of a finished name withdraws the approval, so
     * typing PAST the answer never books it.
     */
    private fun typed(state: CountryDrillRunState, text: String): CountryDrillReduction {
        if (state.feedback is TurnFeedback.Almost || state.feedback == TurnFeedback.Revealed) {
            return unchanged(state)
        }
        if (grade(text, state.task, state.config) != Match.Exact) {
            val withdrawn = if (state.feedback == TurnFeedback.Correct) {
                state.copy(feedback = TurnFeedback.Neutral)
            } else {
                state
            }
            return CountryDrillReduction(withdrawn, listOf(DrillEffect.CancelAdvance))
        }
        // why: the cue sounds once per approval — a keystroke inside an already-approved
        // name must not re-chime on every letter.
        val tone: List<DrillEffect> = if (state.feedback == TurnFeedback.Correct) {
            emptyList()
        } else {
            listOf(DrillEffect.Tone(ToneKind.Correct))
        }
        return CountryDrillReduction(
            state.copy(feedback = TurnFeedback.Correct),
            tone + DrillEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    private fun submit(state: CountryDrillRunState, text: String): CountryDrillReduction {
        if (!state.owesAnswer || text.trim().isEmpty()) return unchanged(state)
        return when (val match = grade(text, state.task, state.config)) {
            Match.Exact -> CountryDrillReduction(
                state.copy(feedback = TurnFeedback.Correct),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ArmAdvance(AdvanceTier.Explicit),
                ),
            )
            // why: no beat on a slip — the pause shows the proper spelling and waits for the
            // tap that books it amber, so the keyboard has to give the button back.
            is Match.Typo -> CountryDrillReduction(
                state.copy(feedback = TurnFeedback.Almost(match.corrected, AlmostReason.Typo)),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ReleaseFocus,
                ),
            )
            else -> CountryDrillReduction(
                state.copy(feedback = TurnFeedback.Revealed),
                listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)),
            )
        }
    }

    private fun reveal(state: CountryDrillRunState): CountryDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        // why: the field stays EMPTY — the card is where the answer stands, and typing it in
        // for the learner would put the same name on screen twice.
        return CountryDrillReduction(
            state.copy(feedback = TurnFeedback.Revealed),
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)),
        )
    }

    private fun confirm(state: CountryDrillRunState, rng: Random): CountryDrillReduction =
        when (state.feedback) {
            TurnFeedback.Neutral -> unchanged(state)
            TurnFeedback.Correct -> booked(state, correct = true, clean = true, rng = rng)
            // The amber hold: accepted, but the pause showed a spelling, so the rung stays.
            is TurnFeedback.Almost -> booked(state, correct = true, clean = false, rng = rng)
            // why: no "I knew it" in a drill — the questions are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> booked(state, correct = false, clean = true, rng = rng)
        }

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    private fun elapsed(state: CountryDrillRunState, rng: Random): CountryDrillReduction =
        if (state.feedback == TurnFeedback.Correct) {
            booked(state, correct = true, clean = true, rng = rng)
        } else {
            unchanged(state)
        }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the rung the booking left. */
    private fun booked(
        state: CountryDrillRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): CountryDrillReduction {
        val next = advanced(state, correct, clean)
        // why: sampled against the id it must avoid — kern resamples once, so a repeat needs
        // two unlucky draws rather than one.
        val question = CountryDrill.sample(state.config.content, next.level, state.config.reverse, state.task.id, rng)
        return CountryDrillReduction(
            next.copy(
                task = question,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next card must
                // never render one frame carrying the last one's answer.
                feedback = TurnFeedback.Neutral,
            ),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    /** The booking itself: the ramp, the streak, the tallies — the rung it reached included. */
    private fun advanced(
        state: CountryDrillRunState,
        correct: Boolean,
        clean: Boolean,
    ): CountryDrillRunState {
        val step = CountryDrill.step(
            level = state.level,
            winsAtLevel = state.winsAtLevel,
            correct = correct,
            clean = clean,
            fast = state.config.fast,
        )
        val streak = if (correct) state.streak + 1 else 0
        return state.copy(
            level = step.level,
            bestLevel = maxOf(state.bestLevel, step.level),
            winsAtLevel = step.winsAtLevel,
            streak = streak,
            bestStreak = maxOf(state.bestStreak, streak),
            missRun = if (correct) 0 else state.missRun + 1,
            outcomes = state.outcomes + tone(correct, clean),
            done = state.done + 1,
        )
    }

    private fun tone(correct: Boolean, clean: Boolean): AnswerTone = when {
        !correct -> AnswerTone.Wrong
        clean -> AnswerTone.Right
        else -> AnswerTone.Tough
    }

    private fun unchanged(state: CountryDrillRunState) = CountryDrillReduction(state, emptyList())
}

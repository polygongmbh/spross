package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The dates drill as pure state plus one reducer — the atlas run's shape on the dates
 * ladder. The run's shape is [DateDrillRunState]; what it can ask is [DateDrill].
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed,
 * so none is taken. No default arguments: they do not cross the ObjC boundary.
 */
object DateDrillRun {

    /** A fresh run. Every run opens at rung 1 however far the learner has climbed. */
    fun open(config: DateDrillRunConfig, rng: Random): DateDrillRunState =
        openAt(config, 1, rng)

    /**
     * The same, forced to one rung — what the record buys is the page and never a head
     * start, so this exists for tests and screenshot drivers, which have no other way to
     * reach the assembled rungs.
     */
    fun openAt(config: DateDrillRunConfig, level: Int, rng: Random): DateDrillRunState {
        val content = config.content
        val start = level.coerceIn(1, DateDrill.maxLevel(content, config.reverse))
        val opening = DateDrill.draw(content, start, config.reverse, null, emptySet(), rng)
        return DateDrillRunState(
            config = config,
            // why: nothing is solved yet, so a fresh calendar's first rung always has a question.
            task = requireNotNull(opening.task) {
                "no dates question for ${content.source}→${content.target}"
            },
            index = 0,
            level = opening.level,
            bestLevel = opening.level,
            winsAtLevel = 0,
            done = 0,
            streak = 0,
            bestStreak = 0,
            missRun = 0,
            outcomes = emptyList(),
            solved = emptySet(),
            feedback = TurnFeedback.Neutral,
            finished = false,
        )
    }

    fun reduce(
        state: DateDrillRunState,
        intent: DateDrillIntent,
        rng: Random,
    ): DateDrillReduction = when (intent) {
        is DateDrillIntent.InputChanged -> typed(state, intent.text)
        is DateDrillIntent.Submit -> submit(state, intent.text)
        DateDrillIntent.Reveal -> reveal(state)
        DateDrillIntent.ConfirmPending -> confirm(state, rng)
        DateDrillIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * Grade [input] against every reading the task accepts, the way a drill grades: word
     * by word, one slip per word, no article forgiven — the pattern authors its own
     * article, and its variants are what admit the accusative.
     *
     * The rungs whose answer is a numeral carry the numbers drill's value check
     * ([NumberReadingIndex]): a day that names another day (`vierte` for `dritte`) is
     * refused and named, never forgiven. Nothing is inherited — the bare-name rungs have
     * no numeral to check, so they pass no index.
     */
    fun grade(
        input: String,
        task: DateDrillTask,
        config: DateDrillRunConfig,
    ): Match = gradeDrillAnswer(
        input = input,
        accepted = task.accepted,
        display = task.display,
        language = config.answerLanguage,
        cardId = "dates",
        normalizer = config.normalizer,
        index = numberIndex(task.kind, config),
    )

    /**
     * Leaving, from the corner or from "Fertig". A pending accepted answer books first,
     * exactly as the explicit tap would — closing may neither lose it nor upgrade it —
     * and a revealed answer nobody confirmed books nothing. An untouched run reports
     * nothing at all, though it still names the rung it opened on, which the page files
     * either way.
     *
     * [standingRecord] is what the platform's store holds now; the write is strictly
     * greater, so re-closing a resumed run never double-claims.
     */
    fun close(state: DateDrillRunState, standingRecord: Int): DateDrillClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = when (state.feedback) {
            TurnFeedback.Correct -> advanced(state, correct = true, clean = true)
            is TurnFeedback.Almost -> advanced(state, correct = true, clean = false)
            else -> state
        }
        val ended = pending.copy(feedback = TurnFeedback.Neutral, otherWord = null, finished = true)
        val summary = if (ended.done == 0) {
            null
        } else {
            DrillRunSummary(ended.done, ended.bestStreak, ended.bestStreak > standingRecord)
        }
        return DateDrillClose(ended, summary, ended.bestLevel, effects)
    }

    // MARK: - Intents

    /**
     * "Finishing the reading IS the answer" — the live approve, the atlas run's rule.
     * EXACT only: the typo budget would fire a letter early and grade the reading before
     * it was finished, and backing out of a finished one withdraws the approval.
     */
    private fun typed(state: DateDrillRunState, text: String): DateDrillReduction {
        if (state.feedback is TurnFeedback.Almost || state.feedback == TurnFeedback.Revealed) {
            return unchanged(state)
        }
        if (grade(text, state.task, state.config) != Match.Exact) {
            val withdrawn = if (state.feedback == TurnFeedback.Correct) {
                state.copy(feedback = TurnFeedback.Neutral)
            } else {
                state
            }
            return DateDrillReduction(withdrawn, listOf(DrillEffect.CancelAdvance))
        }
        // why: the cue sounds once per approval — a keystroke inside an already-approved
        // reading must not re-chime on every letter.
        val tone: List<DrillEffect> = if (state.feedback == TurnFeedback.Correct) {
            emptyList()
        } else {
            listOf(DrillEffect.Tone(ToneKind.Correct))
        }
        return DateDrillReduction(
            state.copy(feedback = TurnFeedback.Correct),
            tone + DrillEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    private fun submit(state: DateDrillRunState, text: String): DateDrillReduction {
        if (!state.owesAnswer || text.trim().isEmpty()) return unchanged(state)
        return when (val match = grade(text, state.task, state.config)) {
            Match.Exact -> DateDrillReduction(
                state.copy(feedback = TurnFeedback.Correct),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ArmAdvance(AdvanceTier.Explicit),
                ),
            )
            // why: no beat on a slip — the pause shows the proper spelling and waits for the
            // tap that books it almost, so the keyboard has to give the button back.
            is Match.Typo -> DateDrillReduction(
                state.copy(feedback = TurnFeedback.Almost(match.corrected, AlmostReason.Typo)),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ReleaseFocus,
                ),
            )
            else -> DateDrillReduction(
                state.copy(feedback = TurnFeedback.Revealed, otherWord = match as? Match.OtherWord),
                listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)),
            )
        }
    }

    private fun reveal(state: DateDrillRunState): DateDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        // why: the field stays EMPTY — the card is where the answer stands, and typing it
        // in for the learner would put the same reading on screen twice.
        return DateDrillReduction(
            state.copy(feedback = TurnFeedback.Revealed),
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)),
        )
    }

    private fun confirm(state: DateDrillRunState, rng: Random): DateDrillReduction =
        when (state.feedback) {
            TurnFeedback.Neutral -> unchanged(state)
            TurnFeedback.Correct -> booked(state, correct = true, clean = true, rng = rng)
            // The almost hold: accepted, but the pause showed a spelling, so the rung stays.
            is TurnFeedback.Almost -> booked(state, correct = true, clean = false, rng = rng)
            // why: no "I knew it" in a drill — the questions are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> booked(state, correct = false, clean = true, rng = rng)
        }

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    private fun elapsed(state: DateDrillRunState, rng: Random): DateDrillReduction =
        if (state.feedback == TurnFeedback.Correct) {
            booked(state, correct = true, clean = true, rng = rng)
        } else {
            unchanged(state)
        }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the rung the booking left. */
    private fun booked(
        state: DateDrillRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): DateDrillReduction {
        val next = advanced(state, correct, clean)
        // why: sampled against the id it must avoid — kern resamples once, so a repeat needs
        // two unlucky draws rather than one.
        val draw = DateDrill.draw(
            state.config.content,
            next.level,
            state.config.reverse,
            state.task.id,
            next.solved,
            rng,
        )
        return DateDrillReduction(
            next.copy(
                // Nothing left to ask: end on the summary, never on a question already answered.
                task = draw.task ?: state.task,
                finished = draw.task == null,
                level = draw.level,
                // A rung the run answered out is a rung it stood on, and the wins banked on
                // the one below stay behind with it.
                bestLevel = maxOf(next.bestLevel, draw.level),
                winsAtLevel = if (draw.level == next.level) next.winsAtLevel else 0,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next card must
                // never render one frame carrying the last one's answer.
                feedback = TurnFeedback.Neutral,
                otherWord = null,
            ),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    /** The booking itself: the ramp, the streak, the tallies — the rung it reached included. */
    private fun advanced(
        state: DateDrillRunState,
        correct: Boolean,
        clean: Boolean,
    ): DateDrillRunState {
        val step = DateDrill.step(
            content = state.config.content,
            reverse = state.config.reverse,
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
            // why: only a CLEAN answer retires a question — a slip and a reveal leave it in
            // the pool, which is what the ramp already says an almost is worth.
            solved = if (correct && clean) state.solved + DrillSolved.key(state.task) else state.solved,
            streak = streak,
            bestStreak = maxOf(state.bestStreak, streak),
            missRun = if (correct) 0 else state.missRun + 1,
            outcomes = state.outcomes + outcome(correct, clean),
            done = state.done + 1,
        )
    }

    /**
     * The value check, on the rungs whose answer is a numeral. Nothing is inherited:
     * `gradeDrillAnswer` defaults to none, and the bare-name rungs stay without one.
     */
    private fun numberIndex(kind: DateTaskKind, config: DateDrillRunConfig): NumberReadingIndex? =
        when (kind) {
            DateTaskKind.Weekday, DateTaskKind.Month -> null
            else -> config.normalizer?.let { NumberReadingIndex.of(config.answerLanguage, it) }
        }

    private fun outcome(correct: Boolean, clean: Boolean): AnswerOutcome = when {
        !correct -> AnswerOutcome.Wrong
        clean -> AnswerOutcome.Right
        else -> AnswerOutcome.Almost
    }

    private fun unchanged(state: DateDrillRunState) = DateDrillReduction(state, emptyList())
}

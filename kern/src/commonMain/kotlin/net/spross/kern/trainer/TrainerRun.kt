package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The slot drill as pure state plus one reducer — the machine both apps used to re-derive.
 * The run's shape is [TrainerRunState]; what it is spelled out of is [TrainerMode].
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed
 * anywhere in the run, so none is taken. No default arguments: they do not cross the ObjC
 * boundary, so every entry point is explicit.
 */
object TrainerRun {

    /** A fresh run: every variant at rung 1, one task already drawn. */
    fun open(mode: TrainerMode, rng: Random): TrainerRunState {
        val levels = mode.variants.associateWith { 1 }
        return TrainerRunState(
            mode = mode,
            current = mode.draw(levels, null, rng),
            index = 0,
            levels = levels,
            winsAtLevel = emptyMap(),
            bestLevels = emptyMap(),
            done = 0,
            streak = 0,
            bestStreak = 0,
            missRun = 0,
            outcomes = emptyList(),
            seenDigitCounts = emptySet(),
            hintUsed = false,
            feedback = TurnFeedback.Neutral,
            finished = false,
        )
    }

    fun reduce(
        state: TrainerRunState,
        intent: TrainerIntent,
        normalizer: AnswerNormalizer?,
        rng: Random,
    ): TrainerReduction = when (intent) {
        is TrainerIntent.InputChanged -> typed(state, intent.text, normalizer)
        is TrainerIntent.Submit -> submit(state, intent.text, normalizer)
        TrainerIntent.Reveal -> reveal(state)
        TrainerIntent.LookUp -> lookUp(state)
        TrainerIntent.ConfirmPending -> confirm(state, rng)
        TrainerIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * Grade [input] against a task the way a drill grades: word by word, one slip per word,
     * nothing forgiven inside a digit ([AnswerNormalizer] with `articleLeniency = false`,
     * `maxTyposPerWord = 1`). A REVERSED task takes exactly this path — its accepted set already
     * carries the notation twins, and digit-bearing words grade exact-only.
     *
     * [Match.OtherWord] where the slip NAMES another value ([otherNumber] and the
     * [NumberReadingIndex] behind it): the drill whose job is keeping numbers apart refuses a
     * different number the typo budget would have forgiven, and carries what it was. A null
     * [normalizer] (a preview with no language info) falls back to a plain case- and
     * punctuation-insensitive comparison, with no index and no refusal.
     */
    fun grade(input: String, task: TrainerTask, normalizer: AnswerNormalizer?): Match =
        gradeDrillAnswer(
            input = input,
            accepted = task.accepted,
            display = task.display,
            language = task.language,
            cardId = "drill",
            normalizer = normalizer,
            index = normalizer?.let { NumberReadingIndex.of(task.language, it) },
        )

    /**
     * Is the learner mid-way through a longer accepted answer? A clock reading is accepted with
     * and without the part of the day, so "son las nueve" is both a finished answer and the first
     * half of "son las nueve de la noche" — and a field that confirms itself on the shorter one
     * takes the fuller answer away before it can be typed. Only the reading the reveal TEACHES
     * confirms on its own; anything shorter that another reading continues waits for a check.
     */
    fun stillGrowing(input: String, task: TrainerTask): Boolean {
        val typed = plainAnswerForm(input.trim())
        if (typed.isEmpty() || typed == plainAnswerForm(task.display)) return false
        return task.accepted.any { plainAnswerForm(it).startsWith("$typed ") }
    }

    /**
     * Leaving the run. A pending accepted answer books first, exactly as the explicit tap would —
     * closing may neither lose it nor upgrade it — and a revealed answer nobody confirmed books
     * nothing. An untouched run stores nothing at all.
     *
     * [standingRecord] and [standingProgress] are what the platform's stores hold now; both
     * writes are strictly-greater, so re-closing a resumed run never double-claims.
     */
    fun close(
        state: TrainerRunState,
        standingRecord: Int,
        standingProgress: Map<String, Int>,
    ): TrainerClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = when (state.feedback) {
            TurnFeedback.Correct -> advanced(state, correct = true, outcome = state.cleanOutcome)
            is TurnFeedback.Almost -> advanced(state, correct = true, outcome = AnswerOutcome.Almost)
            else -> state
        }
        val ended = pending.copy(feedback = TurnFeedback.Neutral, otherWord = null, hintUsed = false, finished = true)
        if (ended.done == 0) {
            return TrainerClose(ended, null, state.mode.recordKey, emptyMap(), effects)
        }
        val bookings = ended.bestLevels
            .map { (variant, best) -> state.mode.progressKey(variant) to best }
            .filter { (key, best) -> best > (standingProgress[key] ?: 0) }
            .toMap()
        return TrainerClose(
            state = ended,
            summary = DrillRunSummary(ended.done, ended.bestStreak, ended.bestStreak > standingRecord),
            recordKey = state.mode.recordKey,
            progressBookings = bookings,
            effects = effects,
        )
    }

    // MARK: - Intents

    private fun submit(
        state: TrainerRunState,
        text: String,
        normalizer: AnswerNormalizer?,
    ): TrainerReduction {
        if (!state.owesAnswer || text.trim().isEmpty()) return unchanged(state)
        return when (val match = grade(text, state.currentTask, normalizer)) {
            Match.Exact -> TrainerReduction(
                state.copy(feedback = TurnFeedback.Correct),
                listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ArmAdvance(AdvanceTier.Explicit)),
            )
            // why: no beat on a slip — the pause shows the proper spelling, and the tap that ends
            // it books the answer almost.
            is Match.Typo -> TrainerReduction(
                state.copy(feedback = TurnFeedback.Almost(match.corrected, AlmostReason.Typo)),
                listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ReleaseFocus),
            )
            else -> TrainerReduction(
                state.copy(feedback = TurnFeedback.Revealed, otherWord = match as? Match.OtherWord),
                listOf(DrillEffect.Tone(ToneKind.Wrong)),
            )
        }
    }

    /**
     * "Finishing the word IS the answer" — the live approve. Drills have no reveal-then-retype
     * step, so the guard only has to keep clear of an almost hold and of an answer still growing.
     */
    private fun typed(
        state: TrainerRunState,
        text: String,
        normalizer: AnswerNormalizer?,
    ): TrainerReduction {
        if (state.feedback is TurnFeedback.Almost || state.feedback == TurnFeedback.Revealed) {
            return unchanged(state)
        }
        val trimmed = text.trim()
        val approves = trimmed.isNotEmpty() &&
            !stillGrowing(trimmed, state.currentTask) &&
            grade(trimmed, state.currentTask, normalizer) == Match.Exact
        if (!approves) {
            // A field edited back out of the answer withdraws the approval it just earned.
            val withdrawn = if (state.feedback == TurnFeedback.Correct) {
                state.copy(feedback = TurnFeedback.Neutral)
            } else {
                state
            }
            return TrainerReduction(withdrawn, listOf(DrillEffect.CancelAdvance))
        }
        // why: the cue sounds once per approval — a keystroke inside an already-approved answer
        // must not re-chime on every letter.
        val tone: List<DrillEffect> = if (state.feedback == TurnFeedback.Correct) {
            emptyList()
        } else {
            listOf(DrillEffect.Tone(ToneKind.Correct))
        }
        return TrainerReduction(
            state.copy(feedback = TurnFeedback.Correct),
            tone + DrillEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    private fun reveal(state: TrainerRunState): TrainerReduction {
        if (!state.owesAnswer) return unchanged(state)
        // why: the field stays empty — the card is where the answer stands, and typing it in for
        // the learner would put the same word on screen twice.
        return TrainerReduction(
            state.copy(feedback = TurnFeedback.Revealed),
            listOf(DrillEffect.Tone(ToneKind.Reveal)),
        )
    }

    /**
     * why: a look-up while the answer is still owed costs the rung — the task books almost. Once
     * the answer is in, nothing is owed and reading is free.
     */
    private fun lookUp(state: TrainerRunState): TrainerReduction =
        if (state.owesAnswer) unchanged(state.copy(hintUsed = true)) else unchanged(state)

    private fun confirm(state: TrainerRunState, rng: Random): TrainerReduction = when (state.feedback) {
        TurnFeedback.Neutral -> unchanged(state)
        TurnFeedback.Correct -> booked(state, correct = true, outcome = state.cleanOutcome, rng = rng)
        is TurnFeedback.Almost -> booked(state, correct = true, outcome = AnswerOutcome.Almost, rng = rng)
        // why: no "Wusste ich" in a drill — the tasks are generated, so self-reporting after
        // seeing the answer proves nothing; revealed simply counts as a miss.
        TurnFeedback.Revealed -> booked(state, correct = false, outcome = AnswerOutcome.Wrong, rng = rng)
    }

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    private fun elapsed(state: TrainerRunState, rng: Random): TrainerReduction =
        if (state.feedback == TurnFeedback.Correct) {
            booked(state, correct = true, outcome = state.cleanOutcome, rng = rng)
        } else {
            unchanged(state)
        }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the rungs the booking left. */
    private fun booked(
        state: TrainerRunState,
        correct: Boolean,
        outcome: AnswerOutcome,
        rng: Random,
    ): TrainerReduction {
        val next = advanced(state, correct, outcome)
        return TrainerReduction(
            next.copy(
                current = state.mode.draw(next.levels, state.currentTask.prompt, rng),
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next prompt must
                // never render one frame carrying the last one's answer or its almost debt.
                feedback = TurnFeedback.Neutral,
                otherWord = null,
                hintUsed = false,
            ),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    /**
     * The booking itself: the ramp for the variant that asked, the streak, the tallies. The other
     * variants of a mixed run stand exactly where they were.
     */
    private fun advanced(state: TrainerRunState, correct: Boolean, outcome: AnswerOutcome): TrainerRunState {
        val variant = state.currentVariant
        val step = DrillRamp.step(
            level = state.currentLevel,
            winsAtLevel = state.winsAtLevel[variant] ?: 0,
            correct = correct,
            clean = outcome != AnswerOutcome.Almost,
            maxLevel = state.mode.maxLevel(variant),
            winsRequired = state.mode.winsToAdvance,
        )
        val streak = if (correct) state.streak + 1 else 0
        return state.copy(
            levels = state.levels + (variant to step.level),
            winsAtLevel = state.winsAtLevel + (variant to step.winsAtLevel),
            bestLevels = state.bestLevels + (variant to maxOf(state.bestLevels[variant] ?: 1, step.level)),
            seenDigitCounts = state.currentDigits
                ?.let { state.seenDigitCounts + it }
                ?: state.seenDigitCounts,
            streak = streak,
            bestStreak = maxOf(state.bestStreak, streak),
            missRun = if (correct) 0 else state.missRun + 1,
            outcomes = state.outcomes + outcome,
            done = state.done + 1,
        )
    }

    private fun unchanged(state: TrainerRunState) = TrainerReduction(state, emptyList())
}

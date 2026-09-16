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
 * The run's shape is [NumbersRunState]; what it is spelled out of is [NumbersMode].
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed
 * anywhere in the run, so none is taken. No default arguments: they do not cross the ObjC
 * boundary, so every entry point is explicit.
 */
object NumbersRun {

    /** A fresh run: every variant at Sprosse 1, one task already drawn. */
    fun open(mode: NumbersMode, rng: Random): NumbersRunState =
        openAt(mode, mode.variants.associateWith { 1 }, rng)

    /**
     * The same, forced to given Sprossen — the deterministic way to reach a stage. A variant
     * [levels] leaves out opens at 1; every level is clamped to the variant's ladder.
     */
    fun openAt(mode: NumbersMode, levels: Map<NumbersExercise, Int>, rng: Random): NumbersRunState {
        val start = mode.variants.associateWith { exercise ->
            (levels[exercise] ?: 1).coerceIn(1, mode.maxLevel(exercise))
        }
        val opening = mode.draw(start, null, emptySet(), rng)
        return NumbersRunState(
            mode = mode,
            // why: nothing is solved yet, so the draw always has a Sprosse to ask from.
            current = requireNotNull(opening.drawn) { "no task at Sprosse 1 of ${mode.recordKey}" },
            index = 0,
            levels = opening.levels,
            winsAtLevel = emptyMap(),
            bestLevels = emptyMap(),
            core = DrillRunCore(),
            seenDigitCounts = emptySet(),
            hintUsed = false,
            feedback = TurnFeedback.Neutral,
            finished = false,
        )
    }

    fun reduce(
        state: NumbersRunState,
        intent: NumbersIntent,
        normalizer: AnswerNormalizer?,
        rng: Random,
    ): NumbersReduction = when (intent) {
        is NumbersIntent.InputChanged -> typed(state, intent.text, normalizer)
        is NumbersIntent.Submit -> submit(state, intent.text, normalizer)
        NumbersIntent.Reveal -> reveal(state)
        NumbersIntent.LookUp -> lookUp(state)
        NumbersIntent.ConfirmPending -> confirm(state, rng)
        NumbersIntent.AdvanceElapsed -> elapsed(state, rng)
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
    fun grade(input: String, task: NumbersTask, normalizer: AnswerNormalizer?): Match =
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
    fun stillGrowing(input: String, task: NumbersTask): Boolean {
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
        state: NumbersRunState,
        standingRecord: Int,
        standingProgress: Map<String, Int>,
    ): NumbersClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = when (state.feedback) {
            TurnFeedback.Correct -> advanced(state, correct = true, outcome = state.cleanOutcome)
            is TurnFeedback.Almost -> advanced(state, correct = true, outcome = AnswerOutcome.Almost)
            else -> state
        }
        val ended = pending.copy(feedback = TurnFeedback.Neutral, otherWord = null, hintUsed = false, finished = true)
        if (ended.done == 0) {
            return NumbersClose(ended, null, state.mode.recordKey, emptyMap(), effects)
        }
        val bookings = ended.bestLevels
            .map { (exercise, best) -> state.mode.progressKey(exercise) to best }
            .filter { (key, best) -> best > (standingProgress[key] ?: 0) }
            .toMap()
        return NumbersClose(
            state = ended,
            summary = DrillRunSummary(ended.done, ended.bestStreak, ended.bestStreak > standingRecord),
            recordKey = state.mode.recordKey,
            progressBookings = bookings,
            effects = effects,
        )
    }

    // MARK: - Intents

    /**
     * The explicit check. Nothing typed means the ask to see the answer ([reveal]) — the run
     * has ONE primary action, and its button and its Enter key may not disagree on it.
     */
    private fun submit(
        state: NumbersRunState,
        text: String,
        normalizer: AnswerNormalizer?,
    ): NumbersReduction {
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        return when (val match = grade(text, state.currentTask, normalizer)) {
            Match.Exact -> NumbersReduction(
                state.copy(feedback = TurnFeedback.Correct),
                listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ArmAdvance(AdvanceTier.Explicit)),
            )
            // why: no beat on a slip — the pause shows the proper spelling, and the tap that ends
            // it books the answer almost.
            is Match.Typo -> NumbersReduction(
                state.copy(feedback = TurnFeedback.Almost(match.corrected, AlmostReason.Typo)),
                listOf(DrillEffect.Tone(ToneKind.Correct), DrillEffect.ReleaseFocus),
            )
            else -> NumbersReduction(
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
        state: NumbersRunState,
        text: String,
        normalizer: AnswerNormalizer?,
    ): NumbersReduction {
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
            return NumbersReduction(withdrawn, listOf(DrillEffect.CancelAdvance))
        }
        // why: the cue sounds once per approval — a keystroke inside an already-approved answer
        // must not re-chime on every letter.
        val tone: List<DrillEffect> = if (state.feedback == TurnFeedback.Correct) {
            emptyList()
        } else {
            listOf(DrillEffect.Tone(ToneKind.Correct))
        }
        return NumbersReduction(
            state.copy(feedback = TurnFeedback.Correct),
            tone + DrillEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    private fun reveal(state: NumbersRunState): NumbersReduction {
        if (!state.owesAnswer) return unchanged(state)
        // why: the field stays empty — the card is where the answer stands, and typing it in for
        // the learner would put the same word on screen twice.
        return NumbersReduction(
            state.copy(feedback = TurnFeedback.Revealed),
            listOf(DrillEffect.Tone(ToneKind.Reveal)),
        )
    }

    /**
     * why: a look-up while the answer is still owed costs the Sprosse — the task books almost. Once
     * the answer is in, nothing is owed and reading is free.
     */
    private fun lookUp(state: NumbersRunState): NumbersReduction =
        if (state.owesAnswer) unchanged(state.copy(hintUsed = true)) else unchanged(state)

    private fun confirm(state: NumbersRunState, rng: Random): NumbersReduction = when (state.feedback) {
        TurnFeedback.Neutral -> unchanged(state)
        TurnFeedback.Correct -> booked(state, correct = true, outcome = state.cleanOutcome, rng = rng)
        is TurnFeedback.Almost -> booked(state, correct = true, outcome = AnswerOutcome.Almost, rng = rng)
        // why: no "Wusste ich" in a drill — the tasks are generated, so self-reporting after
        // seeing the answer proves nothing; revealed simply counts as a miss.
        TurnFeedback.Revealed -> booked(state, correct = false, outcome = AnswerOutcome.Wrong, rng = rng)
    }

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    private fun elapsed(state: NumbersRunState, rng: Random): NumbersReduction =
        if (state.feedback == TurnFeedback.Correct) {
            booked(state, correct = true, outcome = state.cleanOutcome, rng = rng)
        } else {
            unchanged(state)
        }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the Sprossen the booking left. */
    private fun booked(
        state: NumbersRunState,
        correct: Boolean,
        outcome: AnswerOutcome,
        rng: Random,
    ): NumbersReduction {
        val next = advanced(state, correct, outcome)
        val draw = next.mode.draw(next.levels, state.currentTask.prompt, next.solved, rng)
        return NumbersReduction(
            climbed(next, draw).copy(
                // Nothing left to ask anywhere: end on the summary, never on a repeat.
                current = draw.drawn ?: next.current,
                finished = draw.drawn == null,
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
     * Adopt the Sprossen the draw climbed to. A Sprosse the run was carried past because its prompts
     * were answered out is a Sprosse it STOOD on, so the close books it like any other; the wins
     * banked on the one below stay behind with it.
     */
    private fun climbed(state: NumbersRunState, draw: NumbersDraw): NumbersRunState {
        val moved = draw.levels.filter { (exercise, level) -> level != state.levels[exercise] }
        if (moved.isEmpty()) return state
        return state.copy(
            levels = draw.levels,
            winsAtLevel = state.winsAtLevel + moved.map { (exercise, _) -> exercise to 0 },
            bestLevels = state.bestLevels + moved.map { (exercise, level) ->
                exercise to maxOf(state.bestLevels[exercise] ?: 1, level)
            },
        )
    }

    /**
     * The booking itself: the ramp for the variant that asked, the streak, the tallies. The other
     * variants of a mixed run stand exactly where they were.
     */
    private fun advanced(state: NumbersRunState, correct: Boolean, outcome: AnswerOutcome): NumbersRunState {
        val exercise = state.currentVariant
        val clean = outcome != AnswerOutcome.Almost
        val step = DrillRamp.step(
            level = state.currentLevel,
            winsAtLevel = state.winsAtLevel[exercise] ?: 0,
            correct = correct,
            clean = clean,
            winsRequired = state.mode.winsToAdvance,
        )
        return state.copy(
            levels = state.levels + (exercise to step.level),
            winsAtLevel = state.winsAtLevel + (exercise to step.winsAtLevel),
            bestLevels = state.bestLevels + (exercise to maxOf(state.bestLevels[exercise] ?: 1, step.level)),
            seenDigitCounts = state.currentDigits
                ?.let { state.seenDigitCounts + it }
                ?: state.seenDigitCounts,
            core = state.core.book(correct, clean, DrillSolved.key(exercise, state.currentTask)),
        )
    }

    private fun unchanged(state: NumbersRunState) = NumbersReduction(state, emptyList())
}

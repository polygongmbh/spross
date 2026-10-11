package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The slot drill as pure state plus one reducer, the one machine both apps drive.
 * The run's shape is [NumbersRunState]; what it is spelled out of is [NumbersMode].
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is read
 * anywhere in the run: a timed run's end arrives as [NumbersIntent.TimeUp]. No default
 * arguments: they do not cross the ObjC boundary, so every entry point is explicit.
 */
object NumbersRun {

    /**
     * A fresh run: every exercise at Sprosse 1, one task already drawn.
     * [standingRecord] is what the platform's store holds under [NumbersMode.recordKey], and
     * [standingProgress] what it holds per exercise, keyed as [close] books it —
     * beating either is what a pause for improving names ([DrillPacing]).
     */
    fun open(
        mode: NumbersMode,
        standingRecord: Int,
        standingProgress: Map<String, Int>,
        rng: Random,
    ): NumbersRunState =
        openAt(mode, mode.exercises.associateWith { 1 }, standingRecord, standingProgress, rng)

    /**
     * The same, forced to given Sprossen — the deterministic way to reach a stage. An exercise
     * [sprossen] leaves out opens at 1; every Sprosse is clamped to the exercise's ladder.
     */
    fun openAt(
        mode: NumbersMode,
        sprossen: Map<NumbersExercise, Int>,
        standingRecord: Int,
        standingProgress: Map<String, Int>,
        rng: Random,
    ): NumbersRunState {
        val start = mode.exercises.associateWith { exercise ->
            (sprossen[exercise] ?: 1).coerceIn(1, mode.maxSprosse(exercise))
        }
        val opening = mode.draw(start, null, emptySet(), rng)
        return NumbersRunState(
            mode = mode,
            // why: nothing is solved yet, so the draw always has a Sprosse to ask from.
            current = requireNotNull(opening.drawn) { "no task at Sprosse 1 of ${mode.recordKey}" },
            index = 0,
            sprossen = opening.sprossen,
            winsAtSprosse = emptyMap(),
            bestSprossen = emptyMap(),
            core = DrillRunCore(
                pacing = DrillPacing.opening(mode.exercises.singleOrNull()?.let { opening.sprossen[it] }, standingRecord),
            ),
            seenDigitCounts = emptySet(),
            seenFormKeys = emptySet(),
            hintUsed = false,
            feedback = TurnFeedback.Neutral,
            finished = false,
            standingSprossen = mode.exercises.associateWith { standingProgress[mode.progressKey(it)] ?: 0 },
        )
    }

    fun reduce(
        state: NumbersRunState,
        intent: NumbersIntent,
        normalizer: AnswerNormalizer?,
        rng: Random,
    ): NumbersReduction = when (intent) {
        is NumbersIntent.InputChanged -> typed(state, intent.text, normalizer, rng)
        is NumbersIntent.Submit -> submit(state, intent.text, normalizer, rng)
        NumbersIntent.Reveal -> reveal(state, rng)
        NumbersIntent.LookUp -> lookUp(state)
        NumbersIntent.ConfirmPending -> confirm(state, rng)
        NumbersIntent.AdvanceElapsed -> elapsed(state, rng)
        NumbersIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
        NumbersIntent.TimeUp -> timeUp(state)
    }


    /**
     * Grade [input] against a task the way a drill grades: no article forgiven, nothing
     * forgiven inside a digit ([AnswerNormalizer.drill]). A REVERSED task takes exactly this path — its accepted set already
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
        val pending = TypedDrillVerdicts.pending(state.feedback)
            ?.let { NumbersBooking.advanced(state, it.correct, state.cleanness(it)) }
            ?: state
        val ended = pending.copy(feedback = TurnFeedback.Neutral, otherWord = null, hintUsed = false, finished = true)
        if (ended.done == 0) {
            return NumbersClose(ended, null, state.mode.recordKey, emptyMap(), effects)
        }
        // why: a challenge follows its script, so it books no ladder and no record.
        val scripted = state.challenge != null
        val bookings = if (scripted) emptyMap() else ended.bestSprossen
            .map { (exercise, best) -> state.mode.progressKey(exercise) to best }
            .filter { (key, best) -> best > (standingProgress[key] ?: 0) }
            .toMap()
        val timed = state.challenge?.let { TimedOutcome(ended.score, it) }
        return NumbersClose(
            state = ended,
            summary = DrillRunSummary(ended.done, ended.bestAnswerStreak, !scripted && ended.bestAnswerStreak > standingRecord, timed),
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
        rng: Random,
    ): NumbersReduction {
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state, rng)
        val match = grade(text, state.currentTask, normalizer)
        if (state.timed) {
            return when (match) {
                Match.Exact -> NumbersBooking.raced(state, ToneKind.Correct, correct = true, clean = true, rng)
                is Match.Typo -> NumbersBooking.raced(state, ToneKind.Almost, correct = true, clean = false, rng)
                else -> NumbersBooking.raced(state, ToneKind.Wrong, correct = false, clean = false, rng)
            }
        }
        val verdict = TypedDrillVerdicts.submit(match, silence = false)
        return NumbersReduction(state.copy(feedback = verdict.feedback, otherWord = verdict.otherWord), verdict.effects)
    }

    /**
     * "Finishing the word IS the answer" — the live approve ([TypedDrillVerdicts.typed]),
     * held back while the answer is still growing ([stillGrowing]).
     */
    private fun typed(
        state: NumbersRunState,
        text: String,
        normalizer: AnswerNormalizer?,
        rng: Random,
    ): NumbersReduction {
        val trimmed = text.trim()
        val verdict = TypedDrillVerdicts.typed(state.feedback) {
            trimmed.isNotEmpty() &&
                !stillGrowing(trimmed, state.currentTask) &&
                grade(trimmed, state.currentTask, normalizer) == Match.Exact
        } ?: return unchanged(state)
        if (state.timed && verdict.feedback == TurnFeedback.Correct) {
            return NumbersBooking.raced(state, ToneKind.Correct, correct = true, clean = true, rng)
        }
        return NumbersReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /** The answer asked for on an empty field; a challenge's Skip books the miss instead, its answer never shown. */
    private fun reveal(state: NumbersRunState, rng: Random): NumbersReduction {
        if (!state.owesAnswer) return unchanged(state)
        if (state.timed) return NumbersBooking.raced(state, ToneKind.Wrong, correct = false, clean = false, rng)
        val verdict = TypedDrillVerdicts.reveal(silence = false)
        return NumbersReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /**
     * why: a look-up while the answer is still owed costs the Sprosse — the task books almost. Once
     * the answer is in, nothing is owed and reading is free.
     */
    private fun lookUp(state: NumbersRunState): NumbersReduction =
        if (state.owesAnswer) unchanged(state.copy(hintUsed = true)) else unchanged(state)

    private fun confirm(state: NumbersRunState, rng: Random): NumbersReduction =
        TypedDrillVerdicts.confirmed(state.feedback)
            ?.let { NumbersBooking.booked(state, it.correct, state.cleanness(it), rng) }
            ?: unchanged(state)

    /** The run is over; what is pending is the close's to book, as the ✕ would. */
    private fun timeUp(state: NumbersRunState): NumbersReduction =
        if (state.timed && !state.finished) {
            NumbersReduction(state.copy(finished = true), listOf(DrillEffect.CancelAdvance, DrillEffect.Silence))
        } else {
            unchanged(state)
        }

    private fun elapsed(state: NumbersRunState, rng: Random): NumbersReduction =
        TypedDrillVerdicts.elapsed(state.feedback)
            ?.let { NumbersBooking.booked(state, it.correct, state.cleanness(it), rng) }
            ?: unchanged(state)

    private fun unchanged(state: NumbersRunState) = NumbersReduction(state, emptyList())
}

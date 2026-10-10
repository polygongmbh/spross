package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
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
            ?.let { advanced(state, it.correct, state.cleanness(it)) }
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
                Match.Exact -> raced(state, ToneKind.Correct, correct = true, clean = true, rng)
                is Match.Typo -> raced(state, ToneKind.Almost, correct = true, clean = false, rng)
                else -> raced(state, ToneKind.Wrong, correct = false, clean = false, rng)
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
            return raced(state, ToneKind.Correct, correct = true, clean = true, rng)
        }
        return NumbersReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /** The answer asked for on an empty field; a challenge's Skip books the miss instead, its answer never shown. */
    private fun reveal(state: NumbersRunState, rng: Random): NumbersReduction {
        if (!state.owesAnswer) return unchanged(state)
        if (state.timed) return raced(state, ToneKind.Wrong, correct = false, clean = false, rng)
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
            ?.let { booked(state, it.correct, state.cleanness(it), rng) }
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
            ?.let { booked(state, it.correct, state.cleanness(it), rng) }
            ?: unchanged(state)

    // MARK: - Booking

    /**
     * A challenge books an answer the moment it is graded and asks the next question:
     * nothing is shown, so nothing waits — a shown answer would be a breather against the clock.
     */
    private fun raced(state: NumbersRunState, tone: ToneKind, correct: Boolean, clean: Boolean, rng: Random): NumbersReduction =
        booked(state, correct, clean, rng).let { it.copy(effects = listOf(DrillEffect.Tone(tone)) + it.effects) }

    /** Book the answer, then put the next question up at the Sprossen the booking left. */
    private fun booked(
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
    private fun advanced(state: NumbersRunState, correct: Boolean, clean: Boolean): NumbersRunState {
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

    private fun unchanged(state: NumbersRunState) = NumbersReduction(state, emptyList())
}

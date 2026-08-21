package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Card
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.alsoAccepts

/**
 * The letter drill as pure state plus one reducer. The run's shape is [LetterDrillRunState];
 * what it can ask is [LetterDrillAvailability.Report].
 *
 * Its rungs are STAGES — they change what a question is rather than how big the number is — and
 * its verdict ladder carries a third outcome no slot task can produce. That is the whole of what
 * it does not share with the slot run; the ramp, the effects and the summary are the same ones.
 */
object LetterDrillRun {

    /** A fresh run at the rung the learner's vocabulary opens on. */
    fun open(config: LetterDrillRunConfig, rng: Random): LetterDrillRunState =
        openAt(config, config.report.entryLevel, rng)

    /** The same, forced to one rung — the deterministic way to reach a stage. */
    fun openAt(config: LetterDrillRunConfig, level: Int, rng: Random): LetterDrillRunState {
        val start = level.coerceIn(1, config.report.maxLevel)
        return LetterDrillRunState(
            config = config,
            task = sample(config, start, null, null, rng),
            index = 0,
            level = start,
            winsAtLevel = 0,
            done = 0,
            streak = 0,
            bestStreak = 0,
            missRun = 0,
            outcomes = emptyList(),
            chosen = null,
            feedback = TurnFeedback.Neutral,
            finished = false,
        )
    }

    fun reduce(
        state: LetterDrillRunState,
        intent: LetterDrillIntent,
        rng: Random,
    ): LetterDrillReduction = when (intent) {
        is LetterDrillIntent.Choose -> choose(state, intent.glyph)
        is LetterDrillIntent.Submit -> submit(state, intent.text)
        LetterDrillIntent.Reveal -> reveal(state)
        LetterDrillIntent.ConfirmPending -> confirm(state, rng)
        LetterDrillIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * What a typed answer earns, in ladder ORDER.
     *
     * Outside dictation — and defensively where the card or the grader is missing — a glyph is
     * exact after normalization with no typo budget: a one-glyph answer with a slip allowance
     * grades nothing at all.
     *
     * Dictation runs the whole catalog: exact wins; then a form the CARD itself teaches, which is
     * checked BEFORE the grader's own verdict because the review flow explicitly teaches those
     * forms ("auch: …") — it is never wrong and never somebody else's word, it simply is not what
     * played; then a slip; then the miss, which is also where the catalog-wide grader withdraws
     * typo credit for a genuinely different word.
     */
    fun verdict(
        input: String,
        task: LetterDrillTask,
        card: Card?,
        grader: CatalogAnswerGrader?,
    ): LetterVerdict {
        val trimmed = input.trim()
        if (task.stage != LetterStage.Dictation || card == null || grader == null) {
            return if (LetterDrill.gradeLetter(trimmed, task)) LetterVerdict.Clean else LetterVerdict.Wrong
        }
        val graded = grader.grade(trimmed, LetterDrill.dictationGradingCard(card, task))
        if (graded == Match.Exact) return LetterVerdict.Clean
        if (alsoAccepts(card, trimmed)) return LetterVerdict.Heard(task.display)
        return when (graded) {
            is Match.Typo -> LetterVerdict.Typo(graded.corrected)
            else -> LetterVerdict.Wrong
        }
    }

    /**
     * Leaving the run. A pending accepted answer books exactly as the explicit tap would, so
     * closing can neither lose it nor upgrade it; a revealed answer nobody confirmed books
     * nothing. [DrillRunSummary.newRecord] is always false — the letter drill keeps no record
     * store, so nothing it does can beat one.
     */
    fun close(state: LetterDrillRunState): LetterDrillClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = when (state.feedback) {
            TurnFeedback.Correct -> advanced(state, correct = true, clean = true)
            is TurnFeedback.Almost -> advanced(state, correct = true, clean = false)
            else -> state
        }
        val ended = pending.copy(feedback = TurnFeedback.Neutral, chosen = null, finished = true)
        val summary = if (ended.done == 0) {
            null
        } else {
            DrillRunSummary(ended.done, ended.bestStreak, newRecord = false)
        }
        return LetterDrillClose(ended, summary, effects)
    }

    // MARK: - Intents

    private fun choose(state: LetterDrillRunState, glyph: String): LetterDrillReduction {
        val task = state.task ?: return unchanged(state)
        if (state.chosen != null || !state.owesAnswer) return unchanged(state)
        val picked = state.copy(chosen = glyph)
        if (glyph != task.display) {
            return LetterDrillReduction(
                picked.copy(feedback = TurnFeedback.Revealed),
                listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)),
            )
        }
        return LetterDrillReduction(
            picked.copy(feedback = TurnFeedback.Correct),
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ArmAdvance(AdvanceTier.Explicit),
            ),
        )
    }

    private fun submit(state: LetterDrillRunState, text: String): LetterDrillReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.owesAnswer || text.trim().isEmpty()) return unchanged(state)
        val card = state.config.cards[task.answerRef]
        return when (val verdict = verdict(text, task, card, state.config.dictationGrader)) {
            LetterVerdict.Clean -> LetterDrillReduction(
                state.copy(feedback = TurnFeedback.Correct),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ArmAdvance(AdvanceTier.Explicit),
                ),
            )
            // why: both almost holds wait for a tap, and a held keyboard covers the button they
            // wait for — so neither arms a beat and both give the field back.
            is LetterVerdict.Typo -> almost(state, verdict.corrected, AlmostReason.Typo)
            is LetterVerdict.Heard -> almost(state, verdict.played, AlmostReason.Heard)
            LetterVerdict.Wrong -> LetterDrillReduction(
                state.copy(feedback = TurnFeedback.Revealed),
                listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)),
            )
        }
    }

    private fun almost(
        state: LetterDrillRunState,
        form: String,
        reason: AlmostReason,
    ): LetterDrillReduction = LetterDrillReduction(
        state.copy(feedback = TurnFeedback.Almost(form, reason)),
        listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Correct), DrillEffect.ReleaseFocus),
    )

    private fun reveal(state: LetterDrillRunState): LetterDrillReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        // why: the field stays EMPTY — the card carries the answer, and typing it in for the
        // learner would put the same word on screen twice.
        return LetterDrillReduction(
            state.copy(feedback = TurnFeedback.Revealed),
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)),
        )
    }

    private fun confirm(state: LetterDrillRunState, rng: Random): LetterDrillReduction =
        when (state.feedback) {
            TurnFeedback.Neutral -> unchanged(state)
            TurnFeedback.Correct -> booked(state, correct = true, clean = true, rng = rng)
            is TurnFeedback.Almost -> booked(state, correct = true, clean = false, rng = rng)
            TurnFeedback.Revealed -> booked(state, correct = false, clean = true, rng = rng)
        }

    private fun elapsed(state: LetterDrillRunState, rng: Random): LetterDrillReduction =
        if (state.feedback == TurnFeedback.Correct) {
            booked(state, correct = true, clean = true, rng = rng)
        } else {
            unchanged(state)
        }

    // MARK: - Booking

    private fun booked(
        state: LetterDrillRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): LetterDrillReduction {
        val next = advanced(state, correct, clean)
        val question = sample(
            state.config,
            next.level,
            state.task?.answerRef,
            state.task?.let { if (it.gapText == null) null else it.promptText },
            rng,
        )
        return LetterDrillReduction(
            next.copy(
                task = question,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next one must never
                // render a frame carrying the last one's answer.
                feedback = TurnFeedback.Neutral,
                chosen = null,
                // Nothing left to ask: end on the summary, never on a blank card.
                finished = question == null,
            ),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    private fun advanced(
        state: LetterDrillRunState,
        correct: Boolean,
        clean: Boolean,
    ): LetterDrillRunState {
        val step = DrillRamp.step(
            level = state.level,
            winsAtLevel = state.winsAtLevel,
            correct = correct,
            clean = clean,
            maxLevel = state.config.report.maxLevel,
            winsRequired = state.config.report.winsToAdvance,
        )
        val streak = if (correct) state.streak + 1 else 0
        return state.copy(
            level = step.level,
            winsAtLevel = step.winsAtLevel,
            streak = streak,
            bestStreak = maxOf(state.bestStreak, streak),
            missRun = if (correct) 0 else state.missRun + 1,
            outcomes = state.outcomes + outcome(correct, clean),
            done = state.done + 1,
        )
    }

    private fun outcome(correct: Boolean, clean: Boolean): AnswerOutcome = when {
        !correct -> AnswerOutcome.Wrong
        clean -> AnswerOutcome.Right
        else -> AnswerOutcome.Almost
    }

    /**
     * One question at [level]: dictation draws from the box, every other stage from the alphabet.
     * [avoiding] is the previous answer and [avoidingWord] the word it gapped, each of which kern
     * resamples once. Null ⇒ this device can ask nothing more.
     */
    private fun sample(
        config: LetterDrillRunConfig,
        level: Int,
        avoiding: String?,
        avoidingWord: String?,
        rng: Random,
    ): LetterDrillTask? {
        val report = config.report
        if (LetterDrill.stageFor(level) == LetterStage.Dictation &&
            report.dictationCandidates.isNotEmpty()
        ) {
            return LetterDrill.sampleDictation(
                report.dictationCandidates,
                report.alphabet,
                level,
                avoiding,
                rng,
            )
        }
        val alphabet = report.alphabet ?: return null
        if (report.promptableRefs.isEmpty()) return null
        return LetterDrill.sample(
            alphabet,
            report::examples,
            level,
            report.promptableRefs,
            avoiding,
            avoidingWord,
            rng,
        )
    }

    private fun unchanged(state: LetterDrillRunState) = LetterDrillReduction(state, emptyList())
}

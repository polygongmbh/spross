package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Card
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.alsoAccepts
import net.spross.kern.session.AnswerNormalizer

/**
 * The letter drill as pure state plus one reducer. The run's shape is [LetterDrillRunState];
 * what it can ask is [LetterDrillAvailability.Report].
 *
 * Its Sprossen are STAGES — they change what a question is rather than how big the number is.
 * That is the whole of what it does not share with the typed drills; the verdict ladder
 * ([TypedDrillVerdicts]), the ramp, the effects and the summary are the same ones.
 */
object LetterDrillRun {

    /** A fresh run where the ladder opens ([LetterDrillAvailability.Report.openingLevel]). */
    fun open(config: LetterDrillRunConfig, rng: Random): LetterDrillRunState =
        openAt(config, config.report.openingLevel(config.cleared), rng)

    /** The same, forced to one Sprosse — the deterministic way to reach a stage. */
    fun openAt(config: LetterDrillRunConfig, level: Int, rng: Random): LetterDrillRunState {
        val start = level.coerceIn(1, config.report.maxLevel)
        val opening = draw(config, start, null, null, emptySet(), rng)
        return LetterDrillRunState(
            config = config,
            task = opening.task,
            index = 0,
            level = opening.level,
            winsAtLevel = 0,
            clearedSprossen = emptySet(),
            blemished = false,
            core = DrillRunCore(),
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
        is LetterDrillIntent.InputChanged -> typed(state, intent.text)
        is LetterDrillIntent.Submit -> submit(state, intent.text)
        LetterDrillIntent.Reveal -> reveal(state)
        LetterDrillIntent.ConfirmPending -> confirm(state, rng)
        LetterDrillIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * What a typed answer is worth, in ladder ORDER — [Match.Exact], [Match.Typo] or [Match.Wrong].
     *
     * Outside dictation — and defensively where the card or the grader is missing — a glyph is
     * exact after normalization with no typo budget: a one-glyph answer with a slip allowance
     * grades nothing at all.
     *
     * Dictation asks the one word that PLAYED, not a catalog card: exact wins; another form the
     * card lists ([alsoAccepts]) is a word that did not play, and is refused before a slip budget
     * could soften it into an almost; then a slip; then the miss, which is also where the
     * catalog-wide grader withdraws typo credit for a genuinely different word.
     */
    fun grade(
        input: String,
        task: LetterDrillTask,
        card: Card?,
        grader: CatalogAnswerGrader?,
    ): Match {
        val trimmed = input.trim()
        if (task.stage != LetterStage.Dictation || card == null || grader == null) {
            return if (LetterDrill.gradeLetter(trimmed, task)) Match.Exact else Match.Wrong
        }
        val graded = grader.grade(trimmed, LetterDrill.dictationGradingCard(card, task))
        if (graded == Match.Exact) return graded
        if (alsoAccepts(card, trimmed)) return Match.Wrong
        // why: the drill names no other word — the reveal is the played one alone.
        return graded as? Match.Typo ?: Match.Wrong
    }

    /**
     * Leaving the run. A pending accepted answer books exactly as the explicit tap would, so
     * closing can neither lose it nor upgrade it; a revealed answer nobody confirmed books
     * nothing. [DrillRunSummary.newRecord] is always false — the letter drill keeps no record
     * store, so nothing it does can beat one. What it does leave is the tile and typed
     * Sprossen it climbed off clean ([LetterDrillClose.clearedSprossen]).
     */
    fun close(state: LetterDrillRunState): LetterDrillClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = TypedDrillVerdicts.pending(state.feedback)
            ?.let { advanced(state, it.correct, it.clean) }
            ?: state
        val ended = pending.copy(feedback = TurnFeedback.Neutral, chosen = null, finished = true)
        val summary = if (ended.done == 0) {
            null
        } else {
            DrillRunSummary(ended.done, ended.bestStreak, newRecord = false)
        }
        // why: dictation draws from the box, which grows — a Sprosse of it climbed today
        // says nothing about the words it will hold tomorrow.
        val cleared = ended.clearedSprossen.filter { LetterDrill.stageFor(it) != LetterStage.Dictation }
        return LetterDrillClose(ended, summary, cleared.toSet(), effects)
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

    /**
     * "Finishing the word IS the answer" — the live approve every typed drill shares
     * ([TypedDrillVerdicts.typed]). A tile question has no field, so a keystroke means nothing.
     *
     * No verdict here says its answer ([DrillEffect.SayAnswer]): the question already WAS the
     * sound, and the answer is the glyph or the word it said.
     */
    private fun typed(state: LetterDrillRunState, text: String): LetterDrillReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.typing) return unchanged(state)
        val verdict = TypedDrillVerdicts.typed(state.feedback, answer = null) {
            grade(text, task, state.config.cards[task.answerRef], state.config.dictationGrader) == Match.Exact
        } ?: return unchanged(state)
        return LetterDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /**
     * The explicit check. Nothing typed means the ask to see the answer ([reveal]) — the run
     * has ONE primary action, and its button and its Enter key may not disagree on it.
     */
    private fun submit(state: LetterDrillRunState, text: String): LetterDrillReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val card = state.config.cards[task.answerRef]
        val verdict = TypedDrillVerdicts.submit(grade(text, task, card, state.config.dictationGrader), answer = null)
        return LetterDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun reveal(state: LetterDrillRunState): LetterDrillReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal(answer = null)
        return LetterDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun confirm(state: LetterDrillRunState, rng: Random): LetterDrillReduction =
        TypedDrillVerdicts.confirmed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    private fun elapsed(state: LetterDrillRunState, rng: Random): LetterDrillReduction =
        TypedDrillVerdicts.elapsed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    // MARK: - Booking

    private fun booked(
        state: LetterDrillRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): LetterDrillReduction {
        val next = advanced(state, correct, clean)
        val question = draw(
            state.config,
            next.level,
            state.task?.answerRef,
            state.task?.let { if (it.gapText == null) null else it.promptText },
            next.solved,
            rng,
        )
        return LetterDrillReduction(
            next.copy(
                task = question.task,
                level = question.level,
                // A Sprosse the run was carried past keeps none of the wins banked below it.
                winsAtLevel = if (question.level == next.level) next.winsAtLevel else 0,
                // A Sprosse answered out is a Sprosse climbed off, and books on the same terms.
                clearedSprossen = DrillSprossen.leaving(
                    next.clearedSprossen,
                    next.level,
                    question.level,
                    next.blemished,
                ),
                blemished = next.blemished && question.level == next.level,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next one must never
                // render a frame carrying the last one's answer.
                feedback = TurnFeedback.Neutral,
                chosen = null,
                // Nothing left to ask: end on the summary, never on a blank card.
                finished = question.task == null,
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
            winsRequired = state.config.report.winsToAdvance,
        )
        val blemished = DrillSprossen.blemished(state.blemished, correct, clean)
        return state.copy(
            level = step.level,
            winsAtLevel = step.winsAtLevel,
            clearedSprossen = DrillSprossen.leaving(state.clearedSprossen, state.level, step.level, blemished),
            blemished = blemished && step.level == state.level,
            core = state.core.book(correct, clean, state.task?.let { DrillSolved.key(it) }),
        )
    }

    /**
     * The first Sprosse at or above [from] with something left to ask ([DrillLadder.climb]).
     * A stage the run has answered out is climbed past rather than repeated ([DrillSolved]).
     */
    private fun draw(
        config: LetterDrillRunConfig,
        from: Int,
        avoiding: String?,
        avoidingWord: String?,
        solved: Set<String>,
        rng: Random,
    ): DrillLadder.Sprosse<LetterDrillTask> =
        DrillLadder.climb(from, config.report.maxLevel) { level ->
            sample(config, level, avoiding, avoidingWord, solved, rng)
        }

    /**
     * One question at [level]: dictation draws from the box, every other stage from the alphabet.
     * [avoiding] is the previous answer and [avoidingWord] the word it gapped, each of which kern
     * resamples once. Null ⇒ this device, at this Sprosse, can ask nothing more.
     */
    private fun sample(
        config: LetterDrillRunConfig,
        level: Int,
        avoiding: String?,
        avoidingWord: String?,
        solved: Set<String>,
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
                solved,
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
            solved,
            rng,
        )
    }

    private fun unchanged(state: LetterDrillRunState) = LetterDrillReduction(state, emptyList())
}

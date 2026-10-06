package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * The opposites drill as pure state plus one reducer — the word scramble's typed run
 * ([WordScrambleRun]) over a different question: a word in the language being learned, and
 * its opposite typed back in the same language.
 *
 * The Sprossen are BANDS ([OppositesAvailability.Prompt.sprosse]): adjectives, then verbs,
 * then the prompts with more than one opposite. Answering the top band out ends the run, and
 * a band the box holds nothing for is climbed past. Nothing it does books a review.
 *
 * Kern never self-randomizes: every draw takes the caller's [Random].
 */
object OppositesRun {

    /** Three clean answers carry a Sprosse, as in the word scramble ([WordScrambleRun.WINS_TO_ADVANCE]). */
    const val WINS_TO_ADVANCE: Int = 3

    /** A fresh run, at the foot of the ladder: it fast-climbs the Sprossen earlier runs cleared. */
    fun open(config: OppositesRunConfig, rng: Random): OppositesRunState {
        val opening = draw(config, 1, null, emptySet(), rng)
        return OppositesRunState(
            config = config,
            task = opening.task,
            index = 0,
            sprosse = opening.sprosse,
            bestSprosse = opening.sprosse,
            winsAtSprosse = 0,
            clearedSprossen = emptySet(),
            core = DrillRunCore(pacing = DrillPacing.opening(opening.sprosse, standingRecord = 0)),
            feedback = TurnFeedback.Neutral,
            finished = opening.task == null,
        )
    }

    fun reduce(state: OppositesRunState, intent: OppositesIntent, rng: Random): OppositesReduction =
        when (intent) {
            is OppositesIntent.InputChanged -> typed(state, intent.text)
            is OppositesIntent.Submit -> submit(state, intent.text)
            OppositesIntent.Reveal -> reveal(state)
            OppositesIntent.ConfirmPending -> confirm(state, rng)
            OppositesIntent.AdvanceElapsed -> elapsed(state, rng)
            OppositesIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
        }

    /**
     * Grade [input] against every opposite at once ([OppositesTask.accepted]), forgiving the
     * slips their length forgives.
     *
     * The prompt itself is refused first: a pair like ausziehen/einziehen sits inside the typo
     * budget of its own opposite, and writing the word back is the one answer that is never
     * a slip of the right one.
     */
    fun grade(input: String, task: OppositesTask, config: OppositesRunConfig): Match {
        fun against(forms: List<String>) = gradeDrillAnswer(
            input = input,
            accepted = forms,
            display = forms.first(),
            language = task.language,
            cardId = task.cardId,
            normalizer = config.normalizer,
        )
        if (against(task.promptForms) == Match.Exact) return Match.Wrong
        return against(task.accepted)
    }

    /**
     * Leaving the run, on [WordScrambleRun.close]'s terms: a pending accepted answer books as
     * the tap would, a revealed one nobody confirmed books nothing, and no record is kept.
     */
    fun close(state: OppositesRunState): OppositesClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = TypedDrillVerdicts.pending(state.feedback)
            ?.let { advanced(state, it.correct, it.clean) }
            ?: state
        val ended = pending.copy(feedback = TurnFeedback.Neutral, finished = true)
        val summary = if (ended.done == 0) null else DrillRunSummary(ended.done, ended.bestAnswerStreak, newRecord = false)
        return OppositesClose(ended, summary, ended.bestSprosse, ended.clearedSprossen, effects)
    }

    // MARK: - Intents

    private fun typed(state: OppositesRunState, text: String): OppositesReduction {
        val task = state.task ?: return unchanged(state)
        val verdict = TypedDrillVerdicts.typed(state.feedback, state.saidAnswer) {
            grade(text, task, state.config) == Match.Exact
        } ?: return unchanged(state)
        return OppositesReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /** Nothing typed means the ask to see the answer ([reveal]) — one primary action. */
    private fun submit(state: OppositesRunState, text: String): OppositesReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val verdict = TypedDrillVerdicts.submit(grade(text, task, state.config), state.saidAnswer)
        return OppositesReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun reveal(state: OppositesRunState): OppositesReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal(state.saidAnswer)
        return OppositesReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun confirm(state: OppositesRunState, rng: Random): OppositesReduction =
        TypedDrillVerdicts.confirmed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    private fun elapsed(state: OppositesRunState, rng: Random): OppositesReduction =
        TypedDrillVerdicts.elapsed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    // MARK: - Booking

    private fun booked(state: OppositesRunState, correct: Boolean, clean: Boolean, rng: Random): OppositesReduction {
        val next = advanced(state, correct, clean)
        val question = draw(state.config, next.sprosse, state.task?.cardId, next.core.solved, rng)
        val moved = next.copy(
            task = question.task,
            sprosse = question.sprosse,
            bestSprosse = maxOf(next.bestSprosse, question.sprosse),
            // A Sprosse the run was carried past keeps none of the wins banked below it.
            winsAtSprosse = if (question.sprosse == next.sprosse) next.winsAtSprosse else 0,
            // A Sprosse answered out is a Sprosse climbed off, and books on the same terms.
            clearedSprossen = DrillSprossen.leaving(next.clearedSprossen, next.sprosse, question.sprosse, next.core.slipped),
            index = state.index + 1,
            // why: cleared in the SAME transaction as the question — the next card must
            // never render a frame carrying the last one's answer.
            feedback = TurnFeedback.Neutral,
            finished = question.task == null,
        )
        val paced = moved.copy(core = moved.core.paced(moved.sprosse, moved.newSprossen, endless = !moved.finished))
        return OppositesReduction(paced, listOf(DrillEffect.CancelAdvance, DrillEffect.Silence))
    }

    private fun advanced(state: OppositesRunState, correct: Boolean, clean: Boolean): OppositesRunState {
        val step = DrillRamp.step(
            sprosse = state.sprosse,
            winsAtSprosse = state.winsAtSprosse,
            correct = correct,
            clean = clean,
            winsRequired = DrillSprossen.winsRequired(state.sprosse, state.config.cleared, state.core.slipped, WINS_TO_ADVANCE),
            top = state.config.report.maxSprosse,
        )
        val core = state.core.book(correct, clean, state.task?.let { DrillSolved.key(it) })
        return state.copy(
            sprosse = step.sprosse,
            bestSprosse = maxOf(state.bestSprosse, step.sprosse),
            winsAtSprosse = step.winsAtSprosse,
            clearedSprossen = DrillSprossen.leaving(state.clearedSprossen, state.sprosse, step.sprosse, core.slipped),
            core = core,
        )
    }

    /** The first Sprosse at or above [from] with a prompt left to ask ([DrillLadder.climb]). */
    private fun draw(
        config: OppositesRunConfig,
        from: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): DrillLadder.Sprosse<OppositesTask> =
        DrillLadder.climb(from, config.report.maxSprosse) { sprosse ->
            sample(config.report, sprosse, avoiding, solved, rng)
        }

    /**
     * One question at [sprosse], drawn evenly across its band's unasked prompts — the bands do
     * not nest. [avoiding] is the prompt just asked. Null ⇒ the Sprosse is spent.
     */
    private fun sample(
        report: OppositesAvailability.Report,
        sprosse: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): OppositesTask? {
        val open = report.promptsAt(sprosse).filter { DrillSolved.oppositeKey(it.card.id) !in solved }
        if (open.isEmpty()) return null
        val prompt = DrillLadder.pickAvoiding(open, rng) { it.card.id == avoiding }
        return OppositesTask(
            cardId = prompt.card.id,
            language = prompt.card.target.lang,
            sprosse = sprosse,
            prompt = prompt.form,
            gloss = prompt.gloss,
            answers = prompt.opposites.map { OppositesAnswer(it.text, it.gloss) },
            accepted = prompt.opposites.flatMap { it.card.target.let { t -> listOf(t.text) + t.teaches + t.accepts } }.distinct(),
            promptForms = prompt.card.target.let { listOf(it.text) + it.teaches + it.accepts },
        )
    }

    private fun unchanged(state: OppositesRunState) = OppositesReduction(state, emptyList())
}

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

    /** Three clean answers carry a Sprosse ([DrillRamp.USUAL_WINS]). */
    const val WINS_TO_ADVANCE: Int = DrillRamp.USUAL_WINS

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
            OppositesIntent.ConfirmPending -> booked(state, TypedDrillVerdicts.confirmed(state.feedback), rng)
            OppositesIntent.AdvanceElapsed -> booked(state, TypedDrillVerdicts.elapsed(state.feedback), rng)
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

    /** Leaving the run, on [WordScrambleRun.close]'s terms ([LadderStanding.closing]): no record is kept. */
    fun close(state: OppositesRunState): OppositesClose {
        val ended = LadderStanding.closing(state, ::advanced).copy(feedback = TurnFeedback.Neutral, finished = true)
        val summary = LadderStanding.summary(ended, standingRecord = null)
        return OppositesClose(ended, summary, ended.bestSprosse, ended.clearedSprossen, LadderStanding.EFFECTS)
    }

    // MARK: - Intents

    private fun typed(state: OppositesRunState, text: String): OppositesReduction {
        val task = state.task ?: return unchanged(state)
        val verdict = TypedDrillVerdicts.typed(state.feedback) {
            grade(text, task, state.config) == Match.Exact
        } ?: return unchanged(state)
        return OppositesReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /** Nothing typed means the ask to see the answer ([reveal]) — one primary action. */
    private fun submit(state: OppositesRunState, text: String): OppositesReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val verdict = TypedDrillVerdicts.submit(grade(text, task, state.config))
        return OppositesReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun reveal(state: OppositesRunState): OppositesReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal()
        return OppositesReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    // MARK: - Booking

    private fun booked(state: OppositesRunState, answer: DrillBooking?, rng: Random): OppositesReduction {
        answer ?: return unchanged(state)
        val next = advanced(state, answer)
        val question = draw(state.config, next.sprosse, state.task?.cardId, next.core.solved, rng)
        val moved = next.withStanding(next.standing.carriedTo(question.sprosse)).copy(
            task = question.task,
            index = state.index + 1,
            // why: cleared in the SAME transaction as the question — the next card must
            // never render a frame carrying the last one's answer.
            feedback = TurnFeedback.Neutral,
            finished = question.task == null,
        )
        val paced = moved.copy(core = moved.core.paced(moved.sprosse, moved.newSprossen, endless = !moved.finished))
        return OppositesReduction(paced, LadderStanding.EFFECTS)
    }

    private fun advanced(state: OppositesRunState, answer: DrillBooking): OppositesRunState =
        state.withStanding(state.standing.answered(
            answer,
            winsRequired = DrillSprossen.winsRequired(state.sprosse, state.config.cleared, state.core.slipped, WINS_TO_ADVANCE),
            top = state.config.report.maxSprosse,
            solves = state.task?.let { DrillSolved.key(it) },
        ))

    private val OppositesRunState.standing: LadderStanding
        get() = LadderStanding(sprosse, bestSprosse, winsAtSprosse, clearedSprossen, core)

    private fun OppositesRunState.withStanding(to: LadderStanding): OppositesRunState = copy(
        sprosse = to.sprosse,
        bestSprosse = to.bestSprosse,
        winsAtSprosse = to.winsAtSprosse,
        clearedSprossen = to.clearedSprossen,
        core = to.core,
    )

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

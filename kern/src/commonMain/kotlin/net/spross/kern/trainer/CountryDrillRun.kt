package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.AnswerNormalizer

/**
 * The atlas drill as pure state plus one reducer — the third sibling of [NumbersRun] and
 * [LetterDrillRun]. The run's shape is [CountryDrillRunState]; what it can ask is
 * [CountryDrill].
 *
 * It types like the slot run — writing the name out IS the answer — and climbs like the
 * letter run, one ladder for the whole run rather than one per exercise. That, and the Sprosse
 * it REACHED being what a close reports, is the whole of what it does not share with them;
 * the ramp, the effects and the summary are the same ones.
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed, so
 * none is taken. No default arguments: they do not cross the ObjC boundary.
 */
object CountryDrillRun {

    /** A fresh run from the foot of the ladder. */
    fun open(config: CountryDrillRunConfig, rng: Random): CountryDrillRunState =
        openAt(config, 1, rng)

    /**
     * A fresh run opened ON [sprosse], clamped to the ladder. The page opens a run on the lowest
     * Sprosse the learner has not answered out ([CountryDrillClose.clearedSprossen]), or on
     * the one they tapped.
     */
    fun openAt(config: CountryDrillRunConfig, sprosse: Int, rng: Random): CountryDrillRunState {
        val start = sprosse.coerceIn(1, CountryDrill.MAX_SPROSSE)
        // A run opens on a Sprosse ARRIVED at — the lowest one not answered out, or the one
        // tapped — so its first question is one that Sprosse added.
        val opening =
            CountryDrill.draw(config.content, start, config.reverse, null, emptySet(), rng, arriving = true)
        val content = config.content
        return CountryDrillRunState(
            config = config,
            // why: nothing is solved yet, so only an atlas with no rows at all comes back empty.
            task = requireNotNull(opening.task) {
                "no atlas question for ${content.source}→${content.target}"
            },
            index = 0,
            sprosse = opening.sprosse,
            bestSprosse = opening.sprosse,
            winsAtSprosse = 0,
            core = DrillRunCore(pacing = DrillPacing.opening(opening.sprosse, config.standingRecord)),
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
        CountryDrillIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
    }

    /**
     * Grade [input] against every form the task accepts, the way a drill grades: no
     * article forgiven — the atlas authors "die Schweiz" and the
     * bare form beside it, so leniency would accept an article the learner never wrote.
     *
     * [Match.OtherWord] where the forgiven slip is really ANOTHER entry's name in the same
     * kind ([CountryNameIndex]): `Ĉilio` for `Ĉinio` is Chile, not a typo of China, and the
     * refusal carries which.
     */
    fun grade(
        input: String,
        task: CountryDrillTask,
        config: CountryDrillRunConfig,
    ): Match {
        val match = gradeDrillAnswer(
            input = input,
            accepted = task.accepted,
            display = task.display,
            language = config.answerLanguage,
            cardId = "atlas",
            normalizer = config.normalizer,
        )
        if (match == Match.Exact) return match
        return config.nameIndex?.otherName(task.kind, input) ?: match
    }

    /**
     * Leaving, from the corner or from "Fertig". A pending accepted answer books first,
     * exactly as the explicit tap would — closing may neither lose it nor upgrade it — and a
     * revealed answer nobody confirmed books nothing. An untouched run reports nothing at
     * all, though it still names the Sprosse it opened on, which the page files either way.
     *
     * [standingRecord] is what the platform's store holds now; the write is strictly
     * greater, so re-closing a resumed run never double-claims.
     */
    fun close(state: CountryDrillRunState, standingRecord: Int): CountryDrillClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = TypedDrillVerdicts.pending(state.feedback)
            ?.let { advanced(state, it.correct, it.clean) }
            ?: state
        val ended = pending.copy(feedback = TurnFeedback.Neutral, otherWord = null, finished = true)
        val summary = if (ended.done == 0) {
            null
        } else {
            DrillRunSummary(ended.done, ended.bestStreak, ended.bestStreak > standingRecord)
        }
        val cleared = CountryDrill.cleared(state.config.content, state.config.reverse, ended.core.solvedClean)
        return CountryDrillClose(ended, summary, ended.bestSprosse, cleared, effects)
    }

    // MARK: - Intents

    /**
     * "Finishing the name IS the answer" — the live approve, the review loop's rule and the
     * one a learner arrives here already knowing ([TypedDrillVerdicts.typed]).
     */
    private fun typed(state: CountryDrillRunState, text: String): CountryDrillReduction {
        val verdict = TypedDrillVerdicts.typed(state.feedback, state.saidAnswer) {
            grade(text, state.task, state.config) == Match.Exact
        } ?: return unchanged(state)
        return CountryDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /**
     * The explicit check. Nothing typed means the ask to see the answer ([reveal]) — the run
     * has ONE primary action, and its button and its Enter key may not disagree on it.
     */
    private fun submit(state: CountryDrillRunState, text: String): CountryDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val verdict = TypedDrillVerdicts.submit(grade(text, state.task, state.config), state.saidAnswer)
        return CountryDrillReduction(
            state.copy(feedback = verdict.feedback, otherWord = verdict.otherWord),
            verdict.effects,
        )
    }

    private fun reveal(state: CountryDrillRunState): CountryDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal(state.saidAnswer)
        return CountryDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun confirm(state: CountryDrillRunState, rng: Random): CountryDrillReduction =
        TypedDrillVerdicts.confirmed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    private fun elapsed(state: CountryDrillRunState, rng: Random): CountryDrillReduction =
        TypedDrillVerdicts.elapsed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    // MARK: - Booking

    /** Book the answer, then put the next question up at the Sprosse the booking left. */
    private fun booked(
        state: CountryDrillRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): CountryDrillReduction {
        val next = advanced(state, correct, clean)
        // why: sampled against the id it must avoid — kern resamples once, so a repeat needs
        // two unlucky draws rather than one.
        val draw = CountryDrill.draw(
            state.config.content,
            next.sprosse,
            state.config.reverse,
            state.task.id,
            next.solved,
            rng,
            arriving = next.sprosse > state.sprosse,
        )
        return CountryDrillReduction(
            paced(next.copy(
                // Nothing left to ask: end on the summary, never on a question already answered.
                task = draw.task ?: state.task,
                finished = draw.task == null,
                sprosse = draw.sprosse,
                // A Sprosse the run answered out is a Sprosse it stood on, and the wins banked on the
                // one below stay behind with it.
                bestSprosse = maxOf(next.bestSprosse, draw.sprosse),
                winsAtSprosse = if (draw.sprosse == next.sprosse) next.winsAtSprosse else 0,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next card must
                // never render one frame carrying the last one's answer.
                feedback = TurnFeedback.Neutral,
                otherWord = null,
            )),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    /** The pause a booked answer leaves due, if one is ([DrillPacing]). */
    private fun paced(state: CountryDrillRunState): CountryDrillRunState =
        state.copy(core = state.core.paced(state.sprosse, state.newSprossen, endless = !state.finished))

    /** The booking itself: the ramp, the streak, the tallies — the Sprosse it reached included. */
    private fun advanced(
        state: CountryDrillRunState,
        correct: Boolean,
        clean: Boolean,
    ): CountryDrillRunState {
        val step = CountryDrill.step(
            sprosse = state.sprosse,
            winsAtSprosse = state.winsAtSprosse,
            correct = correct,
            clean = clean,
            fast = state.config.fast,
        )
        return state.copy(
            sprosse = step.sprosse,
            bestSprosse = maxOf(state.bestSprosse, step.sprosse),
            winsAtSprosse = step.winsAtSprosse,
            core = state.core.book(correct, clean, DrillSolved.key(state.task)),
        )
    }

    private fun unchanged(state: CountryDrillRunState) = CountryDrillReduction(state, emptyList())
}

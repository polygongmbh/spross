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
        CountryDrillIntent.ConfirmPending -> booked(state, TypedDrillVerdicts.confirmed(state.feedback), rng)
        CountryDrillIntent.AdvanceElapsed -> booked(state, TypedDrillVerdicts.elapsed(state.feedback), rng)
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
     * Leaving, from the corner or from "Fertig" ([LadderStanding.closing]). An untouched run
     * reports nothing at all, though it still names the Sprosse it opened on, which the page
     * files either way.
     *
     * [standingRecord] is what the platform's store holds now; the write is strictly
     * greater, so re-closing a resumed run never double-claims.
     */
    fun close(state: CountryDrillRunState, standingRecord: Int): CountryDrillClose {
        val ended = LadderStanding.closing(state, ::advanced)
            .copy(feedback = TurnFeedback.Neutral, otherWord = null, finished = true)
        val summary = LadderStanding.summary(ended, standingRecord)
        val cleared = CountryDrill.cleared(state.config.content, state.config.reverse, ended.core.solvedClean)
        return CountryDrillClose(ended, summary, ended.bestSprosse, cleared, LadderStanding.EFFECTS)
    }

    // MARK: - Intents

    /**
     * "Finishing the name IS the answer" — the live approve, the review loop's rule and the
     * one a learner arrives here already knowing ([TypedDrillVerdicts.typed]).
     */
    private fun typed(state: CountryDrillRunState, text: String): CountryDrillReduction {
        val verdict = TypedDrillVerdicts.typed(state.feedback) {
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
        val verdict = TypedDrillVerdicts.submit(grade(text, state.task, state.config))
        return CountryDrillReduction(
            state.copy(feedback = verdict.feedback, otherWord = verdict.otherWord),
            verdict.effects,
        )
    }

    private fun reveal(state: CountryDrillRunState): CountryDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal()
        return CountryDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the Sprosse the booking left. */
    private fun booked(state: CountryDrillRunState, answer: DrillBooking?, rng: Random): CountryDrillReduction {
        answer ?: return unchanged(state)
        val next = advanced(state, answer)
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
        val moved = next.withStanding(next.standing.carriedTo(draw.sprosse)).copy(
            // Nothing left to ask: end on the summary, never on a question already answered.
            task = draw.task ?: state.task,
            finished = draw.task == null,
            index = state.index + 1,
            // why: cleared in the SAME transaction as the question — the next card must
            // never render one frame carrying the last one's answer.
            feedback = TurnFeedback.Neutral,
            otherWord = null,
        )
        // The pause a booked answer leaves due, if one is ([DrillPacing]).
        val paced = moved.copy(core = moved.core.paced(moved.sprosse, moved.newSprossen, endless = !moved.finished))
        return CountryDrillReduction(paced, LadderStanding.EFFECTS)
    }

    /** The booking itself: the ramp, the answer streak, the tallies — the Sprosse it reached included. */
    private fun advanced(state: CountryDrillRunState, answer: DrillBooking): CountryDrillRunState =
        state.withStanding(state.standing.answered(
            answer,
            winsRequired = CountryDrill.winsToAdvance(state.config.fast),
            solves = DrillSolved.key(state.task),
        ))

    /** The run clears by answering a Sprosse out ([CountryDrill.cleared]), so its standing carries no climb ledger. */
    private val CountryDrillRunState.standing: LadderStanding
        get() = LadderStanding(sprosse, bestSprosse, winsAtSprosse, emptySet(), core)

    private fun CountryDrillRunState.withStanding(to: LadderStanding): CountryDrillRunState = copy(
        sprosse = to.sprosse,
        bestSprosse = to.bestSprosse,
        winsAtSprosse = to.winsAtSprosse,
        core = to.core,
    )

    private fun unchanged(state: CountryDrillRunState) = CountryDrillReduction(state, emptyList())
}

package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * A pair drill as pure state plus one reducer — a sibling of [NumbersRun] and [LetterDrillRun],
 * written once for the atlas ([CountryDrillRun]) and the calendar ([DateDrillRun]).
 * The run's shape is [PairDrillRunState]; what it can ask is its config's
 * ([PairDrillRunConfig]), which is the only place the two drills differ.
 *
 * It types like the slot run — writing the answer out IS the answer, on the verdict ladder
 * [TypedDrillVerdicts] climbs — and climbs like the letter run, one ladder for the whole run
 * rather than one per exercise. The ramp, the effects and the summary are every laddered
 * run's ([LadderStanding]).
 *
 * Each drill's run is an object of its own on this class, so a run state crosses to Swift
 * typed by the question it holds.
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed, so
 * none is taken. No default arguments: they do not cross the ObjC boundary.
 */
abstract class PairDrillRun<T : PairDrillTask> internal constructor() {

    /** A fresh run from the foot of the ladder. */
    fun open(config: PairDrillRunConfig<T>, rng: Random): PairDrillRunState<T> = openAt(config, 1, rng)

    /**
     * A fresh run opened ON [sprosse], clamped to the ladder. The page opens a run on the lowest
     * Sprosse the learner has not answered out ([PairDrillClose.clearedSprossen]), or on the
     * one they tapped.
     */
    fun openAt(config: PairDrillRunConfig<T>, sprosse: Int, rng: Random): PairDrillRunState<T> {
        val start = sprosse.coerceIn(1, config.top)
        // A run opens on a Sprosse ARRIVED at — the lowest one not answered out, or the one
        // tapped — so its first question is one that Sprosse added.
        val opening = config.draw(start, null, emptySet(), rng, arriving = true)
        return PairDrillRunState(
            config = config,
            // why: nothing is solved yet, so only material with no rows at all comes back empty.
            task = requireNotNull(opening.task) { "no ${config.drill} question for ${config.storageKey}" },
            index = 0,
            sprosse = opening.sprosse,
            bestSprosse = opening.sprosse,
            winsAtSprosse = 0,
            core = DrillRunCore(pacing = DrillPacing.opening(opening.sprosse, config.standingRecord)),
            feedback = TurnFeedback.Neutral,
            finished = false,
        )
    }

    fun reduce(state: PairDrillRunState<T>, intent: PairDrillIntent, rng: Random): PairDrillReduction<T> =
        when (intent) {
            is PairDrillIntent.InputChanged -> typed(state, intent.text)
            is PairDrillIntent.Submit -> submit(state, intent.text)
            PairDrillIntent.Reveal -> reveal(state)
            PairDrillIntent.ConfirmPending -> booked(state, TypedDrillVerdicts.confirmed(state.feedback), rng)
            PairDrillIntent.AdvanceElapsed -> booked(state, TypedDrillVerdicts.elapsed(state.feedback), rng)
            PairDrillIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
        }

    /**
     * Leaving, from the corner or from "Fertig" ([LadderStanding.closing]). An untouched run
     * reports nothing at all, though it still names the Sprosse it opened on, which the page
     * files either way.
     *
     * [standingRecord] is what the platform's store holds now; the write is strictly
     * greater, so re-closing a resumed run never double-claims.
     */
    fun close(state: PairDrillRunState<T>, standingRecord: Int): PairDrillClose<T> {
        val ended = LadderStanding.closing(state, ::advanced)
            .copy(feedback = TurnFeedback.Neutral, otherWord = null, finished = true)
        val summary = LadderStanding.summary(ended, standingRecord, state.config.drill)
        val cleared = state.config.answeredOut(ended.core.solvedClean)
        return PairDrillClose(ended, summary, ended.bestSprosse, cleared, LadderStanding.EFFECTS)
    }

    // MARK: - Intents

    /**
     * "Finishing the answer IS the answer" — the live approve, the review loop's rule and the
     * one a learner arrives here already knowing ([TypedDrillVerdicts.typed]).
     */
    private fun typed(state: PairDrillRunState<T>, text: String): PairDrillReduction<T> {
        val verdict = TypedDrillVerdicts.typed(state.feedback) {
            state.config.grade(text, state.task) == Match.Exact
        } ?: return unchanged(state)
        return PairDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /**
     * The explicit check. Nothing typed means the ask to see the answer ([reveal]) — the run
     * has ONE primary action, and its button and its Enter key may not disagree on it.
     */
    private fun submit(state: PairDrillRunState<T>, text: String): PairDrillReduction<T> {
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val verdict = TypedDrillVerdicts.submit(state.config.grade(text, state.task))
        return PairDrillReduction(
            state.copy(feedback = verdict.feedback, otherWord = verdict.otherWord),
            verdict.effects,
        )
    }

    private fun reveal(state: PairDrillRunState<T>): PairDrillReduction<T> {
        if (!state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal()
        return PairDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the Sprosse the booking left. */
    private fun booked(state: PairDrillRunState<T>, answer: DrillBooking?, rng: Random): PairDrillReduction<T> {
        answer ?: return unchanged(state)
        val next = advanced(state, answer)
        // why: sampled against the question it must avoid — kern resamples once, so a repeat
        // needs two unlucky draws rather than one.
        val draw = state.config.draw(next.sprosse, state.task, next.solved, rng, arriving = next.sprosse > state.sprosse)
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
        return PairDrillReduction(paced, LadderStanding.EFFECTS)
    }

    /** The booking itself: the ramp, the answer streak, the tallies — the Sprosse it reached included. */
    private fun advanced(state: PairDrillRunState<T>, answer: DrillBooking): PairDrillRunState<T> {
        val stood = state.withStanding(state.standing.answered(
            answer,
            winsRequired = PairDrill.winsToAdvance(state.config.fast),
            solves = state.config.solvedKey(state.task),
        ))
        // why: booked with the answer, so a first-sight hint is shown for exactly the one card
        // that owed it — a run that closes and reopens meets it again.
        val hint = state.config.firstSight(state.task) ?: return stood
        return stood.copy(hinted = state.hinted + hint)
    }

    /** The run clears by answering a Sprosse out ([PairDrillRunConfig.answeredOut]), so its standing carries no climb ledger. */
    private val PairDrillRunState<T>.standing: LadderStanding
        get() = LadderStanding(sprosse, bestSprosse, winsAtSprosse, emptySet(), core)

    private fun PairDrillRunState<T>.withStanding(to: LadderStanding): PairDrillRunState<T> = copy(
        sprosse = to.sprosse,
        bestSprosse = to.bestSprosse,
        winsAtSprosse = to.winsAtSprosse,
        core = to.core,
    )

    private fun unchanged(state: PairDrillRunState<T>) = PairDrillReduction(state, emptyList())
}

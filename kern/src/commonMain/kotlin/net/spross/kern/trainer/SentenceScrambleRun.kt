package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The sentence scramble as pure state plus one reducer. The run's shape is
 * [SentenceScrambleRunState]; what it can ask is [SentenceScrambleAvailability.Report].
 *
 * It is an ARRANGEMENT drill: the words are given and the order is withheld, so it trains word
 * order rather than vocabulary. Nothing it does books a review — arrangement is not recall, the
 * same reason the letter drill keeps no schedule.
 *
 * Its Sprossen are BANDS of difficulty that do not overlap
 * ([SentenceScrambleAvailability.Report.phrasesAt]): each asks its own slice of the phrases,
 * easiest first, so a Sprosse up is harder phrases and a Sprosse down easier ones.
 * The ladder STOPS at its last band ([DrillRamp.step]'s `top`) — a number past it would
 * promise phrases that do not exist — so answering the top band out ends the run.
 * The ramp, the effects and the summary are the ones every drill shares.
 *
 * Kern never self-randomizes: the deal takes the caller's [Random], so a seeded run is
 * reproducible end to end and identical on both platforms.
 */
object SentenceScrambleRun {

    /** Three clean arrangements carry a Sprosse ([DrillRamp.USUAL_WINS]). */
    const val WINS_TO_ADVANCE: Int = DrillRamp.USUAL_WINS

    /** How many deals a shuffle gets before an already-ordered one is allowed to stand. */
    private const val DEAL_ATTEMPTS = 8

    /**
     * A fresh run, at the foot of the ladder: it fast-climbs the Sprossen earlier runs cleared
     * ([DrillSprossen.winsRequired]).
     */
    fun open(config: SentenceScrambleRunConfig, rng: Random): SentenceScrambleRunState =
        openAt(config, 1, rng)

    /** The same, forced to one Sprosse — the deterministic way to reach a band. */
    fun openAt(
        config: SentenceScrambleRunConfig,
        sprosse: Int,
        rng: Random,
    ): SentenceScrambleRunState {
        val start = sprosse.coerceIn(1, config.report.maxSprosse)
        val opening = draw(config, start, null, emptySet(), rng)
        return SentenceScrambleRunState(
            config = config,
            task = opening.task,
            placed = emptyList(),
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

    fun reduce(
        state: SentenceScrambleRunState,
        intent: SentenceScrambleIntent,
        rng: Random,
    ): SentenceScrambleReduction = when (intent) {
        is SentenceScrambleIntent.PlaceAtom -> place(state, intent.index)
        is SentenceScrambleIntent.ReturnAtom -> take(state, intent.index)
        SentenceScrambleIntent.ConfirmPending -> booked(state, TypedDrillVerdicts.confirmed(state.feedback), rng)
        SentenceScrambleIntent.AdvanceElapsed -> booked(state, TypedDrillVerdicts.elapsed(state.feedback), rng)
        SentenceScrambleIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
    }

    /**
     * Leaving the run, on [WordScrambleRun.close]'s terms ([LadderStanding.closing]):
     * no streak record is kept, and the Sprosse the run stands on is not booked.
     */
    fun close(state: SentenceScrambleRunState): SentenceScrambleClose {
        val ended = LadderStanding.closing(state, ::advanced).copy(feedback = TurnFeedback.Neutral, finished = true)
        val summary = LadderStanding.summary(ended, standingRecord = null)
        return SentenceScrambleClose(ended, summary, ended.bestSprosse, ended.clearedSprossen, LadderStanding.EFFECTS)
    }

    // MARK: - Intents

    /**
     * Committing the LAST atom is the answer: there is no check tap, the same way a finished
     * name needs none on the typed drills. A wrong order opens the card on the authored one
     * rather than letting the arrangement be permuted until it lands.
     */
    private fun place(state: SentenceScrambleRunState, index: Int): SentenceScrambleReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.owesAnswer) return unchanged(state)
        if (index !in task.shuffled.indices || state.isPlaced(index)) return unchanged(state)
        val next = state.copy(placed = state.placed + index)
        if (!next.complete) return SentenceScrambleReduction(next, emptyList())
        return if (ScrambleGrading.isSolved(next.placedAtoms, task.canonical, task.alternatives)) {
            SentenceScrambleReduction(
                next.copy(feedback = TurnFeedback.Correct),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ArmAdvance(AdvanceBeat.Explicit),
                ),
            )
        } else {
            SentenceScrambleReduction(
                next.copy(feedback = TurnFeedback.Revealed),
                listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)),
            )
        }
    }

    /** An atom taken back, while the arrangement is still the learner's to give. */
    private fun take(state: SentenceScrambleRunState, index: Int): SentenceScrambleReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        if (index !in state.placed.indices) return unchanged(state)
        val kept = state.placed.filterIndexed { at, _ -> at != index }
        return SentenceScrambleReduction(state.copy(placed = kept), emptyList())
    }

    // MARK: - Booking

    private fun booked(
        state: SentenceScrambleRunState,
        answer: DrillBooking?,
        rng: Random,
    ): SentenceScrambleReduction {
        answer ?: return unchanged(state)
        val next = advanced(state, answer)
        val question = draw(state.config, next.sprosse, state.task?.cardId, next.solved, rng)
        val moved = next.withStanding(next.standing.carriedTo(question.sprosse)).copy(
            task = question.task,
            index = state.index + 1,
            // why: cleared in the SAME transaction as the question — the next arrangement
            // must never render a frame carrying the last one's atoms.
            placed = emptyList(),
            feedback = TurnFeedback.Neutral,
            // Nothing left to ask: end on the summary, never on a blank card.
            finished = question.task == null,
        )
        // The pause a booked answer leaves due, if one is ([DrillPacing]).
        val paced = moved.copy(core = moved.core.paced(moved.sprosse, moved.newSprossen, endless = !moved.finished))
        return SentenceScrambleReduction(paced, LadderStanding.EFFECTS)
    }

    private fun advanced(state: SentenceScrambleRunState, answer: DrillBooking): SentenceScrambleRunState =
        state.withStanding(state.standing.answered(
            answer,
            winsRequired = DrillSprossen.winsRequired(state.sprosse, state.config.cleared, state.core.slipped, WINS_TO_ADVANCE),
            top = state.config.report.maxSprosse,
            solves = state.task?.let { DrillSolved.key(it) },
        ))

    private val SentenceScrambleRunState.standing: LadderStanding
        get() = LadderStanding(sprosse, bestSprosse, winsAtSprosse, clearedSprossen, core)

    private fun SentenceScrambleRunState.withStanding(to: LadderStanding): SentenceScrambleRunState = copy(
        sprosse = to.sprosse,
        bestSprosse = to.bestSprosse,
        winsAtSprosse = to.winsAtSprosse,
        clearedSprossen = to.clearedSprossen,
        core = to.core,
    )

    /**
     * The first Sprosse at or above [from] with a phrase left to ask ([DrillLadder.climb]).
     * A Sprosse the run has answered out is climbed past rather than repeated ([DrillSolved]).
     */
    private fun draw(
        config: SentenceScrambleRunConfig,
        from: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): DrillLadder.Sprosse<SentenceScrambleTask> =
        DrillLadder.climb(from, config.report.maxSprosse) { sprosse ->
            sample(config.report, sprosse, avoiding, solved, rng)
        }

    /**
     * One question at [sprosse], drawn EVENLY across the phrases of its band still unsolved.
     * The bands do not nest, so nothing in one needs singling out as what it added.
     * [avoiding] is the phrase just asked, which kern resamples once.
     * Null ⇒ this Sprosse has nothing left.
     */
    private fun sample(
        report: SentenceScrambleAvailability.Report,
        sprosse: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): SentenceScrambleTask? {
        val open = report.phrasesAt(sprosse)
            .filter { DrillSolved.sentenceKey(it.card.id) !in solved }
        if (open.isEmpty()) return null
        val pool = open.filter { it.card.id != avoiding }.ifEmpty { open }
        val phrase = pool[rng.nextInt(pool.size)]
        return SentenceScrambleTask(
            cardId = phrase.card.id,
            language = phrase.card.target.lang,
            shuffled = dealt(phrase.atoms, phrase.alternativeOrders, rng),
            canonical = phrase.atoms,
            alternatives = phrase.alternativeOrders,
            display = phrase.card.target.text,
            gloss = phrase.card.source.text,
        )
    }

    /**
     * The atoms dealt out. A deal that comes back in the authored order is re-rolled — the
     * question would be "tap them left to right" — but only so many times, so an arrangement
     * with too few distinct orders still gets dealt rather than looping.
     */
    private fun dealt(
        atoms: List<ScrambleAtom>,
        alternatives: List<List<ScrambleAtom>>,
        rng: Random,
    ): List<ScrambleAtom> {
        var deal = atoms.shuffled(rng)
        var attempts = 0
        while (ScrambleGrading.isSolved(deal, atoms, alternatives) && attempts < DEAL_ATTEMPTS) {
            deal = atoms.shuffled(rng)
            attempts++
        }
        return deal
    }

    private fun unchanged(state: SentenceScrambleRunState) =
        SentenceScrambleReduction(state, emptyList())
}

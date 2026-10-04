package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AdvanceTier
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

    /**
     * Three clean arrangements carry a Sprosse.
     *
     * A band is one difficulty, so three in a row say as much about it as five would;
     * there is no almost to mis-credit either — this drill grades whole-position —
     * which is what made two too quick on the drills that have one.
     */
    const val WINS_TO_ADVANCE: Int = 3

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
        SentenceScrambleIntent.ConfirmPending -> confirm(state, rng)
        SentenceScrambleIntent.AdvanceElapsed -> elapsed(state, rng)
        SentenceScrambleIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
    }

    /**
     * Leaving the run. A pending accepted answer books exactly as the explicit tap would, so
     * closing can neither lose it nor upgrade it; a revealed arrangement nobody confirmed books
     * nothing. [DrillRunSummary.newRecord] is always false — this drill keeps no streak record,
     * so nothing it does can beat one.
     *
     * The Sprosse the run stands on when it leaves is NOT booked: a Sprosse is earned by being
     * climbed off before the run's first slip ([DrillSprossen]), and stopping halfway up one
     * earns nothing.
     */
    fun close(state: SentenceScrambleRunState): SentenceScrambleClose {
        val effects = listOf(DrillEffect.CancelAdvance, DrillEffect.Silence)
        val pending = TypedDrillVerdicts.pending(state.feedback)
            ?.let { advanced(state, it.correct, it.clean) }
            ?: state
        val ended = pending.copy(feedback = TurnFeedback.Neutral, finished = true)
        val summary = if (ended.done == 0) {
            null
        } else {
            DrillRunSummary(ended.done, ended.bestAnswerStreak, newRecord = false)
        }
        return SentenceScrambleClose(ended, summary, ended.bestSprosse, ended.clearedSprossen, effects)
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
                listOfNotNull(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    next.saidAnswer,
                    DrillEffect.ArmAdvance(AdvanceTier.Explicit),
                ),
            )
        } else {
            SentenceScrambleReduction(
                next.copy(feedback = TurnFeedback.Revealed),
                listOfNotNull(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong), next.saidAnswer),
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

    private fun confirm(
        state: SentenceScrambleRunState,
        rng: Random,
    ): SentenceScrambleReduction = TypedDrillVerdicts.confirmed(state.feedback)
        ?.let { booked(state, it.correct, it.clean, rng) }
        ?: unchanged(state)

    private fun elapsed(
        state: SentenceScrambleRunState,
        rng: Random,
    ): SentenceScrambleReduction = TypedDrillVerdicts.elapsed(state.feedback)
        ?.let { booked(state, it.correct, it.clean, rng) }
        ?: unchanged(state)

    // MARK: - Booking

    private fun booked(
        state: SentenceScrambleRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): SentenceScrambleReduction {
        val next = advanced(state, correct, clean)
        val question = draw(
            state.config,
            next.sprosse,
            state.task?.cardId,
            next.solved,
            rng,
        )
        return SentenceScrambleReduction(
            paced(next.copy(
                task = question.task,
                sprosse = question.sprosse,
                bestSprosse = maxOf(next.bestSprosse, question.sprosse),
                // A Sprosse the run was carried past keeps none of the wins banked below it.
                winsAtSprosse = if (question.sprosse == next.sprosse) next.winsAtSprosse else 0,
                // A Sprosse answered out is a Sprosse climbed off, and books on the same terms.
                clearedSprossen = DrillSprossen.leaving(
                    next.clearedSprossen,
                    next.sprosse,
                    question.sprosse,
                    next.core.slipped,
                ),
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next arrangement
                // must never render a frame carrying the last one's atoms.
                placed = emptyList(),
                feedback = TurnFeedback.Neutral,
                // Nothing left to ask: end on the summary, never on a blank card.
                finished = question.task == null,
            )),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    /** The pause a booked answer leaves due, if one is ([DrillPacing]). */
    private fun paced(state: SentenceScrambleRunState): SentenceScrambleRunState = state.copy(
        core = state.core.paced(state.sprosse, state.newSprossen, endless = !state.finished),
    )

    private fun advanced(
        state: SentenceScrambleRunState,
        correct: Boolean,
        clean: Boolean,
    ): SentenceScrambleRunState {
        val held = state.config.cleared
        val step = DrillRamp.step(
            sprosse = state.sprosse,
            winsAtSprosse = state.winsAtSprosse,
            correct = correct,
            clean = clean,
            winsRequired = DrillSprossen.winsRequired(state.sprosse, held, state.core.slipped, WINS_TO_ADVANCE),
            top = state.config.report.maxSprosse,
        )
        val core = state.core.book(correct, clean, state.task?.let { DrillSolved.key(it) })
        return state.copy(
            sprosse = step.sprosse,
            bestSprosse = maxOf(state.bestSprosse, step.sprosse),
            winsAtSprosse = step.winsAtSprosse,
            clearedSprossen = DrillSprossen.leaving(
                state.clearedSprossen,
                state.sprosse,
                step.sprosse,
                core.slipped,
            ),
            core = core,
        )
    }

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

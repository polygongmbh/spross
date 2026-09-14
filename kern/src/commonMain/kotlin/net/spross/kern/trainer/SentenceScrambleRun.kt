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
 * Its Sprossen are LENGTHS, and they ACCUMULATE: a Sprosse adds a longer phrase to what the one
 * below it could ask and keeps everything below ([SentenceScrambleAvailability.Report.atomsAt]),
 * so the ladder tops out at the longest phrase the box has actually unlocked with every shorter
 * one still in the deck. The ramp, the effects and the summary are the ones every drill shares.
 *
 * Kern never self-randomizes: the deal takes the caller's [Random], so a seeded run is
 * reproducible end to end and identical on both platforms.
 */
object SentenceScrambleRun {

    /**
     * Five clean arrangements carry a Sprosse. Two made the ladder climb faster than the learner
     * could feel it — a clean, an almost and a clean promoted on the third answer, which read as
     * the almost having counted.
     */
    const val WINS_TO_ADVANCE: Int = 5

    /** How many deals a shuffle gets before an already-ordered one is allowed to stand. */
    private const val DEAL_ATTEMPTS = 8

    /** A fresh run where the ladder stands ([SentenceScrambleRunConfig.entryLevel]). */
    fun open(config: SentenceScrambleRunConfig, rng: Random): SentenceScrambleRunState =
        openAt(config, config.entryLevel, rng)

    /** The same, forced to one Sprosse — the deterministic way to reach a length. */
    fun openAt(
        config: SentenceScrambleRunConfig,
        level: Int,
        rng: Random,
    ): SentenceScrambleRunState {
        val start = level.coerceIn(1, config.report.maxLevel)
        val opening = draw(config, start, null, emptySet(), rng)
        return SentenceScrambleRunState(
            config = config,
            task = opening.task,
            placed = emptyList(),
            index = 0,
            level = opening.level,
            bestLevel = opening.level,
            winsAtLevel = 0,
            clearedSprossen = emptySet(),
            blemished = false,
            core = DrillRunCore(),
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
        SentenceScrambleIntent.Reveal -> reveal(state)
        SentenceScrambleIntent.ConfirmPending -> confirm(state, rng)
        SentenceScrambleIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * Leaving the run. A pending accepted answer books exactly as the explicit tap would, so
     * closing can neither lose it nor upgrade it; a revealed arrangement nobody confirmed books
     * nothing. [DrillRunSummary.newRecord] is always false — this drill keeps no streak record,
     * so nothing it does can beat one.
     *
     * The Sprosse the run stands on when it leaves is NOT booked: a rung is earned by being
     * climbed off unblemished ([DrillRungs]), and stopping halfway up one earns nothing.
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
            DrillRunSummary(ended.done, ended.bestStreak, newRecord = false)
        }
        return SentenceScrambleClose(ended, summary, ended.bestLevel, ended.clearedSprossen, effects)
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
        return if (ScrambleGrading.isSolved(next.placedAtoms, task.canonical)) {
            SentenceScrambleReduction(
                next.copy(feedback = TurnFeedback.Correct),
                listOf(
                    DrillEffect.Silence,
                    DrillEffect.Tone(ToneKind.Correct),
                    DrillEffect.ArmAdvance(AdvanceTier.Explicit),
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

    private fun reveal(state: SentenceScrambleRunState): SentenceScrambleReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal()
        return SentenceScrambleReduction(state.copy(feedback = verdict.feedback), verdict.effects)
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
        val question = draw(state.config, next.level, state.task?.cardId, next.solved, rng)
        return SentenceScrambleReduction(
            next.copy(
                task = question.task,
                level = question.level,
                bestLevel = maxOf(next.bestLevel, question.level),
                // A Sprosse the run was carried past keeps none of the wins banked below it.
                winsAtLevel = if (question.level == next.level) next.winsAtLevel else 0,
                // A Sprosse answered out is a Sprosse climbed off, and books on the same terms.
                clearedSprossen = DrillRungs.leaving(
                    next.clearedSprossen,
                    next.level,
                    question.level,
                    next.blemished,
                ),
                blemished = next.blemished && question.level == next.level,
                index = state.index + 1,
                // why: cleared in the SAME transaction as the question — the next arrangement
                // must never render a frame carrying the last one's atoms.
                placed = emptyList(),
                feedback = TurnFeedback.Neutral,
                // Nothing left to ask: end on the summary, never on a blank card.
                finished = question.task == null,
            ),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    private fun advanced(
        state: SentenceScrambleRunState,
        correct: Boolean,
        clean: Boolean,
    ): SentenceScrambleRunState {
        val step = DrillRamp.step(
            level = state.level,
            winsAtLevel = state.winsAtLevel,
            correct = correct,
            clean = clean,
            winsRequired = WINS_TO_ADVANCE,
        )
        val blemished = DrillRungs.blemished(state.blemished, correct, clean)
        return state.copy(
            level = step.level,
            bestLevel = maxOf(state.bestLevel, step.level),
            winsAtLevel = step.winsAtLevel,
            clearedSprossen = DrillRungs.leaving(
                state.clearedSprossen,
                state.level,
                step.level,
                blemished,
            ),
            blemished = blemished && step.level == state.level,
            core = state.core.book(correct, clean, state.task?.let { DrillSolved.key(it) }),
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
        DrillLadder.climb(from, config.report.maxLevel) { level ->
            sample(config.report, level, avoiding, solved, rng)
        }

    /**
     * One question at [level], drawn EVENLY across every phrase the Sprosse admits — the atlas'
     * rule on the atlas' kind of ladder. [avoiding] is the phrase just asked, which kern
     * resamples once. Null ⇒ this Sprosse has nothing left.
     *
     * No tier is singled out. Narrowing to the newest length would be the rising floor again
     * under another name, and narrowing to the shortest would make the climb invisible; a flat
     * draw lets the new length in as one more card in the deck, and [DrillSolved] retires each
     * phrase as it is arranged clean, so the deck thins toward whatever the learner still owes
     * rather than toward a length the ladder picked for them.
     */
    private fun sample(
        report: SentenceScrambleAvailability.Report,
        level: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): SentenceScrambleTask? {
        val open = report.phrasesAt(level)
            .filter { DrillSolved.sentenceKey(it.card.id) !in solved }
        if (open.isEmpty()) return null
        val pool = open.filter { it.card.id != avoiding }.ifEmpty { open }
        val phrase = pool[rng.nextInt(pool.size)]
        return SentenceScrambleTask(
            cardId = phrase.card.id,
            language = phrase.card.target.lang,
            shuffled = dealt(phrase.atoms, rng),
            canonical = phrase.atoms,
            display = phrase.card.target.text,
            gloss = phrase.card.source.text,
        )
    }

    /**
     * The atoms dealt out. A deal that comes back in the authored order is re-rolled — the
     * question would be "tap them left to right" — but only so many times, so an arrangement
     * with too few distinct orders still gets dealt rather than looping.
     */
    private fun dealt(atoms: List<ScrambleAtom>, rng: Random): List<ScrambleAtom> {
        var deal = atoms.shuffled(rng)
        var attempts = 0
        while (ScrambleGrading.isSolved(deal, atoms) && attempts < DEAL_ATTEMPTS) {
            deal = atoms.shuffled(rng)
            attempts++
        }
        return deal
    }

    private fun unchanged(state: SentenceScrambleRunState) =
        SentenceScrambleReduction(state, emptyList())
}

package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * The word scramble as pure state plus one reducer. The run's shape is [WordScrambleRunState];
 * what it can ask is [WordScrambleAvailability.Report].
 *
 * It TYPES like the atlas and the slot run — writing the word out is the answer — so a Sprosse
 * changes how much help the cue gives and how much word there is to spell: the first three take
 * the anchors away one at a time ([WordScrambleMasking]) and every Sprosse, those included,
 * raises the length floor the pool is drawn against
 * ([WordScrambleAvailability.Report.lettersAt]). Nothing it does books a review; spelling a word
 * back from its own letters is not the recall the schedule measures.
 *
 * Kern never self-randomizes: every draw and every mix takes the caller's [Random], so a seeded
 * run is reproducible end to end and identical on both platforms.
 */
object WordScrambleRun {

    /**
     * Five clean spellings carry a Sprosse. Two made the ladder climb faster than the learner
     * could feel it — a clean, an almost and a clean promoted on the third answer, which read
     * as the almost having counted.
     */
    const val WINS_TO_ADVANCE: Int = 5

    /**
     * How wide the draw reaches into the shortest words the Sprosse still has unasked. The
     * Sprosse sets the floor ([WordScrambleAvailability.Report.lettersAt]); within it the
     * gentlest words come first, and the window empties from its short end as they are answered.
     */
    private const val DRAW_WINDOW = 8

    /** A fresh run where the ladder stands ([WordScrambleRunConfig.entryLevel]). */
    fun open(config: WordScrambleRunConfig, rng: Random): WordScrambleRunState =
        openAt(config, config.entryLevel, rng)

    /** The same, forced to one Sprosse — the deterministic way to reach a masking stage. */
    fun openAt(config: WordScrambleRunConfig, level: Int, rng: Random): WordScrambleRunState {
        val start = level.coerceIn(1, config.report.maxLevel)
        val opening = draw(config, start, null, emptySet(), rng)
        return WordScrambleRunState(
            config = config,
            task = opening.task,
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
        state: WordScrambleRunState,
        intent: WordScrambleIntent,
        rng: Random,
    ): WordScrambleReduction = when (intent) {
        is WordScrambleIntent.InputChanged -> typed(state, intent.text)
        is WordScrambleIntent.Submit -> submit(state, intent.text)
        WordScrambleIntent.Reveal -> reveal(state)
        WordScrambleIntent.ConfirmPending -> confirm(state, rng)
        WordScrambleIntent.AdvanceElapsed -> elapsed(state, rng)
    }

    /**
     * Grade [input] against the ONE form whose letters were handed over ([WordScrambleTask.accepted]),
     * forgiving the slips that form's LENGTH forgives ([WordScrambleRunConfig.grader]).
     *
     * Deliberately unlike an ordinary produce review, where knowing any authored form is the
     * point: the question here is not "what is this word" but "what do these letters spell", and
     * a synonym or a variant that cannot be written from them is no answer to it. A learner shown
     * the letters of "mpya" who types "kipya" knows the word and still has not read the letters.
     * Typo tolerance applies on top — a slip is a slip — but a DIFFERENT form is not a slip.
     *
     * An anagram that happens to be a different real word is neither caught nor credited:
     * there is no dictionary here, and the catalog can only disprove what it teaches.
     */
    fun grade(input: String, task: WordScrambleTask, config: WordScrambleRunConfig): Match =
        gradeDrillAnswer(
            input = input,
            accepted = task.accepted,
            display = task.display,
            language = task.language,
            cardId = task.cardId,
            normalizer = config.grader,
        )

    /**
     * Leaving the run. A pending accepted answer books exactly as the explicit tap would, so
     * closing can neither lose it nor upgrade it; a revealed answer nobody confirmed books
     * nothing. [DrillRunSummary.newRecord] is always false — this drill keeps no streak record,
     * so nothing it does can beat one.
     *
     * The Sprosse the run stands on when it leaves is NOT booked: a rung is earned by being
     * climbed off unblemished ([DrillRungs]), and stopping halfway up one earns nothing.
     */
    fun close(state: WordScrambleRunState): WordScrambleClose {
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
        return WordScrambleClose(ended, summary, ended.bestLevel, ended.clearedSprossen, effects)
    }

    // MARK: - Intents

    /** "Finishing the word IS the answer" — the live approve ([TypedDrillVerdicts.typed]). */
    private fun typed(state: WordScrambleRunState, text: String): WordScrambleReduction {
        val task = state.task ?: return unchanged(state)
        val verdict = TypedDrillVerdicts.typed(state.feedback) {
            grade(text, task, state.config) == Match.Exact
        } ?: return unchanged(state)
        return WordScrambleReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /**
     * The explicit check. Nothing typed means the ask to see the answer ([reveal]) — the run
     * has ONE primary action, and its button and its Enter key may not disagree on it.
     */
    private fun submit(state: WordScrambleRunState, text: String): WordScrambleReduction {
        val task = state.task ?: return unchanged(state)
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val verdict = TypedDrillVerdicts.submit(grade(text, task, state.config))
        return WordScrambleReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun reveal(state: WordScrambleRunState): WordScrambleReduction {
        if (state.task == null || !state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal()
        return WordScrambleReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    private fun confirm(state: WordScrambleRunState, rng: Random): WordScrambleReduction =
        TypedDrillVerdicts.confirmed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    private fun elapsed(state: WordScrambleRunState, rng: Random): WordScrambleReduction =
        TypedDrillVerdicts.elapsed(state.feedback)
            ?.let { booked(state, it.correct, it.clean, rng) }
            ?: unchanged(state)

    // MARK: - Booking

    private fun booked(
        state: WordScrambleRunState,
        correct: Boolean,
        clean: Boolean,
        rng: Random,
    ): WordScrambleReduction {
        val next = advanced(state, correct, clean)
        val question = draw(state.config, next.level, state.task?.cardId, next.solved, rng)
        return WordScrambleReduction(
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
                // why: cleared in the SAME transaction as the question — the next card must
                // never render a frame carrying the last one's answer.
                feedback = TurnFeedback.Neutral,
                // Nothing left to ask: end on the summary, never on a blank card.
                finished = question.task == null,
            ),
            listOf(DrillEffect.CancelAdvance, DrillEffect.Silence),
        )
    }

    private fun advanced(
        state: WordScrambleRunState,
        correct: Boolean,
        clean: Boolean,
    ): WordScrambleRunState {
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
     * The first Sprosse at or above [from] with a word left to ask ([DrillLadder.climb]). A
     * Sprosse the run has answered out is climbed past rather than repeated ([DrillSolved]) —
     * the same word at a harder mixing is a question of its own, so the pool refills as the
     * ladder rises.
     */
    private fun draw(
        config: WordScrambleRunConfig,
        from: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): DrillLadder.Sprosse<WordScrambleTask> =
        DrillLadder.climb(from, config.report.maxLevel) { level ->
            sample(config.report, level, avoiding, solved, rng)
        }

    /**
     * One question at [level], drawn from the shortest words the Sprosse admits that it has not
     * asked yet. [avoiding] is the word just asked, which kern resamples once. Null ⇒ the
     * Sprosse is spent.
     *
     * The Sprosse is a LENGTH FLOOR and it rises ([WordScrambleAvailability.Report.lettersAt]),
     * so a short word drops out of the pool as the ladder climbs — spelling a four-letter word
     * back stops being a question once a learner can spell a ten-letter one, and a run opened
     * high starts on words that are worth opening high for.
     *
     * The FORM comes out of the same [Random] the word did, so a word carrying several spellable
     * forms (a Swahili stem's agreeing forms) still deals reproducibly — and only the forms that
     * clear the floor are dealt, or a stem would answer a long Sprosse with its shortest
     * agreement.
     */
    private fun sample(
        report: WordScrambleAvailability.Report,
        level: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): WordScrambleTask? {
        val floor = report.lettersAt(level)
        val open = report.words
            .filter { DrillSolved.wordKey(level, it.card.id) !in solved }
            .map { it to it.formsFrom(floor) }
            .filter { (_, forms) -> forms.isNotEmpty() }
        if (open.isEmpty()) return null
        val window = open.sortedBy { (_, forms) -> forms.minOf { it.letters } }.take(DRAW_WINDOW)
        val drawn = window.filter { (spelling, _) -> spelling.card.id != avoiding }.ifEmpty { window }
        val (spelling, forms) = drawn[rng.nextInt(drawn.size)]
        val card = spelling.card
        val form = forms[rng.nextInt(forms.size)]
        return WordScrambleTask(
            cardId = card.id,
            language = card.target.lang,
            level = level,
            scrambled = WordScrambleMasking.scramble(form, level, rng),
            accepted = listOf(form),
            display = form,
            gloss = card.source.text,
        )
    }

    private fun unchanged(state: WordScrambleRunState) = WordScrambleReduction(state, emptyList())
}

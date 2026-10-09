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
 * changes how much help the cue gives and how much word there is to spell: the third takes the
 * opening letter away ([WordScrambleMasking]) and every Sprosse raises the minimum word length
 * ([WordScrambleAvailability.Report.lettersAt]). The ladder STOPS at the longest Sprosse the
 * pool fills ([DrillRamp.step]'s `top`), so answering it out ends the run. Nothing it does books
 * a review; spelling a word back from its own letters is not the recall the schedule measures.
 *
 * Kern never self-randomizes: every draw and every mix takes the caller's [Random], so a seeded
 * run is reproducible end to end and identical on both platforms.
 */
object WordScrambleRun {

    /** Three clean spellings carry a Sprosse ([DrillRamp.USUAL_WINS]). */
    const val WINS_TO_ADVANCE: Int = DrillRamp.USUAL_WINS

    /**
     * How wide the draw reaches into the shortest words the Sprosse still has unasked. The
     * Sprosse sets the floor ([WordScrambleAvailability.Report.lettersAt]); within it the
     * gentlest words come first, and the window empties from its short end as they are answered.
     */
    private const val DRAW_WINDOW = 8

    /**
     * A fresh run, at the foot of the ladder: it fast-climbs the Sprossen earlier runs cleared
     * ([DrillSprossen.winsRequired]).
     */
    fun open(config: WordScrambleRunConfig, rng: Random): WordScrambleRunState =
        openAt(config, 1, rng)

    /** The same, forced to one Sprosse — the deterministic way to reach a masking stage. */
    fun openAt(config: WordScrambleRunConfig, sprosse: Int, rng: Random): WordScrambleRunState {
        val start = sprosse.coerceIn(1, config.report.maxSprosse)
        val opening = draw(config, start, null, emptySet(), rng)
        return WordScrambleRunState(
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

    fun reduce(
        state: WordScrambleRunState,
        intent: WordScrambleIntent,
        rng: Random,
    ): WordScrambleReduction = when (intent) {
        is WordScrambleIntent.InputChanged -> typed(state, intent.text)
        is WordScrambleIntent.Submit -> submit(state, intent.text)
        WordScrambleIntent.Reveal -> reveal(state)
        WordScrambleIntent.ConfirmPending -> booked(state, TypedDrillVerdicts.confirmed(state.feedback), rng)
        WordScrambleIntent.AdvanceElapsed -> booked(state, TypedDrillVerdicts.elapsed(state.feedback), rng)
        WordScrambleIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
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
            normalizer = config.normalizer,
        )

    /**
     * Leaving the run ([LadderStanding.closing]). [DrillRunSummary.newRecord] is always false —
     * this drill keeps no streak record, so nothing it does can beat one.
     *
     * The Sprosse the run stands on when it leaves is NOT booked: a Sprosse is earned by being
     * climbed off before the run's first slip ([DrillSprossen]), and stopping halfway up one
     * earns nothing.
     */
    fun close(state: WordScrambleRunState): WordScrambleClose {
        val ended = LadderStanding.closing(state, ::advanced).copy(feedback = TurnFeedback.Neutral, finished = true)
        val summary = LadderStanding.summary(ended, standingRecord = null)
        return WordScrambleClose(ended, summary, ended.bestSprosse, ended.clearedSprossen, LadderStanding.EFFECTS)
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

    // MARK: - Booking

    private fun booked(state: WordScrambleRunState, answer: DrillBooking?, rng: Random): WordScrambleReduction {
        answer ?: return unchanged(state)
        val next = advanced(state, answer)
        val question = draw(state.config, next.sprosse, state.task?.cardId, next.solved, rng)
        val moved = next.withStanding(next.standing.carriedTo(question.sprosse)).copy(
            task = question.task,
            index = state.index + 1,
            // why: cleared in the SAME transaction as the question — the next card must
            // never render a frame carrying the last one's answer.
            feedback = TurnFeedback.Neutral,
            // Nothing left to ask: end on the summary, never on a blank card.
            finished = question.task == null,
        )
        // The pause a booked answer leaves due, if one is ([DrillPacing]).
        val paced = moved.copy(core = moved.core.paced(moved.sprosse, moved.newSprossen, endless = !moved.finished))
        return WordScrambleReduction(paced, LadderStanding.EFFECTS)
    }

    private fun advanced(state: WordScrambleRunState, answer: DrillBooking): WordScrambleRunState =
        state.withStanding(state.standing.answered(
            answer,
            winsRequired = DrillSprossen.winsRequired(state.sprosse, state.config.cleared, state.core.slipped, WINS_TO_ADVANCE),
            top = state.config.report.maxSprosse,
            solves = state.task?.let { DrillSolved.key(it) },
        ))

    private val WordScrambleRunState.standing: LadderStanding
        get() = LadderStanding(sprosse, bestSprosse, winsAtSprosse, clearedSprossen, core)

    private fun WordScrambleRunState.withStanding(to: LadderStanding): WordScrambleRunState = copy(
        sprosse = to.sprosse,
        bestSprosse = to.bestSprosse,
        winsAtSprosse = to.winsAtSprosse,
        clearedSprossen = to.clearedSprossen,
        core = to.core,
    )

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
        DrillLadder.climb(from, config.report.maxSprosse) { sprosse ->
            sample(config.report, sprosse, avoiding, solved, rng)
        }

    /**
     * One question at [sprosse], drawn from the shortest words the Sprosse admits that it has not
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
        sprosse: Int,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): WordScrambleTask? {
        val floor = report.lettersAt(sprosse)
        val open = report.words
            .filter { DrillSolved.wordKey(sprosse, it.card.id) !in solved }
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
            sprosse = sprosse,
            scrambled = WordScrambleMasking.scramble(form, sprosse, rng),
            accepted = listOf(form),
            display = form,
            gloss = card.source.text,
        )
    }

    private fun unchanged(state: WordScrambleRunState) = WordScrambleReduction(state, emptyList())
}

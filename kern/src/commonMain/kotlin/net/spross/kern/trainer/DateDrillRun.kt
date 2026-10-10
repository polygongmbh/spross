package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.AnswerNormalizer

/**
 * The dates drill as pure state plus one reducer — the atlas run's shape on the dates
 * ladder. The run's shape is [DateDrillRunState]; what it can ask is [DateDrill].
 *
 * Kern never self-randomizes: every draw takes the caller's [Random]. No clock is needed,
 * so none is taken. No default arguments: they do not cross the ObjC boundary.
 */
object DateDrillRun {

    /** A fresh run from the foot of the ladder. */
    fun open(config: DateDrillRunConfig, rng: Random): DateDrillRunState =
        openAt(config, 1, rng)

    /**
     * A fresh run opened ON [sprosse], clamped to the ladder. The page opens a run on the lowest
     * Sprosse the learner has not answered out ([DateDrillClose.clearedSprossen]), or on the
     * one they tapped.
     */
    fun openAt(config: DateDrillRunConfig, sprosse: Int, rng: Random): DateDrillRunState {
        val content = config.content
        val start = sprosse.coerceIn(1, DateDrill.maxSprosse(content, config.reverse))
        // A run opens on a Sprosse ARRIVED at, so its first question is one that Sprosse added.
        val opening =
            DateDrill.draw(content, start, config.reverse, null, emptySet(), rng, arriving = true)
        return DateDrillRunState(
            config = config,
            // why: nothing is solved yet, so a fresh calendar's first Sprosse always has a question.
            task = requireNotNull(opening.task) {
                "no dates question for ${content.source}→${content.target}"
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
        state: DateDrillRunState,
        intent: DateDrillIntent,
        rng: Random,
    ): DateDrillReduction = when (intent) {
        is DateDrillIntent.InputChanged -> typed(state, intent.text)
        is DateDrillIntent.Submit -> submit(state, intent.text)
        DateDrillIntent.Reveal -> reveal(state)
        DateDrillIntent.ConfirmPending -> booked(state, TypedDrillVerdicts.confirmed(state.feedback), rng)
        DateDrillIntent.AdvanceElapsed -> booked(state, TypedDrillVerdicts.elapsed(state.feedback), rng)
        DateDrillIntent.KeepPracticing -> unchanged(state.copy(core = state.core.resumed()))
    }

    /**
     * Grade [input] against every reading the task accepts, the way a drill grades: no
     * article forgiven — the pattern authors its own
     * article, and its `accepts` are what admit the accusative.
     *
     * The Sprossen whose answer is a numeral carry the numbers drill's value check
     * ([NumberReadingIndex]): a day that names another day (`vierte` for `dritte`) is
     * refused and named, never forgiven. Nothing is inherited — the bare-name Sprossen have
     * no numeral to check, so they pass no index.
     *
     * The bare-name Sprossen carry the calendar's own instead ([DateNameIndex], on a miss
     * only): where the whole answer is the name, `Juli` typed for `Juni` is July and the
     * refusal says so. An assembled date keeps its bridge — a month slip inside one is a
     * typo by the owner's ruling, so the index is never consulted above the bare Sprossen.
     * The warm-up Sprosse consults neither: a tapped tile is a name the learner READ, so a
     * miss is nothing but the wrong one of four and the reveal already stands on the card.
     */
    fun grade(
        input: String,
        task: DateDrillTask,
        config: DateDrillRunConfig,
    ): Match {
        val match = gradeDrillAnswer(
            input = input,
            accepted = task.accepted,
            display = task.display,
            language = config.answerLanguage,
            cardId = "dates",
            normalizer = config.normalizer,
            index = numberIndex(task, config),
        )
        if (match == Match.Exact) return match
        return when (task.kind) {
            DateTaskKind.Weekday, DateTaskKind.Month ->
                config.nameIndex?.otherName(task, input) ?: match
            else -> match
        }
    }

    /**
     * Leaving, from the corner or from "Fertig" ([LadderStanding.closing]). An untouched run
     * reports nothing at all, though it still names the Sprosse it opened on, which the page
     * files either way.
     *
     * [standingRecord] is what the platform's store holds now; the write is strictly
     * greater, so re-closing a resumed run never double-claims.
     */
    fun close(state: DateDrillRunState, standingRecord: Int): DateDrillClose {
        val ended = LadderStanding.closing(state, ::advanced)
            .copy(feedback = TurnFeedback.Neutral, otherWord = null, finished = true)
        val summary = LadderStanding.summary(ended, standingRecord)
        val cleared = DateDrill.cleared(state.config.content, state.config.reverse, ended.core.solvedClean)
        return DateDrillClose(ended, summary, ended.bestSprosse, cleared, LadderStanding.EFFECTS)
    }

    // MARK: - Intents

    /** "Finishing the reading IS the answer" — the live approve ([TypedDrillVerdicts.typed]). */
    private fun typed(state: DateDrillRunState, text: String): DateDrillReduction {
        val verdict = TypedDrillVerdicts.typed(state.feedback) {
            grade(text, state.task, state.config) == Match.Exact
        } ?: return unchanged(state)
        return DateDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    /**
     * The explicit check. Nothing typed means the ask to see the answer ([reveal]) — the run
     * has ONE primary action, and its button and its Enter key may not disagree on it.
     */
    private fun submit(state: DateDrillRunState, text: String): DateDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        if (AnswerNormalizer.isBlankAnswer(text)) return reveal(state)
        val verdict = TypedDrillVerdicts.submit(grade(text, state.task, state.config))
        return DateDrillReduction(
            state.copy(feedback = verdict.feedback, otherWord = verdict.otherWord),
            verdict.effects,
        )
    }

    private fun reveal(state: DateDrillRunState): DateDrillReduction {
        if (!state.owesAnswer) return unchanged(state)
        val verdict = TypedDrillVerdicts.reveal()
        return DateDrillReduction(state.copy(feedback = verdict.feedback), verdict.effects)
    }

    // MARK: - Booking

    /** Book the answer, then put the next question up at the Sprosse the booking left. */
    private fun booked(state: DateDrillRunState, answer: DrillBooking?, rng: Random): DateDrillReduction {
        answer ?: return unchanged(state)
        val next = advanced(state, answer)
        // why: sampled against the key it must avoid — kern resamples once, so a repeat needs
        // two unlucky draws rather than one.
        val draw = DateDrill.draw(
            state.config.content,
            next.sprosse,
            state.config.reverse,
            DrillSolved.key(state.task),
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
        return DateDrillReduction(paced, LadderStanding.EFFECTS)
    }

    /** The booking itself: the ramp, the answer streak, the tallies — the Sprosse it reached included. */
    private fun advanced(state: DateDrillRunState, answer: DrillBooking): DateDrillRunState {
        val stood = state.withStanding(state.standing.answered(
            answer,
            winsRequired = DateDrill.winsToAdvance(state.config.fast),
            solves = DrillSolved.key(state.task),
        ))
        // why: booked with the answer, so the word is shown for exactly the one card
        // that owed it — a run that closes and reopens meets it again, which is the
        // place-value hint's rule and the honest one for a Sprosse climbed twice.
        return stood.copy(seenKinds = state.seenKinds + state.task.kind)
    }

    /** The run clears by answering a Sprosse out ([DateDrill.cleared]), so its standing carries no climb ledger. */
    private val DateDrillRunState.standing: LadderStanding
        get() = LadderStanding(sprosse, bestSprosse, winsAtSprosse, emptySet(), core)

    private fun DateDrillRunState.withStanding(to: LadderStanding): DateDrillRunState = copy(
        sprosse = to.sprosse,
        bestSprosse = to.bestSprosse,
        winsAtSprosse = to.winsAtSprosse,
        core = to.core,
    )

    /**
     * The value check, on the Sprossen whose answer is a NUMERAL — a reading like `dritte`,
     * never a date like `3.6.`. Nothing is inherited: `gradeDrillAnswer` defaults to none,
     * and the bare-name and parsed Sprossen stay without one.
     */
    private fun numberIndex(task: DateDrillTask, config: DateDrillRunConfig): NumberReadingIndex? =
        when {
            // A date written in digits is graded exact-only by the normalizer itself, and an
            // index of NUMBER WORDS has nothing to say about `3.6.` either way.
            task.digits -> null
            task.kind == DateTaskKind.NameChoice -> null
            task.kind == DateTaskKind.Weekday || task.kind == DateTaskKind.Month -> null
            else -> config.normalizer?.let { NumberReadingIndex.of(config.answerLanguage, it) }
        }

    private fun unchanged(state: DateDrillRunState) = DateDrillReduction(state, emptyList())
}

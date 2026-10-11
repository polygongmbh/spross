package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.Question
import net.spross.kern.session.Saying
import net.spross.kern.session.TurnFeedback

/**
 * Everything one pair drill run is fixed to, resolved when it opens and never per question:
 * which way round it asks, how long a Sprosse is, and the grader — and, through the drill's
 * own subclass ([CountryDrillRunConfig], [DateDrillRunConfig]), the joined material and what
 * the drill asks, draws and grades out of it.
 *
 * The internal members are that drill's half of [PairDrillRun]; nothing else differs.
 */
abstract class PairDrillRunConfig<T : PairDrillTask> internal constructor(
    private val source: Language,
    private val target: Language,
    /** Which side prompts: forward asks in the language the learner knows. */
    val reverse: Boolean,
    /**
     * Whether a Sprosse falls on ONE clean win instead of three. Its price is
     * [PairDrill.fastUnlocked]'s and the page that opened the run has already paid it.
     */
    val fast: Boolean,
    /**
     * The STRICT drill grader for the language the answer is owed in — which is the
     * learner's OWN on a reversed run. Null (a preview with no language info) grades plainly.
     */
    val normalizer: AnswerNormalizer?,
    /**
     * The answer-streak record the platform's store holds for this page — beating it is what a pause
     * for improving names ([DrillPacing]); 0 where none stood yet.
     */
    val standingRecord: Int,
    /**
     * The Sprossen earlier runs answered out in THIS run's direction, as the platform's store
     * holds them ([NumbersMode.clearedKey]) — answering out one it does not hold is what a
     * pause for improving names ([DrillPacing]).
     */
    val cleared: Set<Int>,
) {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = PairDrill.answerLanguage(source, target, reverse)

    /** The language the prompt is written in — the other side of the same pair. */
    val promptLanguage: Language get() = PairDrill.promptLanguage(source, target, reverse)

    /** The roster entry and the key its pair's ladder is filed under. */
    internal abstract val drill: Drill
    internal abstract val storageKey: String

    /** The ladder's last Sprosse with content of its own. */
    internal abstract val top: Int

    /**
     * The first Sprosse at or above [sprosse] with a question left, never one [solved] holds,
     * resampled once off [avoid] — the drill's own climb ([DrillLadder.climb]).
     */
    internal abstract fun draw(
        sprosse: Int,
        avoid: T?,
        solved: Set<String>,
        rng: Random,
        arriving: Boolean,
    ): DrillLadder.Sprosse<T>

    /** [input] against everything [task] accepts, with the drill's own refusals. */
    internal abstract fun grade(input: String, task: T): Match

    /** What a clean answer to [task] retires ([DrillSolved]). */
    internal abstract fun solvedKey(task: T): String

    /** The Sprossen [solved] answers out ([DrillSolved.cleared]). */
    internal abstract fun answeredOut(solved: Set<String>): Set<Int>

    /** The first-sight hint [task] shows once per run, by the key [PairDrillRunState.hinted] keeps; null where none. */
    internal open fun firstSight(task: T): String? = null

    /** The card, as the drill states its question. */
    internal abstract fun question(run: PairDrillRunState<T>): Question

    /** How the answer is given — written, or off tiles. */
    internal abstract fun controls(run: PairDrillRunState<T>): AnswerControls

    internal fun bookings(summary: DrillRunSummary?, bestSprosse: Int, clearedSprossen: Set<Int>): DrillBookings =
        DrillBookings.typed(drill, storageKey, target, reverse, summary, bestSprosse, clearedSprossen)
}

/**
 * One pair drill run, whole and immutable: the question on screen, what the answers have done
 * to the ladder, and the tallies the close reports.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the
 * focus, and hands text in through [PairDrillIntent]. What is in here is every rule that
 * decides what the text means.
 *
 * No FSRS and no box at all: the material is the catalog's, not the learner's own words,
 * so nothing is scheduled and nothing is read. The one thing that outlives a run is
 * [bestSprosse], which the page that started it files.
 */
data class PairDrillRunState<T : PairDrillTask>(
    val config: PairDrillRunConfig<T>,
    /** The question on screen. A fresh run always has one — a Sprosse with none is not a Sprosse. */
    val task: T,
    /** Bumped per question — what the card's identity and an autoplay effect key on. */
    override val index: Int,
    val sprosse: Int,
    val bestSprosse: Int,
    val winsAtSprosse: Int,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    override val core: DrillRunCore,
    override val feedback: TurnFeedback,
    /** What a refused answer actually NAMED (Uswidi is Schweden) — only beside a Revealed miss. */
    val otherWord: Match.OtherWord? = null,
    /** The first-sight hints already shown ([PairDrillRunConfig.firstSight]); each is shown once. */
    val hinted: Set<String> = emptySet(),
    override val finished: Boolean,
) : DrillRunProgress {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = config.answerLanguage

    /**
     * The language the prompt is written in. Nothing on screen names it; it tags the prompt
     * for a screen reader, which is the reading such a user gets in place of autoplay.
     */
    val promptLanguage: Language get() = config.promptLanguage

    /** The Sprossen this run answered out that the store did not hold — what a pause for improving names. */
    internal val newSprossen: Int
        get() = (config.answeredOut(core.solvedClean) - config.cleared).size

    /** A reversed run's prompt, which is then the form in the language being learned; a forward one says nothing until the reveal. */
    override val promptSaying: Saying?
        get() = if (config.reverse) task.promptText?.let { Saying(it, promptLanguage) } else null

    /** The answer, where it is owed in the language being learned; a reversed run answers in the learner's own. */
    override val answerSaying: Saying?
        get() = if (config.reverse) null else Saying(task.display, answerLanguage)

    override val question: Question get() = config.question(this)

    override val controls: AnswerControls get() = config.controls(this)
}

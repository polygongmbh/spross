package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language

/**
 * What a run asks, in which language, and how it is played — the run SPEC, never edited
 * once the run is open.
 *
 * Several variants already interleave (a draw picks one per task), which is why
 * [DrillModifier.Mix] is about direction and magnitude rather than about variety.
 *
 * [selection] is what the learner picked; [variants] is what survives — a Phrases pick with
 * no frames is dropped rather than letting a draw reach into an empty list, and a selection
 * that empties out falls back to counting, because a run with nothing to ask is not a run.
 */
data class NumbersMode(
    val selection: List<NumbersExercise>,
    /** The language answers are typed in — the one being learned. */
    val language: Language,
    /** The prompt side of a sentence; null where Phrases is not on offer. */
    val phraseSource: Language?,
    /** The frames Phrases draws from — carried, not looked up, so a run samples the set it opened with. */
    val templates: List<PhraseTemplate>,
    val modifiers: Set<DrillModifier>,
) {

    /** Never empty: what the run may actually draw. */
    val variants: List<NumbersExercise> = selection
        .filter { it != NumbersExercise.Phrases || templates.isNotEmpty() }
        .ifEmpty { listOf(NumbersExercise.Counting) }

    /** One variant, played plain. */
    constructor(exercise: NumbersExercise, language: Language) :
        this(listOf(exercise), language, null, emptyList(), emptySet())

    /** A selection of slot variants, played with [modifiers] — no sentence frames. */
    constructor(selection: List<NumbersExercise>, language: Language, modifiers: Set<DrillModifier>) :
        this(selection, language, null, emptyList(), modifiers)

    /** One clean win per Sprosse instead of two. */
    val isFast: Boolean get() = DrillModifier.Fast in modifiers

    /** How long a Sprosse is in this run. */
    val winsToAdvance: Int get() = Numbers.winsToAdvance(isFast)

    /**
     * Mix widens Forms out of the Numbers Sprosse — which only means something while the run
     * is climbing one. Without Numbers selected, Forms keeps its own gentler ladder.
     */
    val mixesForms: Boolean
        get() = DrillModifier.Mix in modifiers && NumbersExercise.Counting in variants

    /**
     * Which way round the next task is asked. Mix flips per task — that, and the widened
     * form magnitudes, is what Mix adds over simply selecting several variants;
     * [DrillModifier.Reverse] alone holds one direction for the whole run.
     */
    fun drawsReversed(rng: Random): Boolean =
        if (DrillModifier.Mix in modifiers) rng.nextBoolean() else DrillModifier.Reverse in modifiers

    /**
     * Ramp ceiling of one exercise: kern's per-reading ceiling, and for sentences the highest
     * ceiling among the frames the run happens to carry.
     */
    fun maxLevel(exercise: NumbersExercise): Int {
        val reading = exercise.reading
            ?: return templates.maxOfOrNull { Numbers.maxLevel(it.slotKind) } ?: 1
        return Numbers.maxLevel(reading)
    }

    /**
     * Identity a streak record is kept under: the whole selection AND how it was played —
     * `Counting+Clock.rev.fast.de`. A run that interleaves two exercises is a different feat
     * from either alone, and a reversed or fast run a different feat again, so none of them
     * may share a standing record.
     *
     * CAUTION, live quirk carried over verbatim: [recordLanguage] takes the pair suffix
     * whenever [phraseSource] stands, EVEN when Phrases is not among [variants] — the
     * numbers overview passes the source whenever the pair realizes frames, so a
     * counting-only run in a phrase-capable pair files under `Counting.de-uk`, not
     * `Counting.uk`.
     */
    val recordKey: String
        get() = (
            listOf(variants.joinToString("+") { it.storageTag }) +
                DrillModifier.entries.filter { it in modifiers }.map { it.storageTag } +
                listOf(recordLanguage)
            ).joinToString(".")

    /** Identity a Sprosse is kept under, per variant — deliberately NOT [recordKey]. */
    fun progressKey(exercise: NumbersExercise): String = progressKey(exercise, language)

    /**
     * One fresh task from the selection, each variant at its own Sprosse: never a prompt
     * [solved] already holds and never the one on screen ([avoiding]), so no question is
     * asked twice in a run ([DrillSolved]).
     *
     * A Sprosse whose values keep coming back solved is spent, and the draw climbs past it
     * rather than repeating it — which is why the Sprossen come back with the task. A variant
     * that has run out altogether hands the turn to the next one, so a mixed run outlives
     * the exercise that ran dry; only when every variant is out is [NumbersDraw.drawn] null.
     *
     * Every random choice a run makes goes through this one [rng] — the variant pick, the
     * frame pick, Mix's per-task direction flip and the value itself — so a seeded run is
     * reproducible end to end instead of three-quarters of the way.
     */
    fun draw(
        levels: Map<NumbersExercise, Int>,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): NumbersDraw {
        val first = variants[rng.nextInt(variants.size)]
        for (exercise in listOf(first) + variants.filter { it != first }) {
            val fresh = drawVariant(exercise, levels, avoiding, solved, rng)
            if (fresh != null) return fresh
        }
        return NumbersDraw(null, levels)
    }

    /**
     * The first Sprosse at or above [exercise]'s with a value left to ask ([DrillLadder.climb]);
     * null once it is out, which hands the turn to the next variant of a mixed run.
     */
    private fun drawVariant(
        exercise: NumbersExercise,
        levels: Map<NumbersExercise, Int>,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): NumbersDraw? {
        val climbed = DrillLadder.climb(levels[exercise] ?: 1, maxLevel(exercise)) { level ->
            drawUnsolved(exercise, level, levels, avoiding, solved, rng)
        }
        val drawn = climbed.task ?: return null
        return NumbersDraw(drawn, levels + (exercise to climbed.level))
    }

    /**
     * One value from [level] the run does not already hold. The Sprosse draws rather than
     * enumerates, so [DrillSolved.SPENT_ATTEMPTS] repeats in a row is what spent means here.
     */
    private fun drawUnsolved(
        exercise: NumbersExercise,
        level: Int,
        levels: Map<NumbersExercise, Int>,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): DrawnTask? {
        repeat(DrillSolved.SPENT_ATTEMPTS) {
            val drawn = drawOnce(exercise, level, levels, rng)
            if (DrillSolved.key(exercise, drawn.task) !in solved && drawn.task.prompt != avoiding) {
                return drawn
            }
        }
        return null
    }

    private fun drawOnce(
        exercise: NumbersExercise,
        level: Int,
        levels: Map<NumbersExercise, Int>,
        rng: Random,
    ): DrawnTask {
        val forward = drawForward(exercise, level, levels[NumbersExercise.Counting] ?: 1, rng)
        val reversed = drawsReversed(rng)
        // The flip happens HERE and nowhere else: kern hands back an ordinary task with the
        // reading as its prompt, so no surface below has to ask the direction.
        return DrawnTask(exercise, if (reversed) Numbers.reversed(forward) else forward, reversed)
    }

    private fun drawForward(
        exercise: NumbersExercise,
        level: Int,
        magnitudeDigits: Int,
        rng: Random,
    ): NumbersTask {
        val reading = exercise.reading
            // why: non-empty by construction — the frameless Phrases pick was dropped above.
            ?: return PhraseSlots.sample(templates[rng.nextInt(templates.size)], level, rng)
        // Mix's second half: a form takes its magnitude from the numbers Sprosse the run stands
        // on, so a topped-out climb reads "−4 072 918", not "−7".
        if (reading == NumbersReading.Form && mixesForms) {
            return Numbers.sampleForms(language, level, magnitudeDigits, rng)
        }
        return Numbers.sample(reading, language, level, rng)
    }

    private val recordLanguage: String
        get() = phraseSource?.let { "$it-$language" } ?: language

    companion object {
        /** Store prefix of the streak records — the full key is this plus [recordKey]. */
        const val RECORD_PREFIX: String = "trainer.record."

        /** Store prefix of the Sprosse high-waters — the full key is this plus [progressKey]. */
        const val PROGRESS_PREFIX: String = "trainer.level."

        /** Store prefix of the most-answers-in-one-run records ([DrillRunSummary.done]). */
        const val ANSWERS_PREFIX: String = "trainer.answers."

        /**
         * Store prefix of the answered-out Sprosse masks: bit n-1 stands for Sprosse n. Filed
         * per DIRECTION — a reversed key wears [REVERSED_SUFFIX] — because a row means a
         * different question either way round (the reversed calendar drops its full date, so
         * the row above it moves down one).
         */
        const val CLEARED_PREFIX: String = "trainer.cleared."

        const val REVERSED_SUFFIX: String = ".rev"

        /** Where [key]'s mask is filed for one direction — the full key is [CLEARED_PREFIX] plus this. */
        fun clearedKey(key: String, reverse: Boolean): String =
            if (reverse) key + REVERSED_SUFFIX else key

        /** The Sprossen a mask holds, [CLEARED_PREFIX]'s reading. */
        fun clearedSprossen(mask: Int): Set<Int> =
            (1..Int.SIZE_BITS - 1).filter { mask and (1 shl (it - 1)) != 0 }.toSet()

        /** The mask a set of Sprossen writes, [CLEARED_PREFIX]'s spelling. */
        fun clearedMask(sprossen: Set<Int>): Int =
            sprossen.filter { it in 1 until Int.SIZE_BITS }.fold(0) { mask, n -> mask or (1 shl (n - 1)) }

        /**
         * The lowest Sprosse [cleared] does not hold, clamped to [top] — where a run opens.
         *
         * Only a ladder that enumerates resumes like this. A slot run opens at Sprosse 1
         * however far the learner has climbed ([NumbersRun.open]): the progress kept per
         * variant ([progressKey], [DrillUnlocks]) buys ACCESS to an exercise, never a head
         * start inside one.
         */
        fun entrySprosse(cleared: Set<Int>, top: Int): Int =
            ((1..maxOf(1, top)).firstOrNull { it !in cleared }) ?: maxOf(1, top)

        /**
         * Whether a tapped [sprosse] may open a run: the entry or anything below it, or a
         * Sprosse some run has reached — never one the learner has not been on yet.
         */
        fun openable(sprosse: Int, cleared: Set<Int>, bestSprosse: Int, top: Int): Boolean =
            sprosse in 1..maxOf(entrySprosse(cleared, top), bestSprosse)

        /**
         * Where an exercise's highest-ever Sprosse is filed, so the overview can read the whole
         * ladder without building a run.
         */
        fun progressKey(exercise: NumbersExercise, language: Language): String =
            "${exercise.storageTag}.$language"

        /** One reading, played plain — [NumbersReading.Year] and [NumbersReading.Fraction] fold in. */
        fun slots(reading: NumbersReading, language: Language): NumbersMode =
            NumbersMode(reading.exercise, language)
    }
}

/**
 * The generator behind an exercise — null for Phrases, whose slot kind is named by each FRAME
 * rather than by the exercise, and differs between them. Public: the chrome names an exercise
 * by this same half, and a platform re-deriving it from the enum cases is the map drifting
 * from itself.
 */
val NumbersExercise.reading: NumbersReading?
    get() = when (this) {
        NumbersExercise.Counting -> NumbersReading.Cardinal
        NumbersExercise.Clock -> NumbersReading.Clock
        NumbersExercise.Forms -> NumbersReading.Form
        NumbersExercise.Phrases -> null
    }

/**
 * An exercise's face, borrowing the slot kind's glyph where it has one — Phrases has none,
 * so it wears its own.
 */
fun numbersExerciseEmoji(exercise: NumbersExercise): String =
    exercise.reading?.let(::numbersReadingEmoji) ?: "💬"

/**
 * The ladder a reading is climbed on. Year maps onto Counting because it has no Sprosse
 * of its own; Fraction belongs to Forms — a fraction is one of the number forms.
 */
internal val NumbersReading.exercise: NumbersExercise
    get() = when (this) {
        NumbersReading.Cardinal, NumbersReading.Year -> NumbersExercise.Counting
        NumbersReading.Clock -> NumbersExercise.Clock
        NumbersReading.Form, NumbersReading.Fraction -> NumbersExercise.Forms
    }

/** The word a record or a Sprosse is filed under: the case name, so the two never drift. */
internal val NumbersExercise.storageTag: String
    get() = name

/** Short and fixed, for the same reason. */
internal val DrillModifier.storageTag: String
    get() = when (this) {
        DrillModifier.Reverse -> "rev"
        DrillModifier.Fast -> "fast"
        DrillModifier.Mix -> "mix"
    }

/**
 * A drawn task, the variant that offered it, and which way round it is asked.
 *
 * Both ride along rather than being derived: a phrase task's own [NumbersTask.kind] names the
 * slot generator behind the sentence and not the variant the run picked, and a reversed task
 * is deliberately indistinguishable from a forward one — every surface renders
 * [NumbersTask.prompt] and grades [NumbersTask.accepted] whichever way it was built.
 * [reversed] exists for the ONE thing that has to know: a reversed task owes digits.
 */
data class DrawnTask(
    val exercise: NumbersExercise,
    val task: NumbersTask,
    val reversed: Boolean,
)

/**
 * What [NumbersMode.draw] hands back: the question, and the Sprossen the run stands on now that
 * it has been drawn — a variant whose Sprosse was answered out has climbed past it.
 *
 * [drawn] is null exactly when every variant has run out of fresh prompts at every Sprosse,
 * which ends the run on its summary rather than asking anything a second time.
 */
data class NumbersDraw(
    val drawn: DrawnTask?,
    val levels: Map<NumbersExercise, Int>,
)

/**
 * Which exercises a pair can be asked at all, and which of them one run may combine.
 *
 * The registry half is not the ladder: a language with no forms reading and a pair the
 * catalog realizes no frame for have nothing to unlock, so they are absent rather than
 * locked — a padlock that can never open is a lie.
 */
object DrillSelection {

    /** Every variant this pair could ever offer, in ladder order. [phrasesRealized]: the pair has frames. */
    fun offered(language: Language, phrasesRealized: Boolean): List<NumbersExercise> =
        NumbersExercise.entries.filter { exercise ->
            when (exercise) {
                NumbersExercise.Counting, NumbersExercise.Clock -> true
                NumbersExercise.Phrases -> phrasesRealized
                NumbersExercise.Forms -> Numbers.supportsForms(language)
            }
        }

    /**
     * Mixing several exercises into one run is itself earned: while any offered variant is
     * still locked a run asks ONE thing at a time, and only a fully open ladder lets picks
     * combine. A learner who has just met the clock is asked to climb it, not to dilute it.
     */
    fun combining(offered: List<NumbersExercise>, progress: Map<NumbersExercise, Int>): Boolean =
        offered.all { DrillUnlocks.unlocked(it, progress) }

    /**
     * What tapping [tapped] leaves picked. While the ladder is closed the picks are a radio
     * that never empties — the tapped row simply becomes the only one, so the start button
     * always has something to open.
     */
    fun toggled(picked: List<NumbersExercise>, tapped: NumbersExercise, combining: Boolean): List<NumbersExercise> {
        if (!combining) return listOf(tapped)
        val next = if (tapped in picked) picked - tapped else picked + tapped
        return ordered(next)
    }

    /**
     * The picks as the ladder now stands: never one whose row is a padlock, and only one of
     * them while the list is a radio. Re-run whenever the ladder is read — a closing run can
     * open a Sprosse, and the picks may predate it.
     */
    fun normalized(
        picked: List<NumbersExercise>,
        offered: List<NumbersExercise>,
        progress: Map<NumbersExercise, Int>,
    ): List<NumbersExercise> {
        val open = picked.filter { DrillUnlocks.unlocked(it, progress) }
        if (combining(offered, progress)) return ordered(open)
        // why: a set has no first — the ladder's own order decides which of several
        // survives, so the same state always collapses the same way.
        val one = offered.firstOrNull { it in open }
            ?: offered.firstOrNull { DrillUnlocks.unlocked(it, progress) }
        return listOfNotNull(one)
    }

    private fun ordered(picked: List<NumbersExercise>): List<NumbersExercise> =
        NumbersExercise.entries.filter { it in picked }
}

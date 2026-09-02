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
data class TrainerMode(
    val selection: List<DrillVariant>,
    /** The language answers are typed in — the one being learned. */
    val language: Language,
    /** The prompt side of a sentence; null where Phrases is not on offer. */
    val phraseSource: Language?,
    /** The frames Phrases draws from — carried, not looked up, so a run samples the set it opened with. */
    val templates: List<PhraseTemplate>,
    val modifiers: Set<DrillModifier>,
) {

    /** Never empty: what the run may actually draw. */
    val variants: List<DrillVariant> = selection
        .filter { it != DrillVariant.Phrases || templates.isNotEmpty() }
        .ifEmpty { listOf(DrillVariant.Numbers) }

    /** One variant, played plain. */
    constructor(variant: DrillVariant, language: Language) :
        this(listOf(variant), language, null, emptyList(), emptySet())

    /** A selection of slot variants, played with [modifiers] — no sentence frames. */
    constructor(selection: List<DrillVariant>, language: Language, modifiers: Set<DrillModifier>) :
        this(selection, language, null, emptyList(), modifiers)

    /** One clean win per Sprosse instead of two. */
    val isFast: Boolean get() = DrillModifier.Fast in modifiers

    /** How long a Sprosse is in this run. */
    val winsToAdvance: Int get() = Trainer.winsToAdvance(isFast)

    /**
     * Mix widens Forms out of the Numbers Sprosse — which only means something while the run
     * is climbing one. Without Numbers selected, Forms keeps its own gentler ladder.
     */
    val mixesForms: Boolean
        get() = DrillModifier.Mix in modifiers && DrillVariant.Numbers in variants

    /**
     * Which way round the next task is asked. Mix flips per task — that, and the widened
     * form magnitudes, is what Mix adds over simply selecting several variants;
     * [DrillModifier.Reverse] alone holds one direction for the whole run.
     */
    fun drawsReversed(rng: Random): Boolean =
        if (DrillModifier.Mix in modifiers) rng.nextBoolean() else DrillModifier.Reverse in modifiers

    /**
     * Ramp ceiling of one variant: kern's per-kind ceiling, and for sentences the highest
     * ceiling among the frames the run happens to carry.
     */
    fun maxLevel(variant: DrillVariant): Int {
        val kind = variant.slotKind
            ?: return templates.maxOfOrNull { Trainer.maxLevel(it.slotKind) } ?: 1
        return Trainer.maxLevel(kind)
    }

    /**
     * Identity a streak record is kept under: the whole selection AND how it was played —
     * `Numbers+Clock.rev.fast.de`. A run that interleaves two variants is a different feat
     * from either alone, and a reversed or fast run a different feat again, so none of them
     * may share a standing record.
     *
     * CAUTION, live quirk carried over verbatim: [recordLanguage] takes the pair suffix
     * whenever [phraseSource] stands, EVEN when Phrases is not among [variants] — the
     * numbers overview passes the source whenever the pair realizes frames, so a
     * Numbers-only run in a phrase-capable pair files under `Numbers.de-uk`, not
     * `Numbers.uk`. Reproduced rather than tidied: the keys are already written.
     */
    val recordKey: String
        get() = (
            listOf(variants.joinToString("+") { it.storageTag }) +
                DrillModifier.entries.filter { it in modifiers }.map { it.storageTag } +
                listOf(recordLanguage)
            ).joinToString(".")

    /** Identity a Sprosse is kept under, per variant — deliberately NOT [recordKey]. */
    fun progressKey(variant: DrillVariant): String = progressKey(variant, language)

    /**
     * One fresh task from the selection, each variant at its own Sprosse: never a prompt
     * [solved] already holds and never the one on screen ([avoiding]), so no question is
     * asked twice in a run ([DrillSolved]).
     *
     * A Sprosse whose values keep coming back solved is spent, and the draw climbs past it
     * rather than repeating it — which is why the Sprossen come back with the task. A variant
     * that has run out altogether hands the turn to the next one, so a mixed run outlives
     * the exercise that ran dry; only when every variant is out is [TrainerDraw.drawn] null.
     *
     * Every random choice a run makes goes through this one [rng] — the variant pick, the
     * frame pick, Mix's per-task direction flip and the value itself — so a seeded run is
     * reproducible end to end instead of three-quarters of the way.
     */
    fun draw(
        levels: Map<DrillVariant, Int>,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): TrainerDraw {
        val first = variants[rng.nextInt(variants.size)]
        for (variant in listOf(first) + variants.filter { it != first }) {
            val fresh = drawVariant(variant, levels, avoiding, solved, rng)
            if (fresh != null) return fresh
        }
        return TrainerDraw(null, levels)
    }

    /**
     * The first Sprosse at or above [variant]'s with a value left to ask ([DrillLadder.climb]);
     * null once it is out, which hands the turn to the next variant of a mixed run.
     */
    private fun drawVariant(
        variant: DrillVariant,
        levels: Map<DrillVariant, Int>,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): TrainerDraw? {
        val climbed = DrillLadder.climb(levels[variant] ?: 1, maxLevel(variant)) { level ->
            drawUnsolved(variant, level, levels, avoiding, solved, rng)
        }
        val drawn = climbed.task ?: return null
        return TrainerDraw(drawn, levels + (variant to climbed.level))
    }

    /**
     * One value from [level] the run does not already hold. The Sprosse draws rather than
     * enumerates, so [DrillSolved.SPENT_ATTEMPTS] repeats in a row is what spent means here.
     */
    private fun drawUnsolved(
        variant: DrillVariant,
        level: Int,
        levels: Map<DrillVariant, Int>,
        avoiding: String?,
        solved: Set<String>,
        rng: Random,
    ): DrawnTask? {
        repeat(DrillSolved.SPENT_ATTEMPTS) {
            val drawn = drawOnce(variant, level, levels, rng)
            if (DrillSolved.key(variant, drawn.task) !in solved && drawn.task.prompt != avoiding) {
                return drawn
            }
        }
        return null
    }

    private fun drawOnce(
        variant: DrillVariant,
        level: Int,
        levels: Map<DrillVariant, Int>,
        rng: Random,
    ): DrawnTask {
        val forward = drawForward(variant, level, levels[DrillVariant.Numbers] ?: 1, rng)
        val reversed = drawsReversed(rng)
        // The flip happens HERE and nowhere else: kern hands back an ordinary task with the
        // reading as its prompt, so no surface below has to ask the direction.
        return DrawnTask(variant, if (reversed) Trainer.reversed(forward) else forward, reversed)
    }

    private fun drawForward(
        variant: DrillVariant,
        level: Int,
        magnitudeDigits: Int,
        rng: Random,
    ): TrainerTask {
        val kind = variant.slotKind
            // why: non-empty by construction — the frameless Phrases pick was dropped above.
            ?: return PhraseSlots.sample(templates[rng.nextInt(templates.size)], level, rng)
        // Mix's second half: a form takes its magnitude from the numbers Sprosse the run stands
        // on, so a topped-out climb reads "−4 072 918", not "−7".
        if (kind == TrainerKind.Forms && mixesForms) {
            return Trainer.sampleForms(language, level, magnitudeDigits, rng)
        }
        return Trainer.sample(kind, language, level, rng)
    }

    private val recordLanguage: String
        get() = phraseSource?.let { "$it-$language" } ?: language

    companion object {
        /** Store prefix of the streak records — the full key is this plus [recordKey]. */
        const val RECORD_PREFIX: String = "trainer.record."

        /** Store prefix of the Sprosse high-waters — the full key is this plus [progressKey]. */
        const val PROGRESS_PREFIX: String = "trainer.level."

        /**
         * Where a variant's highest-ever Sprosse is filed, so the overview can read the whole
         * ladder without building a run. Kotlin's own spelling for the slot variants and the
         * lowercase word for Phrases: those exact strings are already stored, and a tidier
         * scheme would silently reset every Sprosse a learner has climbed.
         */
        fun progressKey(variant: DrillVariant, language: Language): String =
            "${variant.storageTag}.$language"

        /** One slot kind, played plain — [TrainerKind.Years] and [TrainerKind.Fraction] fold in. */
        fun slots(kind: TrainerKind, language: Language): TrainerMode =
            TrainerMode(kind.drillVariant, language)
    }
}

/**
 * The generator behind a variant — null for Phrases, whose slot kind is named by each FRAME
 * rather than by the variant, and differs between them. Public: the chrome names a variant
 * by this same half, and a platform re-deriving it from the enum cases is the map drifting
 * from itself.
 */
val DrillVariant.slotKind: TrainerKind?
    get() = when (this) {
        DrillVariant.Numbers -> TrainerKind.Numbers
        DrillVariant.Clock -> TrainerKind.Clock
        DrillVariant.Forms -> TrainerKind.Forms
        DrillVariant.Phrases -> null
    }

/**
 * A run variant's face, borrowing the slot kind's glyph where it has one — Phrases has none,
 * so it wears its own.
 */
fun drillVariantEmoji(variant: DrillVariant): String =
    variant.slotKind?.let(::trainerKindEmoji) ?: "💬"

/**
 * The ladder variant a slot kind belongs to. Years maps onto Numbers because it has no Sprosse
 * of its own; Fraction belongs to Forms — a fraction is one of the number forms.
 */
internal val TrainerKind.drillVariant: DrillVariant
    get() = when (this) {
        TrainerKind.Numbers, TrainerKind.Years -> DrillVariant.Numbers
        TrainerKind.Clock -> DrillVariant.Clock
        TrainerKind.Forms, TrainerKind.Fraction -> DrillVariant.Forms
    }

/** The word a record or a Sprosse is filed under. Stored, so it may not follow the screen name. */
internal val DrillVariant.storageTag: String
    get() = if (this == DrillVariant.Phrases) "phrases" else name

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
 * Both ride along rather than being derived: a phrase task's own [TrainerTask.kind] names the
 * slot generator behind the sentence and not the variant the run picked, and a reversed task
 * is deliberately indistinguishable from a forward one — every surface renders
 * [TrainerTask.prompt] and grades [TrainerTask.accepted] whichever way it was built.
 * [reversed] exists for the ONE thing that has to know: a reversed task owes digits.
 */
data class DrawnTask(
    val variant: DrillVariant,
    val task: TrainerTask,
    val reversed: Boolean,
)

/**
 * What [TrainerMode.draw] hands back: the question, and the Sprossen the run stands on now that
 * it has been drawn — a variant whose Sprosse was answered out has climbed past it.
 *
 * [drawn] is null exactly when every variant has run out of fresh prompts at every Sprosse,
 * which ends the run on its summary rather than asking anything a second time.
 */
data class TrainerDraw(
    val drawn: DrawnTask?,
    val levels: Map<DrillVariant, Int>,
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
    fun offered(language: Language, phrasesRealized: Boolean): List<DrillVariant> =
        DrillVariant.entries.filter { variant ->
            when (variant) {
                DrillVariant.Numbers, DrillVariant.Clock -> true
                DrillVariant.Phrases -> phrasesRealized
                DrillVariant.Forms -> Trainer.supportsForms(language)
            }
        }

    /**
     * Mixing several exercises into one run is itself earned: while any offered variant is
     * still locked a run asks ONE thing at a time, and only a fully open ladder lets picks
     * combine. A learner who has just met the clock is asked to climb it, not to dilute it.
     */
    fun combining(offered: List<DrillVariant>, progress: Map<DrillVariant, Int>): Boolean =
        offered.all { DrillUnlocks.unlocked(it, progress) }

    /**
     * What tapping [tapped] leaves picked. While the ladder is closed the picks are a radio
     * that never empties — the tapped row simply becomes the only one, so the start button
     * always has something to open.
     */
    fun toggled(picked: List<DrillVariant>, tapped: DrillVariant, combining: Boolean): List<DrillVariant> {
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
        picked: List<DrillVariant>,
        offered: List<DrillVariant>,
        progress: Map<DrillVariant, Int>,
    ): List<DrillVariant> {
        val open = picked.filter { DrillUnlocks.unlocked(it, progress) }
        if (combining(offered, progress)) return ordered(open)
        // why: a set has no first — the ladder's own order decides which of several
        // survives, so the same state always collapses the same way.
        val one = offered.firstOrNull { it in open }
            ?: offered.firstOrNull { DrillUnlocks.unlocked(it, progress) }
        return listOfNotNull(one)
    }

    private fun ordered(picked: List<DrillVariant>): List<DrillVariant> =
        DrillVariant.entries.filter { it in picked }
}

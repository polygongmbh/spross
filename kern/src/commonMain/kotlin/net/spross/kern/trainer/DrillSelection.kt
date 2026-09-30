package net.spross.kern.trainer

import net.spross.kern.model.Language

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
 * The ladder a reading is climbed on. Year maps onto Counting because it has no Sprosse
 * of its own, and neither has Phone — a phone number is digits read out; Fraction belongs
 * to Forms — a fraction is one of the number forms.
 */
internal val NumbersReading.exercise: NumbersExercise
    get() = when (this) {
        NumbersReading.Cardinal, NumbersReading.Year, NumbersReading.Phone -> NumbersExercise.Counting
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
        DrillModifier.Timed -> "timed"
    }

/**
 * Which exercises a pair can be asked at all, and which of them one run may combine.
 *
 * The registry half is not the ladder: a language with no forms reading and a pair the
 * catalog realizes no frame for have nothing to unlock, so they are absent rather than
 * locked — a padlock that can never open is a lie.
 */
object DrillSelection {

    /** Every exercise this pair could ever offer, in ladder order. [phrasesRealized]: the pair has frames. */
    fun offered(language: Language, phrasesRealized: Boolean): List<NumbersExercise> =
        NumbersExercise.entries.filter { exercise ->
            when (exercise) {
                NumbersExercise.Counting, NumbersExercise.Clock -> true
                NumbersExercise.Phrases -> phrasesRealized
                NumbersExercise.Forms -> Numbers.supportsForms(language)
            }
        }

    /**
     * Mixing several exercises into one run is itself earned: while any offered exercise is
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

package net.spross.kern.trainer

import net.spross.kern.design.ChoiceVerdict
import net.spross.kern.design.Palette
import net.spross.kern.design.Swatch

/**
 * How a sentence scramble's arrangement stands, and the skin its tile bank wears for it.
 *
 * Anything but [Owed] locks every chip: the question has been answered,
 * and an order that could still be permuted afterwards would let a learner brute-force one.
 * The drill grades by position, so there is no near miss to render.
 *
 * The arrangement's edge says the verdict: dashed in [Palette.borderStrong] while the row is still open,
 * solid in [Palette.success] or [Palette.wrong] once graded — and a screen reader hears it as [spoken].
 */
enum class ScrambleVerdict {
    Owed, Correct, Wrong;

    val locked: Boolean get() = this != Owed

    val edge: Swatch
        get() = when (this) {
            Owed -> Palette.borderStrong
            Correct -> Palette.success
            Wrong -> Palette.wrong
        }

    /** The edge's stroke, in points: a hairline while open, a firm line once graded. */
    val edgeWidth: Double get() = if (locked) 2.0 else 1.0

    /** The edge's dash and gap, in points; empty for a solid line. */
    val edgeDash: List<Double> get() = if (locked) emptyList() else listOf(5.0, 4.0)

    /** What a screen reader hears after the arrangement; null while it is still owed. */
    val spoken: ChoiceVerdict?
        get() = when (this) {
            Owed -> null
            Correct -> ChoiceVerdict.Correct
            Wrong -> ChoiceVerdict.Wrong
        }

    /**
     * Whether the arrangement row stands on the card.
     * A graded card with nothing placed is a reveal nobody arranged for —
     * the row would hold its reserve and its hint over an answer there is no longer one to give.
     */
    fun showsRow(placedAny: Boolean): Boolean = !locked || placedAny

    companion object {
        fun of(run: DrillRunProgress): ScrambleVerdict = when {
            run.owesAnswer -> Owed
            run.answerAccepted -> Correct
            else -> Wrong
        }
    }
}

/** The scramble tile bank's own measures, beside the verdict skin it wears. */
object ScrambleBank {
    /** The opacity of a chip already carried up, which stays in the bank rather than vanishing. */
    const val SPENT_ALPHA: Double = 0.35
}

package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import net.spross.kern.box.TreeTransition

/**
 * The summary's tree rising out of the ground after a round, at a progress 0…1:
 * the platform runs the clock — [DELAY_MILLIS], then a spring of [SPRING_RESPONSE] seconds
 * damped [SPRING_DAMPING] — and draws the FINISHED tree's [TreePicture] at each moment.
 * The one tree in the app that moves: the Trees picture holds still, since a box grows over weeks,
 * but here a round has just finished and something did in fact just happen.
 *
 * Two motions, and they say different things:
 *   · the whole tree RISES, from a crouch to its full height, even when the round moved no count —
 *     holding a hard area steady earned the tree standing up;
 *   · what the round CHANGED ([ranks]) arrives after it, mark by mark,
 *     so the eye goes to the new leaf rather than over a crown that all wobbles alike.
 */
class TreeRise(transition: TreeTransition) {
    /** The ranks the round moved: the only marks that move on their own, every other one is drawn settled. */
    val ranks: List<Int> = transition.changedRanks
    private val standing = transition.standingCount

    /**
     * The share of its finished height the tree rises from: an area worked from nothing rises from nothing,
     * everything else from where it stood, or from the crouch, whichever is lower.
     */
    private val from: Double = AreaTree.height(transition.after).let { full ->
        if (full > 0) min(CROUCH, AreaTree.height(transition.before) / full) else CROUCH
    }

    /** How tall the tree stands at [progress], as a share of its finished height; the platform scales the picture about its foot. */
    fun risen(progress: Double): Double =
        // Clamped at the top: the spring settles from above, and an overshoot would run into the screen edge.
        max(0.05, from + (1 - from) * progress.coerceIn(0.0, 1.0))

    /** Whether any of [ranks] is still on its way at [progress]; once none is, every mark is drawn settled. */
    fun arriving(progress: Double): Boolean = ranks.isNotEmpty() && progress < OPENS + STAGGER + TAKES

    /** How big the mark at [rank] is drawn at [progress], against its settled size. */
    fun scale(rank: Int, progress: Double): Double {
        val order = ranks.indexOf(rank)
        if (order < 0) return 1.0
        val share = if (ranks.size > 1) order.toDouble() / (ranks.size - 1) else 0.0
        val t = ((progress - (OPENS + STAGGER * share)) / TAKES).coerceIn(0.0, 1.0)
        // A mark the round HUNG arrives out of nothing; one it only moved a stage was already hanging,
        // and popping it in from zero would read as the word having been taken off the tree first.
        return if (rank >= standing) pop(t) else swell(t)
    }

    companion object {
        /** The wait before the rise starts, and its spring: how long it takes to settle, and how little it overshoots. */
        const val DELAY_MILLIS = 250
        const val SPRING_RESPONSE = 1.5
        const val SPRING_DAMPING = 0.85

        /** A tree never starts taller than this share of where it ends, however little the round changed. */
        private const val CROUCH = 0.78

        /** The first marks wait until the tree is most of the way up, so an arrival lands ON the tree. */
        private const val OPENS = 0.42
        /**
         * A mark takes this much of the rise to arrive, and the last one starts this far after the first.
         * Both inside the rise: a spring approaches its end slowly, and motion timed to the last of it drags.
         */
        private const val TAKES = 0.24
        private const val STAGGER = 0.30

        /** Out of nothing, past full size, back to it: the overshoot makes a leaf appearing an event rather than a redraw. */
        private fun pop(t: Double): Double {
            val over = 1.9; val past = t - 1
            return 1 + (over + 1) * past * past * past + over * past * past
        }

        /** Already there, so it swells and settles back: a word that settled opens where it hangs. */
        private fun swell(t: Double): Double = 1 + 0.25 * sin(PI * t)
    }
}

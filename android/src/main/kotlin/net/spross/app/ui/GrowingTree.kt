package net.spross.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlinx.coroutines.delay
import net.spross.kern.box.TreeTransition
import net.spross.kern.design.AreaTree

/**
 * The area a round worked hardest, rising out of the ground — the one tree in the app that
 * moves, because a round has just finished and something did in fact just happen.
 *
 * Two motions: the whole tree RISES from a crouch to its full height, even when the round
 * moved no count (holding a hard area steady earned the tree standing up); then what the
 * round CHANGED ([TreeTransition.changedRanks]) arrives mark by mark, so the eye goes to
 * the new leaf. The tree drawn is always the finished one; with animations switched off
 * in the system settings it stands finished at once.
 */
@Composable
internal fun GrowingTree(transition: TreeTransition, garden: String, height: Dp, modifier: Modifier = Modifier) {
    val progress = remember(transition) { Animatable(0f) }
    LaunchedEffect(transition) {
        delay(250)
        // iOS's `response: 1.5, dampingFraction: 0.85`: stiffness is (2π / 1.5)².
        progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 17.5f))
    }
    val colors = Theme.colors
    Spacer(
        modifier.fillMaxWidth().height(height).clearAndSetSemantics {}.drawWithCache {
            val stand = AreaTree.solitary(size.width / density.toDouble(), size.height / density.toDouble())
            val foot = Offset((stand.footX * density).toFloat(), (stand.footY * density).toFloat())
            val planted = PlantedTree(transition.after, garden, foot, (stand.height * density).toFloat(), density)
            val full = AreaTree.height(transition.after).toFloat()
            val was = AreaTree.height(transition.before).toFloat()
            // An area worked from nothing rises from nothing; the rest from where it stood,
            // or from the crouch, whichever is lower.
            val from = if (full > 0f) minOf(CROUCH, was / full) else CROUCH
            onDrawBehind {
                val t = progress.value.coerceIn(0f, 1f)
                val arrival = Arrival(transition, t)
                val art = planted.skeleton?.takeIf { arrival.moving }
                    ?.let { TreeArt.build(planted.tree, planted.seed, it, planted.unit, arrival::scale) }
                    ?: planted.art
                scale(max(0.05f, from + (1 - from) * t), pivot = planted.foot) {
                    drawTree(planted, colors, art)
                }
            }
        },
    )
}

/** A tree never starts taller than this share of where it ends. */
private const val CROUCH = 0.78f

/** How big each of the round's own marks is drawn at one moment of the rise; the rest are settled. */
internal class Arrival(transition: TreeTransition, progress: Float) {
    private val scales = HashMap<Int, Float>()

    /** Whether any mark is still on its way — otherwise the settled paths draw. */
    val moving: Boolean

    init {
        val ranks = transition.changedRanks
        val hanging = transition.standingCount
        for ((order, rank) in ranks.withIndex()) {
            val share = if (ranks.size > 1) order.toFloat() / (ranks.size - 1) else 0f
            val t = ((progress - (OPENS + STAGGER * share)) / TAKES).coerceIn(0f, 1f)
            // A mark the round HUNG arrives out of nothing; one it only moved a tier was
            // already hanging, so it swells where it hangs instead.
            scales[rank] = if (rank >= hanging) pop(t) else swell(t)
        }
        moving = ranks.isNotEmpty() && progress < OPENS + STAGGER + TAKES
    }

    fun scale(rank: Int): Float = scales[rank] ?: 1f

    private companion object {
        /** The first marks wait until the tree is most of the way up. */
        const val OPENS = 0.42f
        const val TAKES = 0.24f
        const val STAGGER = 0.30f

        /** Out of nothing, past full size, back to it. */
        fun pop(t: Float): Float {
            val over = 1.9f; val past = t - 1
            return 1 + (over + 1) * past * past * past + over * past * past
        }

        fun swell(t: Float): Float = 1 + 0.25f * kotlin.math.sin(PI_F * t)
    }
}

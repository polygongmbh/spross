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
import kotlin.math.PI
import kotlin.math.pow
import kotlinx.coroutines.delay
import net.spross.kern.box.TreeTransition
import net.spross.kern.design.AreaTree
import net.spross.kern.design.TreeRise

/**
 * The area a round worked hardest, rising out of the ground as kern's [TreeRise] says;
 * this runs it on Compose's animation clock.
 * With animations switched off in the system settings it stands finished at once.
 */
@Composable
internal fun GrowingTree(transition: TreeTransition, garden: String, height: Dp, modifier: Modifier = Modifier) {
    val progress = remember(transition) { Animatable(0f) }
    val rise = remember(transition) { TreeRise(transition) }
    LaunchedEffect(transition) {
        delay(TreeRise.DELAY_MILLIS.toLong())
        // A spring's response converts to Compose's stiffness as (2π / response)².
        val stiffness = (2 * PI / TreeRise.SPRING_RESPONSE).pow(2).toFloat()
        progress.animateTo(1f, spring(dampingRatio = TreeRise.SPRING_DAMPING.toFloat(), stiffness = stiffness))
    }
    val colors = Theme.colors
    Spacer(
        modifier.fillMaxWidth().height(height).clearAndSetSemantics {}.drawWithCache {
            val stand = AreaTree.solitary(size.width / density.toDouble(), size.height / density.toDouble())
            val foot = Offset((stand.footX * density).toFloat(), (stand.footY * density).toFloat())
            val planted = PlantedTree(transition.after, garden, foot, (stand.height * density).toFloat(), density)
            onDrawBehind {
                val t = progress.value.toDouble()
                val arriving = if (rise.arriving(t)) rise.ranks.associateWith { rise.scale(it, t).toFloat() } else emptyMap()
                val risen = rise.risen(t).toFloat()
                scale(risen, pivot = planted.foot) { drawTree(planted, colors, arriving) }
            }
        },
    )
}

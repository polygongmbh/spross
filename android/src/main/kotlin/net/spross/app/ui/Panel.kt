package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import net.spross.kern.design.CardSurface

/**
 * The ONE raised surface: the card fill, a soft shadow under it, and the hairline that
 * closes the edge.
 *
 * Every panel in the app wears this, not just the card a session asks its question on —
 * paper at `#FBFBF6` on paper at `#F2F1EA` is a four-percent step, so a panel with no
 * shadow under it is not a panel, it is a rectangle nobody can find. M3's own `Card`
 * defaults to zero elevation and the theme deliberately kills its tonal tint
 * (`surfaceTint = Transparent`), which left every surface but this one perfectly flat.
 *
 * The hairline is deliberately faint: the fill and the shadow carry the boundary and the
 * edge only closes it (kern's `CardSurface`, as iOS `cardSurface` draws it).
 *
 * iOS keeps a flat `panelSurface()` beside `cardSurface()`, reserving the shadow and hairline
 * for cards alone; on this platform's weaker surface/background contrast a flat panel reads
 * as a rectangle nobody can find, so every panel here wears them.
 */
@Composable
fun Modifier.panel(shape: Shape = MaterialTheme.shapes.medium): Modifier = this
    .dropShadow(shape, CARD_SHADOW)
    .background(MaterialTheme.colorScheme.surface, shape)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = CardSurface.HAIRLINE.toFloat()), shape)

/**
 * A group of rows on its raised tile: [panel] with the page's inset inside it, full width.
 * Every settings group, overview block and Home card is this one cut, so no two drift apart
 * in how far their rows stand from the edge.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    spacing: Dp = Theme.spacing.lg,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth().panel(shape).padding(Theme.spacing.lg),
    verticalArrangement = Arrangement.spacedBy(spacing),
    horizontalAlignment = horizontalAlignment,
    content = content,
)

/**
 * The one card shadow — soft and low, so the card LIFTS rather than casting a box.
 *
 * An elevation shadow is the platform's, cut for the platform's own depth ladder: tight,
 * dark, and hard at the edge. The canonical one is kern's `CardSurface` bloom,
 * drawn here rather than asked for so both apps lift their cards the same amount.
 */
private val CARD_SHADOW = Shadow(
    radius = CardSurface.SHADOW_RADIUS.dp,
    color = Color.Black,
    offset = DpOffset(0.dp, CardSurface.SHADOW_Y.dp),
    alpha = CardSurface.SHADOW_ALPHA.toFloat(),
)

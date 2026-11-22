package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The ONE raised surface, an elevated card as Material draws one: the surface fill at
 * `ElevatedCard`'s resting elevation.
 *
 * Every panel in the app wears this, not just the card a session asks its question on —
 * paper at `#FBFBF6` on paper at `#F2F1EA` is a four-percent step, so a panel with no
 * shadow under it is not a panel, it is a rectangle nobody can find. The theme kills M3's
 * tonal tint (`surfaceTint = Transparent`), so the elevation shows as its shadow alone.
 */
@Composable
fun Modifier.panel(shape: Shape = MaterialTheme.shapes.medium): Modifier = this
    .shadow(PANEL_ELEVATION, shape, clip = false)
    .background(MaterialTheme.colorScheme.surface, shape)

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

/** M3's elevation level 1, where an `ElevatedCard` rests. */
private val PANEL_ELEVATION = 1.dp

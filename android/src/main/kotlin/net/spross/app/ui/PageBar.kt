package net.spross.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The inset M3 gives a top app bar's title, narrower than the page's own margin. */
private val BAR_TITLE_INSET = 16.dp

/**
 * A tab page's top app bar: large over the paper at rest, collapsing into the card fill once
 * content scrolls under it. The hairline fades in with the collapse — the same one the tab bar
 * wears, so a card passing under either bar never merges into it.
 *
 * The title is drawn twice by M3, large and collapsed; [isExpandedTitle] tells them apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) {
    Column {
        LargeTopAppBar(
            // The page's margin is wider than the bar's own title inset.
            title = { Box(Modifier.padding(start = Theme.spacing.xl - BAR_TITLE_INSET)) { title() } },
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = Theme.colors.surface,
            ),
        )
        HorizontalDivider(
            Modifier.graphicsLayer { alpha = scrollBehavior.state.collapsedFraction },
            color = Theme.colors.separator,
        )
    }
}

/** Whether a [PageTopBar] title is being drawn in the bar's large state rather than collapsed. */
@Composable
@ReadOnlyComposable
fun isExpandedTitle(): Boolean =
    LocalTextStyle.current.fontSize > MaterialTheme.typography.titleLarge.fontSize

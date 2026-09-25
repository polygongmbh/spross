package net.spross.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp

/**
 * A tab page's title: the first thing in its scrolling content, scrolling away with it —
 * a tab page has no way out to keep in reach, so nothing about it stays pinned.
 *
 * [eyebrow] is an all-caps label over the title; it takes a label's tracking, since the ramp's
 * own is zeroed for running text ([Theme]) and reads cramped on a few capitalized words.
 * A title too long for the width shrinks a step rather than pushing the page down a third line.
 */
@Composable
fun PageTitle(
    title: String?,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Theme.spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(
                eyebrow,
                style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (title != null) Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 20.sp,
                    maxFontSize = MaterialTheme.typography.headlineLarge.fontSize,
                ),
            )
        }
        actions()
    }
}

/**
 * The bar of a screen pushed over a tab page: pinned, since it holds the way back out.
 * Once content scrolls under it, it takes the card fill over the hairline the tab bar wears,
 * so a card passing under it never merges into it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushedTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = Theme.colors.surface,
            ),
        )
        HorizontalDivider(
            Modifier.graphicsLayer { alpha = scrollBehavior.state.overlappedFraction },
            color = Theme.colors.separator,
        )
    }
}

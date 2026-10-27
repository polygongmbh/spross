package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * A bottom sheet whose [content] may outgrow it: the content scrolls, and the [action] under
 * it stands OUTSIDE the scrolling half, so a long list or comment never scrolls the sheet's
 * primary button out of reach.
 * On a split window it stands as a readable column on the trailing side ([sheetPlacement]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrowableSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    action: @Composable ColumnScope.() -> Unit,
) {
    val placement = sheetPlacement()
    // why: the sheet is a window of its own, which would read its type at the platform's scale
    // rather than at the one a big window stands at (TypeScaled).
    val density = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.offset(x = placement.offset),
        sheetState = rememberModalBottomSheetState(),
        sheetMaxWidth = placement.maxWidth,
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(Theme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    content = content,
                )
                action()
            }
        }
    }
}

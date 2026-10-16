package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A bottom sheet whose [content] may outgrow it: the content scrolls, and the [action] under
 * it stands OUTSIDE the scrolling half, so a long list or comment never scrolls the sheet's
 * primary button out of reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrowableSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    action: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
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

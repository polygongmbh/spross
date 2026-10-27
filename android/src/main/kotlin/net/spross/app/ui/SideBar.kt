package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A full-screen task's own controls on their side, where the window splits: a vertical toolbar
 * at the leading edge, Material's floating toolbar turned upright on a large screen, holding the
 * controls of the page it stands on — never the app's navigation, which a task hides.
 * On a phone the same controls stand across the top, where the sides have no room.
 *
 * Symbols only: the way out heads it, a way on stands at its foot; anything that needs its
 * words stays a labeled button in the content.
 */
@Composable
fun TaskSideBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight().padding(end = Theme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        content = content,
    )
}

/** The side bar's way on — start, done — as the one filled symbol in it, named for TalkBack as its button would be. */
@Composable
fun SideBarAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Icon(icon, contentDescription = null)
    }
}

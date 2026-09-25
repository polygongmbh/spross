package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.spross.app.AppModel

/**
 * The third section: the pair being learnt, whether words are read aloud, the backup, the
 * one destructive door, and the way to who spoke the recordings.
 *
 * What stands here is [BoxSettingsSection]'s; this screen only gives it the page.
 */
@Composable
fun SettingsScreen(model: AppModel) {
    val catalog = model.catalog
    val box = model.box
    if (catalog == null || box == null) return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Theme.spacing.xl, end = Theme.spacing.xl, bottom = Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        PageTitle(model.chrome.settingsTitle)
        BoxSettingsSection(model, catalog, box)
    }
}

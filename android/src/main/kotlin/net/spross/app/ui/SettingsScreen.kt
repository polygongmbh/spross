package net.spross.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import net.spross.app.AppModel

/**
 * The third section: the pair being learnt, whether words are read aloud, the backup, the
 * one destructive door, and the way to who spoke the recordings.
 *
 * What stands here is [BoxSettingsSection]'s; this screen only gives it the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: AppModel) {
    val catalog = model.catalog
    val box = model.box
    if (catalog == null || box == null) return
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(model.chrome.settingsTitle, PageBarTitle) },
                scrollBehavior = scrollBehavior,
                colors = pageBarColors(),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.spacing.xl, vertical = Theme.spacing.lg),
        ) {
            BoxSettingsSection(model, catalog, box)
        }
    }
}

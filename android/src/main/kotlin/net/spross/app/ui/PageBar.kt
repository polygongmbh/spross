package net.spross.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A page's top app bar: the paper at rest, the recessed fill once content scrolls under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pageBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
)

/** The inset M3 gives a top app bar's title, narrower than the page's own margin. */
private val BAR_TITLE_INSET = 16.dp

/** Lines a top app bar's title up with the page content under it. */
val PageBarTitle: Modifier = Modifier.padding(start = Theme.spacing.xl - BAR_TITLE_INSET)

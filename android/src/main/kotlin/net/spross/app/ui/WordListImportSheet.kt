package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.spross.app.AppModel
import net.spross.app.keepHarvested
import net.spross.kern.box.HarvestWord

/**
 * A word-list file picked in Settings, reviewed before its pairs go in as own words —
 * the same list the chat's way back shows ([HarvestReview]).
 */
@Composable
internal fun WordListImportSheet(model: AppModel, harvested: List<HarvestWord>, onDismiss: () -> Unit) {
    val chrome = model.chrome
    var picked by remember(harvested) { mutableStateOf(harvested.initiallyPicked()) }
    GrowableSheet(
        onDismiss = onDismiss,
        content = {
            Text(chrome.importWordsTitle, style = MaterialTheme.typography.titleLarge)
            HarvestReview(harvested, picked, chrome) { target ->
                picked = if (target in picked) picked - target else picked + target
            }
        },
        action = {
            val kept = harvested.kept(picked)
            HarvestKeepButton(kept, chrome) {
                model.keepHarvested(kept)
                onDismiss()
            }
        },
    )
}

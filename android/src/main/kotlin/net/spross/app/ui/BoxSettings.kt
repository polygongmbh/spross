package net.spross.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.audioSources
import net.spross.app.restartOnboarding
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.LanguageChoices

/**
 * What the settings hold: which pair is being learned, whether words are read aloud,
 * the backup, and the one destructive door — plus the way to who spoke the recordings.
 *
 * Neither picker hides the other's pick: choosing the language the OTHER side holds SWAPS
 * them wherever that swapped pair is one the catalog can teach ([LanguageChoices]), so a
 * pair set backwards is fixed in one move rather than two.
 */
@Composable
fun BoxSettingsSection(model: AppModel, catalog: Catalog, box: BoxState) {
    val chrome = model.chrome
    var confirmingReset by remember { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    // Offers a save-file sheet for this language first when there's settled progress worth
    // keeping — a safety net ahead of the confirmation, never a gate on it: whether the save
    // lands, fails, or is canceled, the destructive confirmation still opens after.
    val resetExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        scope.launch {
            if (uri != null) {
                withContext(Dispatchers.IO) { writeBackupJson(model, resolver, uri, box.joinStamp.target) }
            }
            confirmingReset = true
        }
    }
    val selection = LanguageChoices.Selection(box.joinStamp.source, box.joinStamp.target)
    val targets = remember(catalog, selection) {
        LanguageChoices.targetChoices(catalog, selection)
    }
    val targetName = LanguageChoices.name(box.joinStamp.target, catalog.languages[box.joinStamp.target])

    fun apply(next: LanguageChoices.Selection) {
        // why: a tap on the row already in force must not rebuild the box — re-joining is
        // neither free nor silent.
        appliedPair(next, selection)?.let { (source, target) ->
            model.completeOnboarding(source, target)
        }
    }

    val audioSources = model.audioSources(box.joinStamp.target)

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
        SettingsGroup {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                LanguageMenu(
                    title = chrome.settingsKnownTitle,
                    selected = selection.source,
                    choices = catalog.coveredSources(),
                    catalog = catalog,
                    modifier = Modifier.weight(1f),
                    enabled = !model.switchingLanguage,
                    // The guard the whole picker rests on: only a language the catalog
                    // DECLARES may be asked about its targets, so the rows come from
                    // `coveredSources` and never from a device locale.
                    onPick = { apply(LanguageChoices.pickSource(catalog, selection, it)) },
                )
                LanguageMenu(
                    title = chrome.settingsLearningTitle,
                    selected = selection.target ?: selection.source,
                    choices = targets,
                    catalog = catalog,
                    modifier = Modifier.weight(1f),
                    enabled = !model.switchingLanguage,
                    onPick = { apply(LanguageChoices.pickTarget(selection, it)) },
                )
            }
            // why: the re-join and box walk behind a pick take a beat — said here rather
            // than left silent, so a second tap while it settles reads as "still working"
            // and not as the row having ignored the first one.
            if (model.switchingLanguage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    SettingHint(chrome.settingsProfileSwitching)
                }
            } else {
                SettingHint(chrome.settingsProfileHint)
            }
        }
        SettingsGroup { LearnerNameSetting(model) }
        // why: nothing can say this language — a row whose every option is silence is not a
        // choice, and the two "on" segments would both promise a sound that cannot be made.
        if (!audioSources.silent) SettingsGroup { ReadAloudSetting(model, box.joinStamp.target) }
        SettingsGroup {
            BackupSetting(model, catalog, box.joinStamp.target)
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                TextButton(
                    onClick = { model.restartOnboarding() },
                    contentPadding = SETTINGS_BUTTON_PADDING,
                ) {
                    Text(chrome.settingsRestartTutorialButton)
                }
                SettingHint(chrome.settingsRestartTutorialHint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                TextButton(
                    onClick = {
                        if ((model.stats?.allSettledCount ?: 0) > 0) {
                            resetExport.launch("Spross-${box.joinStamp.target}-${LocalDate.now()}.json")
                        } else {
                            confirmingReset = true
                        }
                    },
                    contentPadding = SETTINGS_BUTTON_PADDING,
                ) {
                    Text(chrome.settingsResetButton.format(targetName), color = Theme.colors.wrong)
                }
                SettingHint(chrome.settingsResetHint.format(targetName))
            }
        }
        AboutFooter(model)
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            // The question IS the title, as on iOS: the button behind it already names the
            // language, and a title repeating it over the same sentence reads twice.
            title = { Text(chrome.settingsResetConfirm.format(targetName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    // Which of the box's contents survive is the ENGINE's ruling: schedules
                    // and tallies go, the join, the configuration and the learner's own
                    // words stay.
                    model.updateBox { BoxEngine.reset(it) }
                }) { Text(chrome.commonReset, color = Theme.colors.wrong) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(chrome.commonCancel) }
            },
        )
    }
}

/** One settings group, on a panel of its own. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().panel()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            content = content,
        )
    }
}

/**
 * A settings `TextButton`'s own default inset reads as indented under the label above it;
 * dropping the horizontal half lines its text up with the group's own edge instead.
 * Shared with [BackupSetting], whose Export/Import buttons sit in the same groups.
 */
internal val SETTINGS_BUTTON_PADDING = PaddingValues(horizontal = 0.dp, vertical = Theme.spacing.sm)

/**
 * What the greeting calls the learner — free text, and empty is an answer: clearing the
 * field takes the name away again ([AppModel.renameLearner]).
 */
@Composable
private fun LearnerNameSetting(model: AppModel) {
    val chrome: Chrome = model.chrome
    // why: the field keeps what is typed, spaces and all — the store is what trims, so a
    // space before a second name does not vanish under the finger that typed it.
    var draft by rememberSaveable { mutableStateOf(model.learnerName.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(chrome.settingsNameTitle, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; model.renameLearner(it) },
            singleLine = true,
            placeholder = { Text(chrome.settingsNamePlaceholder) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingHint(chrome.settingsNameHint)
    }
}

@Composable
internal fun SettingHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

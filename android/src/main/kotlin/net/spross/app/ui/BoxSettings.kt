package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.Language

/**
 * The block under the shelves: which pair is being learnt, whether words are read aloud,
 * and the one destructive door — plus the way to who spoke the recordings.
 *
 * Neither picker hides the other's pick: choosing the language the OTHER side holds SWAPS
 * them wherever that swapped pair is one the catalog can teach ([LanguageChoices]), so a
 * pair set backwards is fixed in one move rather than two.
 */
@Composable
fun BoxSettingsSection(model: AppModel, catalog: Catalog, box: BoxState) {
    val chrome = model.chrome
    var confirmingReset by remember { mutableStateOf(false) }
    val selection = LanguageChoices.Selection(box.joinStamp.source, box.joinStamp.target)
    val targets = remember(catalog, selection) {
        LanguageChoices.targetChoices(catalog, selection)
    }
    val targetName = catalog.languages[box.joinStamp.target]?.name ?: box.joinStamp.target

    fun apply(next: LanguageChoices.Selection) {
        // why: a tap on the row already in force must not rebuild the box — re-joining is
        // neither free nor silent.
        appliedPair(next, selection)?.let { (source, target) ->
            model.completeOnboarding(source, target)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.l)) {
        Text(chrome.settingsTitle, style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier.fillMaxWidth().panel(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(DlSpace.l),
                verticalArrangement = Arrangement.spacedBy(DlSpace.l),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.l)) {
                    LanguageMenu(
                        title = chrome.iSpeak,
                        selected = selection.source,
                        choices = catalog.coveredSources(),
                        catalog = catalog,
                        modifier = Modifier.weight(1f),
                        // The guard the whole picker rests on: only a language the catalog
                        // DECLARES may be asked about its targets, so the rows come from
                        // `coveredSources` and never from a device locale.
                        onPick = { apply(LanguageChoices.pickSource(catalog, selection, it)) },
                    )
                    LanguageMenu(
                        title = chrome.iLearn,
                        selected = selection.target ?: selection.source,
                        choices = targets,
                        catalog = catalog,
                        modifier = Modifier.weight(1f),
                        onPick = { apply(LanguageChoices.pickTarget(selection, it)) },
                    )
                }
                SettingHint(chrome.profileHint)
                HorizontalDivider(color = Dl.colors.separator)
                ReadAloudSetting(model)
                HorizontalDivider(color = Dl.colors.separator)
                Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
                    TextButton(onClick = { confirmingReset = true }) {
                        Text(chrome.resetButton.format(targetName), color = Dl.colors.wrong)
                    }
                    SettingHint(chrome.resetHint.format(targetName))
                }
            }
        }
        AboutFooter(model)
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            // The question IS the title, as on iOS: the button behind it already names the
            // language, and a title repeating it over the same sentence reads twice.
            title = { Text(chrome.resetConfirm.format(targetName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    // Which of the box's contents survive is the ENGINE's ruling: schedules
                    // and tallies go, the join, the configuration and the learner's own
                    // words stay.
                    model.updateBox { BoxEngine.reset(it) }
                }) { Text(chrome.reset, color = Dl.colors.wrong) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(chrome.cancel) }
            },
        )
    }
}

/**
 * One side of the pair. The collapsed label carries the flag and the English exonym — it
 * has half a row to live in — while the open menu has room for "🇺🇦 Українська · Ukrainian".
 *
 * A recessed field with a chevron, the iOS cut's shape: an outlined BUTTON drew the pick in
 * accent ink and centered it, so the name read as an action and a label too wide for half a
 * row was clipped from both ends down to its flag. Here the name is text on a control — ink
 * on the recessed fill, left where a value belongs, stepping down before it is cut.
 */
@Composable
private fun LanguageMenu(
    title: String,
    selected: Language,
    choices: List<Language>,
    catalog: Catalog,
    onPick: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = LanguageChoices.pickerLabel(selected, catalog.languages[selected])
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // why: the spring sits OUTSIDE the fill — a press shrinks the field,
                    // not just the name inside it.
                    .pressSpring()
                    .clip(MaterialTheme.shapes.small)
                    .background(Dl.colors.surfaceTint)
                    .clickable(role = Role.DropdownList) { open = true }
                    // why: one stable label, the pick as its VALUE — the field's own text is
                    // a merged child, so without this TalkBack announces which language but
                    // never which of the two questions it answers.
                    .semantics { contentDescription = title; stateDescription = label }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = DlSpace.m, vertical = DlSpace.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DlSpace.xs),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = PICKER_FLOOR,
                        maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                )
                Icon(
                    SprossIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(LanguageChoices.pickerRow(code, catalog.languages[code])) },
                        onClick = { open = false; onPick(code) },
                    )
                }
            }
        }
    }
}

/** Where an exonym too wide for half a row bottoms out; iOS scales its own to 0.8. */
private val PICKER_FLOOR = 12.sp

/**
 * The same one device-scoped flag the session's top bar switches, and a standing home for
 * the disclosure that a tap on a word speaks it even while this is off.
 */
@Composable
private fun ReadAloudSetting(model: AppModel) {
    val chrome: Chrome = model.chrome
    val muted = model.pronouncer.muted
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                chrome.audioToggle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = !muted,
                onCheckedChange = { model.pronouncer.muted = !it },
                // why: one stable label, the state as its VALUE — the row's text is a
                // sibling node, so without this TalkBack announces a switch with no name.
                modifier = Modifier.semantics {
                    contentDescription = chrome.audioToggle
                    stateDescription = if (muted) chrome.stateOff else chrome.stateOn
                },
            )
        }
        SettingHint(chrome.audioToggleHint)
    }
}

@Composable
private fun SettingHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

package net.spross.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.kern.catalog.LanguageChoices

/**
 * First-launch (and "change languages") picker — iOS OnboardingView parity:
 * chrome is ENGLISH (it renders before the user's language is known), rows
 * are "🇩🇪 German", and neither side hides the other's pick — choosing it
 * swaps the two selections ([LanguageChoices]).
 *
 * One side is open at a time and the other shows its pick as a row that opens it:
 * both lists at once is more of the screen than the two questions are worth, and a
 * pick answers its own question — so choosing a source hands the screen to the target.
 * The known side opens folded, the device language being a good guess already.
 */
@Composable
fun OnboardingScreen(model: AppModel) {
    val catalog = model.catalog ?: return
    val chrome = remember { Chrome.forSource("en") }
    val editing = (model.screen as? Screen.Onboarding)?.editing == true
    val initialSource = model.box?.joinStamp?.source ?: model.defaultSource(catalog)
    var source by rememberSaveable { mutableStateOf(initialSource) }
    var target by rememberSaveable {
        mutableStateOf(
            model.box?.joinStamp?.target
                ?: catalog.availableTargets(initialSource).firstOrNull()?.code
        )
    }
    var pickingSource by rememberSaveable { mutableStateOf(false) }
    val choices = remember(catalog, source, target) {
        LanguageChoices.targetChoices(catalog, LanguageChoices.Selection(source, target))
    }

    fun apply(sel: LanguageChoices.Selection) {
        source = sel.source
        target = sel.target
    }

    val label: (String) -> String = { LanguageChoices.pickerRow(it, catalog.languages[it]) }

    // why: one list open at a time keeps the picker on one screen — the scroll is the
    // reachability floor for a large font scale, never a step of the flow.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(chrome.chooseTitle, style = MaterialTheme.typography.headlineSmall)

        PickerSection(
            heading = chrome.iSpeak,
            open = pickingSource,
            options = catalog.coveredSources(),
            selected = source,
            chrome = chrome,
            label = label,
            onOpen = { pickingSource = true },
        ) { code ->
            apply(LanguageChoices.pickSource(catalog, LanguageChoices.Selection(source, target), code))
            pickingSource = false
        }

        PickerSection(
            heading = chrome.iLearn,
            open = !pickingSource,
            options = choices,
            selected = target,
            chrome = chrome,
            label = label,
            onOpen = { pickingSource = false },
        ) { code ->
            apply(LanguageChoices.pickTarget(LanguageChoices.Selection(source, target), code))
        }

        Button(
            onClick = { target?.let { model.completeOnboarding(source, it) } },
            enabled = target != null,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.letsGo)
        }
        if (editing) {
            TextButton(onClick = { model.cancelOnboarding() }, modifier = Modifier.fillMaxWidth()) {
                Text(chrome.backLabel)
            }
        }
    }
}

/**
 * One side of the pair: its question, and either the languages to choose from or the
 * chosen one as the row that opens them again.
 */
@Composable
private fun PickerSection(
    heading: String,
    open: Boolean,
    options: List<String>,
    selected: String?,
    chrome: Chrome,
    label: (String) -> String,
    onOpen: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        if (open) {
            options.forEach { code ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = selected == code,
                        role = Role.RadioButton,
                        onClick = { onPick(code) },
                    ),
                ) {
                    RadioButton(selected = selected == code, onClick = null)
                    Text(label(code), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                    .clickable(onClick = onOpen)
                    .semantics { stateDescription = chrome.stateCollapsed },
            ) {
                Text(
                    selected?.let(label).orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val choices = remember(catalog, source, target) {
        LanguageChoices.targetChoices(catalog, LanguageChoices.Selection(source, target))
    }

    fun apply(sel: LanguageChoices.Selection) {
        source = sel.source
        target = sel.target
    }

    fun label(code: String) = LanguageChoices.pickerRow(code, catalog.languages[code])

    // why: the whole picker fits one screen at default type — the scroll is the
    // reachability floor for a large font scale, never a step of the flow.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(chrome.chooseTitle, style = MaterialTheme.typography.headlineSmall)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(chrome.iSpeak, style = MaterialTheme.typography.titleSmall)
            // why: scrollable — a row names its language in its own words too, which is
            // what makes it findable and what makes the strip wider than the screen.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                catalog.coveredSources().forEach { code ->
                    if (code == source) {
                        Button(
                            onClick = {},
                            shape = MaterialTheme.shapes.small,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Text(label(code), maxLines = 1)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                apply(
                                    LanguageChoices.pickSource(
                                        catalog,
                                        LanguageChoices.Selection(source, target),
                                        code,
                                    )
                                )
                            },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Text(label(code), maxLines = 1)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(chrome.iLearn, style = MaterialTheme.typography.titleSmall)
            choices.forEach { code ->
                val pick = {
                    apply(LanguageChoices.pickTarget(LanguageChoices.Selection(source, target), code))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .selectable(selected = target == code, role = Role.RadioButton, onClick = pick),
                ) {
                    RadioButton(selected = target == code, onClick = null)
                    Text(label(code), style = MaterialTheme.typography.bodyLarge)
                }
            }
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

package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LanguagePicker
import net.spross.app.Screen

/**
 * First-launch (and "change languages") picker — iOS OnboardingView parity:
 * chrome is ENGLISH (it renders before the user's language is known), rows
 * are "🇩🇪 German", and neither side hides the other's pick — choosing it
 * swaps the two selections ([LanguagePicker]).
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
        LanguagePicker.targetChoices(
            LanguagePicker.Selection(source, target),
            catalog::availableTargets,
        )
    }

    fun apply(sel: LanguagePicker.Selection) {
        source = sel.source
        target = sel.target
    }

    fun label(code: String) = LanguagePicker.rowLabel(code, catalog.languages[code])

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(chrome.chooseTitle, style = MaterialTheme.typography.headlineMedium)

        Text(chrome.iSpeak, style = MaterialTheme.typography.titleMedium)
        // why: scrollable — a row names its language in its own words too, which is
        // what makes it findable and what makes the strip wider than the screen.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            catalog.coveredSources().forEach { code ->
                if (code == source) {
                    Button(onClick = {}, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text(label(code), maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            apply(
                                LanguagePicker.pickSource(
                                    LanguagePicker.Selection(source, target),
                                    code,
                                    catalog::availableTargets,
                                )
                            )
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(label(code), maxLines = 1)
                    }
                }
            }
        }

        Text(chrome.iLearn, style = MaterialTheme.typography.titleMedium)
        choices.forEach { option ->
            val pick = {
                apply(LanguagePicker.pickTarget(LanguagePicker.Selection(source, target), option.code))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(selected = target == option.code, onClick = pick)
                Column {
                    Text(label(option.code), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${option.conceptCount} ${chrome.conceptsSuffix}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { target?.let { model.completeOnboarding(source, it) } },
            enabled = target != null,
            modifier = Modifier.fillMaxWidth(),
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

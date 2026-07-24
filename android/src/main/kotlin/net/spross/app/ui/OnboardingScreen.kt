package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Screen

@Composable
fun OnboardingScreen(model: AppModel) {
    val catalog = model.catalog ?: return
    val chrome = model.chrome
    val editing = (model.screen as? Screen.Onboarding)?.editing == true
    var source by rememberSaveable {
        mutableStateOf(model.box?.joinStamp?.source ?: model.defaultSource(catalog))
    }
    var target by rememberSaveable { mutableStateOf(model.box?.joinStamp?.target) }
    val targets = catalog.availableTargets(source)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(chrome.chooseTitle, style = MaterialTheme.typography.headlineMedium)

        Text(chrome.iSpeak, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.coveredSources(catalog).forEach { code ->
                val name = catalog.languages[code]?.name ?: code
                if (code == source) {
                    Button(onClick = {}, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text(name, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            source = code
                            target = null
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(name, maxLines = 1)
                    }
                }
            }
        }

        Text(chrome.iLearn, style = MaterialTheme.typography.titleMedium)
        targets.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(selected = target == option.code, onClick = { target = option.code })
                Column {
                    Text(option.name, style = MaterialTheme.typography.bodyLarge)
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

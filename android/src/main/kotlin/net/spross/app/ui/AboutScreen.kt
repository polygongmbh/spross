package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.catalog.AudioCredit

/**
 * Who publishes the app ([LegalSection]) and who spoke the bundled recordings. Which build
 * is installed and the read-aloud switch stand in the box's own settings, where the door to
 * this screen is.
 *
 * The credits come from `Catalog.audioCredits()`, derived from the SHIPPED manifests,
 * so this screen can neither credit what is not bundled nor miss what is — and it
 * ships in the same change as the audio it attributes.
 */
@Composable
fun AboutScreen(model: AppModel) {
    val chrome = model.chrome
    BackHandler { model.closeAbout() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.aboutButton,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { model.closeAbout() }) { Icon(SprossIcons.Close, contentDescription = null) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            item { LegalSection(chrome) }
            item {
                Text(chrome.creditsTitle, style = MaterialTheme.typography.titleLarge)
            }
            for ((language, credits) in creditSections(model)) {
                item { LanguageHeading(model, language) }
                items(credits.size) { index -> CreditGroup(credits[index], chrome) }
                item { CreditFooter(chrome) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * Kern emits the groups in language-declaration order, so the distinct languages in
 * first-seen order are the sections.
 */
private fun creditSections(model: AppModel): List<Pair<String, List<AudioCredit>>> {
    val sections = LinkedHashMap<String, MutableList<AudioCredit>>()
    for (credit in model.catalog?.audioCredits().orEmpty()) {
        sections.getOrPut(credit.language) { mutableListOf() } += credit
    }
    return sections.map { (language, credits) -> language to credits.toList() }
}

@Composable
private fun LanguageHeading(model: AppModel, language: String) {
    val info = model.catalog?.languages?.get(language)
    val flag = info?.flag?.let { "$it " }.orEmpty()
    Text(
        "$flag${info?.name ?: language}",
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * One speaker under one license, folding open to the recordings themselves: a bare
 * count is weaker attribution than the files, and both BY and BY-SA ask for a link to
 * the work where giving one is reasonable. BY and BY-SA stay separate groups by
 * construction — one notice cannot carry both.
 */
@Composable
private fun CreditGroup(credit: AudioCredit, chrome: Chrome) {
    var expanded by remember(credit) { mutableStateOf(false) }
    val uris = LocalUriHandler.current
    Column(modifier = Modifier.panel()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(credit.author, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    chrome.creditsRecordings.format(credit.files.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    credit.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (credit.licenseUrl == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    // why: public-domain files have no deed to link to.
                    modifier = credit.licenseUrl?.let { url ->
                        Modifier.clickable { uris.openUri(url) }
                    } ?: Modifier,
                )
            }
            if (expanded) {
                // why: one Commons recording fetched for two slugs ships twice, so
                // neither the label nor the source is a unique identity — index it.
                credit.files.forEachIndexed { index, file ->
                    Spacer(Modifier.height(4.dp))
                    Text(file.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        file.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uris.openUri(commonsUrl(file.source)) },
                    )
                    if (index == credit.files.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

/** The second license obligation beside naming the speaker: nothing was re-encoded. */
@Composable
private fun CreditFooter(chrome: Chrome) {
    Column {
        Text(
            chrome.creditsUnmodified,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            chrome.creditsCommons,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The file's page on Commons — those names carry spaces and Cyrillic alike. */
private fun commonsUrl(source: String): String =
    "https://commons.wikimedia.org/wiki/File:" +
        URLEncoder.encode(source, "UTF-8").replace("+", "%20")

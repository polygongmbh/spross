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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.catalog.AudioCredit

/**
 * Android's only settings surface beyond the language switch: the app's version, the
 * read-aloud row (iOS carries this one in Box settings — Android has no settings
 * screen, a deliberate delta) and who spoke the bundled recordings.
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
            item { VersionLine() }
            item { ReadAloudRow(model) }
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
private fun VersionLine() {
    val context = LocalContext.current
    // why: read off the installed package rather than BuildConfig — the app declares
    // no buildConfig feature, and this is the version the store actually shipped.
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
    Text(
        "Spross v$version",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The same one device-scoped flag the session's top bar switches, and the place the
 * tap-to-replay gesture is disclosed — the card grows no mark for it, so the hint
 * line is where it is named, including that a tap speaks even when this is off.
 */
@Composable
private fun ReadAloudRow(model: AppModel) {
    val chrome = model.chrome
    val muted = model.pronouncer.muted
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                // why: one stable label, the state as its VALUE — the same rule the
                // top bar's switch follows. The label is set HERE, not left to the
                // row's text: that text is a sibling node, so without this TalkBack
                // would announce a switch with no name at all.
                modifier = Modifier.semantics {
                    contentDescription = chrome.audioToggle
                    stateDescription = if (muted) chrome.stateOff else chrome.stateOn
                },
            )
        }
        Text(
            chrome.audioToggleHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
 * One speaker under one licence, folding open to the recordings themselves: a bare
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
                    credit.licence,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (credit.licenceUrl == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    // why: public-domain files have no deed to link to.
                    modifier = credit.licenceUrl?.let { url ->
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

/** The second licence obligation beside naming the speaker: nothing was re-encoded. */
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

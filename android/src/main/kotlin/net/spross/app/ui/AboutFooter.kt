package net.spross.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.Legal

/**
 * The box's foot: the two doors out of it, and under them the build that is running —
 * which is itself the door to a newer one.
 *
 * The version carries the update because an update is the thing it is ABOUT; a button
 * beside it would have said the same noun twice, and four peers in a column read as a
 * list of unrelated errands. It sits UNDER the doors so the foot ends on what this copy
 * is, and it wears their accent, because a build stamp that reads as inert is one nobody
 * taps.
 *
 * The address is printed rather than hidden behind a verb, so it is readable on a device
 * that carries no mail app at all — a deliberate delta from the iPhone, which says
 * "Send feedback" over the same mailto.
 */
@Composable
fun AboutFooter(model: AppModel) {
    val context = LocalContext.current
    val version = appVersion()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // why: the address is the longer label and grows with the font scale, so the pair
        // wraps to a second line rather than clipping it.
        FlowRow(horizontalArrangement = Arrangement.Center) {
            // why: an address is not an errand — spoken, it says where the mail would go and
            // never that a mail is what this opens. The envelope carries that on screen; the
            // door is named for the reader it does not reach.
            FooterDoor(
                SprossIcons.Envelope,
                Legal.CONTACT_ADDRESS,
                spoken = "${model.chrome.settingsFeedback} · ${Legal.CONTACT_ADDRESS}",
            ) { context.openFeedbackMail(version) }
            FooterDoor(SprossIcons.Info, model.chrome.settingsAbout) { model.openAbout() }
        }
        UpdateLine(model.chrome, version)
    }
}

/**
 * The build that is running — the footer's stamp, and the subject a feedback mail rides in
 * on, wherever one is opened ([LegalSection]).
 *
 * why: read off the installed package rather than BuildConfig — the app declares no
 * buildConfig feature, and this is the version the store actually shipped.
 */
@Composable
internal fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        "Spross v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}

/**
 * One door: the mark of what kind it is, then its own name. [spoken] stands in for that
 * name where what is printed is not what the door does.
 */
@Composable
private fun FooterDoor(
    icon: ImageVector,
    label: String,
    spoken: String? = null,
    onClick: () -> Unit,
) {
    // why: the padding is tighter than a button's default so the pair still shares one row
    // on a narrow phone — the address is long, and wrapping is the fallback, not the intent.
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = DlSpace.s, vertical = DlSpace.s),
        modifier = spoken?.let {
            Modifier.semantics(mergeDescendants = true) { contentDescription = it }
        } ?: Modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(DlSpace.xs))
        Text(label)
    }
}

/**
 * Obtainium's Add-App screen if it answers, the offer if it does not.
 *
 * why: a phone that already has Obtainium wants no dialog — the tap lands on a prefilled
 * Add-App screen and the learner is subscribed to every release from then on. Only a phone
 * that cannot answer the scheme is asked which of the two doors it wants, because there
 * the choice is real: a tool that watches for updates, or this one build by hand.
 */
@Composable
private fun UpdateLine(chrome: Chrome, version: String) {
    val context = LocalContext.current
    var offering by remember { mutableStateOf(false) }
    TextButton(
        onClick = { if (!context.openObtainium()) offering = true },
        contentPadding = PaddingValues(horizontal = DlSpace.m, vertical = DlSpace.s),
        // why: the number alone says nothing about where it leads, and a screen reader has
        // no accent to go on — so the door is NAMED here, for the one reader that cannot
        // see it is one.
        modifier = Modifier.semantics { contentDescription = "$version · ${chrome.settingsUpdateButton}" },
    ) {
        Text(version, style = MaterialTheme.typography.bodySmall)
    }
    if (offering) UpdateOffer(chrome) { offering = false }
}

/** Both doors named, neither taken for the learner — dismissing picks neither. */
@Composable
private fun UpdateOffer(chrome: Chrome, onDismiss: () -> Unit) {
    val uris = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chrome.settingsUpdateTitle) },
        text = { Text(chrome.settingsUpdateOffer) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                uris.openUri(OBTAINIUM_URL)
            }) { Text(chrome.settingsUpdateObtainium) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                uris.openUri(RELEASES_URL)
            }) { Text(chrome.settingsUpdateDownload) }
        },
    )
}

/**
 * `obtainium://add/<url>` — the action is the URI's HOST, and the payload its path, which
 * Obtainium decodes back into the source to track. Catching the miss is what lets this work
 * without declaring the scheme in `<queries>`: an unresolvable intent throws rather than
 * returning, so the failure is the signal that Obtainium is absent.
 */
private fun Context.openObtainium(): Boolean =
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("obtainium://add/" + Uri.encode(REPO_URL))))
    }.isSuccess

/**
 * A mailto: through ACTION_SENDTO, so only mail apps answer it. The build rides in the
 * subject — a report is actionable once it names the version it came from — and a device
 * with no mail client stays put instead of crashing.
 *
 * [body] is what the catalog feedback fills in ([BoxFeedbackSection]); the footer's own
 * door opens an empty mail, since the learner is the one with something to say there.
 */
internal fun Context.openFeedbackMail(subject: String, body: String? = null) {
    val query = "subject=${Uri.encode(subject)}" +
        (body?.let { "&body=${Uri.encode(it)}" } ?: "")
    runCatching {
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Legal.CONTACT_ADDRESS}?$query")))
    }
}

/**
 * Where a newer build comes from. The app checks for none of it itself: it declares no
 * INTERNET permission, and every path above hands a URL to another app and stops.
 */
private const val REPO_URL = "https://github.com/polygongmbh/spross"

/** The release the workflow attaches `spross-<version>.apk` to. */
private const val RELEASES_URL = "$REPO_URL/releases/latest"

/** Obtainium's own download page — the store this app is not in. */
private const val OBTAINIUM_URL = "https://obtainium.imranr.dev/"

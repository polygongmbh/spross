package net.spross.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import net.spross.app.Chrome

/**
 * Where the box's footer says newer builds come from. The app itself never checks for one:
 * it declares no INTERNET permission and asks for none here — every path below hands a URL
 * to another app and stops.
 */
private const val REPO_URL = "https://github.com/polygongmbh/spross"

/** The release the workflow attaches `spross-<version>.apk` to. */
private const val RELEASES_URL = "$REPO_URL/releases/latest"

/** Obtainium's own download page — the store this app is not in. */
private const val OBTAINIUM_URL = "https://obtainium.imranr.dev/"

/**
 * Obtainium's Add-App screen if it answers, the offer if it does not.
 *
 * why: a phone that already has Obtainium wants no dialog — the tap lands on a prefilled
 * Add-App screen and the learner is subscribed to every release from then on. Only a phone
 * that cannot answer the scheme is asked which of the two doors it wants, because there
 * the choice is real: a tool that watches for updates, or this one build by hand.
 */
@Composable
fun UpdateRow(chrome: Chrome) {
    val context = LocalContext.current
    var offering by remember { mutableStateOf(false) }
    TextButton(onClick = { if (!context.openObtainium()) offering = true }) {
        Text(chrome.updateButton)
    }
    if (offering) UpdateOffer(chrome) { offering = false }
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

/** Both doors named, neither taken for the learner — dismissing picks neither. */
@Composable
private fun UpdateOffer(chrome: Chrome, onDismiss: () -> Unit) {
    val uris = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chrome.updateOfferTitle) },
        text = { Text(chrome.updateOfferBody) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                uris.openUri(OBTAINIUM_URL)
            }) { Text(chrome.updateViaObtainium) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                uris.openUri(RELEASES_URL)
            }) { Text(chrome.updateDownload) }
        },
    )
}

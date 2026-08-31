package net.spross.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome
import net.spross.kern.Legal

/**
 * Who publishes the app, and where its privacy policy stands — the two things a German
 * provider (§ 5 DDG) has to make findable in the app itself, and this build has no store
 * listing to hold them instead (`docs/distribution.md`).
 *
 * Laid out the way a German Impressum reads, as on the other phone: the company over its
 * address, then one labeled line per registry fact. Nothing here is a link except the two
 * that lead somewhere, so the block stays a block.
 */
@Composable
fun LegalSection(chrome: Chrome) {
    val context = LocalContext.current
    val uris = LocalUriHandler.current
    val version = appVersion()
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
        Text(chrome.legalTitle, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.fillMaxWidth().panel().padding(DlSpace.l),
            verticalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            Column {
                Text(chrome.legalCompany, style = MaterialTheme.typography.titleSmall)
                Text(chrome.legalAddressValue, style = MaterialTheme.typography.bodyMedium)
            }
            Column {
                ImprintLine(chrome.legalDirectorLabel, chrome.legalDirectorValue)
                ImprintLine(chrome.legalRegisterLabel, chrome.legalRegisterValue)
                ImprintLine(chrome.legalVatLabel, chrome.legalVatValue)
                ImprintLine(chrome.legalContactLabel, Legal.CONTACT_ADDRESS) {
                    context.openFeedbackMail(version)
                }
            }
            TextButton(
                onClick = { uris.openUri(Legal.PRIVACY_URL) },
                contentPadding = PaddingValues(horizontal = DlSpace.s, vertical = DlSpace.s),
            ) {
                Text(chrome.legalPrivacy, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * "Registergericht: Amtsgericht Coburg, HRB 7580" — label and fact on one line, which is
 * how the notice is read and half the height of stacking them. [onOpen] is for the one
 * fact that is a live address rather than a line of text.
 */
@Composable
private fun ImprintLine(label: String, value: String, onOpen: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Dl.colors.textSecondary,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (onOpen == null) Dl.colors.textPrimary else Dl.colors.accent,
            modifier = if (onOpen == null) {
                Modifier
            } else {
                Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onOpen)
            },
        )
    }
}

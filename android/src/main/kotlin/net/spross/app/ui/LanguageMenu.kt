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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.Language

/**
 * One side of the pair. The collapsed label carries the flag and the English exonym — it
 * has half a row to live in — while the open menu has room for "🇺🇦 Українська · Ukrainian".
 *
 * A recessed field with a chevron, the iOS cut's shape: an outlined BUTTON drew the pick in
 * accent ink and centered it, so the name read as an action and a label too wide for half a
 * row was clipped from both ends down to its flag. Here the name is text on a control — ink
 * on the recessed fill, left where a value belongs, stepping down before it is cut.
 *
 * A plain click opens a [DropdownMenu] under the row: under [pressSpring], M3's
 * `ExposedDropdownMenuBox` drops every press held longer than an instant, so a real finger
 * never opens it. The row stays custom rather than a full M3 `TextField`: a filled field's
 * fixed label gutter would cost the pill its 48 dp floor and the autosize step that keeps a
 * long exonym ("Українська") on one line without shrinking below [PICKER_FLOOR].
 */
@Composable
internal fun LanguageMenu(
    title: String,
    selected: Language,
    choices: List<Language>,
    catalog: Catalog,
    onPick: (Language) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val label = LanguageChoices.pickerLabel(selected, catalog.languages[selected])
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // why: the spring sits OUTSIDE the fill — a press shrinks the field,
                    // not just the name inside it.
                    .pressSpring()
                    .clip(MaterialTheme.shapes.small)
                    .background(Theme.colors.surfaceTint)
                    .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                    // why: one stable label, the pick as its VALUE — the field's own text is
                    // a merged child, so without this TalkBack announces which language but
                    // never which of the two questions it answers.
                    .semantics { contentDescription = title; stateDescription = label }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
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
            // why: the rows name each language twice ("Deutsch · German"); held to the
            // half-width field they would wrap, so the list takes the width it needs.
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(LanguageChoices.pickerRow(code, catalog.languages[code])) },
                        onClick = { open = false; onPick(code) },
                        // why: the open list marks the CURRENT pick — otherwise the only
                        // trace of it is the field text now hidden behind the menu.
                        trailingIcon = if (code == selected) {
                            { Icon(SprossIcons.Check, contentDescription = null, tint = Theme.colors.accent) }
                        } else null,
                    )
                }
            }
        }
    }
}

/** Where an exonym too wide for half a row bottoms out; iOS scales its own to 0.8. */
private val PICKER_FLOOR = 12.sp

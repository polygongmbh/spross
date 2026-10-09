package net.spross.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.Language

/**
 * One side of the pair, as Material's exposed dropdown: a read-only outlined field whose
 * label is the question it answers. The field carries the flag and the English exonym — it
 * has half a row to live in — while the open menu has room for "🇺🇦 Українська · Ukrainian".
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = LanguageChoices.pickerLabel(selected, catalog.languages[selected]),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        // why: the rows name each language twice ("Deutsch · German"); held to the
        // half-width field they would wrap, so the list takes the width it needs.
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            matchAnchorWidth = false,
        ) {
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

package net.spross.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.box.AreaGroupSection
import net.spross.kern.box.AreaStatistics
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine

/**
 * A foldable group row — a hairline and no card of its own, so the area cards below stay
 * the heaviest thing on the screen. Folded shut, the emojis are all that says what is inside.
 */
@Composable
internal fun GroupHeader(
    section: AreaGroupSection,
    emojis: String,
    open: Boolean,
    chrome: Chrome,
    onToggle: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(onClick = onToggle)
                .semantics {
                    stateDescription = if (open) chrome.stateExpanded else chrome.stateCollapsed
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
        ) {
            Text(if (open) "⌄" else "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(emojis, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        HorizontalDivider(color = Dl.colors.separator)
    }
}

/**
 * One area as a single foldable card: its heading and bar, its words underneath once opened.
 * Packing sits BESIDE the heading as its own tap target — it must never cost the fold.
 */
@Composable
internal fun AreaSection(
    model: AppModel,
    area: String,
    naming: AreaNaming,
    stats: AreaStatistics?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chrome = model.chrome
    val box = model.box ?: return
    val packable = BoxBrowser.enqueueableCardIds(box, area)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(DlSpace.l)) {
            Row(verticalAlignment = Alignment.Top) {
                AreaChip(
                    name = naming.title(area),
                    emoji = naming.emoji(area),
                    subtitle = naming.subtitle(area),
                    stats = stats,
                    chrome = chrome,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggle)
                        .semantics {
                            stateDescription =
                                if (expanded) chrome.stateExpanded else chrome.stateCollapsed
                        },
                )
                PackControl(chrome, packable.size) {
                    model.updateBox { BoxEngine.enqueue(it, packable) }
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = DlSpace.m),
                    verticalArrangement = Arrangement.spacedBy(DlSpace.s),
                ) {
                    BoxBrowser.cardsInArea(box, area).forEach { card ->
                        BoxCardRow(model, card)
                    }
                }
            }
        }
    }
}

/**
 * What packing this shelf would add, as a control: a plus while there is anything left to
 * take in, a settled check once there is not. The count rides in the spoken label rather
 * than on the button's face, which keeps the heading one line tall.
 */
@Composable
internal fun PackControl(chrome: Chrome, count: Int, onPack: () -> Unit) {
    if (count > 0) {
        TextButton(
            onClick = onPack,
            modifier = Modifier.semantics { contentDescription = chrome.packArea.format(count) },
        ) {
            Text("＋", style = MaterialTheme.typography.titleLarge)
        }
    } else {
        Text(
            SEAL,
            color = Dl.colors.success,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .padding(DlSpace.m)
                .semantics { contentDescription = chrome.packDone },
        )
    }
}

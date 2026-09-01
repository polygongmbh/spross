package net.spross.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    stateDescription = if (open) chrome.a11yStateExpanded else chrome.a11yStateCollapsed
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
        ) {
            Icon(
                if (open) SprossIcons.ChevronDown else SprossIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    /** Handed on to every row's menu: writing an own word from one of these. */
    onWriteOwn: (OwnWordDraft) -> Unit,
) {
    val chrome = model.chrome
    val box = model.box ?: return
    // why: both numbers come from the one pass the model already took — asked here,
    // each was a scan and a sort of the whole box, per shelf, per frame. The pack
    // itself still derives its ids from the state it is about to change, so the
    // count and the action cannot come apart.
    val counts = model.shelfCounts[area]
    val cards = remember(box.cards, area, expanded) {
        if (expanded) BoxBrowser.cardsInArea(box, area) else emptyList()
    }

    Column(
        modifier = Modifier.fillMaxWidth().panel(),
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
                                if (expanded) chrome.a11yStateExpanded else chrome.a11yStateCollapsed
                        },
                )
                PackControl(chrome, counts?.packable ?: 0, counts?.queued ?: 0,
                    onPack = {
                        model.updateBox { BoxEngine.enqueue(it, BoxBrowser.enqueueableCardIds(it, area)) }
                    },
                    onUnpack = { model.updateBox { BoxEngine.dequeueArea(it, area) } })
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = DlSpace.m),
                    verticalArrangement = Arrangement.spacedBy(DlSpace.s),
                ) {
                    cards.forEach { card ->
                        BoxCardRow(model, card, onWriteOwn = onWriteOwn)
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
 *
 * Once nothing is left to pack, a shelf holding words still queued for a round offers to
 * take them back out AS A BATCH ([onUnpack]) — the area is the unit this control acts on.
 */
@Composable
internal fun PackControl(
    chrome: Chrome,
    count: Int,
    queuedCount: Int,
    onPack: () -> Unit,
    onUnpack: () -> Unit,
) {
    if (count > 0) {
        TextButton(
            onClick = onPack,
            modifier = Modifier.semantics { contentDescription = chrome.boxShelfPack.format(count) },
        ) {
            Icon(SprossIcons.PackIn, contentDescription = null)
        }
    } else if (queuedCount > 0) {
        TextButton(
            onClick = onUnpack,
            modifier = Modifier.semantics { contentDescription = chrome.boxShelfUnpack.format(queuedCount) },
        ) {
            Icon(SprossIcons.PackOut, contentDescription = null, tint = Dl.colors.success)
        }
    } else {
        Text(
            SEAL,
            color = Dl.colors.success,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .padding(DlSpace.m)
                .semantics { contentDescription = chrome.boxShelfPacked },
        )
    }
}

/**
 * Per-area heading: emoji, name, the catalog's own flavor clause where it authors one,
 * the two (or three) counts, and the bar underneath them.
 *
 * Plain content, no card chrome of its own: it sits inside the area's card, and a second
 * background there would read as a card inside a card.
 */
@Composable
fun AreaChip(
    name: String,
    emoji: String,
    subtitle: String?,
    stats: AreaStatistics?,
    chrome: Chrome,
    modifier: Modifier = Modifier,
) {
    val consolidated = stats?.consolidated ?: 0
    val learning = stats?.learning ?: 0
    val locked = stats?.phrasesLocked ?: 0
    val spoken = buildList {
        add(name)
        add(chrome.progressConsolidatedCount.format(consolidated))
        add(chrome.progressLearningCount.format(learning))
        if (locked > 0) add(chrome.boxAreaPhrasesLocked.format(locked))
    }.joinToString(", ")

    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.m)) {
            // Two counts where the bar beneath draws three rungs: there is room here for
            // the split that matters (cleared the bar, or not yet), and the bar carries
            // the finer one.
            CountLabel("$SEAL ${chrome.progressConsolidatedCount.format(consolidated)}", Dl.colors.grown)
            CountLabel("$LEAF ${chrome.progressLearningCount.format(learning)}", Dl.colors.success)
            // why: the padlock carries the "locked", so the text only names what is
            // locked — and it appears only when it says something.
            if (locked > 0) CountLabel("$LOCK ${chrome.boxAreaPhrasesLockedShort.format(locked)}")
        }
        AreaProgressBar(stats ?: EMPTY_AREA)
    }
}

@Composable
private fun CountLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
    )
}

/** What an area the statistics have not caught up with draws: a bar with nothing on it. */
private val EMPTY_AREA = AreaStatistics(
    name = "",
    total = 0,
    active = 0,
    consolidated = 0,
    settling = 0,
    phrasesLocked = 0,
    phrasesUnlocked = 0,
)

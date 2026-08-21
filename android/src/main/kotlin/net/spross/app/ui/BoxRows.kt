package net.spross.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.CardDisplay
import net.spross.app.Chrome
import net.spross.app.audio.Pronouncer
import net.spross.kern.box.AreaStatistics
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.CardRowState
import net.spross.kern.box.OwnWords
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.model.Card
import net.spross.kern.model.Realization
import net.spross.kern.model.shownArticle

/**
 * One word as the box lists it: its picture, the TARGET citation over the word the learner
 * already knows, and its standing.
 *
 * The row itself is the audio control — no speaker icon competing with the wake and pack
 * controls for width; a plain tap anywhere on it speaks the target, whether or not reading
 * aloud is switched on (a tap is a request, never an autoplay).
 *
 * [pack] is the row's one variation, and it is offered ONLY where a single word can be
 * packed — a search hit, which the learner went looking for by name. In an area listing no
 * such offer is made (the shelf's own control packs there), which is why the offer is a
 * parameter rather than something the row works out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxCardRow(model: AppModel, card: Card, pack: (() -> Unit)? = null) {
    val chrome = model.chrome
    val state = model.box ?: return
    val standing = BoxBrowser.cardRowState(state, card.id, packOffered = pack != null)
    val pronounce = model.boxPronounceAction(card.target)
    // why: only a word the learner wrote is theirs to delete — a catalog word can be put
    // to sleep, never removed, so it grows no such gesture at all.
    val remove: (() -> Unit)? = when {
        OwnWords.owns(card.id) -> ({ model.updateBox { BoxEngine.removeOwnWord(it, card.id) } })
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val actions = buildList {
        if (pronounce != null) add(CustomAccessibilityAction(chrome.pronounce) { pronounce(); true })
        if (remove != null) add(CustomAccessibilityAction(chrome.ownWordRemove) { remove(); true })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.small,
            )
            .semantics { customActions = actions }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = pronounce != null || remove != null,
                onLongClick = remove,
                onClick = pronounce ?: {},
            )
            .padding(horizontal = DlSpace.m, vertical = DlSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        // why: the picture repeats the word beside it — spoken, it is noise.
        Text(
            card.emoji ?: LEAF,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Column(Modifier.weight(1f)) {
            // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
            Text(
                localizedTarget(Dl.colors.articleColoredText(card.target), card.target.lang),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                card.source.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        CardStanding(model, card, standing, pack, chrome)
    }
}

/**
 * The row's right edge, drawn. WHICH of the five things a row has to state is the box's
 * ruling ([BoxBrowser.cardRowState]) — including that an unexposed card states nothing at
 * all, since in a shelf of unstarted words a "new" badge would be most of the rows.
 */
@Composable
private fun CardStanding(
    model: AppModel,
    card: Card,
    standing: CardRowState,
    pack: (() -> Unit)?,
    chrome: Chrome,
) {
    when (standing) {
        CardRowState.Sleeping -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.xs),
        ) {
            Text("💤", modifier = Modifier.semantics { contentDescription = chrome.suspended })
            TextButton(onClick = {
                model.updateBox { BoxEngine.setSuspended(it, card.id, false) }
            }) {
                Text(chrome.wake, style = MaterialTheme.typography.bodySmall)
            }
        }

        CardRowState.PackOffered -> pack?.let {
            TextButton(
                onClick = it,
                modifier = Modifier.semantics { contentDescription = chrome.packWord },
            ) {
                Icon(SprossIcons.Plus, contentDescription = null)
            }
        }

        CardRowState.Packed -> Text(
            SEAL,
            color = Dl.colors.success,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .sizeIn(minHeight = 48.dp)
                .semantics { contentDescription = chrome.packedWord },
        )

        CardRowState.Plain -> Unit

        is CardRowState.Standing -> PhaseBadge(standing.phase, standing.consolidated, chrome)
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
        add(chrome.dayConsolidated.format(consolidated))
        add(chrome.progressFresh.format(learning))
        if (locked > 0) add(chrome.phrasesLockedSpoken.format(locked))
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
            CountLabel("$SEAL ${chrome.dayConsolidated.format(consolidated)}")
            CountLabel("$LEAF ${chrome.progressFresh.format(learning)}")
            // why: the padlock carries the "locked", so the text only names what is
            // locked — and it appears only when it says something.
            if (locked > 0) CountLabel("$LOCK ${chrome.phrasesLocked.format(locked)}")
        }
        AreaProgressBar(stats ?: EMPTY_AREA)
    }
}

@Composable
private fun CountLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** What an area the statistics have not caught up with draws: a bar with nothing on it. */
private val EMPTY_AREA = AreaStatistics(
    name = "",
    total = 0,
    active = 0,
    consolidated = 0,
    phrasesLocked = 0,
    phrasesUnlocked = 0,
)

/**
 * Tap-to-replay for a word standing OUTSIDE a session, where the language is the card's own
 * rather than the run's. Null where the device can neither play nor speak it — a word that
 * cannot be heard grows no gesture that does nothing.
 */
fun AppModel.boxPronounceAction(target: Realization): (() -> Unit)? {
    val pronunciation: Pronunciation =
        catalog?.pronunciation(target.lang, target.text) ?: return null
    if (!pronouncer.canPronounce(pronunciation)) return null
    // The citation form is the canonical one here — no rotation reaches a browser row — so
    // the voice says it with its article, exactly as the row draws it (`docs/read-aloud.md`).
    val article = shownArticle(CardDisplay.article(target), target.text, target.text)
    return { pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP, article) }
}

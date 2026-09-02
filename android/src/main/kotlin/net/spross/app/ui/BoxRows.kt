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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import net.spross.app.removeOwnWord
import net.spross.app.reportedIssue
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.CardRowState
import net.spross.kern.box.OwnWords
import net.spross.kern.model.kindEmoji
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
 * aloud is switched on (a tap is a request, never an autoplay). A long press opens the
 * word's own menu ([BoxRowMenu]) — everything a learner might want to do to this one word —
 * and a reported word wears its flag beside whatever standing it already had.
 *
 * [pack] is the row's one variation, and it is offered ONLY where a single word can be
 * packed — a search hit, which the learner went looking for by name. In an area listing no
 * such offer is made (the shelf's own control packs there), which is why the offer is a
 * parameter rather than something the row works out. Taking a word back OUT of the queue
 * needs no such parameter: [BoxEngine.dequeue] is offered wherever a queued row is drawn.
 * The menu offers both regardless: a menu opened by name is the learner naming this word.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxCardRow(
    model: AppModel,
    card: Card,
    pack: (() -> Unit)? = null,
    /** Opens the own-word form — on a copy of this card, or on the word itself. */
    onWriteOwn: ((OwnWordDraft) -> Unit)? = null,
) {
    val chrome = model.chrome
    val state = model.box ?: return
    val standing = BoxBrowser.cardRowState(state, card.id, packOffered = pack != null)
    // why: this hands back a fresh closure every time it is asked, so unremembered it
    // changed the row's own click identity once a frame and no row could ever be skipped.
    val pronounce = remember(card.id, model.catalog) { model.boxPronounceAction(card.target) }
    // why: only a word the learner wrote is theirs to delete — a catalog word can be put
    // to sleep, never removed, so it grows no such gesture at all.
    val remove: (() -> Unit)? = remember(card.id) {
        if (OwnWords.owns(card.id)) ({ model.removeOwnWord(card.id) }) else null
    }
    var menuOpen by remember(card.id) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val actions = remember(pronounce, remove, chrome) {
        buildList {
            if (pronounce != null) add(CustomAccessibilityAction(chrome.a11yActionPronounce) { pronounce(); true })
            if (remove != null) add(CustomAccessibilityAction(chrome.boxOwnWordRemove) { remove(); true })
        }
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
                enabled = true,
                onLongClickLabel = chrome.boxCardActions,
                onLongClick = { menuOpen = true },
                onClick = pronounce ?: {},
            )
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        // why: the picture repeats the word beside it — spoken, it is noise.
        Text(
            card.emoji ?: kindEmoji(card.kind),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Column(Modifier.weight(1f)) {
            // Exposure surfaces render the TARGET side first (`kern/docs/reports.md`).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                Text(
                    localizedTarget(Theme.colors.articleColoredText(card.target), card.target.lang),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // why: `pronounce` is nil for exactly the words neither a recording nor the
                // device's own voice can say — the one state where the row's tap-to-speak
                // does nothing, so it is the one that needs to say so.
                if (pronounce == null) {
                    Icon(
                        SprossIcons.SpeakerOff,
                        contentDescription = chrome.boxCardNoAudio,
                        tint = Theme.colors.textSecondary,
                        modifier = Modifier.size(SPEAKER_GLYPH),
                    )
                }
            }
            Text(
                card.source.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // why: standing apart from the badge on purpose — a report says nothing about where
        // the word stands, and a reported word keeps whatever badge it had.
        if (model.reportedIssue(card.id) != null) {
            Text("🚩", modifier = Modifier.semantics { contentDescription = chrome.reportReported })
        }
        CardStanding(model, card, standing, pack, chrome)
        BoxRowMenu(
            model = model,
            card = card,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onWriteOwn = onWriteOwn,
        )
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
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text("💤", modifier = Modifier.semantics { contentDescription = chrome.boxCardSuspended })
            TextButton(onClick = {
                model.updateBox { BoxEngine.setSuspended(it, card.id, false, model.now()) }
            }) {
                Text(chrome.boxCardWake, style = MaterialTheme.typography.bodySmall)
            }
        }

        CardRowState.PackOffered -> pack?.let {
            TextButton(
                onClick = it,
                modifier = Modifier.semantics { contentDescription = chrome.boxCardPack },
            ) {
                Icon(SprossIcons.PackIn, contentDescription = null)
            }
        }

        // Direct tap, no confirmation: nothing has been studied yet, so taking a queued
        // word back out costs it nothing (mirrors CardRowState.Sleeping's own "Wake" tap).
        // Offered per word only where packOffered gates it — an area listing takes its
        // whole queue out through the shelf's own control (PackControl) instead.
        is CardRowState.Packed -> if (standing.removalOffered) {
            TextButton(
                onClick = { model.updateBox { BoxEngine.dequeue(it, card.id) } },
                modifier = Modifier.semantics { contentDescription = chrome.boxCardUnpack },
            ) {
                Icon(SprossIcons.PackOut, contentDescription = null, tint = Theme.colors.success)
            }
        } else {
            // A pill, not an icon: a bare tray glyph reads as a control here too, and
            // this one has none — the shelf's own takes the whole queue out. Clay, not
            // green: green is the growth ladder's, and a queued word is not on it yet.
            Pill(chrome.boxCardQueued, Theme.colors.accent)
        }

        CardRowState.Plain -> Unit

        is CardRowState.Standing -> PhaseBadge(standing, chrome)
    }
}

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

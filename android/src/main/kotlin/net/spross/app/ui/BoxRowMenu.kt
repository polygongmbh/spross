package net.spross.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.forgetCard
import net.spross.app.ownWords
import net.spross.app.removeOwnWord
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.CardRowState
import net.spross.kern.model.Card

/**
 * Everything a learner might want to do to ONE word, on the long press every box row carries.
 *
 * Only what applies, and in one order wherever a row is drawn: where the word stands in the
 * queue, then where it stands in the schedule, then what can be made of it, then what is
 * wrong with it, and last — set apart — taking it out for good.
 *
 * WHICH of those apply is kern's answer, not this file's: [BoxBrowser.cardRowState] rules on
 * the standing, and it is asked with `packOffered = true` because the MENU always offers the
 * queue. The row's own right edge is the surface that varies (a shelf packs by the shelf);
 * a menu opened by name is the learner naming this one word.
 *
 * The session card's menu is deliberately NOT this one ([ReportableCard]): a round is no
 * place to reorganize the box.
 */
@Composable
internal fun BoxRowMenu(
    model: AppModel,
    card: Card,
    expanded: Boolean,
    onDismiss: () -> Unit,
    /** Opens the own-word form on the draft handed to it — a copy of this card, or an edit. */
    onWriteOwn: ((OwnWordDraft) -> Unit)?,
) {
    val chrome = model.chrome
    val state = model.box ?: return
    val standing = BoxBrowser.cardRowState(state, card.id, packOffered = true)
    val scheduled = standing is CardRowState.Sleeping || standing is CardRowState.Standing
    val own = model.ownWords.firstOrNull { it.id == card.id }
    val stamp = state.joinStamp

    CardMenu(
        model = model,
        card = card,
        expanded = expanded,
        // Nothing was being answered here, so the report carries no typed answer.
        learnerInput = "",
        onDismiss = onDismiss,
        before = { close ->
            // why: the row has no width for a gloss and the box has no card to reveal, so
            // the long press is where a word explains itself — the same line the session's
            // reveal wears ([PauseLine]), above every action rather than among them.
            (card.target.note ?: card.source.note)?.let { note ->
                PauseLine(note, Modifier.widthIn(max = 280.dp).padding(Theme.spacing.md))
                HorizontalDivider()
            }
            when (standing) {
                CardRowState.PackOffered -> MenuAction(chrome.boxCardPack) {
                    close()
                    model.updateBox { BoxEngine.enqueue(it, listOf(card.id)) }
                }

                is CardRowState.Packed -> MenuAction(chrome.boxCardUnpack) {
                    close()
                    model.updateBox { BoxEngine.dequeue(it, card.id) }
                }

                is CardRowState.Standing -> MenuAction(chrome.boxCardSleep) {
                    close()
                    model.updateBox { BoxEngine.setSuspended(it, card.id, true, model.now()) }
                }

                CardRowState.Sleeping -> MenuAction(chrome.boxCardWake) {
                    close()
                    model.updateBox { BoxEngine.setSuspended(it, card.id, false, model.now()) }
                }

                CardRowState.Plain -> Unit
            }
            if (scheduled) {
                MenuAction(chrome.boxCardForget) { close(); model.forgetCard(card.id) }
            }
            onWriteOwn?.let { write ->
                // Both sides carried over: the learner wants THIS pair in their own words,
                // not a blank sheet with the search box's guess in it.
                MenuAction(chrome.boxCardOwnFrom) {
                    close()
                    write(
                        OwnWordDraft(
                            known = card.source.text,
                            learning = card.target.text,
                            emoji = card.emoji.orEmpty(),
                        ),
                    )
                }
                own?.let { word ->
                    MenuAction(chrome.boxOwnWordEdit) {
                        close()
                        write(OwnWordDraft.of(word, stamp.source, stamp.target))
                    }
                }
            }
        },
        after = { close ->
            // why: only a word the learner wrote is theirs to delete — a catalog word can be
            // put to sleep, never removed, so it grows no such entry at all. Last and in the
            // error color: the one irreversible thing in the menu.
            own?.let { word ->
                MenuAction(chrome.boxOwnWordRemove, destructive = true) {
                    close()
                    model.removeOwnWord(word.id)
                }
            }
        },
    )
}

/** One entry of a word's menu; [destructive] is the only one that is not undoable. */
@Composable
internal fun MenuAction(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        onClick = onClick,
    )
}

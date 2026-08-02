package net.spross.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.SessionUi
import net.spross.app.audio.Pronouncer
import net.spross.app.pronounceAction
import net.spross.app.pronounceTarget
import net.spross.kern.catalog.speechKey
import net.spross.kern.model.Card
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Rating
import net.spross.kern.session.Match
import net.spross.kern.session.spokenOnly

private sealed interface ProduceMode {
    data object Idle : ProduceMode
    data object Correct : ProduceMode
    data class Typo(val corrected: String) : ProduceMode
    /** Right word, wrong one: a form this card accepts that is not what played. */
    data class Heard(val spoken: String) : ProduceMode
    data object Wrong : ProduceMode
    /** Wrong, but the typed form is another concept's word — the reveal names it. */
    data class OtherWord(val word: String, val meanings: List<String>) : ProduceMode
    data object SelfGrade : ProduceMode
}

/**
 * The form a reveal leaves standing on the card: a typo's own correction — the
 * spelling the learner missed — and otherwise the bare target word. Never the
 * article-carrying citation: articles are taught by their color, never spoken.
 */
private fun revealedForm(mode: ProduceMode, card: Card): String =
    if (mode is ProduceMode.Typo) mode.corrected else card.target.text

/** A form the card itself lists as a synonym or a variant — right word, wrong one. */
private fun alsoAccepted(input: String, card: Card): Boolean {
    val typed = speechKey(input)
    return (card.target.synonyms + card.target.variants).any { speechKey(it) == typed }
}

@Composable
fun ProduceCard(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    val targetName = model.catalog?.languages?.get(card.target.lang)?.name ?: card.target.lang
    var input by remember(card.id) { mutableStateOf("") }
    var mode by remember(card.id) { mutableStateOf<ProduceMode>(ProduceMode.Idle) }

    val heard = ui.producePrompt == ProducePrompt.Sound

    fun check() {
        if (input.isBlank()) return
        // why: asked by ear ⇒ only the form that PLAYED counts, the letter drill's own
        // rule (`spokenOnly`) — crediting a synonym would credit a word never heard.
        val graded = if (heard) spokenOnly(card, card.target.text) else card
        // why: the catalog grader, not the bare normalizer — a form another
        // concept owns is that word, not a forgiven slip of this one (kern §6).
        val match = model.produceGrader?.grade(input, graded) ?: return
        // why: BEFORE the verdict — the narrowed answer set would otherwise fail a
        // synonym the reveal itself teaches ("auch: …"). Amber, never wrong.
        if (heard && match !is Match.Exact && alsoAccepted(input, card)) {
            mode = ProduceMode.Heard(card.target.text)
            return
        }
        mode = when (match) {
            is Match.Exact -> ProduceMode.Correct
            is Match.Typo -> ProduceMode.Typo(match.corrected)
            is Match.OtherWord -> ProduceMode.OtherWord(match.word, match.meanings)
            is Match.Wrong -> ProduceMode.Wrong
        }
    }

    // The one-shot fired flag: the word belongs to the card, not to the transition
    // that happened to reach it, so a second path can never say it twice.
    var spoken by remember(card.id) { mutableStateOf(false) }

    // why: clean correct answers auto-advance after ~1.2 s (design.md review UX);
    // typos and reveals wait for an explicit tap instead.
    LaunchedEffect(mode) {
        when (val current = mode) {
            ProduceMode.Idle -> Unit
            // Deliberately silent: the card is already flipping, in less time than a
            // word lasts — a word cut off every time teaches worse than one not
            // played, and the next recognition of the card says it in full (§6.2).
            ProduceMode.Correct -> {
                delay(1200)
                model.answerCurrent(Rating.Good)
            }
            // The paths that HOLD the learner on the answer, and the only ones that
            // speak: the beat lets the feedback chime finish first, and the effect
            // dies with the card, so a tap through inside it takes the word along.
            else -> {
                delay(CHIME_CLEARANCE_MS)
                if (!spoken) {
                    spoken = true
                    model.pronounceTarget(revealedForm(current, card), Pronouncer.Trigger.AUTO)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (ui.emojiCue == EmojiCue.Upfront) {
            Text(card.emoji.orEmpty(), fontSize = 64.sp)
        }
        if (heard) {
            // why: the meaning is withheld ON PURPOSE, so no cue rides along with it —
            // the replay glyph is the whole question. Autoplay already said the word;
            // this is the way to hear it again.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { model.pronounceTarget(card.target.text, Pronouncer.Trigger.TAP) }
                    // why: merged, or the loudspeaker would be a node of its own and
                    // TalkBack would read the picture after the button it belongs to.
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = chrome.pronounce
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("🔊", fontSize = 30.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.source.text, style = MaterialTheme.typography.headlineLarge)
                if (card.promptFeminineMarker) {
                    Text(" ♀", style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            // why: readOnly (not disabled) after grading — the keyboard stays up
            // so Enter still advances past the reveal (design.md review UX).
            readOnly = mode != ProduceMode.Idle,
            placeholder = { Text(chrome.answerPlaceholder.format(targetName)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                when (mode) {
                    ProduceMode.Idle -> check()
                    is ProduceMode.Typo, is ProduceMode.Heard -> model.answerCurrent(Rating.Hard)
                    ProduceMode.Wrong, is ProduceMode.OtherWord -> model.answerCurrent(Rating.Again)
                    else -> Unit
                }
            }),
            singleLine = true,
        )

        // Tap-to-replay is asked for inside the reveal branches rather than hoisted:
        // resolving a form is cheap but not free, and Idle recomposes on every keystroke.
        when (val current = mode) {
            ProduceMode.Idle -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        input = card.target.text
                        mode = ProduceMode.SelfGrade
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(chrome.reveal)
                }
                Button(
                    onClick = { check() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(chrome.check)
                }
            }
            ProduceMode.Correct -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.emojiCue == EmojiCue.OnReveal) {
                    Text(card.emoji.orEmpty(), fontSize = 64.sp)
                }
                // why: no replay here, and nothing said — the card is on its way out
                // (the iOS clean-correct decision, and a decision it is).
                Text(
                    localizedTarget("✓ ${card.target.text}", card.target.lang),
                    style = MaterialTheme.typography.titleLarge,
                    color = ToneRight,
                )
            }
            is ProduceMode.Typo -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(chrome.typoNote, color = ToneTough,
                    style = MaterialTheme.typography.bodyMedium)
                TargetReveal(
                    card.target, chrome,
                    pronounce = model.pronounceAction(current.corrected),
                )
                Button(
                    onClick = { model.answerCurrent(Rating.Hard) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.next)
                }
            }
            is ProduceMode.Heard -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // why: same slot as the typo note — both say what became of the answer.
                Text(chrome.heardInstead.format(current.spoken), color = ToneTough,
                    style = MaterialTheme.typography.bodyMedium)
                TargetReveal(
                    card.target, chrome,
                    pronounce = model.pronounceAction(current.spoken),
                )
                Button(
                    onClick = { model.answerCurrent(Rating.Hard) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.next)
                }
            }
            is ProduceMode.OtherWord -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // why: same slot as the typo note — both say what became of the answer.
                Text(
                    chrome.otherWordNote.format(current.word, current.meanings.joinToString(", ")),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TargetReveal(
                    card.target, chrome,
                    pronounce = model.pronounceAction(card.target.text),
                )
                Button(
                    onClick = { model.answerCurrent(Rating.Again) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.next)
                }
            }
            ProduceMode.Wrong -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetReveal(
                    card.target, chrome,
                    pronounce = model.pronounceAction(card.target.text),
                )
                Button(
                    onClick = { model.answerCurrent(Rating.Again) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.next)
                }
            }
            ProduceMode.SelfGrade -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetReveal(
                    card.target, chrome,
                    pronounce = model.pronounceAction(card.target.text),
                )
                RatingButtons(chrome, onRate = { model.answerCurrent(it) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

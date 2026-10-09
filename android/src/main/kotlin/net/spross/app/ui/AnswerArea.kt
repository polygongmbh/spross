package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.design.Palette
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerControls.Confirm
import net.spross.kern.session.AnswerControls.GiveUp
import net.spross.kern.session.AnswerControls.Primary
import net.spross.kern.session.AnswerControls.Slot
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.SelfGrading
import net.spross.kern.session.TurnFeedback

/**
 * What stands under a question card, review and drill alike: kern's [AnswerControls] drawn.
 * Which controls stand is kern's call; this only draws them and hands each tap to [actions].
 * The field, the correction box, the self-grade row and every button are drawn here once;
 * the one input a drill brings of its own — the tiles — plugs in as [tiles].
 * An arrangement's bank is the card itself, so its slot draws nothing here
 * (iOS `AnswerArea`).
 */
@Composable
fun AnswerArea(
    controls: AnswerControls,
    /** The learner's text in the field the slot asks for; the caller keeps one per field. */
    text: String,
    chrome: Chrome,
    actions: AnswerActions,
    /** What the field asks for, named in the language it is owed in. */
    placeholder: String = "",
    /** Who holds the focus; null lets the field claim the keyboard as it mounts ([AnswerField]). */
    focus: FocusRequester? = null,
    /** The armed beat became a tap ([net.spross.app.DrillRun.awaitsConfirm]). */
    awaitsConfirm: Boolean = false,
    /** The correction box's speaker; null where the surface has nothing to say it with. */
    correctionVoice: CorrectionVoice? = null,
    /** The line under the slot: the self-grade's question, or the write-out's coaching. */
    caption: String? = null,
    /** The Next button's line in the language being learned ([targetChrome]); null drops it. */
    nextSubtitle: String? = null,
    tiles: @Composable (options: List<String>, answer: String) -> Unit = { _, _ -> },
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        when (val slot = controls.slot) {
            // why: a locked, empty field is not an input — it has nothing of the learner's to
            // show and cannot be typed into, so its placeholder would be an invitation it cannot honor.
            is Slot.Typed -> if (slot.editable || !AnswerNormalizer.isBlankAnswer(text)) {
                key(Slot.Typed::class) {
                    Field(controls, text, chrome, actions, placeholder, focus, numberPad = slot.numberPad, locked = !slot.editable)
                }
            }
            is Slot.WriteOut -> {
                key(Slot.WriteOut::class) {
                    Field(controls, text, chrome, actions, placeholder, focus, numberPad = false, locked = false)
                }
                caption?.let { PauseLine(it) }
                if (slot.missed) {
                    // why: the answer is already on the card, so this points back to it — and
                    // TalkBack has no cue to say the copy came back a different word.
                    Text(
                        chrome.sessionCopyMismatch,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            is Slot.Choices -> tiles(slot.options, slot.answer)
            Slot.Arrangement, null -> {}
            Slot.SelfGrade -> VerdictButtons(chrome, actions.selfGrade, caption = caption ?: chrome.sessionRatingQuestion)
        }
        if (controls.slot is Slot.Typed || controls.slot is Slot.WriteOut) {
            (controls.fieldFeedback as? TurnFeedback.Almost)?.let { almost ->
                AlmostCorrection(
                    almostCaption(almost.reason, chrome),
                    almost.correctForm,
                    chrome,
                    correctionVoice?.pronounce?.invoke(almost.correctForm),
                    playing = correctionVoice?.isPlaying?.invoke(almost.correctForm) == true,
                )
            }
        }
        Buttons(controls, text, chrome, actions, awaitsConfirm, nextSubtitle)
    }
}

/** The field, every keystroke handed to kern: writing the answer out IS the answer. */
@Composable
private fun Field(
    controls: AnswerControls,
    text: String,
    chrome: Chrome,
    actions: AnswerActions,
    placeholder: String,
    focus: FocusRequester?,
    numberPad: Boolean,
    locked: Boolean,
) {
    AnswerField(
        value = text,
        onValueChange = actions.type,
        placeholder = placeholder,
        feedback = controls.fieldFeedback,
        chrome = chrome,
        onDone = actions.submit,
        focus = focus,
        numberPad = numberPad,
        locked = locked,
    )
}

@Composable
private fun Buttons(
    controls: AnswerControls,
    text: String,
    chrome: Chrome,
    actions: AnswerActions,
    awaitsConfirm: Boolean,
    nextSubtitle: String?,
) {
    controls.primary?.let { primary ->
        PrimaryAction(primary, text, chrome, if (primary == Primary.Reveal) actions.reveal else actions.submit)
        // why: this card's whole content is a sound, and a learner who cannot listen to it
        // would otherwise answer blind — under the primary action, the way out and not through.
        if (controls.cantListen) {
            TextButton(onClick = actions.cantListen, modifier = Modifier.fillMaxWidth()) {
                Text(chrome.sessionHearCantListen, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    // A verdict that holds stands until tapped; one kern arms a beat for needs the tap only
    // where no timer runs — under a screen reader.
    val showsConfirm = controls.confirm == Confirm.Always ||
        (controls.confirm == Confirm.WhenNoBeat && awaitsConfirm)
    if (showsConfirm || controls.stop) {
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            if (showsConfirm) NextButton(chrome, nextSubtitle, actions.confirm)
            if (controls.stop) DrillStopOffer(chrome, actions.stop)
        }
    }
    when (controls.giveUp) {
        GiveUp.Next -> NextButton(chrome, nextSubtitle, actions.giveUp)
        // why: always reachable — a step that cannot be left is a trap.
        GiveUp.Skip -> TextButton(
            onClick = actions.giveUp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(chrome.sessionSkip, style = MaterialTheme.typography.bodyMedium)
        }
        null -> {}
    }
}

/**
 * ONE primary action, and kern decides which: a blank submit reveals, a typed one checks
 * (`AnswerNormalizer.isBlankAnswer`). The label only says which.
 */
@Composable
private fun PrimaryAction(primary: Primary, text: String, chrome: Chrome, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            if (primary == Primary.Reveal || AnswerNormalizer.isBlankAnswer(text)) chrome.commonReveal else chrome.commonCheck,
        )
    }
}

/**
 * The button that books what the verdict already said, or goes on past a miss — its word
 * over the same word in the language being learned, for the immersion (iOS `ActionLabel`).
 */
@Composable
private fun NextButton(chrome: Chrome, subtitle: String?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(chrome.commonNext)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(Palette.SUBTITLE.toFloat()))
            }
        }
    }
}

/**
 * The chrome of the language being learned, for an action's subtitle; null where that
 * language has none of its own (kern's `LanguageChoices.hasChrome`, no fallback) or the
 * screen already reads in it.
 */
val AppModel.targetChrome: Chrome?
    get() = profile.target
        ?.takeIf(LanguageChoices::hasChrome)
        ?.let(Chrome::forSource)
        ?.takeIf { it != chrome }

/** Which of the ambers a hold was, in the learner's own words. */
fun almostCaption(reason: AlmostReason, chrome: Chrome): String = when (reason) {
    AlmostReason.Typo -> chrome.sessionAlmostTypo
    AlmostReason.Merged -> chrome.sessionAlmostMerged
}

/**
 * Where each control the answer area draws hands its tap. A surface fills in the ones its
 * controls can show; the rest stay inert.
 */
class AnswerActions(
    /** The Submit primary and Enter in the field. */
    val submit: () -> Unit = {},
    /** A keystroke, with the field's text after it. */
    val type: (String) -> Unit = {},
    val reveal: () -> Unit = {},
    val confirm: () -> Unit = {},
    val giveUp: () -> Unit = {},
    val stop: () -> Unit = {},
    val selfGrade: (SelfGrading.Verdict) -> Unit = {},
    val cantListen: () -> Unit = {},
)

/**
 * How the correction box says the form it carries: null from [pronounce] drops the speaker,
 * and [isPlaying] pulses it while that form is sounding.
 */
class CorrectionVoice(
    val pronounce: (String) -> (() -> Unit)?,
    val isPlaying: (String) -> Boolean = { false },
)

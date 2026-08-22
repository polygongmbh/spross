package net.spross.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.audio.CueSounds
import net.spross.kern.session.CopyStep
import net.spross.kern.session.SelfGrading
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The parts of a turn both roles wear: the field the learner types into, the three
 * verdicts a reveal hands over to, and the write-it-out step a miss can open.
 *
 * Every rule behind them is kern's `TurnMachine`, reached through [TurnFlow] — these
 * render its state and hand taps back.
 */

/**
 * The typed field, whichever of the turn's three it currently is.
 *
 * It claims focus as it MOUNTS: a request made before the field is on screen lands on
 * nothing, so the field asking for itself is the only ordering that holds — and it is
 * what makes the write-out step usable the moment "Unbekannt" opens it.
 *
 * Never read-only, not even after grading: a miss keeps typing, because the retype IS the
 * answer. Kern ignores text in the states that decide nothing.
 */
@Composable
fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    feedback: TurnFeedback,
    chrome: Chrome,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Dl.colors
    val tint: Color? = when (feedback) {
        TurnFeedback.Correct -> palette.success
        is TurnFeedback.Almost -> palette.amber
        else -> null
    }
    // why: correctness is never color alone — the mark says it on screen, the state
    // description says it to TalkBack, and the tint is the third telling of the same thing.
    val mark: (@Composable () -> Unit)? = if (feedback == TurnFeedback.Correct) {
        { Icon(SprossIcons.Check, contentDescription = null, tint = palette.success) }
    } else {
        null
    }
    val focus = remember { FocusRequester() }
    // why: a requester answers only once its node has been placed, and one frame is what
    // that takes; a request fired inside the same composition lands on nothing.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .semantics {
                when (feedback) {
                    TurnFeedback.Correct -> stateDescription = chrome.answerCorrect
                    // why: the amber edge is the whole of what tells a near miss from a
                    // clean answer, and a color says nothing to TalkBack (WCAG 1.4.1).
                    is TurnFeedback.Almost -> stateDescription = chrome.answerAlmost
                    else -> {}
                }
            },
        placeholder = { Text(placeholder) },
        trailingIcon = mark,
        colors = if (tint == null) {
            OutlinedTextFieldDefaults.colors()
        } else {
            OutlinedTextFieldDefaults.colors(focusedBorderColor = tint, unfocusedBorderColor = tint)
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
    )
}

/**
 * The self-grade row: three verdicts, never four — under the question they answer.
 *
 * The learner says whether the word came; the clock behind them decides whether one that
 * came, came instantly (kern's `SelfGrading`). Nobody can pick their way to a long
 * interval — Easy is EARNED by answering fast, which is why it is not on screen.
 *
 * The labels name what the LEARNER knows, never what the scheduler will do, so none of
 * them wears an FSRS rating's name. Ordered best to worst, so the miss ends up under a
 * resting thumb with the middle verdict keeping the two opposites apart. Each carries a
 * mark as well as its color.
 *
 * [caption] is the standing question; the first round's coaching passes its own, so only
 * ever one line stands there.
 */
@Composable
fun VerdictButtons(
    chrome: Chrome,
    flow: TurnFlow,
    modifier: Modifier = Modifier,
    caption: String = chrome.ratingQuestion,
) {
    val palette = Dl.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.s)) {
            VerdictTile(SprossIcons.Check, chrome.good, palette.success, Modifier.weight(1f)) {
                flow.selfGrade(SelfGrading.Verdict.Knew)
            }
            VerdictTile(SprossIcons.Dot, chrome.hard, palette.amber, Modifier.weight(1f)) {
                flow.selfGrade(SelfGrading.Verdict.Tough)
            }
            VerdictTile(SprossIcons.Close, chrome.unknown, palette.wrong, Modifier.weight(1f)) {
                flow.selfGrade(SelfGrading.Verdict.Unknown)
            }
        }
        PauseLine(caption)
    }
}

/**
 * One verdict: its mark over its name, both in the verdict's own color on that color's
 * own wash.
 *
 * A TINTED tile, never a saturated slab. Three solid blocks of forest, ochre and brick is
 * the loudest thing on a screen whose whole job is one quiet word — and a filled button
 * reads as "do this", which is wrong for a row where the learner picks the true one rather
 * than the recommended one. The wash and the edge say the same thing at a tenth the volume
 * (iOS `GradeButton`, same 14 % fill and 35 % edge).
 *
 * The mark sits in a fixed-height slot because the three glyphs are not the same height,
 * and without it the names sit off each other's line.
 */
@Composable
private fun VerdictTile(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .heightIn(min = VERDICT_TILE)
            .background(Dl.colors.wash(color), shape)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .pressSpring()
            .padding(horizontal = DlSpace.xs, vertical = DlSpace.s),
        verticalArrangement = Arrangement.spacedBy(DlSpace.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decorative: the name under it is the label, and TalkBack reading "checkmark"
        // before "Wusste ich" says the same thing twice.
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.height(22.dp))
        // why: "Gar nicht" outruns a third of a narrow row — the name steps down to fit
        // rather than losing its tail (the iOS tiles scale the same way).
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 9.sp,
                maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        )
    }
}

/** The iOS tile's own floor — a verdict is a target for a resting thumb, not a link. */
private val VERDICT_TILE = 60.dp

/**
 * The write-it-out step: a word you MISSED is typed once with the answer in view.
 *
 * It is encoding, never a grade — the rating the turn already chose is applied unchanged
 * when the word stands written (kern `TurnWriteOut`). The word finishing IS the action,
 * so nothing here asks for a confirming tap; only the way out is a button, because a step
 * you cannot leave is a trap.
 */
@Composable
fun WriteOutStep(model: AppModel, flow: TurnFlow, step: CopyStep, targetName: String) {
    val chrome = model.chrome
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnswerField(
            value = flow.copyInput,
            onValueChange = flow::writeCopy,
            placeholder = chrome.copyPrompt.format(targetName),
            feedback = if (step.written) TurnFeedback.Correct else TurnFeedback.Neutral,
            chrome = chrome,
            onDone = { flow.submitCopy() },
        )
        // why: the field opened by itself after a miss — the first round says what it is
        // FOR, or copying a word off the card reads as busywork.
        if (model.coachActive) PauseLine(chrome.coachWrite)
        if (step.missed) {
            Text(
                chrome.copyMismatch,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // why: TalkBack has no cue to tell it the copy came back a different
                // word — the line announces itself where the learner's focus already is.
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        TextButton(
            onClick = { flow.skipCopy() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(chrome.skipStep, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The "Weiter" that stands in for a beat under a screen reader — same rating, one tap. */
@Composable
fun ConfirmButton(chrome: Chrome, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(chrome.next)
    }
}

/**
 * What kern's verdict cue becomes on this platform: the chime [CueSounds] holds, and — on
 * a wrong answer alone — a haptic under it.
 *
 * The three sounds carried the verdict on iOS from the start while this surface only ever
 * buzzed, which is why the buzz used to answer all three: it was standing in for them.
 * With the clips bundled it no longer stands in for anything, so the haptic falls back to
 * the one place iOS puts it — a gentle wake-up on a miss, never on a reveal or a hit.
 */
fun View.cueTone(kind: ToneKind, sounds: CueSounds) {
    sounds.play(kind)
    if (kind != ToneKind.Wrong) return
    // why: the expressive constant only exists from API 30 — an older device taps with the
    // long press it has always had rather than letting a miss pass unfelt.
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}

/** The target language as the learner's chrome names it — what the write-out asks for. */
fun AppModel.targetName(ui: SessionUi): String {
    val lang = ui.card?.target?.lang ?: return ""
    return languageName(lang)
}

/**
 * The language the ANSWER field asks for, named. Kern's `TurnState.answerLang` decides
 * which side that is — the meaning on a card asked by ear, the target everywhere else —
 * and the placeholder is the one place the learner is told.
 */
fun AppModel.answerName(flow: TurnFlow): String = languageName(flow.state.answerLang)

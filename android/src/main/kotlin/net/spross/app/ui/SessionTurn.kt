package net.spross.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
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
    // why: correctness is never colour alone — the mark says it on screen, the state
    // description says it to TalkBack, and the tint is the third telling of the same thing.
    val mark: (@Composable () -> Unit)? = if (feedback == TurnFeedback.Correct) {
        { Text("✓", color = palette.success) }
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
                if (feedback == TurnFeedback.Correct) stateDescription = chrome.answerCorrect
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
 * The self-grade row: three verdicts, never four.
 *
 * The learner says whether the word came; the clock behind them decides whether one that
 * came, came instantly (kern's `SelfGrading`). Nobody can pick their way to a long
 * interval — Easy is EARNED by answering fast, which is why it is not on screen.
 *
 * Ordered best to worst, so the miss ends up under a resting thumb with the middle
 * verdict keeping the two opposites apart. Each carries a mark as well as its colour.
 */
@Composable
fun VerdictButtons(chrome: Chrome, flow: TurnFlow, modifier: Modifier = Modifier) {
    val palette = Dl.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VerdictButton("✓ ${chrome.good}", palette.success, Modifier.weight(1f)) {
            flow.selfGrade(SelfGrading.Verdict.Knew)
        }
        VerdictButton("~ ${chrome.hard}", palette.amber, Modifier.weight(1f)) {
            flow.selfGrade(SelfGrading.Verdict.Tough)
        }
        VerdictButton("✗ ${chrome.unknown}", palette.wrong, Modifier.weight(1f)) {
            flow.selfGrade(SelfGrading.Verdict.Unknown)
        }
    }
}

@Composable
private fun VerdictButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        // The ink cut for accent fills, never white: in the dark the accents are pastels
        // and white sinks to about 1.8:1 on them.
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Dl.colors.onColor,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        // why: "✗ Unbekannt" outruns a third of a 320dp row and lost its name to
        // maxLines = 1 — the label now steps down to fit instead (the iOS rating
        // buttons scale the same way); the cap keeps the short verdicts at full size.
        Text(
            label,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 9.sp,
                maxFontSize = LocalTextStyle.current.fontSize,
            ),
        )
    }
}

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
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(chrome.next)
    }
}

/**
 * What kern's verdict cue becomes on this platform: a haptic, and nothing audible.
 *
 * The chimes are iOS resources (`App/Resources/Sounds/`) and this app bundles none, so
 * the verdict is FELT rather than heard — the one channel that is free of the
 * read-aloud switch and of the media volume both, exactly as a chime is meant to be.
 */
fun View.cueTone(kind: ToneKind) {
    val constant = when (kind) {
        // why: the expressive pair only exists from API 30 — an older device falls back to
        // the key press it has always had rather than going silent about the verdict.
        ToneKind.Correct ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        ToneKind.Wrong ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        ToneKind.Reveal -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}

/** The target language as the learner's chrome names it — what a field asks for by name. */
fun AppModel.targetName(ui: SessionUi): String {
    val lang = ui.card?.target?.lang ?: return ""
    return catalog?.languages?.get(lang)?.name ?: lang
}

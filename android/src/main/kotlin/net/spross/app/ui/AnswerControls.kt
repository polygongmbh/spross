package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.TurnFeedback

/**
 * What stands under a question that is WRITTEN out: the field, the one primary action, and
 * what each verdict puts in its place.
 *
 * The review loop and all four drills ask this way, so they wear this — what differs
 * between them is what the field is asked FOR, never how it asks. Every rule behind the
 * branches is kern's: which answers are accepted, which pause waits for a tap, and that a
 * blank submit MEANS reveal.
 */

/**
 * The field and the one primary action under it.
 *
 * [after] is what a run hangs below the verdict — the way out where it is offered, the
 * numbers drill's look-up — inside this column, so it keeps the rhythm of the controls it
 * belongs to.
 */
@Composable
fun TypedAnswerControls(
    input: String,
    onType: (String) -> Unit,
    placeholder: String,
    feedback: TurnFeedback,
    awaitsConfirm: Boolean,
    chrome: Chrome,
    focus: FocusRequester,
    /** Button and Enter alike: kern checks what stands in the field, or reveals on nothing. */
    onPrimary: () -> Unit,
    /** Enter: check while the answer is owed, otherwise book what stands. */
    onEnter: () -> Unit,
    onConfirm: () -> Unit,
    speakCorrection: (String) -> (() -> Unit)? = { null },
    digits: Boolean = false,
    after: @Composable ColumnScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        AnswerField(
            value = input,
            onValueChange = onType,
            placeholder = placeholder,
            feedback = feedback,
            chrome = chrome,
            onDone = onEnter,
            focus = focus,
            digits = digits,
        )
        // ONE primary action, and only while the answer is still owed.
        if (feedback == TurnFeedback.Neutral) PrimaryAction(input, chrome, onPrimary)
        AnswerVerdict(feedback, awaitsConfirm, chrome, onConfirm, speakCorrection)
        after()
    }
}

/**
 * The button that submits: it READS as reveal on an empty field and as check on a written
 * one, because kern's submit does both and the label is all that tells the learner which
 * it will be (`AnswerNormalizer.isBlankAnswer`).
 */
@Composable
fun PrimaryAction(input: String, chrome: Chrome, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(if (AnswerNormalizer.isBlankAnswer(input)) chrome.commonReveal else chrome.commonCheck)
    }
}

/**
 * What the answer earned, and the tap that books it.
 *
 * The answer itself is on the CARD — a miss opens it there — so nothing repeats it here;
 * what this adds is which of the two ambers a near miss was, and the way on where kern
 * armed no beat of its own.
 *
 * [missed] is the review loop's alone: a word the learner owns is retyped with the answer
 * in view, and that retype is the way on. A drill has nothing to retype — its questions are
 * generated, so self-reporting after seeing the answer proves nothing and a reveal simply
 * counts as a miss.
 */
@Composable
fun AnswerVerdict(
    feedback: TurnFeedback,
    awaitsConfirm: Boolean,
    chrome: Chrome,
    onConfirm: () -> Unit,
    /** Says the form a near miss owed back; null where nothing here can say it. */
    speakCorrection: (String) -> (() -> Unit)? = { null },
    missed: (@Composable () -> Unit)? = null,
) {
    when (feedback) {
        TurnFeedback.Neutral -> {}
        // why: nothing is drawn for a clean answer — it already stands in the learner's own
        // text with the field's checkmark, and the card is on its way out. Under a screen
        // reader no beat ever armed, so the tap that replaces it is all there is.
        TurnFeedback.Correct -> if (awaitsConfirm) ConfirmButton(chrome, onConfirm)
        is TurnFeedback.Almost -> Column(
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            AlmostCorrection(
                almostCaption(feedback.reason, chrome),
                feedback.correctForm,
                chrome,
                speakCorrection(feedback.correctForm),
            )
            ConfirmButton(chrome, onConfirm)
        }
        TurnFeedback.Revealed -> if (missed != null) missed() else ConfirmButton(chrome, onConfirm)
    }
}

/** Which of the ambers a hold was, in the learner's own words. */
fun almostCaption(reason: AlmostReason, chrome: Chrome): String = when (reason) {
    AlmostReason.Typo -> chrome.sessionAlmostTypo
    AlmostReason.Heard -> chrome.sessionAlmostHeard
    AlmostReason.Merged -> chrome.sessionAlmostMerged
}

/** The "Weiter" that stands in for a beat under a screen reader — same rating, one tap. */
@Composable
fun ConfirmButton(chrome: Chrome, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(chrome.commonNext)
    }
}

package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.NumbersFlow
import net.spross.app.speakFormOnTap
import net.spross.kern.model.Language
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback
import net.spross.kern.session.AnswerNormalizer

/**
 * What a slot run puts on screen: the prompt card and the controls under it.
 *
 * A drill card is a review card — the same face ([CardFace]) and the same reveal
 * ([CardReveal]) — and it carries NOTHING but the prompt: the run's header line already
 * names what is drilled and how far the ramp has come, and the field's placeholder names
 * what is owed, so a badge here would be the third telling of what one tap said.
 */

/**
 * How large the question is set. WHAT is asked picks it — there is room for one numeral
 * where there is none for a whole line ([Theme.Prompt]).
 */
enum class PromptSize {
    /** A numeral the whole card is about. */
    Digits,

    /** One word, whole or with its letters mixed. */
    Word,

    /** A prompt made of words, wrapped over lines. */
    Sentence;

    val fontSize: TextUnit
        get() = when (this) {
            Digits -> Theme.prompt.digits
            Word -> Theme.prompt.word
            Sentence -> Theme.prompt.sentence
        }

    val lines: Int get() = if (this == Sentence) 4 else 1
}

/**
 * One big prompt on the app's own card face, and the same reveal a vocabulary card grows.
 *
 * Takes the QUESTION rather than a run, so anything that has one can wear the card: a
 * numeral the slot run drew, a date, a word with its letters thrown out of order.
 *
 * [promptLabel] is what a screen reader hears in its place, where what is written is not a
 * word anything can read; null ⇒ the prompt reads as itself.
 */
@Composable
fun DrillPromptCard(
    prompt: AnnotatedString,
    promptLabel: String?,
    size: PromptSize,
    /** The canonical answer, and the language it is said in. */
    answer: String,
    language: Language,
    /** The meaning — under the answer, and never before it. */
    gloss: String?,
    /** The answer is out: the card grows it below the prompt, like a vocabulary card. */
    revealed: Boolean,
    /** Says the revealed answer; null where nothing here can, which drops the speaker. */
    pronounce: (() -> Unit)?,
    chrome: Chrome,
    /** A short fact about THIS prompt ("Neue Stelle: mia"), shown until the answer arrives. */
    hint: String? = null,
    /** What a refused answer actually named — the nudge line under the reveal. */
    otherWord: Match.OtherWord? = null,
) {
    CardFace(Modifier.heightIn(min = Theme.reserve.drillCard)) {
        Text(
            prompt,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = size.fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = if (size == PromptSize.Digits) FontFamily.Monospace else FontFamily.Default,
            ),
            textAlign = TextAlign.Center,
            maxLines = size.lines,
            modifier = if (promptLabel == null) {
                Modifier
            } else {
                Modifier.semantics { contentDescription = promptLabel }
            },
        )
        if (revealed) {
            CardReveal(note = gloss) {
                SpokenWord(pronounce, chrome) {
                    Text(
                        localizedTarget(answer, language),
                        style = MaterialTheme.typography.titleLarge,
                        color = Theme.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            otherWord?.let { other ->
                // why: same line as the review session's — both explain what became
                // of the answer, so they read alike.
                PauseLine(chrome.sessionOtherWord.format(other.word, other.meanings.joinToString(", ")))
            }
            return@CardFace
        }
        // why: the reveal TAKES this slot rather than stacking under it — the hint is
        // scaffolding for a prompt still unanswered, and a fact about THIS question.
        hint?.let { DrillHintPill(it) }
    }
}

/**
 * The slot run's question, on that card.
 *
 * A prompt made of WORDS is laid out like one — smaller and wrapped — where a numeral gets
 * the one big line. Asked of the PROMPT rather than of the run, so a composed sentence and
 * a reversed reading are both read as what they are.
 */
@Composable
fun DrillPromptCard(model: AppModel, flow: NumbersFlow, chrome: Chrome) {
    val state = flow.state
    val task = state.currentTask
    val wordy = task.promptDisplay.any { it.isLetter() }
    DrillPromptCard(
        prompt = AnnotatedString(task.promptDisplay),
        promptLabel = null,
        size = if (wordy) PromptSize.Sentence else PromptSize.Digits,
        answer = task.display,
        language = state.mode.language,
        gloss = task.gloss,
        revealed = state.showsAnswer,
        pronounce = model.speakFormOnTap(task.display, state.mode.language),
        chrome = chrome,
        // A fact about THIS number: the place word the first time a length is asked.
        hint = state.placeValueHint?.let { chrome.numbersNewPlace.format(it) },
        otherWord = state.otherWord,
    )
}

/**
 * The field and the one primary action under it. A reversed task owes DIGITS, so its
 * placeholder names those rather than the language — "auf Spanisch" over a number pad asks
 * for the wrong thing.
 */
@Composable
fun NumbersControls(
    model: AppModel,
    flow: NumbersFlow,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val state = flow.state
    val placeholder = if (state.currentReversed) {
        chrome.numbersAnswerPlaceholder
    } else {
        chrome.sessionAnswerPlaceholder.format(model.languageName(state.mode.language))
    }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        AnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = placeholder,
            feedback = state.feedback,
            chrome = chrome,
            focus = inputFocus,
            onDone = { flow.enter() },
            digits = state.currentReversed,
        )
        when (val feedback = state.feedback) {
            TurnFeedback.Neutral -> Button(
                onClick = { flow.primary() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (AnswerNormalizer.isBlankAnswer(flow.input)) chrome.commonReveal else chrome.commonCheck)
            }
            // why: nothing is drawn for a clean answer — it already stands in the learner's
            // own text with the field's checkmark, and the card is on its way out.
            TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
            is TurnFeedback.Almost -> AlmostLine(model, flow, feedback.correctForm, chrome)
            // why: no "Wusste ich" in a drill — the tasks are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> ConfirmButton(chrome) { flow.confirm() }
        }
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
        // Outside the feedback switch: a miss is exactly when a learner wants to look the
        // word up, and the "?" raises the very table the numbers page shows.
        if (state.offersLookUp) {
            TextButton(onClick = { flow.lookUp() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "? ${chrome.numbersLookup}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Theme.colors.textSecondary,
                )
            }
        }
    }
}

/** A slip: the box spells the word out, and the tap that ends the pause books it amber. */
@Composable
private fun AlmostLine(model: AppModel, flow: NumbersFlow, form: String, chrome: Chrome) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        AlmostCorrection(
            chrome.sessionAlmostTypo,
            form,
            chrome,
            model.speakFormOnTap(form, flow.state.mode.language),
        )
        ConfirmButton(chrome) { flow.confirm() }
    }
}

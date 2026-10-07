package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.QuestionDriver
import net.spross.app.isSounding
import net.spross.kern.session.AnswerControls.Slot

/** The answer area over a flow: its controls, its field's text, its armed beat and its intents. */
@Composable
fun AnswerArea(
    flow: QuestionDriver,
    chrome: Chrome,
    placeholder: String = "",
    focus: FocusRequester? = null,
    correctionVoice: CorrectionVoice? = null,
    caption: String? = null,
    nextSubtitle: String? = null,
    /** The way out, where the controls offer one — the screen's, since what a close files is its own. */
    onStop: () -> Unit = {},
    tiles: @Composable (options: List<String>, answer: String) -> Unit = { _, _ -> },
) {
    AnswerArea(
        controls = flow.controls ?: return,
        text = flow.fieldText,
        chrome = chrome,
        actions = flow.answerActions(onStop),
        placeholder = placeholder,
        focus = focus,
        awaitsConfirm = flow.awaitsConfirm,
        correctionVoice = correctionVoice,
        caption = caption,
        nextSubtitle = nextSubtitle,
        tiles = tiles,
    )
}

/**
 * The answer area under every drill: the field and the one primary action to kern, the held
 * verdict's Next, and the way out where kern offers it ([net.spross.kern.trainer.DrillRunProgress.controls]).
 * The placeholder names the language the answer is owed in — or digits, written alike in every one.
 */
@Composable
fun DrillAnswerArea(
    model: AppModel,
    flow: QuestionDriver,
    focus: FocusRequester?,
    onStop: () -> Unit,
    speakCorrection: (String) -> (() -> Unit)? = { null },
    tiles: @Composable (options: List<String>, answer: String) -> Unit = { _, _ -> },
) {
    val chrome = model.chrome
    val typed = flow.controls?.slot as? Slot.Typed
    AnswerArea(
        flow = flow,
        chrome = chrome,
        placeholder = when {
            typed == null -> ""
            typed.digits -> chrome.numbersAnswerPlaceholder
            else -> chrome.sessionAnswerPlaceholder.format(model.languageName(typed.lang))
        },
        focus = focus,
        correctionVoice = CorrectionVoice(speakCorrection, model::isSounding),
        nextSubtitle = model.targetChrome?.commonNext,
        onStop = onStop,
        tiles = tiles,
    )
}

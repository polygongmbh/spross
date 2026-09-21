package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.WordScrambleFlow
import net.spross.app.newWordScramble
import net.spross.app.speakFormOnTap
import net.spross.kern.trainer.ScrambledWord
import net.spross.kern.trainer.WordScrambleTask

/**
 * The word scramble: a word the box already holds, with its letters thrown out of order,
 * written back out. The answer is TYPED — handing the same letters back as tiles would leave
 * nothing to retrieve but their order, where writing the word out IS the spelling — so it
 * wears the typed card every other trainer drill wears (`docs/drills-words.md`).
 *
 * Stateless like the letter drill: no review is ever booked, and the box is READ for the
 * words it has consolidated and never written. The RUN is kern's, reached through
 * [WordScrambleFlow]: the draw, the masking ladder and the verdict ladder are all its.
 */
@Composable
fun WordScrambleScreen(model: AppModel) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = rememberRun(model, Screen.Home) {
        model.newWordScramble(onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    } ?: return
    val state = flow.state
    // The scrambles have no page of their own to land on, so the figures go back to Home with
    // the learner; this drill keeps no streak record, and no high-water Sprosse beside the
    // mask, because nothing reads one back.
    val leave = {
        val closed = flow.close()
        // why: what the NEXT run reads — it opens on the lowest Sprosse the mask does not
        // hold, so a Sprosse climbed clean is never asked for twice.
        model.trainer.store.bookCleared(flow.clearedKey, closed.clearedSprossen)
        model.finishDrill(Screen.Home, closed.summary, chrome.trainerDrillWordScramble)
    }

    val inputFocus = remember { FocusRequester() }
    QuestionFocus(state.index, model.pronouncer, inputFocus)

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        progress = state,
        sprosse = chrome.trainerSprosse.format(state.level),
    ) {
        val task = state.task ?: return@DrillRunScaffold
        DrillPromptCard(
            prompt = mixedWord(task.scrambled),
            promptLabel = spelledOut(task.scrambled),
            size = PromptSize.Word,
            answer = task.display,
            language = task.language,
            gloss = task.gloss,
            revealed = state.showsAnswer,
            pronounce = model.speakFormOnTap(task.display, task.language),
            chrome = chrome,
        )
        Controls(model, flow, task, chrome, inputFocus, leave)
    }
}

/**
 * The prompt: the letters as kern mixed them, with the ones the Sprosse left standing
 * set bold. Kern says how many hold at the front ([ScrambledWord.fixedLeading]) and this side
 * says what that looks like — weight alone, because the anchor is a recognition aid the ladder
 * takes away, and an aid on its way out is not worth a legend.
 */
fun mixedWord(word: ScrambledWord): AnnotatedString {
    val letters = word.display
    val lead = minOf(word.fixedLeading, letters.length)
    val anchored = SpanStyle(fontWeight = FontWeight.Bold)
    return buildAnnotatedString {
        withStyle(anchored) { append(letters.take(lead)) }
        append(letters.substring(lead))
    }
}

/**
 * A mixed word is not a word, and a voice reading it as one says nothing a learner can spell
 * from — so it is spelled OUT, letter by letter.
 */
fun spelledOut(word: ScrambledWord): String = word.display.toList().joinToString(", ")

/**
 * The field and what stands under it: one primary action while the spelling is owed, the tap
 * that books a pause where kern armed none, and the way out on the second miss in a row.
 */
@Composable
private fun Controls(
    model: AppModel,
    flow: WordScrambleFlow,
    task: WordScrambleTask,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val state = flow.state
    TypedAnswerControls(
        input = flow.input,
        onType = flow::type,
        placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(task.language)),
        feedback = state.feedback,
        awaitsConfirm = flow.awaitsConfirm,
        chrome = chrome,
        focus = inputFocus,
        onPrimary = flow::primary,
        onEnter = flow::enter,
        onConfirm = flow::confirm,
        speakCorrection = { model.speakFormOnTap(it, task.language) },
    ) {
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

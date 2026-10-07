package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Screen
import net.spross.app.WordScrambleFlow
import net.spross.app.closeScramble
import net.spross.app.newWordScramble
import net.spross.app.speakFormOnTap
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.ScrambledWord
import net.spross.kern.trainer.WordScrambleTask

/**
 * The word scramble: a word the box already holds, with its letters thrown out of order,
 * written back out. The answer is TYPED — handing the same letters back as tiles would leave
 * nothing to retrieve but their order, where writing the word out IS the spelling — so it
 * wears the typed card every other trainer drill wears (`docs/drills-words.md`).
 *
 * Stateless like the letter drill: no review is ever booked, and the box is READ for the
 * words it has settled and never written. The RUN is kern's, reached through
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
    val leave = {
        val closed = flow.close()
        model.closeScramble(Drill.WordScramble, model.chrome.trainerDrillWordScramble, flow.clearedKey, closed.clearedSprossen, closed.summary)
    }

    val inputFocus = remember { FocusRequester() }
    QuestionFocus(state.index to state.pause, model.pronouncer, inputFocus)

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        progress = state,
        sprosse = chrome.trainerSprosse.format(state.sprosse),
    ) {
        val task = state.task ?: return@DrillRunScaffold
        if (state.question == null) return@DrillRunScaffold
        QuestionStage(state, key = { it.index }) { shown ->
            QuestionCard(
                shown.question ?: return@QuestionStage,
                chrome,
                voice = model.cardVoice,
                promptLabel = shown.task?.let { spelledOut(it.scrambled) },
            )
        }
        Controls(model, flow, task, inputFocus, leave)
    }
}

/**
 * A mixed word is not a word, and a voice reading it as one says nothing a learner can spell
 * from — so it is spelled OUT, letter by letter.
 */
fun spelledOut(word: ScrambledWord): String = word.display.toList().joinToString(", ")

/** Kern's controls on the shared answer area. */
@Composable
private fun Controls(
    model: AppModel,
    flow: WordScrambleFlow,
    task: WordScrambleTask,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val controls = flow.state.controls ?: return
    DrillAnswerArea(
        model, controls, flow.input, flow.awaitsConfirm, inputFocus,
        onType = flow::type, onEnter = flow::enter, onConfirm = flow::confirm, onStop = onFinish,
        speakCorrection = { model.speakFormOnTap(it, task.language) },
    )
}

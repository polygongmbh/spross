package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Screen
import net.spross.app.closeScramble
import net.spross.app.newOpposites
import net.spross.app.speakFormOnTap
import net.spross.kern.trainer.Drill

/**
 * The opposites drill: a word the box holds, and its opposite typed back in the same language
 * (`docs/drills-words.md`). It wears the word scramble's typed card; the reveal names EVERY
 * opposite, since a word the language writes alike for two meanings has one for each.
 */
@Composable
fun OppositesScreen(model: AppModel) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = rememberRun(model, Screen.Home) {
        model.newOpposites(onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    } ?: return
    val state = flow.state
    val leave = {
        val closed = flow.close()
        model.closeScramble(Drill.Opposites, model.chrome.trainerDrillOpposites, flow.clearedKey, closed.clearedSprossen, closed.summary)
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
        QuestionCard(state.question ?: return@DrillRunScaffold, chrome, voice = model.cardVoice)
        DrillAnswerArea(
            model, state.controls ?: return@DrillRunScaffold, flow.input, flow.awaitsConfirm, inputFocus,
            onType = flow::type, onEnter = flow::enter, onConfirm = flow::confirm, onStop = leave,
            speakCorrection = { model.speakFormOnTap(it, task.language) },
        )
    }
}

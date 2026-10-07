package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LetterDrillFlow
import net.spross.app.Screen
import net.spross.app.audio.Pronouncer
import net.spross.app.finishDrill
import net.spross.app.letterReplay
import net.spross.app.letterSpeaker
import net.spross.app.newLetterDrill
import net.spross.app.playLetterPrompt
import net.spross.app.stampRun
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.LetterDrillRunState
import net.spross.kern.trainer.LetterDrillTask

/**
 * The letter drill: hear a sound, find the letter. Four glyph tiles, then confusable ones,
 * then typing the glyph, and finally dictation of words the learner already holds — one
 * Sprosse, mapped to formats by kern's `LetterDrillRun`, which owns every rule below.
 *
 * The one screen in the app that shows nothing: everything the learner is given is the
 * sound, so entering it is the request to hear one. Every autoplay goes out as
 * [Pronouncer.Trigger.ESSENTIAL]: no mute reaches it, and only the TalkBack gate applies,
 * without this screen testing for it.
 *
 * What stands under the card lives in LetterDrillFormats.kt; the run itself in `LetterDrillFlow`.
 */
@Composable
fun LetterDrillScreen(model: AppModel) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = rememberRun(model, Screen.Letters) {
        model.newLetterDrill(onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    } ?: return
    val state = flow.state
    // The letter drill keeps no streak record; what it files is the mask the next run opens above.
    val leave = {
        val closed = flow.close()
        model.stampRun(Drill.Letters, closed.summary)
        model.trainer.store.bookCleared(LetterDrillRunState.storageKey(closed.state.config.report.language), closed.clearedSprossen)
        model.finishDrill(Screen.Letters, closed.summary, chrome.trainerDrillLetters)
    }

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        progress = state,
        // One Sprosse, mapped to formats by kern — there is no Sprosse to name.
        sprosse = null,
        speaksPastMute = true,
    ) {
        val task = state.task ?: return@DrillRunScaffold
        Run(model, flow, task, chrome, leave)
    }
}

@Composable
private fun Run(
    model: AppModel,
    flow: LetterDrillFlow,
    task: LetterDrillTask,
    chrome: Chrome,
    onFinish: () -> Unit,
) {
    val state = flow.state
    val replayFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }

    // why: keyed on the question, and a LaunchedEffect fires on FIRST composition too —
    // so the first question of a run speaks without a second hook.
    LaunchedEffect(state.index) { model.playLetterPrompt(task) }
    // The one drill whose question is the SOUND, so the replay button is what a screen
    // reader is handed — never both it and the field, or TalkBack would be dragged off the
    // button it was just given.
    QuestionFocus(
        state.index,
        model.pronouncer,
        field = inputFocus.takeIf { state.typing },
        screenReader = replayFocus,
    )

    val question = state.question ?: return
    QuestionStage(question) { shown ->
        QuestionCard(
            shown,
            chrome,
            // why: the prompt plays out of the drill's own letter recording, never the form-keyed lookup.
            voice = CardVoice { saying ->
                if (saying == shown.prompt.saying) model.letterReplay(task) else model.letterSpeaker(task, saying.form)
            },
            // why: the outgoing card lets go of the requester, so TalkBack's hand-off lands on the incoming one.
            replayFocus = replayFocus.takeIf { shown.key == question.key },
        )
    }
    LetterAnswer(model, flow, task, inputFocus, onFinish)
}

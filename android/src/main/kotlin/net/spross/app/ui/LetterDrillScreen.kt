package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import net.spross.kern.trainer.LetterFormat

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
 * Format bodies live in LetterDrillFormats.kt; the run itself in `LetterDrillFlow`.
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

    HearPrompt(model, flow, task, chrome, replayFocus)
    when (task.format) {
        LetterFormat.ChoiceEasy, LetterFormat.ChoiceConfusable ->
            ChoiceFormat(flow, task, chrome)
        LetterFormat.Typed, LetterFormat.Dictation ->
            TypedFormat(model, flow, task, chrome, inputFocus)
    }
    if (state.offersFinish) DrillStopOffer(chrome, onFinish)
}

/**
 * The audio question on the app's own card face: what is being asked, one big replay
 * button, the gap word with the asked grapheme blanked, and — once the answer is in — the
 * same reveal a vocabulary card grows. No answer ever renders before that, and that is the
 * whole point.
 */
@Composable
private fun HearPrompt(
    model: AppModel,
    flow: LetterDrillFlow,
    task: LetterDrillTask,
    chrome: Chrome,
    replayFocus: FocusRequester,
) {
    val question = when {
        task.format == LetterFormat.Dictation -> chrome.lettersAskDictation
        task.gapText == null -> chrome.lettersAskHear
        else -> chrome.lettersAskSpell
    }
    val replay = model.letterReplay(task)
    CardFace {
        Text(
            question,
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        ReplayGlyph(replay, chrome, replayFocus)
        task.gapText?.let {
            Text(localizedTarget(it, task.language), fontSize = Theme.prompt.word, fontWeight = FontWeight.Bold)
        }
        if (flow.state.showsAnswer) {
            // why: the meaning is a REVEAL, never a cue — a dictation that shows what the
            // word means is no longer taken from the sound.
            CardReveal(note = task.gloss) {
                SpokenWord(model.letterSpeaker(task, task.display), chrome) {
                    Text(
                        localizedTarget(task.display, task.language),
                        style = MaterialTheme.typography.titleLarge,
                        color = Theme.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

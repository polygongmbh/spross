package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import net.spross.app.AppModel
import net.spross.app.Screen
import net.spross.app.closeScramble
import net.spross.app.newOpposites
import net.spross.app.speakFormOnTap
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.OppositesTask

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
        val answer = answerLine(task)
        DrillPromptCard(
            prompt = AnnotatedString(task.prompt),
            promptLabel = null,
            size = PromptSize.Word,
            answer = answer,
            language = task.language,
            gloss = glossLine(task),
            revealed = state.showsAnswer,
            pronounce = model.speakFormOnTap(answer, task.language),
            chrome = chrome,
        )
        val placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(task.language))
        TypedAnswerControls(
            input = flow.input,
            onType = flow::type,
            placeholder = placeholder,
            feedback = state.feedback,
            awaitsConfirm = flow.awaitsConfirm,
            chrome = chrome,
            focus = inputFocus,
            onPrimary = flow::primary,
            onEnter = flow::enter,
            onConfirm = flow::confirm,
            speakCorrection = { model.speakFormOnTap(it, task.language) },
        ) {
            if (state.offersFinish) DrillStopOffer(chrome, leave)
        }
    }
}

/** Every opposite on one line — two where the prompt merges two meanings. */
fun answerLine(task: OppositesTask): String = task.answers.joinToString(" · ") { it.text }

/** What the prompt means, then what its opposites mean, in the learner's own language. */
fun glossLine(task: OppositesTask): String =
    "${task.gloss} ↔ ${task.answers.joinToString(" · ") { it.gloss }}"

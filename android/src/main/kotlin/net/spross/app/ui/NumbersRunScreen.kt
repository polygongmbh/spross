package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.NumbersFlow
import net.spross.app.badge
import net.spross.app.bookRecord
import net.spross.app.countLine
import net.spross.app.name
import net.spross.app.newTrainerRun
import net.spross.app.speakDrillAnswer
import net.spross.kern.trainer.NumbersExercise
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.NumbersRunState

/**
 * A stateless ENDLESS slot run — numbers, years, the clock, sentences, number forms.
 *
 * The same interaction grammar as the review loop, and no FSRS at all: right or wrong only
 * moves the in-run streak, and nothing ends by itself. Every rule is kern's `NumbersRun`,
 * reached through [NumbersFlow]; this decides what it looks like.
 *
 * The card carries nothing but the prompt: the header line already names what is drilled
 * and how far the ramp has come, and the field's placeholder names what is owed — so a
 * badge on the card would be the third telling of what one tap said.
 */
@Composable
fun NumbersRunScreen(model: AppModel, mode: NumbersMode) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = remember(mode) {
        model.newTrainerRun(mode, onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    }
    if (flow == null) {
        LaunchedEffect(Unit) { model.finishDrill(Screen.Numbers, null, "") }
        return
    }
    val state = flow.state
    val store = model.trainer.store

    // What the result tile says was drilled: a run that asks one thing names it, and one
    // that interleaves several falls back to the hub card's own title.
    val title = mode.exercises.singleOrNull()?.let { chrome.name(it) } ?: chrome.trainerHubTitle

    val leave = {
        val closed = flow.close(store.record(mode.recordKey), store.standing(mode.language))
        store.book(closed.progressBookings)
        closed.summary?.let { model.bookRecord(closed.recordKey, it) }
        model.finishDrill(Screen.Numbers, closed.summary, title)
    }
    BackHandler(enabled = !flow.showingReference) { leave() }
    DrillRunEffects(
        ranOut = flow.ranOut,
        beatToken = flow.beatToken,
        armedBeat = flow.armedBeat,
        onBeatElapsed = flow::advanceElapsed,
        leave = leave,
        pronouncer = model.pronouncer,
    )

    // The revealed reading is spoken like any other answer, once per question however the
    // pause was reached — after a beat, so the verdict cue is out of the way.
    var spoken by remember(state.index) { mutableStateOf(false) }
    LaunchedEffect(state.index, state.showsAnswer) {
        if (!state.showsAnswer || spoken) return@LaunchedEffect
        spoken = true
        delay(CHIME_CLEARANCE_MS)
        model.speakDrillAnswer(state.currentTask.display, mode.language)
    }

    val inputFocus = remember { FocusRequester() }
    QuestionFocus(state.index, model.pronouncer, inputFocus)

    Column(
        modifier = Modifier.fillMaxSize().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        DrillTopBar(model, state.outcomes, state.tally, leave)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            DrillStreakLine(
                sprosse = sprosseText(state, chrome),
                streak = state.streak,
                bestStreak = state.bestStreak,
                chrome = chrome,
                announcesRecord = true,
            )
            DrillPromptCard(model, flow, chrome)
            NumbersControls(model, flow, chrome, inputFocus, leave)
            Spacer(Modifier.height(Theme.spacing.sm))
        }
    }

    if (flow.showingReference) {
        NumberReferenceOverlay(model, mode.language, chrome) { flow.showingReference = false }
    }
}

/**
 * The Sprosse part of the score line, for the exercise that just asked: numbers count DIGITS,
 * everything else counts plain levels — and an exercise with one Sprosse shows none. The face
 * leads only where the run offers more than one exercise, since a run that asks one thing
 * has already said what it asks.
 */
private fun sprosseText(state: NumbersRunState, chrome: Chrome): String? {
    if (!state.showsSprosse) return null
    val sprosse = state.currentLevel
    // why: the digits wording is the numbers drill's own and already wears 🔢 — putting
    // the exercise's face in front would double it.
    if (state.currentExercise == NumbersExercise.Counting) {
        return countLine(chrome.numbersSprosseOne, chrome.numbersSprosse, sprosse)
    }
    val level = chrome.trainerSprosse.format(sprosse)
    if (!state.severalExercises) return level
    return "${chrome.badge(state.currentExercise)} $level"
}

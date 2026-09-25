package net.spross.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.NumbersFlow
import net.spross.app.badge
import net.spross.app.bookRecord
import net.spross.app.countLine
import net.spross.app.finishDrill
import net.spross.app.name
import net.spross.app.newTrainerRun
import net.spross.app.speakDrillAnswer
import net.spross.kern.trainer.NumbersChallenge
import net.spross.kern.trainer.NumbersExercise
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.NumbersRunState
import net.spross.kern.trainer.TimedRun

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
fun NumbersRunScreen(model: AppModel, mode: NumbersMode, challenge: NumbersChallenge? = null) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = rememberRun(model, Screen.Numbers, key = mode to challenge) {
        model.newTrainerRun(mode, challenge, onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    } ?: return
    val state = flow.state
    val store = model.trainer.store

    // The result tile's title: the challenge, the single exercise, or the hub card's title.
    val title = if (challenge != null) {
        chrome.trainerChallengeTitle
    } else {
        mode.exercises.singleOrNull()?.let { chrome.name(it) } ?: chrome.trainerHubTitle
    }

    val leave = {
        val closed = flow.close(store.record(mode.recordKey), store.standing(mode.language))
        store.book(closed.progressBookings)
        closed.summary?.let { model.bookRecord(closed.recordKey, it) }
        model.finishDrill(Screen.Numbers, closed.summary, title)
    }

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
    val secondsLeft = timedClock(flow)

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        progress = state,
        sprosse = sprosseText(state, chrome),
        announcesRecord = true,
        // The table raised over the run takes the back gesture first; the run is still there.
        backLeaves = !flow.showingReference,
        // why: the run says its answers out loud, so it owes the learner a way to
        // silence them here, not in Settings.
        showsMuteButton = true,
        timed = secondsLeft?.let { timedLine(it, state.score, chrome) },
    ) {
        DrillPromptCard(model, flow, chrome)
        NumbersControls(model, flow, chrome, inputFocus, leave)
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

/** A timed run's seconds left, sending kern's intent at zero; null when untimed. */
@Composable
private fun timedClock(flow: NumbersFlow): Int? {
    if (!flow.state.timed) return null
    var left by remember { mutableIntStateOf(TimedRun.SECONDS) }
    LaunchedEffect(flow) {
        val end = SystemClock.elapsedRealtime() + TimedRun.SECONDS * 1_000L
        while (true) {
            val remaining = end - SystemClock.elapsedRealtime()
            left = ((remaining + 999) / 1_000).toInt().coerceAtLeast(0)
            if (remaining <= 0) break
            delay(remaining % 1_000 + 1)
        }
        flow.timeUp()
    }
    return left
}

/** The timed half of the score line: the seconds left, then the score so far. */
private fun timedLine(secondsLeft: Int, score: Int, chrome: Chrome): String =
    "⏱ %d:%02d · %s".format(
        secondsLeft / 60,
        secondsLeft % 60,
        countLine(chrome.trainerRunScoreOne, chrome.trainerRunScore, score),
    )

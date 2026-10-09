package net.spross.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.NumbersFlow
import net.spross.app.Screen
import net.spross.app.bookRecord
import net.spross.app.countLine
import net.spross.app.finishDrill
import net.spross.app.name
import net.spross.app.newTrainerRun
import net.spross.app.stampRun
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.NumbersChallenge
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.NumbersRunState
import net.spross.kern.trainer.TimedRun

/**
 * A stateless ENDLESS slot run — numbers, years, the clock, sentences, number forms.
 *
 * The same interaction grammar as the review loop, and no FSRS at all: right or wrong only
 * moves the in-run answer streak, and nothing ends by itself. Every rule is kern's `NumbersRun`,
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
        model.stampRun(Drill.Numbers, closed.summary)
        model.finishDrill(Screen.Numbers, closed.summary, title)
    }

    val inputFocus = remember { FocusRequester() }
    QuestionFocus(state.index to state.pause, model.pronouncer, inputFocus)
    val secondsLeft = timedClock(flow)

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        progress = state,
        sprosse = sprosseText(state, chrome),
        // The table raised over the run takes the back gesture first; the run is still there.
        backLeaves = !flow.showingReference,
        // why: the run says its answers out loud, so it owes the learner a way to
        // silence them here, not in Settings.
        showsMuteButton = true,
        timed = secondsLeft?.let { timedLine(it, state.score, chrome) },
    ) {
        QuestionStage(flow) { QuestionCard(it, chrome, voice = model.cardVoice) }
        NumbersControls(model, flow, chrome, inputFocus, leave)
    }

    if (flow.showingReference) {
        NumberReferenceOverlay(model, mode.language, chrome) { flow.showingReference = false }
    }
}

/** The Sprosse part of the score line, worded as kern's [NumbersRunState.sprosseLine] says. */
private fun sprosseText(state: NumbersRunState, chrome: Chrome): String? {
    val line = state.sprosseLine ?: return null
    val label = if (line.digits) {
        countLine(chrome.numbersSprosseOne, chrome.numbersSprosse, line.sprosse)
    } else {
        chrome.trainerSprosse.format(line.sprosse)
    }
    return line.emoji?.let { "$it $label" } ?: label
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
            left = TimedRun.secondsLeft(remaining)
            if (remaining <= 0) break
            delay(remaining % 1_000 + 1)
        }
        flow.timeUp()
    }
    return left
}

/** The timed half of the score line: the seconds left, then the score so far. */
private fun timedLine(secondsLeft: Int, score: Int, chrome: Chrome): String =
    "${TimedRun.clock(secondsLeft)} · ${countLine(chrome.trainerRunScoreOne, chrome.trainerRunScore, score)}"

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.TrainerFlow
import net.spross.app.badge
import net.spross.app.countLine
import net.spross.app.name
import net.spross.app.newTrainerRun
import net.spross.app.speakDrillAnswer
import net.spross.kern.trainer.DrillVariant
import net.spross.kern.trainer.TrainerMode
import net.spross.kern.trainer.TrainerRunState

/**
 * A stateless ENDLESS slot run — numbers, years, the clock, sentences, number forms.
 *
 * The same interaction grammar as the review loop, and no FSRS at all: right or wrong only
 * moves the in-run streak, and nothing ends by itself. Every rule is kern's `TrainerRun`,
 * reached through [TrainerFlow]; this decides what it looks like.
 *
 * The card carries nothing but the prompt: the header line already names what is drilled
 * and how far the ramp has come, and the field's placeholder names what is owed — so a
 * badge on the card would be the third telling of what one tap said.
 */
@Composable
fun TrainerSessionScreen(model: AppModel, mode: TrainerMode) {
    val chrome = model.chrome
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val flow = remember(mode) {
        model.newTrainerRun(
            mode,
            onTone = { view.cueTone(it, model.cues) },
            // why: a pause that waits for a tap must not hold the keyboard — it covers
            // the very button the pause is waiting for.
            onReleaseFocus = { focusManager.clearFocus() },
        )
    }
    if (flow == null) {
        LaunchedEffect(Unit) { model.finishDrill(Screen.Numbers, null, "") }
        return
    }
    val state = flow.state
    val store = model.werkstatt.store

    // What the result tile says was drilled: a run that asks one thing names it, and one
    // that interleaves several is the Werkstatt itself.
    val title = mode.variants.singleOrNull()?.let { chrome.name(it) } ?: chrome.trainingTitle

    // why: from the corner or from "Fertig", the close is the same one — kern books what
    // is pending, says what to store, and the page that started the run wears the figures.
    val leave = {
        val closed = flow.close(store.record(mode.recordKey), store.standing(mode.language))
        store.book(closed.progressBookings)
        closed.summary?.let {
            if (it.newRecord) {
                store.bookRecord(closed.recordKey, it.bestStreak)
                // why: the run's own reward, sounded as it closes — the result tile the
                // learner lands on already carries the words, but not until they look.
                model.cues.cheer()
            }
        }
        model.finishDrill(Screen.Numbers, closed.summary, title)
    }
    BackHandler(enabled = !flow.showingReference) { leave() }
    // D5: leaving mid-clip must silence, whichever way the screen goes.
    DisposableEffect(Unit) { onDispose { model.pronouncer.stop() } }

    // The beat kern armed. Nothing is ever armed where a screen reader runs — the flow
    // renders an explicit Weiter instead — so this only waits out beats that may run.
    LaunchedEffect(flow.beatToken) {
        val tier = flow.armedBeat ?: return@LaunchedEffect
        delay(tier.delayMs)
        flow.advanceElapsed()
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

    // The field takes the keyboard back with every question — an amber hold gives it up so
    // the button it waits for is not covered, and the next prompt is typed into, not tapped.
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(state.index) {
        if (model.pronouncer.readsScreenAloud) return@LaunchedEffect
        // why: a requester answers only once its node has been placed; one frame is what
        // that takes, and a request fired inside the same composition lands on nothing.
        withFrameNanos { }
        runCatching { inputFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        DrillTopBar(model, state.outcomes, state.cleanCount, state.done, leave)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            DrillStreakLine(
                rung = rungText(state, chrome),
                streak = state.streak,
                bestStreak = state.bestStreak,
                chrome = chrome,
                announcesRecord = true,
            )
            TrainerPromptCard(model, flow, chrome)
            TrainerControls(model, flow, chrome, inputFocus, leave)
            Spacer(Modifier.height(DlSpace.s))
        }
    }

    if (flow.showingReference) {
        NumberReferenceOverlay(model, mode.language, chrome) { flow.showingReference = false }
    }
}

/**
 * The rung part of the score line, for the variant that just asked: numbers count DIGITS,
 * everything else counts plain levels — and a variant with one rung shows none. The face
 * leads only where the run offers more than one variant, since a run that asks one thing
 * has already said what it asks.
 */
private fun rungText(state: TrainerRunState, chrome: Chrome): String? {
    if (!state.showsRung) return null
    val rung = state.currentLevel
    // why: the digits wording is the numbers drill's own and already wears 🔢 — putting
    // the variant's face in front would double it.
    if (state.currentVariant == DrillVariant.Numbers) {
        return countLine(chrome.digitsOne, chrome.digitsMany, rung)
    }
    val level = chrome.level.format(rung)
    if (!state.severalVariants) return level
    return "${chrome.badge(state.currentVariant)} $level"
}

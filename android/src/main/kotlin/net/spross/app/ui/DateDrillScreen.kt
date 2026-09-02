package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import net.spross.app.DateDrillFlow
import net.spross.app.Screen
import net.spross.app.TrainerStore
import net.spross.app.dateAsk
import net.spross.app.newDateDrill
import net.spross.app.speakDrillAnswer
import net.spross.app.speakFormOnTap
import net.spross.kern.session.TurnFeedback

/**
 * The dates drill: the weekday names alone, the month names alone, the day-of-month
 * numeral alone — and then the whole spoken date assembled out of them. Typed answers
 * only, in whichever direction the page was started with.
 *
 * Stateless like all its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's calendars, not the learner's own words. Closing
 * leaves a summary on the page that opened it.
 *
 * Every rule is kern's `DateDrill`, reached through [DateDrillFlow]; this decides what it
 * looks like. The card is the atlas drill's `CountryPromptCard` — a dates question simply
 * carries no picture, so the leading slot stays empty and the prompt (a name, or a dated
 * line in the prompt side's digits) stands where the country's name would.
 */
@Composable
fun DateDrillScreen(model: AppModel, reverse: Boolean, fast: Boolean) {
    val chrome = model.chrome
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    // why: unkeyed on anything the run does — a foreground that re-sweeps availability must
    // not restart the run underneath it.
    val flow = remember(reverse, fast) {
        model.newDateDrill(
            reverse = reverse,
            fast = fast,
            onTone = { view.cueTone(it, model.cues) },
            // why: a pause that waits for a tap must not hold the keyboard — it covers the
            // very button the pause is waiting for.
            onReleaseFocus = { focusManager.clearFocus() },
        )
    }
    if (flow == null) {
        // Nothing this pair can be asked — the chip gates on the same join, so this is a
        // closed door rather than a screen.
        LaunchedEffect(Unit) { model.finishDrill(Screen.Dates, null, "") }
        return
    }
    val state = flow.state
    val store = model.werkstatt.store
    val stamp = model.box?.joinStamp
    // One key per PAIR, the same one the page reads its best Sprosse back from.
    val key = stamp?.let { TrainerStore.datesKey(it.source, it.target) }

    // why: from the corner or from "Fertig", the close is the same one — a pending answer
    // books exactly as the tap would, and the page that started the run wears the figures.
    val leave = {
        val closed = flow.close(standingRecord = key?.let { store.record(it) } ?: 0)
        if (key != null) {
            // The Sprosse buys nothing (the drill is ungated); it is what the page reads back,
            // and what Fast is priced against.
            store.bookSprosse(key, closed.bestLevel)
            closed.summary?.let {
                if (it.newRecord) {
                    store.bookRecord(key, it.bestStreak)
                    // why: the run's own reward, sounded as it closes — the result tile the
                    // learner lands on already carries the words, but not until they look.
                    model.cues.cheer()
                }
            }
        }
        model.finishDrill(Screen.Dates, closed.summary, chrome.trainerSkillDates)
    }
    BackHandler { leave() }
    // Nothing left to ask: hand the run back, never repeat a question.
    LaunchedEffect(flow.ranOut) { if (flow.ranOut) leave() }
    // D5: leaving mid-word must silence, whichever way the screen goes.
    DisposableEffect(Unit) { onDispose { model.pronouncer.stop() } }

    // The beat kern's siblings arm. Nothing is ever armed where a screen reader runs — the
    // flow renders an explicit Weiter instead — so this only waits out beats that may run.
    LaunchedEffect(flow.beatToken) {
        val tier = flow.armedBeat ?: return@LaunchedEffect
        delay(tier.delayMs)
        flow.advanceElapsed()
    }

    // The revealed reading is spoken like any other answer, once per question however the
    // pause was reached — after a beat, so the verdict cue is out of the way.
    //
    // Never on a REVERSED run: the side answered there is the learner's own language, and
    // every autoplay `read-aloud.md` describes says a target-language form. The speaker
    // beside the reveal still says it on request — a tap outranks the rule.
    var spoken by remember(state.index) { mutableStateOf(false) }
    LaunchedEffect(state.index, state.showsAnswer) {
        if (reverse || !state.showsAnswer || spoken) return@LaunchedEffect
        spoken = true
        delay(CHIME_CLEARANCE_MS)
        model.speakDrillAnswer(state.task.display, state.answerLanguage)
    }

    // The field takes the keyboard back with every question — an amber hold gives it up so
    // the button it waits for is not covered, and the next prompt is typed into.
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
        DrillTopBar(model, state.outcomes, state.tally, leave)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            DrillStreakLine(
                sprosse = chrome.trainerSprosse.format(state.level),
                streak = state.streak,
                bestStreak = state.bestStreak,
                chrome = chrome,
                announcesRecord = true,
            )
            Prompt(model, flow, chrome)
            Controls(model, flow, chrome, inputFocus, leave)
            Spacer(Modifier.height(DlSpace.s))
        }
    }
}

@Composable
private fun Prompt(model: AppModel, flow: DateDrillFlow, chrome: Chrome) {
    val state = flow.state
    val task = state.task
    CountryPromptCard(
        ask = chrome.dateAsk(task.kind),
        // A dates question carries no picture — the prompt is the whole card.
        emoji = null,
        emojiIsGiveaway = false,
        text = task.promptText,
        language = state.promptLanguage,
        // why: a clean answer flips in about a second — opening the card for a beat there
        // would read as a correction the learner did not earn.
        reveal = if (!state.showsAnswer) {
            null
        } else {
            CountryReveal(
                word = task.display,
                note = null,
                language = state.answerLanguage,
                pronounce = model.speakFormOnTap(task.display, state.answerLanguage),
            )
        },
        chrome = chrome,
    )
    if (state.showsAnswer) {
        state.otherWord?.let { other ->
            // why: same line as the review session's — both explain what became of
            // the answer, so they read alike.
            PauseLine(chrome.sessionOtherWord.format(other.word, other.meanings.joinToString(", ")))
        }
    }
}

/**
 * The field and the one primary action under it. The placeholder names the language the
 * answer is owed IN — which is the learner's own on a reversed run, and the only place the
 * direction is spelled out.
 */
@Composable
private fun Controls(
    model: AppModel,
    flow: DateDrillFlow,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val state = flow.state
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
        DrillAnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(state.answerLanguage)),
            feedback = state.feedback,
            chrome = chrome,
            focus = inputFocus,
            onDone = { flow.enter() },
        )
        when (val feedback = state.feedback) {
            // ONE primary action: an empty field reveals, a typed one checks.
            TurnFeedback.Neutral -> Button(
                onClick = { flow.primary() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (flow.input.isBlank()) chrome.sessionReveal else chrome.commonCheck)
            }
            // why: nothing is drawn for a clean answer — it already stands in the learner's
            // own text with the field's checkmark, and the card is on its way out.
            TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
            is TurnFeedback.Almost -> AlmostLine(model, flow, feedback.correctForm, chrome)
            // why: no "I knew it" in a drill — the questions are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> ConfirmButton(chrome) { flow.confirm() }
        }
        // The way out, where it is wanted: under the button that goes on, on the second
        // miss in a row.
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

/** A slip: the box spells the reading out, and the tap that ends the pause books it amber. */
@Composable
private fun AlmostLine(model: AppModel, flow: DateDrillFlow, form: String, chrome: Chrome) {
    val state = flow.state
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
        AlmostCorrection(
            chrome.sessionAlmostTypo,
            form,
            chrome,
            model.speakFormOnTap(form, state.answerLanguage),
        )
        ConfirmButton(chrome) { flow.confirm() }
    }
}

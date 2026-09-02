package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LetterDrillFlow
import net.spross.app.Screen
import net.spross.app.audio.Pronouncer
import net.spross.app.letterReplay
import net.spross.app.newLetterDrill
import net.spross.app.playLetterPrompt
import net.spross.app.letterSpeaker
import net.spross.kern.trainer.LetterDrillTask
import net.spross.kern.trainer.LetterStage

/**
 * The letter drill: hear a sound, find the letter. Four glyph tiles, then confusable ones,
 * then typing the glyph, and finally dictation of words the learner already holds — one
 * Sprosse, mapped to stages by kern's `LetterDrillRun`, which owns every rule below.
 *
 * The one screen in the app that shows nothing: everything the learner is given is the
 * sound. So both silences that can meet them are named rather than left to be guessed at —
 * the read-aloud switch blocks the card with the one tap that undoes it, and every autoplay
 * goes out as [Pronouncer.Trigger.AUTO], where mute and the TalkBack gate apply without
 * this screen testing for either.
 *
 * Stage bodies live in LetterDrillStages.kt; the run itself in `LetterDrillFlow`.
 */
@Composable
fun LetterDrillScreen(model: AppModel) {
    val chrome = model.chrome
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    // why: unkeyed — everything a run draws from is resolved ONCE, as it opens. A
    // foreground that re-sweeps availability must not restart the run underneath it.
    val flow = remember {
        model.newLetterDrill(
            onTone = { view.cueTone(it, model.cues) },
            onReleaseFocus = { focusManager.clearFocus() },
        )
    }
    if (flow == null) {
        // Nothing this device can ask — the start button gates on the same predicate, so
        // this is a closed door rather than a screen.
        LaunchedEffect(Unit) { model.finishDrill(Screen.Letters, null, "") }
        return
    }
    val state = flow.state
    // why: from the corner or from "Fertig", the close is the same one — kern books a
    // pending answer exactly as the tap would, and the page that started the run wears the
    // figures. The letter drill books no Sprosse and keeps no record, so it stores nothing.
    val leave = {
        val closed = flow.close()
        model.finishDrill(Screen.Letters, closed.summary, chrome.trainerSkillLetters)
    }
    BackHandler { leave() }
    // Nothing left to ask: hand the run back, never repeat a question.
    LaunchedEffect(flow.ranOut) { if (flow.ranOut) leave() }
    // D5: leaving mid-clip must silence, whichever way the screen goes.
    DisposableEffect(Unit) { onDispose { model.pronouncer.stop() } }

    LaunchedEffect(flow.beatToken) {
        val tier = flow.armedBeat ?: return@LaunchedEffect
        delay(tier.delayMs)
        flow.advanceElapsed()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        DrillTopBar(model, state.outcomes, state.tally, leave)
        DrillStreakLine(null, state.streak, state.bestStreak, chrome)
        val task = state.task
        if (task != null) Run(model, flow, task, chrome, leave)
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
    val screenReader = model.pronouncer.readsScreenAloud
    val replayFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }

    // why: keyed on the question, and a LaunchedEffect fires on FIRST composition too —
    // so the first question of a run speaks without a second hook.
    LaunchedEffect(state.index) {
        model.playLetterPrompt(task, Pronouncer.Trigger.AUTO)
        // why: a requester answers only once its node has been placed; one frame is what
        // that takes. The audio question then sits one action away for a screen reader,
        // and the keyboard belongs to the field for everybody else — never both, or
        // TalkBack would be dragged off the button it was just given.
        withFrameNanos { }
        if (screenReader) {
            runCatching { replayFocus.requestFocus() }
        } else if (state.typing) {
            runCatching { inputFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        HearPrompt(model, flow, task, chrome, replayFocus)
        when (task.stage) {
            LetterStage.ChoiceEasy, LetterStage.ChoiceConfusable ->
                ChoiceStage(model, flow, task, chrome)
            LetterStage.Typed, LetterStage.Dictation ->
                TypedStage(model, flow, task, chrome, inputFocus)
        }
        // The way out, where it is wanted: under the button that goes on, on the second
        // miss in a row — kern decides which moment that is.
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
        Spacer(Modifier.height(DlSpace.s))
    }
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
    val language = model.languageName(task.language)
    val question = when {
        task.stage == LetterStage.Dictation -> chrome.lettersAskDictation
        task.gapText == null -> chrome.lettersAskHear
        else -> chrome.lettersAskSpell
    }
    val replay = model.letterReplay(task)
    CardFace {
        Text(
            "$question · ${chrome.lettersPromptInLanguage.format(language)}",
            style = MaterialTheme.typography.bodySmall,
            color = Dl.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        ReplayButton(chrome, replay, replayFocus)
        task.gapText?.let {
            Text(localizedTarget(it, task.language), fontSize = DlPrompt.word, fontWeight = FontWeight.Bold)
        }
        if (model.pronouncer.muted) UnmuteRow(model, task, chrome)
        if (flow.state.showsAnswer) {
            // why: the meaning is a REVEAL, never a cue — a dictation that shows what the
            // word means is no longer taken from the sound.
            CardReveal(note = task.gloss) {
                SpokenWord(model.letterSpeaker(task, task.display), chrome) {
                    Text(
                        localizedTarget(task.display, task.language),
                        style = MaterialTheme.typography.titleLarge,
                        color = Dl.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/** Not a control beside the content: on this card it IS the content, well past 48 dp. */
@Composable
private fun ReplayButton(chrome: Chrome, replay: (() -> Unit)?, focus: FocusRequester) {
    val enabled = replay != null
    Box(
        modifier = Modifier
            .size(72.dp)
            .focusRequester(focus)
            // The wash, not the accent at full strength: this is the thing the drill
            // asks you to LISTEN to, and a solid clay disc reads as the button to press
            // next. Disabled keeps the recessed chip fill, so the state still shows.
            .background(
                if (enabled) Dl.colors.wash(Dl.colors.accent) else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            )
            // why: focusable in BOTH states — a disabled clickable carries no focus target,
            // and the screen reader's hand-off to this button would land nowhere on the one
            // device that can neither play nor say the prompt.
            .then(if (replay != null) Modifier.clickable(onClick = replay) else Modifier.focusable())
            // why: merged, or the loudspeaker inside would be a node of its own and
            // TalkBack would read the picture after the button it belongs to.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = chrome.a11yActionReplayPrompt
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SprossIcons.Speaker,
            contentDescription = null,
            tint = if (enabled) Dl.colors.accent else Dl.colors.textSecondary,
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * The blocking row a muted drill shows: the fix and its proof in one tap. Hiding the entry
 * over a state this reversible would only make the drill undiscoverable.
 */
@Composable
private fun UnmuteRow(model: AppModel, task: LetterDrillTask, chrome: Chrome) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(chrome.lettersMutedTitle, style = MaterialTheme.typography.bodyMedium, color = Dl.colors.textSecondary)
        OutlinedButton(
            onClick = {
                model.pronouncer.muted = false
                model.playLetterPrompt(task, Pronouncer.Trigger.TAP)
            },
            modifier = Modifier.heightIn(min = 48.dp).pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.lettersMutedEnable)
        }
    }
}

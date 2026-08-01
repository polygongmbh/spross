package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.LetterDrillFlow
import net.spross.app.LetterFeedback
import net.spross.app.audio.Pronouncer
import net.spross.app.letterReplay
import net.spross.app.newLetterDrill
import net.spross.app.playLetterPrompt
import net.spross.kern.trainer.LetterDrillTask
import net.spross.kern.trainer.LetterStage

/** The one clean-correct beat in the app — the same number the review loop waits. */
private const val CORRECT_BEAT_MS = 1200L

/**
 * The letter drill: hear a sound, find the letter. Four glyph tiles, then confusable
 * ones, then typing the glyph, and finally dictation of words the learner already holds —
 * one rung, mapped to stages by kern.
 *
 * The one screen in the app that shows nothing: everything the learner is given is the
 * sound. So both silences that can meet them are named rather than left to be guessed at
 * — the read-aloud switch blocks the card with the one tap that undoes it, and every
 * autoplay goes out as [Pronouncer.Trigger.AUTO], where mute and the TalkBack gate apply
 * without this screen testing for either.
 *
 * Stage bodies live in LetterDrillStages.kt; the run itself in `LetterDrillFlow`.
 */
@Composable
fun LetterDrillScreen(model: AppModel) {
    val flow = remember(model.box?.joinStamp?.target) { model.newLetterDrill() }
    if (flow == null) {
        // Nothing this device can ask — the chip gates on the same predicate, so this is
        // a closed door rather than a screen.
        LaunchedEffect(Unit) { model.closeLetterDrill() }
        return
    }
    val chrome = model.chrome
    // why: from the summary the door is simply the door; mid-run it books whatever
    // answer is pending first, so closing can neither lose it nor upgrade it.
    val leave = { if (flow.finished || !flow.close()) model.closeLetterDrill() }
    BackHandler { leave() }
    // D5: leaving mid-clip must silence, whichever way the screen goes.
    DisposableEffect(Unit) { onDispose { model.pronouncer.stop() } }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${chrome.level.format(flow.level)} · ${chrome.streak.format(flow.streak)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ReadAloudSwitch(model)
            TextButton(onClick = { leave() }) { Text("✕") }
        }
        if (flow.finished) Summary(model, flow, chrome) else Run(model, flow, chrome)
    }
}

@Composable
private fun Run(model: AppModel, flow: LetterDrillFlow, chrome: Chrome) {
    val task = flow.task ?: return
    val screenReader = model.pronouncer.readsScreenAloud
    val replayFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }
    val typing = task.stage == LetterStage.Typed || task.stage == LetterStage.Dictation

    // why: keyed on the question, and a LaunchedEffect fires on FIRST composition too —
    // so the first question of a run speaks without a second hook.
    LaunchedEffect(flow.index) {
        model.playLetterPrompt(task, Pronouncer.Trigger.AUTO)
        // why: a requester answers only once its node has been placed; one frame is what
        // that takes. The audio question then sits one action away for a screen reader,
        // and the keyboard belongs to the field for everybody else — never both, or
        // TalkBack would be dragged off the button it was just given.
        withFrameNanos { }
        if (screenReader) replayFocus.requestFocus() else if (typing) inputFocus.requestFocus()
    }

    // The clean-correct beat. Under TalkBack it becomes an explicit Weiter instead: a
    // timed screen change truncates the announcement it just made and moves the page
    // under the user.
    LaunchedEffect(flow.feedback) {
        val current = flow.feedback
        if (current is LetterFeedback.Correct && current.correction == null && !screenReader) {
            delay(CORRECT_BEAT_MS)
            flow.next()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        HearPrompt(model, task, chrome, replayFocus)
        when (task.stage) {
            LetterStage.ChoiceEasy, LetterStage.ChoiceConfusable ->
                ChoiceStage(flow, chrome, screenReader)
            LetterStage.Typed, LetterStage.Dictation ->
                TypedStage(model, flow, task, chrome, screenReader, inputFocus)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The audio question: what is being asked, one big replay button, and — for a gap
 * question — the example word with the asked grapheme blanked. No answer ever renders
 * here, and that is the whole point.
 */
@Composable
private fun HearPrompt(
    model: AppModel,
    task: LetterDrillTask,
    chrome: Chrome,
    replayFocus: FocusRequester,
) {
    val language = model.catalog?.languages?.get(task.language)?.name ?: task.language
    val question = when {
        task.stage == LetterStage.Dictation -> chrome.lettersDictation
        task.gapText == null -> chrome.lettersHear
        else -> chrome.lettersSpell
    }
    val replay = model.letterReplay(task)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$question · ${chrome.promptInLanguage.format(language)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            ReplayButton(chrome, replay, replayFocus)
            task.gapText?.let {
                Text(it, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            if (model.pronouncer.muted) UnmuteRow(model, task, chrome)
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
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            )
            // why: focusable in BOTH states — a disabled clickable carries no focus
            // target, and the screen reader's hand-off to this button would land
            // nowhere on the one device that can neither play nor say the prompt.
            .then(if (enabled) Modifier.clickable { replay?.invoke() } else Modifier.focusable())
            // why: merged, or the loudspeaker inside would be a node of its own and
            // TalkBack would read the picture after the button it belongs to.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = chrome.replayPrompt
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("🔊", fontSize = 30.sp)
    }
}

/**
 * The blocking row a muted drill shows: the fix and its proof in one tap. Hiding the
 * chip over a state this reversible would only make the drill undiscoverable.
 */
@Composable
private fun UnmuteRow(model: AppModel, task: LetterDrillTask, chrome: Chrome) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            chrome.audioOff,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = {
            model.pronouncer.muted = false
            model.playLetterPrompt(task, Pronouncer.Trigger.TAP)
        }) {
            Text(chrome.enableSound)
        }
    }
}

/** No confetti stack on this platform yet: what was done, the best run, and the door. */
@Composable
private fun Summary(model: AppModel, flow: LetterDrillFlow, chrome: Chrome) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (flow.bestStreak >= 5) "🎉" else "🌱", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(chrome.tasksDone.format(flow.done), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            chrome.bestStreak.format(flow.bestStreak),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { model.closeLetterDrill() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(chrome.finish)
        }
    }
}

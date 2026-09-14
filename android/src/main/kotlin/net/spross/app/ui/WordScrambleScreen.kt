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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.WordScrambleFlow
import net.spross.app.newWordScramble
import net.spross.app.speakFormOnTap
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.ScrambledWord
import net.spross.kern.trainer.WordScrambleTask

/**
 * The word scramble: a word the box already holds, with its letters thrown out of order,
 * written back out. The answer is TYPED — handing the same letters back as tiles would leave
 * nothing to retrieve but their order, where writing the word out IS the spelling — so it
 * wears the typed card every other trainer drill wears (`docs/drills-words.md`).
 *
 * Stateless like the letter drill: no review is ever booked, and the box is READ for the
 * words it has consolidated and never written. The RUN is kern's, reached through
 * [WordScrambleFlow]: the draw, the masking ladder and the verdict ladder are all its.
 */
@Composable
fun WordScrambleScreen(model: AppModel) {
    val chrome = model.chrome
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    // why: unkeyed — everything a run draws from is resolved ONCE, as it opens. A foreground
    // that re-sweeps availability must not restart the run underneath it.
    val flow = remember {
        model.newWordScramble(
            onTone = { view.cueTone(it, model.cues) },
            // why: a pause that waits for a tap must not hold the keyboard — it covers the
            // very button the pause is waiting for.
            onReleaseFocus = { focusManager.clearFocus() },
        )
    }
    if (flow == null) {
        // Nothing this box can be asked — the chip gates on the same report, so this is a
        // closed door rather than a screen.
        LaunchedEffect(Unit) { model.finishDrill(Screen.Home, null, "") }
        return
    }
    val state = flow.state
    // why: from the corner or from "Fertig", the close is the same one — kern books a pending
    // answer exactly as the tap would. The scrambles have no page of their own to land on, so
    // the figures go back to Home with the learner; this drill books no Sprosse and keeps no
    // record, so it stores nothing.
    val leave = {
        val closed = flow.close()
        model.finishDrill(Screen.Home, closed.summary, chrome.trainerSkillWordScramble)
    }
    BackHandler { leave() }
    DrillRunEffects(
        ranOut = flow.ranOut,
        beatToken = flow.beatToken,
        armedBeat = flow.armedBeat,
        onBeatElapsed = flow::advanceElapsed,
        leave = leave,
        pronouncer = model.pronouncer,
    )

    // The field takes the keyboard back with every question — an amber hold gives it up so
    // the button it waits for is not covered, and the next word is typed into.
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(state.index) {
        // why: not under a screen reader — moving the keyboard focus would drag TalkBack off
        // the card it was just handed.
        if (model.pronouncer.readsScreenAloud) return@LaunchedEffect
        // why: a requester answers only once its node has been placed; one frame is what that
        // takes, and a request fired inside the same composition lands on nothing.
        withFrameNanos { }
        runCatching { inputFocus.requestFocus() }
    }

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
                sprosse = chrome.trainerSprosse.format(state.level),
                streak = state.streak,
                bestStreak = state.bestStreak,
                chrome = chrome,
            )
            val task = state.task
            if (task != null) {
                TrainerPromptCard(
                    prompt = mixedWord(task.scrambled),
                    promptLabel = spelledOut(task.scrambled),
                    size = PromptSize.Word,
                    answer = task.display,
                    language = task.language,
                    gloss = task.gloss,
                    revealed = state.showsAnswer,
                    pronounce = model.speakFormOnTap(task.display, task.language),
                    chrome = chrome,
                )
                Controls(model, flow, task, chrome, inputFocus, leave)
            }
            Spacer(Modifier.height(Theme.spacing.sm))
        }
    }
}

/**
 * The prompt: the letters as kern mixed them, with the ones the Sprosse left standing
 * set bold. Kern says how many hold at each end ([ScrambledWord.fixedLeading] /
 * [ScrambledWord.fixedTrailing]) and this side says what that looks like — weight alone,
 * because the anchors are a recognition aid the ladder takes away Sprosse by Sprosse, and an
 * aid on its way out is not worth a legend.
 */
fun mixedWord(word: ScrambledWord): AnnotatedString {
    val letters = word.display
    val lead = minOf(word.fixedLeading, letters.length)
    val trail = minOf(word.fixedTrailing, letters.length - lead)
    val anchored = SpanStyle(fontWeight = FontWeight.Bold)
    return buildAnnotatedString {
        withStyle(anchored) { append(letters.take(lead)) }
        append(letters.substring(lead, letters.length - trail))
        withStyle(anchored) { append(letters.takeLast(trail)) }
    }
}

/**
 * A mixed word is not a word, and a voice reading it as one says nothing a learner can spell
 * from — so it is spelled OUT, letter by letter.
 */
fun spelledOut(word: ScrambledWord): String = word.display.toList().joinToString(", ")

/**
 * The field and what stands under it: one primary action while the spelling is owed, the tap
 * that books a pause where kern armed none, and the way out on the second miss in a row.
 */
@Composable
private fun Controls(
    model: AppModel,
    flow: WordScrambleFlow,
    task: WordScrambleTask,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val state = flow.state
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        DrillAnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(task.language)),
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
                Text(if (AnswerNormalizer.isBlankAnswer(flow.input)) chrome.commonReveal else chrome.commonCheck)
            }
            // why: nothing is drawn for a clean spelling — it already stands in the learner's
            // own text with the field's checkmark, and the card is on its way out.
            TurnFeedback.Correct -> if (flow.awaitsConfirm) ConfirmButton(chrome) { flow.confirm() }
            is TurnFeedback.Almost -> Column(
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            ) {
                // The amber hold: the box spells the slip out, and the tap books it amber.
                AlmostCorrection(
                    chrome.sessionAlmostTypo,
                    feedback.correctForm,
                    chrome,
                    model.speakFormOnTap(feedback.correctForm, task.language),
                )
                ConfirmButton(chrome) { flow.confirm() }
            }
            // why: no "Wusste ich" in a drill — the words are drawn, so self-reporting after
            // seeing the spelling proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> ConfirmButton(chrome) { flow.confirm() }
        }
        // The way out, where it is wanted: under the button that goes on, on the second miss
        // in a row — kern decides which moment that is.
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

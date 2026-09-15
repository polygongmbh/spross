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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.SentenceScrambleFlow
import net.spross.app.newSentenceScramble
import net.spross.app.speakFormOnTap
import net.spross.kern.trainer.SentenceScrambleTask

/**
 * The sentence scramble: a phrase handed over as its own words, shuffled, and put
 * back into order by tapping. It is the one drill whose answer is an ARRANGEMENT rather than
 * something spelled — the words are given and only their order is withheld
 * (`docs/drills-words.md`).
 *
 * There is no check button: committing the LAST word IS the answer, the way a finished
 * spelling is on the typed drills. Until then a word can be taken back, so a slip of the
 * finger costs a tap rather than the question.
 *
 * Stateless like the letter drill: no review is ever booked, and the box is READ for the
 * phrases it has unlocked and never written. The RUN is kern's, reached through
 * [SentenceScrambleFlow]; the bank and the answer row are [ScrambleTileBank].
 */
@Composable
fun SentenceScrambleScreen(model: AppModel) {
    val chrome = model.chrome
    val view = LocalView.current
    // why: unkeyed — everything a run draws from is resolved ONCE, as it opens. A foreground
    // that re-sweeps availability must not restart the run underneath it.
    val flow = remember { model.newSentenceScramble(onTone = { view.cueTone(it, model.cues) }) }
    if (flow == null) {
        // Nothing this box can be asked — the chip gates on the same report, so this is a
        // closed door rather than a screen.
        LaunchedEffect(Unit) { model.finishDrill(Screen.Home, null, "") }
        return
    }
    val state = flow.state
    // why: from the corner or from "Fertig", the close is the same one — kern books a pending
    // arrangement exactly as the tap would. The scrambles have no page of their own to land
    // on, so the figures go back to Home; this drill keeps no streak record, and no high-water
    // Sprosse beside the mask, because nothing reads one back.
    val leave = {
        val closed = flow.close()
        // why: what the NEXT run reads — it opens on the lowest Sprosse the mask does not
        // hold, so a Sprosse climbed clean is never asked for twice.
        model.trainer.store.bookCleared(flow.clearedKey, closed.clearedSprossen)
        model.finishDrill(Screen.Home, closed.summary, chrome.trainerSkillSentenceScramble)
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

    Column(
        modifier = Modifier.fillMaxSize().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        DrillTopBar(model, state.outcomes, state.tally, leave)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            DrillStreakLine(
                sprosse = chrome.trainerSprosse.format(state.level),
                streak = state.streak,
                bestStreak = state.bestStreak,
                chrome = chrome,
            )
            val task = state.task
            if (task != null) {
                ScrambleTileBank(
                    bank = task.shuffled,
                    placed = state.placedAtoms,
                    isTaken = state::isPlaced,
                    arranged = state.arranged,
                    // Kern's feedback, read — this drill grades by position, so there is no
                    // near miss to render.
                    verdict = when {
                        state.owesAnswer -> ScrambleVerdict.Owed
                        state.answerAccepted -> ScrambleVerdict.Correct
                        else -> ScrambleVerdict.Wrong
                    },
                    chrome = chrome,
                    place = flow::place,
                    take = flow::take,
                )
                if (state.showsAnswer) AnswerCard(model, task, chrome)
                Controls(flow, chrome, leave)
            }
            Spacer(Modifier.height(Theme.spacing.sm))
        }
    }
}

/**
 * The order the catalog authors, once the arrangement has failed to find it — the shared
 * reveal, so a drill card and a vocabulary card grow the same thing. The meaning rides under
 * it and never before it.
 */
@Composable
private fun AnswerCard(model: AppModel, task: SentenceScrambleTask, chrome: Chrome) {
    CardFace {
        CardReveal(note = task.gloss) {
            SpokenWord(model.speakFormOnTap(task.display, task.language), chrome) {
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

@Composable
private fun Controls(flow: SentenceScrambleFlow, chrome: Chrome, onFinish: () -> Unit) {
    val state = flow.state
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        when {
            // ONE primary action while the order is owed. There is no Check: the last word
            // placed grades itself, so this can only be the ask to be shown the order.
            state.owesAnswer -> Button(
                onClick = { flow.reveal() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(chrome.commonReveal)
            }
            state.showsAnswer -> ConfirmButton(chrome) { flow.confirm() }
            // why: the beat never arms under a screen reader, so a clean arrangement would
            // otherwise have nothing to move on with.
            flow.awaitsConfirm -> ConfirmButton(chrome) { flow.confirm() }
        }
        // The way out, where it is wanted: under the button that goes on, on the second miss
        // in a row — kern decides which moment that is.
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

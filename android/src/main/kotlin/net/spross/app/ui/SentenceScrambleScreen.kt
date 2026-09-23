package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.SentenceScrambleFlow
import net.spross.app.finishDrill
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
 * phrases its join carries and never written. The RUN is kern's, reached through
 * [SentenceScrambleFlow]; the bank and the answer row are [ScrambleTileBank].
 */
@Composable
fun SentenceScrambleScreen(model: AppModel) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = rememberRun(model, Screen.Home) {
        model.newSentenceScramble(onTone = hooks.tone)
    } ?: return
    val state = flow.state
    // The scrambles have no page of their own to land on, so a run closes onto Home with no
    // figures; this drill keeps no streak record, and no high-water Sprosse beside the mask,
    // because nothing reads one back.
    val leave = {
        val closed = flow.close()
        // why: what the NEXT run reads — it opens on the lowest Sprosse the mask does not
        // hold, so a Sprosse climbed clean is never asked for twice.
        model.trainer.store.bookCleared(flow.clearedKey, closed.clearedSprossen)
        model.finishDrill(Screen.Home, null, "")
    }

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        progress = state,
        sprosse = chrome.trainerSprosse.format(state.level),
        spacing = Theme.spacing.lg,
    ) {
        val task = state.task ?: return@DrillRunScaffold
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
        ) {
            RevealLines(model, task, state.answerAccepted, state.alternativeMatch, chrome)
        }
        Controls(flow, chrome, leave)
    }
}

/**
 * What the graded arrangement grows, on the answer card itself — the shared reveal, so a drill
 * card and a vocabulary card grow the same thing.
 *
 * The meaning always; the authored order above it only where the arrangement missed, since the
 * chips of a clean one already ARE that order and setting it a second time would read as a
 * correction.
 */
@Composable
private fun RevealLines(
    model: AppModel,
    task: SentenceScrambleTask,
    accepted: Boolean,
    alternativeMatch: Boolean,
    chrome: Chrome,
) {
    CardReveal(note = null) {
        if (!accepted || alternativeMatch) {
            SpokenWord(model.speakFormOnTap(task.display, task.language), chrome) {
                Sentence(
                    localizedTarget(task.display, task.language),
                    Theme.colors.accent,
                    Modifier.weight(1f, fill = false),
                )
            }
        }
        if (!alternativeMatch) {
            Sentence(task.gloss, Theme.colors.textPrimary)
        }
    }
}

/**
 * A phrase on the answer card.
 *
 * Opens at the WORD reveal's size and shrinks only where the phrase is long enough to need it,
 * rather than being set small in advance against the longest one the catalog might hold:
 * this card has the room, since the bank is gone by the time it is drawn and no prompt stands
 * above it. Autosize is insurance for the long phrase, never how a phrase is sized ([Headword]).
 */
@Composable
private fun Sentence(text: AnnotatedString, color: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.headlineSmall
    Text(
        text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 4,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MaterialTheme.typography.titleMedium.fontSize,
            maxFontSize = style.fontSize,
        ),
    )
}

@Composable
private fun Sentence(text: String, color: Color, modifier: Modifier = Modifier) =
    Sentence(AnnotatedString(text), color, modifier)

@Composable
private fun Controls(flow: SentenceScrambleFlow, chrome: Chrome, onFinish: () -> Unit) {
    val state = flow.state
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        // NOTHING while the order is owed — this is the one drill that needs no Reveal. Every
        // word it withholds is already on screen, so placing them all reaches the authored
        // order by itself and books exactly what asking to be shown it would.
        if (state.showsAnswer) ConfirmButton(chrome) { flow.confirm() }
        if (state.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

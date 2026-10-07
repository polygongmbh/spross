package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.TypedDrill
import net.spross.app.bookRecord
import net.spross.app.finishDrill
import net.spross.app.speakFormOnTap
import net.spross.app.stampRun
import net.spross.kern.session.AnswerControls.Slot
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.NumbersMode

/**
 * What tells one typed drill from the other, on a screen that is otherwise the same: where
 * the run goes back to, what its figures are filed under, and how it is opened.
 */
class TypedDrillPage(
    /** The page the run was started from, and the one its summary lands on. */
    val back: Screen,
    /** What the result tile on that page calls this drill. */
    val drill: String,
    /** The roster entry, whose last run a close stamps. */
    val entry: Drill,
    /** The store key for THIS pair, or null before a box has landed. */
    val key: String?,
    /** Opens the run — null where the pair has nothing this drill can ask. */
    val open: (onTone: (ToneKind) -> Unit, onReleaseFocus: () -> Unit) -> TypedDrill?,
)

/**
 * The screen both typed drills wear: the atlas, and the calendar.
 *
 * A card carrying the question, one field, one primary action under it, the beat kern arms,
 * the way out on the second miss in a row, and a close that books whatever stands. None of
 * that differs between the two — what does is [TypedDrillPage] and the run's own [TypedDrill.view].
 *
 * Stateless like all its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's, not the learner's own words. Closing leaves a
 * summary on the page that opened it.
 *
 * Every rule is kern's, reached through the flow; this decides what it looks like.
 */
@Composable
fun TypedDrillScreen(model: AppModel, reverse: Boolean, fast: Boolean, page: TypedDrillPage) {
    val chrome = model.chrome
    val hooks = rememberTurnHooks(model)
    val flow = rememberRun(model, page.back, key = reverse to fast) {
        page.open(hooks.tone, hooks.releaseFocus)
    } ?: return
    val run = flow.view()
    val store = model.trainer.store
    val key = page.key

    val leave = {
        val closed = flow.close(standingRecord = key?.let { store.record(it) } ?: 0)
        if (key != null) {
            // Neither buys a padlock (the drill is ungated); they are what the page reads
            // back — where the next run opens, and what Fast is priced against.
            store.bookSprosse(key, closed.bestSprosse)
            store.bookCleared(NumbersMode.clearedKey(key, reverse), closed.clearedSprossen)
            closed.summary?.let {
                store.bookAnswers(key, it.done)
                model.bookRecord(key, it)
            }
        }
        model.stampRun(page.entry, closed.summary)
        model.finishDrill(page.back, closed.summary, page.drill)
    }

    val paused = flow.progress.pause != null

    val inputFocus = remember { FocusRequester() }
    // A tapped question has no field to fill — a keyboard over the tiles would cover the
    // very answer it is waiting for.
    QuestionFocus(run.index to paused, model.pronouncer, inputFocus.takeIf { flow.progress.controls?.slot !is Slot.Choices })

    DrillRunScaffold(
        model = model,
        run = flow,
        leave = leave,
        outcomes = run.outcomes,
        tally = run.tally,
        sprosse = chrome.trainerSprosse.format(run.sprosse),
        answerStreak = run.answerStreak,
        // why: the run says its answers (and, reversed, its prompts) out loud, so it
        // owes the learner a way to silence them here.
        showsMuteButton = true,
    ) {
        QuestionCard(run.question, chrome, voice = model.cardVoice)
        Controls(model, flow, chrome, inputFocus, leave)
    }
}

/**
 * Kern's controls on the shared answer area — a field where the question is written out,
 * kern's four tiles where it is tapped ([DrillChoiceGrid]). A calendar name is prose: it is
 * set as prose, and a screen reader saying it needs no help.
 */
@Composable
private fun Controls(model: AppModel, flow: TypedDrill, chrome: Chrome, inputFocus: FocusRequester, onFinish: () -> Unit) {
    val controls = flow.progress.controls ?: return
    val lang = (controls.slot as? Slot.Typed)?.lang
    DrillAnswerArea(
        model, controls, flow.input, flow.awaitsConfirm, inputFocus,
        onType = flow::type, onEnter = flow::enter, onConfirm = flow::confirm, onStop = onFinish,
        speakCorrection = { form -> lang?.let { model.speakFormOnTap(form, it) } },
    ) { options, answer ->
        DrillChoiceGrid(
            options = options,
            answer = answer,
            chosen = flow.chosen,
            optionStyle = MaterialTheme.typography.titleMedium,
            chrome = chrome,
            onPick = flow::choose,
        )
    }
}

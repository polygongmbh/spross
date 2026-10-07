package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.Screen
import net.spross.app.TypedDrill
import net.spross.app.TypedDrillView
import net.spross.app.bookRecord
import net.spross.app.finishDrill
import net.spross.app.speakFormOnTap
import net.spross.app.stampRun
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
    QuestionFocus(run.index to paused, model.pronouncer, inputFocus.takeIf { run.prompt.choices == null })

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
        Controls(model, flow, run, chrome, inputFocus, leave)
    }
}

/**
 * The answer and the one primary action under it — a field where the question is written
 * out, kern's four tiles where it is tapped ([DrillChoiceGrid]).
 *
 * The placeholder names the language the answer is owed IN — which is the learner's own on
 * a reversed run, and the only place the direction is spelled out.
 */
@Composable
private fun Controls(
    model: AppModel,
    flow: TypedDrill,
    run: TypedDrillView,
    chrome: Chrome,
    inputFocus: FocusRequester,
    onFinish: () -> Unit,
) {
    val choices = run.prompt.choices
    val speakCorrection = { form: String -> model.speakFormOnTap(form, run.answerLanguage) }
    if (choices != null) {
        // The warm-up Sprosse: the answer is picked, not written, so the field stays away
        // entirely rather than standing unused under the grid — the grid IS the primary
        // action, and it waits on its own. A calendar name is prose — it is set as prose,
        // and a screen reader saying it needs no help.
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            DrillChoiceGrid(
                options = choices,
                answer = run.prompt.display,
                chosen = flow.chosen,
                optionStyle = MaterialTheme.typography.titleMedium,
                chrome = chrome,
                onPick = flow::choose,
            )
            AnswerVerdict(run.feedback, flow.awaitsConfirm, chrome, flow::confirm, speakCorrection)
            if (run.offersFinish) DrillStopOffer(chrome, onFinish)
        }
        return
    }
    TypedAnswerControls(
        input = flow.input,
        onType = flow::type,
        // why: naming the language is right only while the answer is words — a date owed in
        // digits is written the same way in either of them.
        placeholder = if (run.prompt.digits) {
            chrome.numbersAnswerPlaceholder
        } else {
            chrome.sessionAnswerPlaceholder.format(model.languageName(run.answerLanguage))
        },
        feedback = run.feedback,
        awaitsConfirm = flow.awaitsConfirm,
        chrome = chrome,
        focus = inputFocus,
        onPrimary = flow::primary,
        onEnter = flow::enter,
        onConfirm = flow::confirm,
        speakCorrection = speakCorrection,
        numberPad = run.prompt.numberPad,
    ) {
        if (run.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

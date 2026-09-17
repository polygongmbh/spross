package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.Chrome
import net.spross.app.bookRecord
import net.spross.app.Screen
import net.spross.app.TypedDrill
import net.spross.app.TypedDrillView
import net.spross.app.speakDrillAnswer
import net.spross.app.speakFormOnTap
import net.spross.kern.session.ToneKind
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
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    // why: unkeyed on anything the run does — a foreground that re-sweeps availability must
    // not restart the run underneath it.
    val flow = remember(reverse, fast) {
        page.open(
            { view.cueTone(it, model.cues) },
            // why: a pause that waits for a tap must not hold the keyboard — it covers the
            // very button the pause is waiting for.
            { focusManager.clearFocus() },
        )
    }
    if (flow == null) {
        // Nothing this pair can be asked — the chip gates on the same join, so this is a
        // closed door rather than a screen.
        LaunchedEffect(Unit) { model.finishDrill(page.back, null, "") }
        return
    }
    val run = flow.view(chrome)
    val store = model.trainer.store
    val key = page.key

    val leave = {
        val closed = flow.close(standingRecord = key?.let { store.record(it) } ?: 0)
        if (key != null) {
            // Neither buys a padlock (the drill is ungated); they are what the page reads
            // back — where the next run opens, and what Fast is priced against.
            store.bookSprosse(key, closed.bestLevel)
            store.bookCleared(NumbersMode.clearedKey(key, reverse), closed.clearedSprossen)
            closed.summary?.let {
                store.bookAnswers(key, it.done)
                model.bookRecord(key, it)
            }
        }
        model.finishDrill(page.back, closed.summary, page.drill)
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

    // The revealed answer is spoken like any other, once per question however the pause was
    // reached — after a beat, so the verdict cue is out of the way.
    //
    // Never on a REVERSED run: the side answered there is the learner's own language, and
    // every autoplay `read-aloud.md` describes says a target-language form. The speaker
    // beside the reveal still says it on request — a tap outranks the rule.
    var spoken by remember(run.index) { mutableStateOf(false) }
    LaunchedEffect(run.index, run.showsAnswer) {
        if (reverse || !run.showsAnswer || spoken) return@LaunchedEffect
        spoken = true
        delay(CHIME_CLEARANCE_MS)
        model.speakDrillAnswer(run.prompt.display, run.answerLanguage)
    }

    // The QUESTION is said instead on a REVERSED run, where the prompt IS the target-language
    // form and the answer above deliberately stays silent — so without this the whole task
    // would be unhearable. Saying it gives nothing away: the word is already on the card. No
    // beat in front of it, unlike the answer's: nothing has chimed and the question is awaited.
    LaunchedEffect(run.index) {
        if (!reverse) return@LaunchedEffect
        val text = run.prompt.text ?: return@LaunchedEffect
        // A picture is written in no language, so a question that is one has nothing to say.
        val language = run.prompt.language ?: return@LaunchedEffect
        model.speakDrillAnswer(text, language)
    }

    val inputFocus = remember { FocusRequester() }
    // A tapped question has no field to fill — a keyboard over the tiles would cover the
    // very answer it is waiting for.
    QuestionFocus(run.index, model.pronouncer, inputFocus.takeIf { run.prompt.choices == null })

    Column(
        modifier = Modifier.fillMaxSize().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        DrillTopBar(model, run.outcomes, run.tally, leave)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            DrillStreakLine(
                sprosse = chrome.trainerSprosse.format(run.level),
                streak = run.streak,
                bestStreak = run.bestStreak,
                chrome = chrome,
                announcesRecord = true,
            )
            // The tap speaker rides the same rule as the autoplay above: a prompt that is
            // a name, on the side being learned. A tap outranks the mute; this only says
            // whether there is anything to hear.
            Prompt(
                model, run, chrome,
                promptVoice = run.prompt.language?.let { language ->
                    run.prompt.text
                        ?.takeIf { reverse }
                        ?.let { model.speakFormOnTap(it, language) }
                },
            )
            Controls(model, flow, run, chrome, inputFocus, leave)
            Spacer(Modifier.height(Theme.spacing.sm))
        }
    }
}

@Composable
private fun Prompt(
    model: AppModel,
    run: TypedDrillView,
    chrome: Chrome,
    promptVoice: (() -> Unit)?,
) {
    val prompt = run.prompt
    CountryPromptCard(
        ask = prompt.ask,
        emoji = prompt.emoji,
        emojiIsGiveaway = prompt.emojiIsGiveaway,
        text = prompt.text,
        language = prompt.language,
        promptPronounce = promptVoice,
        // why: a clean answer flips in about a second — opening the card for a beat there
        // would read as a correction the learner did not earn.
        reveal = if (!run.showsAnswer) {
            null
        } else {
            CountryReveal(
                word = prompt.display,
                note = prompt.gloss,
                language = run.answerLanguage,
                pronounce = model.speakFormOnTap(prompt.display, run.answerLanguage),
            )
        },
        // The word this question's language adds, the first time it is asked for — the
        // numbers drill's first-sight hint, for a pattern instead of a length.
        hint = prompt.newWord?.let { chrome.datesNewWord.format(it) },
        chrome = chrome,
    )
    if (run.showsAnswer) {
        run.otherWord?.let { other ->
            // why: same line as the review session's — both explain what became of
            // the answer, so they read alike.
            PauseLine(chrome.sessionOtherWord.format(other.word, other.meanings.joinToString(", ")))
        }
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
        digits = run.prompt.digits,
    ) {
        if (run.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

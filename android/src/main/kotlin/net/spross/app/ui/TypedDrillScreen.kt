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
import net.spross.app.Screen
import net.spross.app.TypedDrill
import net.spross.app.TypedDrillView
import net.spross.app.speakDrillAnswer
import net.spross.app.speakFormOnTap
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * What tells one typed drill from the other, on a screen that is otherwise the same: where
 * the run goes back to, what its figures are filed under, and how it is opened.
 */
class TypedDrillPage(
    /** The page the run was started from, and the one its summary lands on. */
    val back: Screen,
    /** What the result tile on that page calls the exercise. */
    val skill: String,
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
        model.finishDrill(page.back, closed.summary, page.skill)
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

    // The field takes the keyboard back with every question — an amber hold gives it up so
    // the button it waits for is not covered, and the next prompt is typed into.
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(run.index) {
        if (model.pronouncer.readsScreenAloud) return@LaunchedEffect
        // why: a requester answers only once its node has been placed; one frame is what
        // that takes, and a request fired inside the same composition lands on nothing.
        withFrameNanos { }
        runCatching { inputFocus.requestFocus() }
    }

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
 * The field and the one primary action under it. The placeholder names the language the
 * answer is owed IN — which is the learner's own on a reversed run, and the only place the
 * direction is spelled out.
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
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        DrillAnswerField(
            value = flow.input,
            onValueChange = flow::type,
            placeholder = chrome.sessionAnswerPlaceholder.format(model.languageName(run.answerLanguage)),
            feedback = run.feedback,
            chrome = chrome,
            focus = inputFocus,
            onDone = { flow.enter() },
        )
        when (val feedback = run.feedback) {
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
            is TurnFeedback.Almost -> AlmostLine(model, flow, run, feedback.correctForm, chrome)
            // why: no "I knew it" in a drill — the questions are generated, so self-reporting
            // after seeing the answer proves nothing; revealed simply counts as a miss.
            TurnFeedback.Revealed -> ConfirmButton(chrome) { flow.confirm() }
        }
        // The way out, where it is wanted: under the button that goes on, on the second
        // miss in a row.
        if (run.offersFinish) DrillStopOffer(chrome, onFinish)
    }
}

/** A slip: the box spells the answer out, and the tap that ends the pause books it amber. */
@Composable
private fun AlmostLine(
    model: AppModel,
    flow: TypedDrill,
    run: TypedDrillView,
    form: String,
    chrome: Chrome,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        AlmostCorrection(
            chrome.sessionAlmostTypo,
            form,
            chrome,
            model.speakFormOnTap(form, run.answerLanguage),
        )
        ConfirmButton(chrome) { flow.confirm() }
    }
}

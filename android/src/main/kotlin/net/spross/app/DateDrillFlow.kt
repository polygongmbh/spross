package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.DateDrill
import net.spross.kern.trainer.DateDrillIntent
import net.spross.kern.trainer.DateDrillRun
import net.spross.kern.trainer.DateDrillRunConfig
import net.spross.kern.trainer.DateDrillRunState

/**
 * One dates run as this platform holds it — the fourth sibling of [TrainerFlow],
 * [LetterDrillFlow] and [CountryDrillFlow], over kern's own [DateDrillRun].
 *
 * Everything decidable is kern's: which question a Sprosse may ask, which date is drawn, what
 * a typed reading earns, how far one answer moves the ramp, which beat is armed. What is
 * left here is the text standing in the field and the beat itself.
 *
 * Stateless like all its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's calendars, not the learner's own words. The one
 * thing that outlives a run is the furthest Sprosse it stood on, which the page that started
 * it files ([TrainerStore.bookSprosse]).
 */
class DateDrillFlow(
    start: DateDrillRunState,
    private val rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : TypedDrill {
    private val beat = DrillBeat(screenReaderOn)
    private val acts = DrillActs(beat, onTone, onReleaseFocus, onSilence)

    var state by mutableStateOf(start)
        private set

    /** The learner's answer text — kern owns what it means, the field is ours. */
    override var input by mutableStateOf("")
        private set

    /**
     * Kern has run out of questions: the screen hands the run back rather than sitting on a
     * card it has already answered. False once the close has been made, whichever way the
     * screen went — a run is handed back once.
     */
    override val ranOut: Boolean get() = state.finished && !handedBack

    private var handedBack = false

    override val armedBeat get() = beat.tier

    override val beatToken get() = beat.token

    /** The beat became a tap: render the explicit "Weiter", which books the same answer. */
    override val awaitsConfirm get() = beat.awaitsConfirm

    /** The tile the warm-up question was answered off, cleared with the question itself. */
    override var chosen by mutableStateOf<String?>(null)
        private set

    /** A live keystroke: writing the reading out IS the answer, within kern's exact-only guard. */
    override fun type(text: String) {
        input = text
        dispatch(DateDrillIntent.InputChanged(text))
    }

    /**
     * A tapped tile: submitted as the text it carries, so kern grades it against the same
     * accepted set a written answer meets and nothing here decides what a tap is worth.
     */
    override fun choose(text: String) {
        chosen = text
        dispatch(DateDrillIntent.Submit(text))
    }

    /**
     * The ONE primary action: an empty field asks to see the answer, a typed one checks it.
     * Kern's Submit is inert on blank text, so which of the two a press is stays ours.
     */
    override fun primary() {
        if (input.isBlank()) dispatch(DateDrillIntent.Reveal) else dispatch(DateDrillIntent.Submit(input))
    }

    /** The tap that books whatever the feedback already said — and the beat's stand-in. */
    override fun confirm() = dispatch(DateDrillIntent.ConfirmPending)

    /** Enter: check while the answer is owed, otherwise book what stands. */
    override fun enter() {
        if (state.owesAnswer) primary() else confirm()
    }

    override fun advanceElapsed() {
        beat.spend()
        dispatch(DateDrillIntent.AdvanceElapsed)
    }

    /**
     * The run as the shared typed-drill screen reads it. A dates question carries no
     * picture, so the leading slot stays empty and the prompt — a name, or a dated line in
     * the prompt side's digits — stands where the country's name would.
     */
    override fun view(chrome: Chrome): TypedDrillView = TypedDrillView(
        index = state.index,
        level = state.level,
        streak = state.streak,
        bestStreak = state.bestStreak,
        outcomes = state.outcomes,
        tally = state.tally,
        feedback = state.feedback,
        showsAnswer = state.showsAnswer,
        offersFinish = state.offersFinish,
        otherWord = state.otherWord,
        answerLanguage = state.answerLanguage,
        prompt = TypedDrillPrompt(
            ask = chrome.dateAsk(state.task.kind),
            text = state.task.promptText,
            language = state.promptLanguage,
            display = state.task.display,
            choices = state.task.choices,
        ),
    )

    /**
     * Leaving, from the corner or from "Fertig". Kern books a pending answer exactly as the
     * tap would, then says what the page owes its store.
     */
    override fun close(standingRecord: Int): TypedDrillClose {
        handedBack = true
        val closed = DateDrillRun.close(state, standingRecord)
        state = closed.state
        input = ""
        acts.carryOut(closed.effects)
        return TypedDrillClose(closed.summary, closed.bestLevel)
    }

    private fun dispatch(intent: DateDrillIntent) {
        val reduction = DateDrillRun.reduce(state, intent, rng)
        // why: cleared in the SAME transaction as the question — the next card must never
        // render one frame carrying the last one's answer.
        if (reduction.state.index != state.index) {
            input = ""
            chosen = null
        }
        state = reduction.state
        acts.carryOut(reduction.effects)
    }
}

/**
 * The run the dates page opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one, built for the language the answer is owed in —
 * which is the learner's OWN language on a reversed run, so the direction is settled here
 * rather than assumed to be the target.
 */
fun AppModel.newDateDrill(
    reverse: Boolean,
    fast: Boolean,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): DateDrillFlow? {
    val content = dates ?: return null
    val info = catalog?.languages?.get(DateDrill.answerLanguage(content, reverse)) ?: return null
    val config = DateDrillRunConfig(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = AnswerNormalizer.drill(info),
    )
    return DateDrillFlow(
        start = DateDrillRun.open(config, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.CountryDrill
import net.spross.kern.trainer.CountryDrillClose
import net.spross.kern.trainer.CountryDrillIntent
import net.spross.kern.trainer.CountryDrillRun
import net.spross.kern.trainer.CountryDrillRunConfig
import net.spross.kern.trainer.CountryDrillRunState

/**
 * One atlas run as this platform holds it — the third sibling of [TrainerFlow] and
 * [LetterDrillFlow], over kern's own [CountryDrillRun].
 *
 * Everything decidable is kern's: which question a rung may ask, which row is drawn, what a
 * typed name earns, how far one answer moves the ramp, which beat is armed. What is left
 * here is the text standing in the field and the beat itself.
 *
 * Stateless like both its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's atlas, not the learner's own words. The one thing
 * that outlives a run is the furthest rung it stood on, which the page that started it
 * files ([TrainerStore.bookRung]).
 */
class CountryDrillFlow(
    start: CountryDrillRunState,
    private val rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) {
    private val beat = DrillBeat(screenReaderOn)
    private val acts = DrillActs(beat, onTone, onReleaseFocus, onSilence)

    var state by mutableStateOf(start)
        private set

    /** The learner's answer text — kern owns what it means, the field is ours. */
    var input by mutableStateOf("")
        private set

    /**
     * Kern has run out of questions: the screen hands the run back rather than sitting on a
     * card it has already answered. False once the close has been made, whichever way the
     * screen went — a run is handed back once.
     */
    val ranOut: Boolean get() = state.finished && !handedBack

    private var handedBack = false

    val armedBeat get() = beat.tier

    val beatToken get() = beat.token

    /** The beat became a tap: render the explicit "Weiter", which books the same answer. */
    val awaitsConfirm get() = beat.awaitsConfirm

    /** A live keystroke: writing the name out IS the answer, within kern's exact-only guard. */
    fun type(text: String) {
        input = text
        dispatch(CountryDrillIntent.InputChanged(text))
    }

    /**
     * The ONE primary action: an empty field asks to see the answer, a typed one checks it.
     * Kern's Submit is inert on blank text, so which of the two a press is stays ours.
     */
    fun primary() {
        if (input.isBlank()) dispatch(CountryDrillIntent.Reveal) else dispatch(CountryDrillIntent.Submit(input))
    }

    /** The tap that books whatever the feedback already said — and the beat's stand-in. */
    fun confirm() = dispatch(CountryDrillIntent.ConfirmPending)

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter() {
        if (state.owesAnswer) primary() else confirm()
    }

    fun advanceElapsed() {
        beat.spend()
        dispatch(CountryDrillIntent.AdvanceElapsed)
    }

    /**
     * Leaving, from the corner or from "Fertig". Kern books a pending answer exactly as the
     * tap would, then says what the page owes its store.
     */
    fun close(standingRecord: Int): CountryDrillClose {
        handedBack = true
        val closed = CountryDrillRun.close(state, standingRecord)
        state = closed.state
        input = ""
        acts.carryOut(closed.effects)
        return closed
    }

    private fun dispatch(intent: CountryDrillIntent) {
        val reduction = CountryDrillRun.reduce(state, intent, rng)
        // why: cleared in the SAME transaction as the question — the next card must never
        // render one frame carrying the last one's answer.
        if (reduction.state.index != state.index) input = ""
        state = reduction.state
        acts.carryOut(reduction.effects)
    }
}

/**
 * The run the atlas page opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one, built for the language the answer is owed in —
 * which is the learner's OWN language on a reversed run, so the direction is settled here
 * rather than assumed to be the target.
 */
fun AppModel.newCountryDrill(
    reverse: Boolean,
    fast: Boolean,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): CountryDrillFlow? {
    val content = atlas ?: return null
    val info = catalog?.languages?.get(CountryDrill.answerLanguage(content, reverse)) ?: return null
    val config = CountryDrillRunConfig(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = AnswerNormalizer.drill(info),
    )
    return CountryDrillFlow(
        start = CountryDrillRun.open(config, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.TrainerClose
import net.spross.kern.trainer.TrainerIntent
import net.spross.kern.trainer.TrainerMode
import net.spross.kern.trainer.TrainerRun
import net.spross.kern.trainer.TrainerRunState

/**
 * One slot run as this platform holds it.
 *
 * Every rule is kern's [TrainerRun] — what an answer is worth, which rung it moves, when
 * the way out is offered, what a look-up costs. What is left here is the platform's half:
 * the text standing in the field, the beat that is armed, whether the reference table is
 * raised. The screen reads this and hands taps back; it decides nothing.
 *
 * The same shape as [TurnFlow] and [LetterDrillFlow], and for the same reason: a run kept
 * out of the composition is a run a test can drive without a device.
 */
class TrainerFlow(
    start: TrainerRunState,
    /** Kern's grader for the drilled language; null (previews) grades plainly. */
    private val normalizer: AnswerNormalizer?,
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
     * The reference table, raised over the run by "?". Not kern's: the look-up's COST is
     * ([TrainerIntent.LookUp] books the amber debt), the panel over the run is chrome.
     */
    var showingReference by mutableStateOf(false)

    val mode: TrainerMode get() = state.mode

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

    /** A live keystroke: finishing the word IS the answer, within kern's growing guard. */
    fun type(text: String) {
        input = text
        dispatch(TrainerIntent.InputChanged(text))
    }

    /**
     * The ONE primary action: an empty field asks to see the answer, a typed one checks it.
     * Kern's Submit is inert on blank text, so which of the two a press is stays ours.
     */
    fun primary() {
        if (input.isBlank()) dispatch(TrainerIntent.Reveal) else dispatch(TrainerIntent.Submit(input))
    }

    /** The tap that books whatever the feedback already said. */
    fun confirm() = dispatch(TrainerIntent.ConfirmPending)

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter() {
        if (state.owesAnswer) dispatch(TrainerIntent.Submit(input)) else confirm()
    }

    fun advanceElapsed() {
        beat.spend()
        dispatch(TrainerIntent.AdvanceElapsed)
    }

    /** The "?": a look-up while the answer is still owed costs the rung — kern books it. */
    fun lookUp() {
        showingReference = true
        dispatch(TrainerIntent.LookUp)
    }

    /**
     * Leaving. Kern books whatever is pending exactly as the explicit tap would and says
     * what the platform owes its stores; the caller writes them and shows the summary.
     */
    fun close(standingRecord: Int, standingProgress: Map<String, Int>): TrainerClose {
        handedBack = true
        val closed = TrainerRun.close(state, standingRecord, standingProgress)
        state = closed.state
        input = ""
        acts.carryOut(closed.effects)
        return closed
    }

    private fun dispatch(intent: TrainerIntent) {
        val reduction = TrainerRun.reduce(state, intent, normalizer, rng)
        // why: the field is cleared in the SAME transaction as the question — the next
        // prompt must never render one frame carrying the last one's answer.
        if (reduction.state.index != state.index) input = ""
        state = reduction.state
        acts.carryOut(reduction.effects)
    }
}

/**
 * The run a mode opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one — no article leniency, one slip per word, nothing
 * forgiven inside a digit — built for the language being answered in.
 */
fun AppModel.newTrainerRun(
    mode: TrainerMode,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): TrainerFlow? {
    val info = catalog?.languages?.get(mode.language) ?: return null
    return TrainerFlow(
        start = TrainerRun.open(mode, rng),
        normalizer = AnswerNormalizer.drill(info),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

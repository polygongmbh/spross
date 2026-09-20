package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.DrillEffect
import net.spross.kern.trainer.DrillRunProgress

/**
 * A run as the shell around it reads one: whether kern has anything left to ask, and the
 * beat it armed before the next question. Every drill screen stands on these four and on
 * nothing else of the run behind it, which is why one shell serves all of them.
 */
interface DrillRun {

    /** Kern has run out of questions: the screen hands the run back, once. */
    val ranOut: Boolean

    /** The beat waiting to elapse, or null where none is armed. */
    val armedBeat: AdvanceTier?

    /** Bumped by every arming — what a timer effect keys on. */
    val beatToken: Int

    /** The beat became a tap: render the explicit "Weiter", which books the same answer. */
    val awaitsConfirm: Boolean

    fun advanceElapsed()
}

/**
 * The driver every drill run stands on: one event put to kern, its next state back, the
 * field cleared in the same transaction as the question, and the effects carried out.
 *
 * The drills ask the same way — a card, a field or a bank of tiles, ONE primary action, an
 * amber hold, a ✕ — so they are this driver with the members below and not five cuts of it
 * (`docs/design.md` § Review UX rules). What actually differs is the machine underneath:
 * kern keeps a heard glyph, a typed numeral, a mixed-up spelling and a shuffled phrase apart
 * on purpose, so each drill hands over its own run state and its own intent vocabulary, and
 * each files a close in stores of its own. Those are the two type parameters and the handful
 * of members under them; nothing else varies.
 *
 * Nothing here decides a rule. Which branch waits, what an answer is worth and when a run is
 * over are kern's, read off [DrillStep] and [DrillEffect]; what this owns is the text in the
 * field, the armed beat, and the acts an effect asks of the device.
 */
abstract class DrillFlow<S, I>(
    start: S,
    private val rng: Random,
    onTone: (ToneKind) -> Unit,
    onReleaseFocus: () -> Unit,
    onSilence: () -> Unit,
    screenReaderOn: () -> Boolean,
) : DrillRun {
    private val beat = DrillBeat(screenReaderOn)
    private val acts = DrillActs(beat, onTone, onReleaseFocus, onSilence)

    /** Kern's whole run state, replaced whole by every reduction. */
    var state by mutableStateOf(start)
        private set

    /**
     * The learner's answer text — the one thing kern deliberately does not hold, and so the
     * one thing this has to clear itself. Empty the whole way through a drill whose answer
     * is an arrangement rather than something spelled.
     */
    var input by mutableStateOf("")
        private set

    /**
     * Kern has run out of questions: the screen hands the run back rather than sitting on a
     * card it has already answered. False once the close has been made, whichever way the
     * screen went — a run is handed back once.
     */
    override val ranOut: Boolean get() = finished(state) && !handedBack

    private var handedBack = false

    override val armedBeat get() = beat.tier

    override val beatToken get() = beat.token

    override val awaitsConfirm get() = beat.awaitsConfirm

    /**
     * A live keystroke: finishing the word IS the answer, within kern's growing guard —
     * where the drill grades one at all ([inputChanged]).
     */
    fun type(text: String) {
        input = text
        inputChanged(text)?.let(::dispatch)
    }

    /**
     * The ONE primary action, button and Enter alike: kern checks what stands in the field,
     * and reveals the answer when nothing does.
     */
    fun primary() {
        submit(input)?.let(::dispatch)
    }

    /** The tap that books whatever the feedback already said — and the beat's stand-in. */
    fun confirm() = dispatch(confirmPending())

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter() {
        if (owesAnswer(state)) primary() else confirm()
    }

    override fun advanceElapsed() {
        beat.spend()
        dispatch(advanceElapsedIntent())
    }

    protected fun dispatch(intent: I) {
        val reduction = reduce(state, intent, rng)
        // why: the field is ours, so kern cannot clear it — and the text has to go in the
        // SAME transaction as the question, or the next prompt renders one frame carrying
        // the last one's answer.
        if (index(reduction.state) != index(state)) {
            input = ""
            onQuestionChanged()
        }
        state = reduction.state
        acts.carryOut(reduction.effects)
    }

    /**
     * The way out every run takes, whatever kern's own close hands the page back: the run is
     * handed over once, the field goes with the question it was owed on, and what kern asked
     * of the device on the way out is carried out.
     */
    protected fun land(ended: S, effects: List<DrillEffect>) {
        handedBack = true
        state = ended
        input = ""
        acts.carryOut(effects)
    }

    /** What a question leaves behind besides the typed text — nothing, unless tiles are asked off. */
    protected open fun onQuestionChanged() {}

    /** One turn of kern's reducer, in this run's own types. */
    protected abstract fun reduce(state: S, intent: I, rng: Random): DrillStep<S>

    /**
     * Which question the run stands on — the card's identity, and what says the run moved on.
     * Read off the state itself wherever kern already says so ([ProgressDrillFlow]).
     */
    protected abstract fun index(state: S): Int

    /** Nothing left to ask. */
    protected abstract fun finished(state: S): Boolean

    protected abstract fun owesAnswer(state: S): Boolean

    protected abstract fun confirmPending(): I

    protected abstract fun advanceElapsedIntent(): I

    /**
     * A live keystroke, in this drill's words. Null where a keystroke means nothing until it
     * is submitted — the letters ladder grades whole answers.
     */
    protected open fun inputChanged(text: String): I? = null

    /**
     * Check and Enter alike. Null where there is no field to check: placing the last atom IS
     * the answer on the drill whose question is an order rather than a spelling.
     */
    protected open fun submit(text: String): I? = null
}

/**
 * The flow over a run that already says where it stands: the slot drill, the letters ladder
 * and the two scrambles, whose kern states all answer [DrillRunProgress].
 *
 * Nothing is added to [DrillFlow] but the reading — where kern names the figures itself, the
 * shell takes them off the state instead of asking each drill to spell the same three out.
 * The typed drills ([TypedDrillFlow]) keep their own, since a country and a date are laddered
 * by rules that share no state type.
 */
abstract class ProgressDrillFlow<S : DrillRunProgress, I>(
    start: S,
    rng: Random,
    onTone: (ToneKind) -> Unit,
    onReleaseFocus: () -> Unit,
    onSilence: () -> Unit,
    screenReaderOn: () -> Boolean,
) : DrillFlow<S, I>(start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn) {

    final override fun index(state: S) = state.index

    final override fun finished(state: S) = state.finished

    final override fun owesAnswer(state: S) = state.owesAnswer
}

/** A reduction as the shared flow reads it: the run as it now stands, and what it asks of the world. */
data class DrillStep<S>(val state: S, val effects: List<DrillEffect>)

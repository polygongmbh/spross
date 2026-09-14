package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.WordScrambleAvailability
import net.spross.kern.trainer.WordScrambleClose
import net.spross.kern.trainer.WordScrambleIntent
import net.spross.kern.trainer.WordScrambleRun
import net.spross.kern.trainer.WordScrambleRunConfig
import net.spross.kern.trainer.WordScrambleRunState

/**
 * One word-scramble run as this platform holds it — the twin of [LetterDrillFlow], over
 * kern's own [WordScrambleRun].
 *
 * Everything decidable is kern's: which word is drawn, how much of its spelling the Sprosse
 * leaves standing, what a typed answer earns. What is left here is the field's text and the
 * armed beat.
 *
 * No review is ever booked: the box is READ for the words it has consolidated and never
 * written, and the run keeps no record — spelling a word back out of its own letters is not
 * the recall the schedule measures.
 */
class WordScrambleFlow(
    start: WordScrambleRunState,
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

    /** The learner's text; kern holds every rule that decides what it means. */
    var input by mutableStateOf("")
        private set

    /**
     * Kern has run out of words: the screen hands the run back rather than sitting on a card
     * it has already answered. False once the close has been made, whichever way the screen
     * went — a run is handed back once.
     */
    val ranOut: Boolean get() = state.finished && !handedBack

    private var handedBack = false

    val armedBeat get() = beat.tier

    val beatToken get() = beat.token

    val awaitsConfirm get() = beat.awaitsConfirm

    /**
     * "Finishing the word IS the answer" — every keystroke is offered to kern, which decides
     * whether it approves, withdraws an approval, or ignores it.
     */
    fun type(text: String) {
        input = text
        dispatch(WordScrambleIntent.InputChanged(text))
    }

    /**
     * The ONE primary action, button and Enter alike: kern checks what stands in the field,
     * and reveals the spelling when nothing does.
     */
    fun primary() = dispatch(WordScrambleIntent.Submit(input))

    fun confirm() = dispatch(WordScrambleIntent.ConfirmPending)

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter() {
        if (state.owesAnswer) primary() else confirm()
    }

    fun advanceElapsed() {
        beat.spend()
        dispatch(WordScrambleIntent.AdvanceElapsed)
    }

    /** Leaving: kern books a pending answer exactly as the tap would, then reports. */
    fun close(): WordScrambleClose {
        handedBack = true
        val closed = WordScrambleRun.close(state)
        state = closed.state
        input = ""
        acts.carryOut(closed.effects)
        return closed
    }

    private fun dispatch(intent: WordScrambleIntent) {
        val reduction = WordScrambleRun.reduce(state, intent, rng)
        // why: cleared with the question itself — the next one must never render a frame
        // carrying the last one's answer.
        if (reduction.state.index != state.index) input = ""
        state = reduction.state
        acts.carryOut(reduction.effects)
    }
}

/**
 * A run, or null where this box holds too few grown words to mix — the chip gates on the
 * same report, so a null here is a closed door rather than a screen.
 *
 * The normalizer is the STRICT drill one for the language being learned, which is the side
 * the spelling is owed on. A profile whose catalog names no such language grades plainly.
 */
fun AppModel.newWordScramble(
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): WordScrambleFlow? {
    val state = box ?: return null
    val report = WordScrambleAvailability.report(state)
    if (!report.drillAvailable) return null
    val info = catalog?.languages?.get(state.joinStamp.target)
    val config = WordScrambleRunConfig(report, info?.let { AnswerNormalizer.drill(it) })
    return WordScrambleFlow(
        start = WordScrambleRun.open(config, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

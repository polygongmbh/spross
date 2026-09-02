package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.LetterDrillClose
import net.spross.kern.trainer.LetterDrillIntent
import net.spross.kern.trainer.LetterDrillRun
import net.spross.kern.trainer.LetterDrillRunConfig
import net.spross.kern.trainer.LetterDrillRunState

/**
 * One letter run as this platform holds it — the twin of [TrainerFlow], over kern's own
 * [LetterDrillRun].
 *
 * Everything decidable is kern's: the ladder, the draw, the ramp step, the three-way
 * verdict a dictated word can earn. What is left here is the field's text and the armed
 * beat. No review is ever booked (D12): the box is READ, for the pacing figures and the
 * dictation pool, and never written.
 */
class LetterDrillFlow(
    start: LetterDrillRunState,
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

    val awaitsConfirm get() = beat.awaitsConfirm

    fun type(text: String) {
        input = text
    }

    /** One attempt per tile — a second tap after the answer is in would be a retry. */
    fun choose(glyph: String) = dispatch(LetterDrillIntent.Choose(glyph))

    /** An empty field reveals (and books a miss), a filled one checks. */
    fun primary() {
        if (input.isBlank()) dispatch(LetterDrillIntent.Reveal) else dispatch(LetterDrillIntent.Submit(input))
    }

    fun confirm() = dispatch(LetterDrillIntent.ConfirmPending)

    fun enter() {
        if (state.owesAnswer) primary() else confirm()
    }

    fun advanceElapsed() {
        beat.spend()
        dispatch(LetterDrillIntent.AdvanceElapsed)
    }

    /** Leaving: kern books a pending answer exactly as the tap would, then reports. */
    fun close(): LetterDrillClose {
        handedBack = true
        val closed = LetterDrillRun.close(state)
        state = closed.state
        input = ""
        acts.carryOut(closed.effects)
        return closed
    }

    private fun dispatch(intent: LetterDrillIntent) {
        val reduction = LetterDrillRun.reduce(state, intent, rng)
        // why: cleared with the question itself — the next one must never render a frame
        // carrying the last one's answer.
        if (reduction.state.index != state.index) input = ""
        state = reduction.state
        acts.carryOut(reduction.effects)
    }
}

/**
 * A run, or null where this device can ask nothing at all — the overview's start button
 * gates on the same predicate, so a null here is a closed door rather than a screen.
 */
fun AppModel.newLetterDrill(
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): LetterDrillFlow? {
    val report = trainer.letters ?: return null
    if (!report.drillAvailable) return null
    val state = box ?: return null
    val info = catalog?.languages?.get(report.language) ?: return null
    val config = LetterDrillRunConfig(
        report = report,
        cards = state.cards,
        // why: the STRICT drill normalizer (no article leniency, a slip per word) with the
        // whole join in view — a per-word budget alone accepts `kufungua` for `kufunga`,
        // and only the catalog-wide grader withdraws that credit.
        dictationGrader = CatalogAnswerGrader(
            AnswerNormalizer.drill(info),
            state.cards.values.toList(),
        ),
    )
    return LetterDrillFlow(
        start = LetterDrillRun.open(config, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

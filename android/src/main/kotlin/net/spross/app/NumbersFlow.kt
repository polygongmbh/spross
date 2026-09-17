package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.NumbersClose
import net.spross.kern.trainer.NumbersIntent
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.NumbersRun
import net.spross.kern.trainer.NumbersRunState

/**
 * One slot run as this platform holds it.
 *
 * Every rule is kern's [NumbersRun] — what an answer is worth, which Sprosse it moves, when
 * the way out is offered, what a look-up costs. What is left here is the platform's half,
 * which is [DrillFlow]'s: the text standing in the field and the beat that is armed. What
 * this drill adds to it is the reference table raised over the run.
 *
 * Kept out of the composition like [TurnFlow], and for the same reason: a run a test can
 * drive without a device.
 */
class NumbersFlow(
    start: NumbersRunState,
    /** Kern's grader for the drilled language; null (previews) grades plainly. */
    private val normalizer: AnswerNormalizer?,
    rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : DrillFlow<NumbersRunState, NumbersIntent>(
    start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn,
) {
    /**
     * The reference table, raised over the run by "?". Not kern's: the look-up's COST is
     * ([NumbersIntent.LookUp] books the amber debt), the panel over the run is chrome.
     */
    var showingReference by mutableStateOf(false)

    val mode: NumbersMode get() = state.mode

    /** The "?": a look-up while the answer is still owed costs the Sprosse — kern books it. */
    fun lookUp() {
        showingReference = true
        dispatch(NumbersIntent.LookUp)
    }

    /**
     * Leaving. Kern books whatever is pending exactly as the explicit tap would and says
     * what the platform owes its stores; the caller writes them and shows the summary.
     */
    fun close(standingRecord: Int, standingProgress: Map<String, Int>): NumbersClose =
        NumbersRun.close(state, standingRecord, standingProgress).also { land(it.state, it.effects) }

    override fun reduce(state: NumbersRunState, intent: NumbersIntent, rng: Random) =
        NumbersRun.reduce(state, intent, normalizer, rng).let { DrillStep(it.state, it.effects) }

    override fun index(state: NumbersRunState) = state.index

    override fun finished(state: NumbersRunState) = state.finished

    override fun owesAnswer(state: NumbersRunState) = state.owesAnswer

    override fun inputChanged(text: String) = NumbersIntent.InputChanged(text)

    override fun submit(text: String) = NumbersIntent.Submit(text)

    override fun confirmPending() = NumbersIntent.ConfirmPending

    override fun advanceElapsedIntent() = NumbersIntent.AdvanceElapsed
}

/**
 * The run a mode opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one — no article leniency, one slip per word, nothing
 * forgiven inside a digit — built for the language being answered in.
 */
fun AppModel.newTrainerRun(
    mode: NumbersMode,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): NumbersFlow? {
    val info = catalog?.languages?.get(mode.language) ?: return null
    return NumbersFlow(
        start = NumbersRun.open(mode, rng),
        normalizer = AnswerNormalizer.drill(info),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

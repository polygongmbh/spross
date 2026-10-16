package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.DrillBookings
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.PairDrillIntent
import net.spross.kern.trainer.PairDrillRun
import net.spross.kern.trainer.PairDrillRunState
import net.spross.kern.trainer.PairDrillTask

/**
 * A TYPED endless drill — the atlas and the calendar — as its screen sees it.
 *
 * Kern runs both on one reducer ([PairDrillRun]), each drawing, grading and laddering by
 * rules of its own. What the SCREEN does with either is one thing — a card, a field, one
 * primary action, a beat, a way out — so the run reaches it through this face.
 *
 * Nothing here decides anything: the run's figures are kern's [DrillRun.progress], read fresh
 * every composition, and the rest is the same handful of taps kern already names.
 */
interface TypedDrill : DrillRun {

    /** The learner's answer text — kern owns what it means, the field is ours. */
    val input: String

    /**
     * The tile this question was answered off, or null while it is still owed — what the
     * grid marks ✓ and ✗ with once a tap has landed. Null the whole way up a drill whose
     * questions are all written.
     */
    val chosen: String?

    /** The Sprosse the run stands on — the one figure a typed run adds to [DrillRun.progress]. */
    val sprosse: Int

    /** A live keystroke: writing the answer out IS the answer, within kern's exact-only guard. */
    fun type(text: String)

    /**
     * An answer arriving whole rather than a letter at a time — a tapped tile, which the
     * screen offers only where kern's controls ask with tiles.
     */
    fun choose(text: String)

    /** The ONE primary action: an empty field asks to see the answer, a typed one checks it. */
    fun primary()

    /** The tap that books whatever the feedback already said — and the beat's stand-in. */
    fun confirm()

    /** Enter: check while the answer is owed, otherwise book what stands. */
    fun enter()

    /** Leaving, from the corner or from "Fertig" — a pending answer books as the tap would. */
    fun close(standingRecord: Int): TypedDrillClose
}

/** What a closed typed run owes the page that started it: its figures, and what it files. */
data class TypedDrillClose(
    /** null ⇒ the run was never answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    val bookings: DrillBookings,
)

/**
 * The platform half of a typed endless drill — the atlas and the calendar — over kern's one
 * pair drill run ([PairDrillRun]), which [run] names the drill of.
 *
 * Everything decidable is kern's: which question a Sprosse may ask, which row is drawn, what a
 * typed answer earns, how far one answer moves the ramp, which beat is armed. The run itself is
 * [DrillFlow], which every drill stands on; what this adds is the face the shared screen reads —
 * [sprosse], [chosen], and a close that hands back the figures the atlas and the calendar both file.
 *
 * Stateless like every drill: no review is ever booked and the box is never read at all — the
 * material is the catalog's, not the learner's own words.
 */
class TypedDrillFlow<T : PairDrillTask>(
    start: PairDrillRunState<T>,
    private val run: PairDrillRun<T>,
    rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : DrillFlow<PairDrillRunState<T>, PairDrillIntent>(start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn),
    TypedDrill {

    /** The tile the question was answered off, cleared with the question itself; null on a written one. */
    override var chosen by mutableStateOf<String?>(null)
        private set

    /**
     * A tapped tile: submitted as the text it carries, so kern grades it against the same
     * accepted set a written answer meets and nothing here decides what a tap is worth.
     */
    override fun choose(text: String) {
        chosen = text
        dispatch(submit(text))
    }

    override fun onQuestionChanged() {
        chosen = null
    }

    /**
     * Leaving, from the corner or from "Fertig". Kern books a pending answer exactly as the
     * tap would, then says what the page owes its store.
     */
    override fun close(standingRecord: Int): TypedDrillClose {
        val closed = run.close(state, standingRecord)
        land(closed.state, closed.effects)
        return TypedDrillClose(closed.summary, closed.bookings())
    }

    override val sprosse: Int get() = state.sprosse

    override fun reduce(state: PairDrillRunState<T>, intent: PairDrillIntent, rng: Random) =
        run.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun inputChanged(text: String): PairDrillIntent = PairDrillIntent.InputChanged(text)

    override fun submit(text: String): PairDrillIntent = PairDrillIntent.Submit(text)

    override fun confirmPending() = PairDrillIntent.ConfirmPending

    override fun advanceElapsedIntent() = PairDrillIntent.AdvanceElapsed

    override fun keepPracticingIntent() = PairDrillIntent.KeepPracticing
}

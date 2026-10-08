package net.spross.app

import kotlin.random.Random
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Question
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.DrillBookings
import net.spross.kern.trainer.DrillEffect
import net.spross.kern.trainer.DrillRunProgress
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.DrillTally

/**
 * A TYPED endless drill — the atlas and the calendar — as its screen sees it.
 *
 * Their kern runs share no type: a country and a date are drawn, graded and laddered by
 * rules of their own, and kern keeps them apart. What the SCREEN does with either is one
 * thing — a card, a field, one primary action, a beat, a way out — so the run reaches it
 * through this face instead of through two screens written twice.
 *
 * Nothing here decides anything: [view] is the run as it stands right now, read fresh every
 * composition, and the rest is the same handful of taps kern already names.
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

    /** The question and the figures as they stand. */
    fun view(): TypedDrillView

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

/** One typed run as it stands: the question, the ladder under it, and the score line. */
data class TypedDrillView(
    /** Bumped per question — what the card's identity and an autoplay effect key on. */
    val index: Int,
    val sprosse: Int,
    val answerStreak: Int,
    val bestAnswerStreak: Int,
    val outcomes: List<AnswerOutcome>,
    val tally: DrillTally,
    /** What the card shows. */
    val question: Question,
)

/** What a closed typed run owes the page that started it: its figures, and what it files. */
data class TypedDrillClose(
    /** null ⇒ the run was never answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    val bookings: DrillBookings,
)

/**
 * The platform half of a typed endless drill — the atlas and the calendar — over whichever
 * run kern keeps it in.
 *
 * Their kern runs share no type on purpose: a country and a date are drawn, graded and
 * laddered by rules of their own. The run itself is [DrillFlow], which every drill stands
 * on; what this adds is the face the shared screen reads — [view], [chosen], and a close
 * that hands back the figures the atlas and the calendar both file.
 */
abstract class TypedDrillFlow<S : DrillRunProgress, I>(
    start: S,
    rng: Random,
    onTone: (ToneKind) -> Unit,
    onReleaseFocus: () -> Unit,
    onSilence: () -> Unit,
    screenReaderOn: () -> Boolean,
) : DrillFlow<S, I>(start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn), TypedDrill {

    /** A run whose questions are all written was never answered off a tile. */
    override val chosen: String? get() = null

    /**
     * A tapped tile: submitted as the text it carries, so kern grades it against the same
     * accepted set a written answer meets and nothing here decides what a tap is worth.
     */
    override fun choose(text: String) {
        submit(text)?.let(::dispatch)
    }

    /**
     * Leaving, from the corner or from "Fertig". Kern books a pending answer exactly as the
     * tap would, then says what the page owes its store.
     */
    override fun close(standingRecord: Int): TypedDrillClose {
        val closed = closeRun(state, standingRecord)
        land(closed.state, closed.effects)
        return closed.close
    }

    /** Kern's close, in this run's own types. */
    protected abstract fun closeRun(state: S, standingRecord: Int): DrillEnd<S>

    abstract override fun inputChanged(text: String): I

    abstract override fun submit(text: String): I
}

/** A close as the shared flow reads it: the ended run, its effects, and what the page owes its store. */
data class DrillEnd<S>(val state: S, val effects: List<DrillEffect>, val close: TypedDrillClose)

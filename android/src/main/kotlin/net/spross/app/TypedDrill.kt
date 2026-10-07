package net.spross.app

import kotlin.random.Random
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Question
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
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
     * screen offers only where the question came with [TypedDrillPrompt.choices].
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

/** What the answer controls read off the question; the card itself draws kern's [Question]. */
data class TypedDrillPrompt(
    /** The canonical answer — the tile the grid marks right. */
    val display: String,
    /**
     * The tiles this question is answered off, in kern's own shuffled order — null where it
     * is written instead, which is every Sprosse above the calendar's warm-up.
     */
    val choices: List<String>? = null,
    /**
     * Whether a DATE is owed rather than a reading — the calendar turned round. The keyboard
     * and the placeholder are the only things that follow from it.
     */
    val digits: Boolean = false,
    /** Whether the owed date can be written on the number pad — see [typableOnNumberPad]. */
    val numberPad: Boolean = false,
)

/**
 * Whether one of [accepted] needs nothing a number pad lacks. The pad carries digits,
 * `.`, `,`, `-` and a space — no `:` and no `/`, so a time or a fraction written that
 * way would be owed on a keyboard that cannot type it.
 */
fun typableOnNumberPad(accepted: List<String>): Boolean =
    accepted.any { form -> form.all { it.isDigit() || it in ".,- \u202F\u00A0" } }

/** One typed run as it stands: the question, the ladder under it, and the score line. */
data class TypedDrillView(
    /** Bumped per question — what the card's identity and an autoplay effect key on. */
    val index: Int,
    val sprosse: Int,
    val answerStreak: Int,
    val bestAnswerStreak: Int,
    val outcomes: List<AnswerOutcome>,
    val tally: DrillTally,
    val feedback: TurnFeedback,
    /** The way out, offered under the button that goes on, on the second miss in a row. */
    val offersFinish: Boolean,
    /** The language an answer is owed in — the learner's own on a reversed run. */
    val answerLanguage: Language,
    val prompt: TypedDrillPrompt,
    /** What the card shows. */
    val question: Question,
)

/** What a closed typed run owes the page that started it. */
data class TypedDrillClose(
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /** The Sprosse the run REACHED, not the one it ends on. */
    val bestSprosse: Int,
    /** The Sprossen the run answered OUT, for the page to add to what it holds. */
    val clearedSprossen: Set<Int>,
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

package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * What the PAIR drills share — the atlas ([CountryDrill]) and the calendar ([DateDrill]):
 * a ladder over material joined from both of a profile's languages, typed in either direction.
 *
 * Both price a Sprosse and the Fast modifier alike and both turn round the same way,
 * so the rules are written here once rather than once per drill.
 * Their run is [PairDrillRun]; what each asks, draws and grades stays its own.
 */
object PairDrill {

    /** Three clean wins a Sprosse ([DrillRamp.USUAL_WINS]): more Sprossen, and more rows standing on each of them. */
    const val WINS_TO_ADVANCE = DrillRamp.USUAL_WINS

    /**
     * How LONG a Sprosse is. Fast spends one clean win instead of the three, and is the reward
     * for having topped the ladder the hard way ([fastUnlocked]) — the numbers drill's rule
     * ([Numbers.winsToAdvance]), read off this ladder's own pacing.
     */
    fun winsToAdvance(fast: Boolean): Int = if (fast) 1 else WINS_TO_ADVANCE

    /**
     * The Sprosse that earns Fast, as the locked switch prices it: the ladder's [top] one —
     * a fixed nine for the atlas, whatever the pair's calendar carries for the dates.
     */
    fun fastPrice(top: Int): Int = top

    /**
     * Whether the Fast modifier is on offer at all. Having EVER stood on the top Sprosse is the
     * price — [bestSprosse] is the highest Sprosse any run reached, which is what the app keeps.
     */
    fun fastUnlocked(bestSprosse: Int, top: Int): Boolean = bestSprosse >= fastPrice(top)

    /**
     * The language an answer is owed in — the learned one, or the learner's own where the
     * run is turned round. Asked of the pair rather than of a run because the page that opens
     * a run has to build the grader for it before there is a run to ask.
     */
    fun answerLanguage(source: Language, target: Language, reverse: Boolean): Language =
        if (reverse) source else target

    /** The other side of the same pair: the language the prompt is written in. */
    fun promptLanguage(source: Language, target: Language, reverse: Boolean): Language =
        if (reverse) target else source
}

/** The part of a pair drill's question the run itself reads: what is said on either side. */
interface PairDrillTask {
    /** The prompt in the language it is written in; null where a picture alone asks. */
    val promptText: String?

    /** The canonical answer, as the card shows it after the reveal. */
    val display: String
}

/**
 * What the learner does to a pair drill run. Writing the answer out IS the answer, so a
 * keystroke is an intent of its own — the slot run's rule, which the letter run does not offer.
 */
sealed class PairDrillIntent {
    /** A live keystroke: an answer finished exactly right needs no check tap. */
    data class InputChanged(val text: String) : PairDrillIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : PairDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : PairDrillIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : PairDrillIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : PairDrillIntent()

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : PairDrillIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class PairDrillReduction<T : PairDrillTask>(val state: PairDrillRunState<T>, val effects: List<DrillEffect>)

/**
 * What a closed pair drill run leaves behind: the figures for the page that started it, and the
 * furthest Sprosse it stood on for that page to file.
 */
data class PairDrillClose<T : PairDrillTask>(
    val state: PairDrillRunState<T>,
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /**
     * The Sprosse the run REACHED, not the one it ends on — the ramp drops back on a miss, and
     * the ladder rewards standing on a Sprosse rather than finishing there.
     */
    val bestSprosse: Int,
    /**
     * The Sprossen this run answered OUT before its first slip ([DrillSprossen]), for the page
     * to add to what it holds — the next run opens above them.
     * Unfiltered: unlike [bestSprosse] there is no standing value to beat.
     */
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
) {
    /** What this run files for the pair it drilled, in the direction it ran. */
    fun bookings(): DrillBookings = state.config.bookings(summary, bestSprosse, clearedSprossen)
}

package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * What the PAIR drills share — the atlas ([CountryDrill]) and the calendar ([DateDrill]):
 * a ladder over material joined from both of a profile's languages, typed in either direction.
 *
 * Both price a Sprosse and the Fast modifier alike and both turn round the same way,
 * so the rules are written here once rather than once per drill;
 * what each asks, draws and grades stays its own.
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

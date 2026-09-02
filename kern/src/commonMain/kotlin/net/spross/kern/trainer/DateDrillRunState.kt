package net.spross.kern.trainer

import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * What the learner does to a dates run. Writing the reading out IS the answer, so a
 * keystroke is an intent of its own — the atlas run's vocabulary, unchanged.
 */
sealed class DateDrillIntent {
    /** A live keystroke: a reading finished exactly right needs no check tap. */
    data class InputChanged(val text: String) : DateDrillIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : DateDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : DateDrillIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : DateDrillIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : DateDrillIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class DateDrillReduction(val state: DateDrillRunState, val effects: List<DrillEffect>)

/**
 * What a closed dates run leaves behind: the figures for the page that started it, and the
 * furthest Sprosse it stood on for that page to file.
 */
data class DateDrillClose(
    val state: DateDrillRunState,
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /**
     * The Sprosse the run REACHED, not the one it ends on — the ramp drops back on a miss,
     * and the ladder rewards standing on a Sprosse rather than finishing there.
     */
    val bestLevel: Int,
    val effects: List<DrillEffect>,
)

/**
 * Everything one dates run is fixed to, resolved when it opens and never per question:
 * the joined calendars, which way round they ask, how long a Sprosse is, and the grader.
 */
class DateDrillRunConfig(
    /** The joined calendars, handed over once by the page that opened the run. */
    val content: DateDrillContent,
    /** Which side prompts: forward asks in the language the learner knows. */
    val reverse: Boolean,
    /**
     * Whether a Sprosse falls on ONE clean win instead of three. Its price is
     * [DateDrill.fastUnlocked]'s and the page that opened the run has already paid it.
     */
    val fast: Boolean,
    /**
     * The STRICT drill grader for the language the answer is owed in — which is the
     * learner's OWN on a reversed run. Null (a preview with no language info) grades plainly.
     */
    val normalizer: AnswerNormalizer?,
) {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = DateDrill.answerLanguage(content, reverse)

    /** The calendar turned around for the refusal check — null exactly where [normalizer] is. */
    internal val nameIndex: DateNameIndex? by lazy {
        normalizer?.let { DateNameIndex(content, reverse, it) }
    }

    /** The language the prompt is written in — the other side of the same pair. */
    val promptLanguage: Language get() = DateDrill.promptLanguage(content, reverse)
}

/**
 * One dates run, whole and immutable: the question on screen, what the answers have done
 * to the ladder, and the tallies the close reports.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the
 * focus, and hands text in through [DateDrillIntent]. What is in here is every rule that
 * decides what the text means.
 *
 * No FSRS and no box at all: the material is the catalog's calendars, not the learner's
 * own words, so nothing is scheduled and nothing is read. The one thing that outlives a
 * run is [bestLevel], which the page that started it files.
 */
data class DateDrillRunState(
    val config: DateDrillRunConfig,
    /** The question on screen. A fresh calendar always has one — a Sprosse with none is none. */
    val task: DateDrillTask,
    /** Bumped per question — what the card's identity and an autoplay effect key on. */
    val index: Int,
    val level: Int,
    val bestLevel: Int,
    val winsAtLevel: Int,
    val done: Int,
    val streak: Int,
    val bestStreak: Int,
    /**
     * Misses in a row already BOOKED — the one on screen is not among them, so 1 while a
     * miss shows means this is the second in a row.
     */
    val missRun: Int,
    val outcomes: List<AnswerOutcome>,
    /**
     * The questions this run has already answered RIGHT ([DrillSolved]): never asked
     * again, and a Sprosse with nothing left outside them is climbed past rather than repeated.
     */
    val solved: Set<String>,
    val feedback: TurnFeedback,
    /** What a refused answer actually NAMED (Juli is July) — only beside a Revealed miss. */
    val otherWord: Match.OtherWord? = null,
    val finished: Boolean,
) {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = config.answerLanguage

    /**
     * The language the prompt is written in. Nothing on screen names it; it tags the
     * prompt for a screen reader, which is the reading such a user gets in place of autoplay.
     */
    val promptLanguage: Language get() = config.promptLanguage

    /** Nothing decided yet — the answer is still the learner's to produce. */
    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    /** Correct or almost: something is pending that closing must book rather than lose. */
    val answerAccepted: Boolean
        get() = feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost

    /** The card may open: the almost hold and the miss each put a reading worth seeing whole. */
    val showsAnswer: Boolean
        get() = feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed

    /**
     * The way out, where it is wanted: under the button that goes on, on the SECOND miss
     * in a row — the rule the sibling drills follow.
     */
    val offersFinish: Boolean get() = missRun >= 1 && feedback == TurnFeedback.Revealed

    val tally: DrillTally get() = DrillTally.of(outcomes)
}

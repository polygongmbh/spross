package net.spross.kern.trainer

import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * What the learner does to an atlas run. Writing the name out IS the answer, so a keystroke
 * is an intent of its own — the slot run's rule, which the letter run does not offer.
 */
sealed class CountryDrillIntent {
    /** A live keystroke: a name finished exactly right needs no check tap. */
    data class InputChanged(val text: String) : CountryDrillIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : CountryDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : CountryDrillIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : CountryDrillIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : CountryDrillIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class CountryDrillReduction(val state: CountryDrillRunState, val effects: List<DrillEffect>)

/**
 * What a closed atlas run leaves behind: the figures for the page that started it, and the
 * furthest rung it stood on for that page to file.
 */
data class CountryDrillClose(
    val state: CountryDrillRunState,
    /** null ⇒ the run was never answered: dismiss, store nothing. */
    val summary: DrillRunSummary?,
    /**
     * The rung the run REACHED, not the one it ends on — the ramp drops back on a miss, and
     * the ladder rewards standing on a rung rather than finishing there.
     */
    val bestLevel: Int,
    val effects: List<DrillEffect>,
)

/**
 * Everything one atlas run is fixed to, resolved when it opens and never per question: the
 * joined atlas, which way round it asks, how long a rung is, and the grader.
 */
class CountryDrillRunConfig(
    /** The joined atlas, handed over once by the page that opened the run. */
    val content: CountryDrillContent,
    /** Which side prompts: forward asks in the language the learner knows. */
    val reverse: Boolean,
    /**
     * Whether a rung falls on ONE clean win instead of three. Its price is
     * [CountryDrill.fastUnlocked]'s and the page that opened the run has already paid it.
     */
    val fast: Boolean,
    /**
     * The STRICT drill grader for the language the answer is owed in — which is the
     * learner's OWN on a reversed run. Null (a preview with no language info) grades plainly.
     */
    val normalizer: AnswerNormalizer?,
) {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = CountryDrill.answerLanguage(content, reverse)

    /** The atlas turned around for the refusal check — null exactly where [normalizer] is. */
    internal val nameIndex: CountryNameIndex? by lazy {
        normalizer?.let { CountryNameIndex(content, reverse, it) }
    }

    /** The language the prompt is written in — the other side of the same pair. */
    val promptLanguage: Language get() = CountryDrill.promptLanguage(content, reverse)
}

/**
 * One atlas run, whole and immutable: the question on screen, what the answers have done to
 * the ladder, and the tallies the close reports.
 *
 * The learner's TEXT is not in here — the platform owns the field, the keyboard and the
 * focus, and hands text in through [CountryDrillIntent]. What is in here is every rule that
 * decides what the text means.
 *
 * No FSRS and no box at all: the material is the catalog's atlas, not the learner's own
 * words, so nothing is scheduled and nothing is read. The one thing that outlives a run is
 * [bestLevel], which the page that started it files.
 */
data class CountryDrillRunState(
    val config: CountryDrillRunConfig,
    /** The question on screen. The atlas always has one — a rung with none is not a rung. */
    val task: CountryDrillTask,
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
     * The questions this run has already answered RIGHT ([DrillSolved]): never asked again,
     * and a rung with nothing left outside them is climbed past rather than repeated.
     */
    val solved: Set<String>,
    val feedback: TurnFeedback,
    /** What a refused answer actually NAMED (Uswidi is Schweden) — only beside a Revealed miss. */
    val otherWord: Match.OtherWord? = null,
    val finished: Boolean,
) {
    /** The language an answer is owed in — the learned one, or the learner's own reversed. */
    val answerLanguage: Language get() = config.answerLanguage

    /**
     * The language the prompt is written in. Nothing on screen names it; it tags the name
     * for a screen reader, which is the reading such a user gets in place of autoplay.
     */
    val promptLanguage: Language get() = config.promptLanguage

    /** Nothing decided yet — the answer is still the learner's to produce. */
    val owesAnswer: Boolean get() = feedback == TurnFeedback.Neutral

    /** Correct or almost: something is pending that closing must book rather than lose. */
    val answerAccepted: Boolean
        get() = feedback == TurnFeedback.Correct || feedback is TurnFeedback.Almost

    /** The card may open: the almost hold and the miss each put a name worth seeing on it. */
    val showsAnswer: Boolean
        get() = feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed

    /**
     * The way out, where it is wanted: under the button that goes on, on the SECOND miss in
     * a row — the rule both sibling drills follow.
     */
    val offersFinish: Boolean get() = missRun >= 1 && feedback == TurnFeedback.Revealed

    val tally: DrillTally get() = DrillTally.of(outcomes)
}

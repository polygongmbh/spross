package net.spross.kern.trainer

import net.spross.kern.session.Saying
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.TurnFeedback

/** What the learner does to an opposites run — the word scramble's typed intents. */
sealed class OppositesIntent {

    /** A live keystroke: an opposite finished exactly right needs no check tap. */
    data class InputChanged(val text: String) : OppositesIntent()

    /** Check/Enter with text standing. */
    data class Submit(val text: String) : OppositesIntent()

    /** "Aufdecken" on an empty field — the card carries every opposite and the question books a miss. */
    data object Reveal : OppositesIntent()

    /** The explicit tap that books whatever the feedback already said. */
    data object ConfirmPending : OppositesIntent()

    /** The platform's armed beat elapsed. */
    data object AdvanceElapsed : OppositesIntent()

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : OppositesIntent()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class OppositesReduction(
    val state: OppositesRunState,
    val effects: List<DrillEffect>,
)

/** What a closed opposites run leaves behind — [WordScrambleClose]'s shape. */
data class OppositesClose(
    val state: OppositesRunState,
    /** null ⇒ nothing was answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    val bestSprosse: Int,
    /** The Sprossen this run climbed off before its first slip ([DrillSprossen]). */
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
)

/** Everything one opposites run is fixed to, resolved when it opens. */
class OppositesRunConfig(
    val report: OppositesAvailability.Report,
    /** The STRICT drill grader for the language being learned; null grades plainly. */
    val normalizer: AnswerNormalizer?,
    /** The Sprossen earlier runs earned, as the platform's store holds them. */
    val cleared: Set<Int> = emptySet(),
)

/**
 * One opposites run, whole and immutable. The learner's TEXT is not in here — the platform
 * owns the field — and no FSRS anywhere: the box is READ for the words it holds, never written.
 */
data class OppositesRunState(
    val config: OppositesRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: OppositesTask?,
    override val index: Int,
    val sprosse: Int,
    val bestSprosse: Int,
    val winsAtSprosse: Int,
    val clearedSprossen: Set<Int>,
    override val core: DrillRunCore,
    override val feedback: TurnFeedback,
    override val finished: Boolean,
) : DrillRunProgress {
    companion object {
        /** One mask per learned language: both sides of the question are written in it. */
        fun storageKey(language: Language): String = "opposites.$language"
    }

    /** The card opens on every verdict: the other opposites are what the drill is there to show. */
    val showsAnswer: Boolean get() = !owesAnswer

    internal val newSprossen: Int get() = (clearedSprossen - config.cleared).size

    /** The word asked about, in the language being learned: hearing it gives no opposite away. */
    override val promptSaying: Saying? get() = task?.let { Saying(it.prompt, it.language) }

    /** Every opposite, so a merge is heard as well as read. */
    override val answerSaying: Saying?
        get() = task?.let { t -> Saying(t.answers.joinToString(", ") { it.text }, t.language) }
}

package net.spross.kern.trainer

import net.spross.kern.session.AnswerControls
import net.spross.kern.model.ClosingNote
import net.spross.kern.model.EmojiCue
import net.spross.kern.session.Question
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

/** What a closed opposites run leaves behind: the figures, and the ladder as [LadderStanding] books it. */
data class OppositesClose(
    val state: OppositesRunState,
    /** null ⇒ nothing was answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    val bestSprosse: Int,
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
) {
    /** What this run files for the [target] language it drilled. */
    fun bookings(target: Language): DrillBookings =
        DrillBookings.masked(Drill.Opposites, OppositesRunState.storageKey(target), target, summary, clearedSprossen)
}

/** Everything one opposites run is fixed to, resolved when it opens. */
class OppositesRunConfig(
    val report: OppositesAvailability.Report,
    /** The STRICT drill grader for the language being learned; null grades plainly. */
    val normalizer: AnswerNormalizer?,
    /** The Sprossen earlier runs earned, as the platform's store holds them. */
    val cleared: Set<Int> = emptySet(),
)

/** Between the opposites of one prompt, and between their meanings. */
private const val OPPOSITES_JOIN = " · "

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

    internal val newSprossen: Int get() = (clearedSprossen - config.cleared).size

    /** The word asked about, in the language being learned: hearing it gives no opposite away. */
    override val promptSaying: Saying? get() = task?.let { Saying(it.prompt, it.language) }

    /** Every opposite, so a merge is heard as well as read. */
    override val answerSaying: Saying?
        get() = task?.let { t -> Saying(t.answers.joinToString(", ") { it.text }, t.language) }

    /**
     * Every opposite on the answer, and the closing line pairing what the prompt means with what each of them does —
     * the one line an accepted opposite grows.
     */
    override val question: Question?
        get() = task?.let { t ->
            Question(
                key = index.toString(),
                ask = null,
                prompt = Question.Side(t.prompt, t.language, Question.Form.Word, saying = promptSaying),
                answer = Question.Side(
                    t.answers.joinToString(OPPOSITES_JOIN) { it.text }, t.language, Question.Form.Word,
                    saying = answerSaying,
                ),
                emoji = null,
                emojiCue = EmojiCue.Upfront,
                opens = showsAnswer,
                growsNote = answerAccepted,
                closing = Question.Closing(
                    note = ClosingNote.Own("${t.gloss} ↔ ${t.answers.joinToString(OPPOSITES_JOIN) { it.gloss }}"),
                ),
            )
        }

    /** Any one opposite, written. */
    override val controls: AnswerControls?
        get() = task?.let { answerControls(typedSlot(it.language)) }
}

package net.spross.kern.session

import net.spross.kern.box.TallyPart
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.tallyParts
import net.spross.kern.model.Rating

/** One answer a round recorded: the card it landed on, its rating, and which kind it was. */
data class RoundAnswer(val cardId: String, val rating: Rating, val kind: TallyPartKind)

/**
 * A round's answers in answer order — the one list every count of the round reads,
 * so the ratings, the cards and the kinds cannot drift apart.
 * A suspended card is no answer and never appears here.
 */
data class RoundTally(val answers: List<RoundAnswer> = emptyList()) {
    val answered: Int get() = answers.size

    /**
     * The cards the round TOUCHED, in answer order — what a surface asking
     * "which part of the box did this round move" reads ([net.spross.kern.box.grownArea]).
     */
    val cardIds: List<String> get() = answers.map { it.cardId }

    /** Each answer as the progress bar groups it. */
    val segments: List<AnswerOutcome> get() = answers.map { outcome(it.rating) }

    val introduced: Int get() = count(TallyPartKind.Introduced)
    val reviewed: Int get() = count(TallyPartKind.Reviewed)
    val settled: Int get() = count(TallyPartKind.Settled)

    /** The round's answers spelled out ([tallyParts]). */
    fun parts(): List<TallyPart> = tallyParts(introduced, reviewed, settled)

    private fun count(kind: TallyPartKind): Int = answers.count { it.kind == kind }

    internal operator fun plus(answer: RoundAnswer): RoundTally = RoundTally(answers + answer)
}

private fun outcome(rating: Rating): AnswerOutcome = when (rating) {
    Rating.Again -> AnswerOutcome.Wrong
    Rating.Hard -> AnswerOutcome.Almost
    Rating.Good, Rating.Easy -> AnswerOutcome.Right
}

package net.spross.kern.design

/** The letter case a card sets a line in, whatever its string table holds. */
enum class LetterCase { AsWritten, Upper }

/**
 * How a question card sets its lines, where the two apps would otherwise each pick their own;
 * the type sizes themselves stay each app's ramp.
 */
object CardType {
    /** The caption over a prompt ([net.spross.kern.session.Question.ask]): a label in capitals, not a sentence. */
    val askCase: LetterCase = LetterCase.Upper

    /**
     * The listening card's meaning comes in under the reveal's divider, as every card's answer does:
     * the divider is what marks a line as the answer rather than more of the prompt.
     */
    const val LISTENING_REVEAL_DIVIDED: Boolean = true
}

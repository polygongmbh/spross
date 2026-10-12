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
}

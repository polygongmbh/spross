package net.spross.kern.design

import net.spross.kern.session.Question

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
     * The lines a drill prompt may wrap to, by what it asks: a sentence reads over four,
     * a name or a dated line over three, and anything else stands on one.
     */
    fun promptLines(form: Question.Form): Int = when (form) {
        Question.Form.Sentence -> 4
        Question.Form.Name -> 3
        else -> 1
    }
}

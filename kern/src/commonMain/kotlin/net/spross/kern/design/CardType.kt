package net.spross.kern.design

import net.spross.kern.session.Question

/**
 * How much of a question card's prompt shows, where the two apps would otherwise each pick their own;
 * how the lines are set stays each app's.
 */
object CardType {
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

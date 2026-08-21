package net.spross.kern.session

import net.spross.kern.catalog.speechKey
import net.spross.kern.model.Card

/**
 * The card a HEARD question is graded against: the real card's IDENTITY with only its
 * answer set narrowed to the form that played.
 *
 * The id, `feminineOf` and `kind` must survive. [CatalogAnswerGrader] skips the prompted
 * concept when it looks for the word somebody else owns, so a synthetic id would let the
 * learner's own concept come back as another word — «мишка» reported as a different word
 * than «миша», naming the right answer as somebody else's. `kind` keys the verb-prefix
 * leniency. `baseAccepted` goes, with the synonyms: the feminine demotion accepts the base
 * word, which in a transcription is simply not what played.
 *
 * One definition for both surfaces that ask by ear — the letter drill's dictation rung and
 * a sound-prompted produce review — because "credit only what was spoken" is one rule.
 */
fun spokenOnly(card: Card, spokenForm: String): Card = card.copy(
    baseAccepted = emptyList(),
    target = card.target.copy(
        text = spokenForm,
        synonyms = emptyList(),
        variants = emptyList(),
    ),
)

/**
 * Whether [input] is a form this card lists as a synonym or a variant — the right word,
 * just not the one that played.
 *
 * The other half of the ear rule [spokenOnly] states: grading narrows to what was spoken,
 * and this names what the narrowing then has to forgive. Almost, never wrong — the reveal
 * itself teaches these forms ("auch: …"), so failing one would contradict the card that is
 * about to show it.
 *
 * Compared by [speechKey], because a learner writing down what they heard carries none of
 * the spelling edges the catalog authors — the stem dash of `-zuri`, the `¡…!` of a Spanish
 * citation, or the case of a noun.
 */
fun alsoAccepts(card: Card, input: String): Boolean {
    val typed = speechKey(input)
    return (card.target.synonyms + card.target.variants).any { speechKey(it) == typed }
}

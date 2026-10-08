package net.spross.kern.session

import net.spross.kern.catalog.speechKey
import net.spross.kern.model.Card
import net.spross.kern.model.stressFolded

/**
 * The card a TRANSCRIPTION is graded against: the real card's IDENTITY with only its
 * answer set narrowed to the form that played.
 *
 * The id and `kind` must survive. [CatalogAnswerGrader] skips the prompted
 * concept when it looks for the word somebody else owns, so a synthetic id would let the
 * learner's own concept come back as another word — «мишка» reported as a different word
 * than «миша», naming the right answer as somebody else's. `kind` keys the verb-prefix
 * leniency.
 *
 * The letter drill's dictation Sprosse is the one surface that transcribes: a sound-prompted
 * produce review owes the meaning instead ([meaningSide]).
 */
fun spokenOnly(card: Card, spokenForm: String): Card = card.copy(
    target = card.target.copy(
        text = spokenForm,
        teaches = emptyList(),
        accepts = emptyList(),
        forms = emptyList(),
    ),
)

/**
 * Whether [input] is a form this card lists as a synonym or a variant — a word of the card,
 * just not the one that played.
 *
 * The other half of the ear rule [spokenOnly] states: grading narrows to what was spoken,
 * and this names what the narrowing refuses outright. A dictation asks one word, not a
 * card, so another of its forms is a miss — even one a slip away from the played form,
 * which a typo budget alone would have held as an almost.
 *
 * Compared by [speechKey], because a learner writing down what they heard carries none of
 * the spelling edges the catalog authors — the stem dash of `-zuri`, the `¡…!` of a Spanish
 * citation, the case of a noun, or the stress mark of uk `пі́вніч`.
 */
fun alsoAccepts(card: Card, input: String): Boolean {
    val typed = stressFolded(speechKey(input))
    return (card.target.teaches + card.target.accepts + card.target.forms.map { it.text })
        .any { stressFolded(speechKey(it)) == typed }
}

/**
 * The card a MEANING answer is graded against: the same card with its two sides SWAPPED,
 * so the source realization is what the answer is measured against.
 *
 * A word asked by ear asks what it MEANS, not how it is spelled — hearing «gari» and
 * writing «gari» back proves only that the ear worked. So the answer set is the source
 * side's `text ∪ teaches ∪ accepts`, which [AnswerNormalizer] already reads off
 * `target`, and the whole grading pipeline is reused rather than re-cut for one prompt.
 *
 * The id and `kind` survive, as they do in [spokenOnly] and for the same reasons.
 */
fun meaningSide(card: Card): Card = card.copy(
    source = card.target,
    target = card.source,
)

package net.spross.kern.model

/** Every dimension some tagged form of this realization inflects along. */
val Realization.inflectedDimensions: Set<FormDimension>
    get() = forms.flatMapTo(mutableSetOf()) { it.tag.dimensions }

/**
 * The dimensions a prompt carries across to the answer: those both sides inflect along.
 * A source that does not inflect (en `my`) pins nothing, so every target form answers it;
 * a dimension only one side has cannot be read off the prompt, so it pins nothing either.
 */
fun pinnedDimensions(card: Card): Set<FormDimension> =
    card.source.inflectedDimensions intersect card.target.inflectedDimensions

/** True when [tag] (null = the citation form) shows [promptTag]'s value on every [pinned] dimension. */
fun agrees(tag: FormTag?, promptTag: FormTag?, pinned: Set<FormDimension>): Boolean =
    pinned.all { tag?.values?.get(it) == promptTag?.values?.get(it) }

/**
 * The target forms a prompt in [promptTag] (null = the source's citation form) is answered with.
 * [right] grade as the answer; [almost] are the card's other forms, which name the word in the wrong form —
 * close enough that grading corrects them to the agreeing one rather than failing them.
 */
data class AnswerForms(val right: List<String>, val almost: List<String>)

fun answerForms(card: Card, promptTag: FormTag?): AnswerForms {
    val pinned = pinnedDimensions(card)
    val target = card.target
    val citation = listOf(target.text) + target.teaches + target.accepts
    val (agreeing, disagreeing) = target.forms.partition { agrees(it.tag, promptTag, pinned) }
    val citationAgrees = agrees(null, promptTag, pinned)
    return AnswerForms(
        right = (if (citationAgrees) citation else emptyList()) + agreeing.map { it.text },
        almost = disagreeing.map { it.text } + if (citationAgrees) emptyList() else citation,
    )
}

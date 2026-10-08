package net.spross.kern.model

/** Every dimension some tagged form of this realization inflects along. */
val Realization.inflectedDimensions: Set<FormDimension>
    get() = forms.flatMapTo(mutableSetOf()) { it.tag.dimensions }

/**
 * The dimensions a prompt carries across to the answer: those both sides inflect along,
 * and number wherever the target inflects it, because a plural means something else.
 * Agreement a source does not inflect for (en `my`) pins nothing, so every target form answers it;
 * a dimension only the source has cannot be read off the answer, so it pins nothing either.
 */
fun pinnedDimensions(card: Card): Set<FormDimension> {
    val target = card.target.inflectedDimensions
    return (card.source.inflectedDimensions intersect target) + (target intersect MEANING_DIMENSIONS)
}

private val MEANING_DIMENSIONS = setOf(FormDimension.Number)

/** True when [tag] (null = the citation form) shows [promptTag]'s value on every [pinned] dimension. */
fun agrees(tag: FormTag?, promptTag: FormTag?, pinned: Set<FormDimension>): Boolean =
    pinned.all { tag?.values?.get(it) == promptTag?.values?.get(it) }

/**
 * The target forms a prompt in [promptTag] (null = the source's citation form) is answered with.
 * [right] grade as the answer; [almost] are the card's other forms, which name the word in the wrong form —
 * close enough that grading corrects them to the agreeing one rather than failing them.
 * [rightForms] are the tagged forms among [right], which the reveal offers with their markers.
 */
data class AnswerForms(val right: List<String>, val almost: List<String>, val rightForms: List<TaggedForm>)

fun answerForms(card: Card, promptTag: FormTag?): AnswerForms {
    val pinned = pinnedDimensions(card)
    val target = card.target
    val citation = listOf(target.text) + target.teaches + target.accepts
    val (agreeing, disagreeing) = target.forms.partition { agrees(it.tag, promptTag, pinned) }
    val citationAgrees = agrees(null, promptTag, pinned)
    return AnswerForms(
        right = (if (citationAgrees) citation else emptyList()) + agreeing.map { it.written },
        almost = disagreeing.map { it.written } + if (citationAgrees) emptyList() else citation,
        rightForms = agreeing,
    )
}

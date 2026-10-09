package net.spross.kern.model

/** What a prompt stands on: the form on screen or playing, and its tag — null for the citation form or a synonym. */
data class PromptForm(val text: String, val tag: FormTag?)

/**
 * The form a turn asks with, by role:
 * recognize rotates the target's citation, its `teaches` and every tagged form;
 * produce rotates the source's citation and those of its tagged forms whose tag the target has too,
 * so a prompt only ever asks for a form the answer side can give;
 * asked by ear, it is the target citation that plays.
 */
fun turnPrompt(card: Card, role: PresentationRole, prompt: ProducePrompt, reviewCount: Int): PromptForm = when {
    role == PresentationRole.Recognize -> rotated(card.id, recognitionForms(card.target), reviewCount)
    prompt == ProducePrompt.Sound -> PromptForm(card.target.text, null)
    else -> rotated(card.id, produceForms(card), reviewCount)
}

private fun recognitionForms(target: Realization): List<PromptForm> =
    (listOf(target.text) + target.teaches).map { PromptForm(it, null) } + target.forms.map { PromptForm(it.text, it.tag) }

private fun produceForms(card: Card): List<PromptForm> {
    val answerable = card.target.forms.mapTo(mutableSetOf()) { it.tag }
    return listOf(PromptForm(card.source.text, null)) +
        card.source.forms.filter { it.tag in answerable }.map { PromptForm(it.text, it.tag) }
}

/** How a side says a form tagged elsewhere: its own form for it, and the [marker] for what that form cannot show. */
data class Counterpart(val text: String, val marker: FormTag?)

/**
 * [side]'s form for a prompt in [tag] (null = citation): the one agreeing on every dimension [side] inflects along,
 * marked with the rest of [tag] — `Lehrerin` is `teacher` ♀ in English, `profesora` in Spanish.
 * A side that lacks the agreeing form says its citation and marks the whole tag.
 */
fun counterpart(side: Realization, tag: FormTag?): Counterpart {
    if (tag == null) return Counterpart(side.text, null)
    val inflected = side.inflectedDimensions
    val own = tag.values.filterKeys { it in inflected }
    val form = if (own.isEmpty()) side.text else side.forms.firstOrNull { it.tag.values == own }?.text
    val unshown = if (form == null) tag.values else tag.values - own.keys
    return Counterpart(form ?: side.text, FormTag(unshown).takeIf { unshown.isNotEmpty() })
}

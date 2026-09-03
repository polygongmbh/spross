package net.spross.kern.model

/**
 * The grammatical gender an article marks.
 *
 * A two-gender language folds onto [Masculine]/[Feminine] rather than minting
 * genders of its own — the distinction a learner needs is which of the target's
 * classes a word belongs to, and every surface already renders exactly two hues
 * for it. [Neuter] is German's alone; a language without one simply never
 * reaches it.
 */
enum class Gender { Masculine, Feminine, Neuter }

/**
 * Which gender a target-language article marks, or null when the box cannot
 * say (no article authored, or one this table does not know — a genderless
 * target and an unlisted article degrade the same way).
 *
 * Plurals and indefinites follow the gender they inflect: `los`/`un` are the
 * masculine's, `las`/`una` the feminine's. The article string is the one the
 * catalog authored in the target's `grammar["gender"]`; matching is
 * case-insensitive because that is authoring slack, not a rule.
 *
 * This is the domain half of what used to be five copied switch statements.
 * The hues each surface paints a gender in are aesthetics and stay with the
 * surface — kern names the gender, never the color.
 */
fun articleGender(article: String?): Gender? = when (article?.lowercase()) {
    "der", "el", "los", "un" -> Gender.Masculine
    "die", "la", "las", "una" -> Gender.Feminine
    "das" -> Gender.Neuter
    else -> null
}

/**
 * The article that may be shown in front of [shownForm] — the card's article
 * only when the form on screen IS the canonical [targetText].
 *
 * A prompt may rotate a synonym in, and a synonym is a different word: it can
 * carry a different gender, so the card's article would mislabel it. Nothing
 * beats a wrong article into a learner, so the article steps aside instead.
 */
fun shownArticle(article: String?, shownForm: String, targetText: String): String? =
    if (shownForm == targetText) article else null

/** Typewriter, curly, and the modifier letter — the apostrophe class an elided article ends on. */
private val ARTICLE_APOSTROPHES = setOf('\u0027', '\u2019', '\u02bc')

/**
 * [form] with [article] written in front of it, or [form] alone where there is none.
 *
 * An ELIDED article writes ONTO its word — "l'acqua", never "l' acqua": the apostrophe is
 * the join, and a space beside it spells a word nobody writes. The rule lives here so the
 * ear and the page cannot disagree about it
 * ([net.spross.kern.catalog.spokenTargetForm] speaks what this writes).
 */
fun articledForm(article: String?, form: String): String {
    val prefix = article?.trim()?.takeIf { it.isNotEmpty() } ?: return form
    return if (prefix.last() in ARTICLE_APOSTROPHES) "$prefix$form" else "$prefix $form"
}

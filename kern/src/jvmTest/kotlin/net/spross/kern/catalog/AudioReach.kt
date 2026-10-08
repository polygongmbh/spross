package net.spross.kern.catalog

import net.spross.kern.model.Language
import net.spross.kern.model.splitArticle

/**
 * What the audio lints measure a form-keyed recording against: what some card in a
 * language shows, whichever concept it belongs to — a recording is reachable while its
 * form stands on any card, so a merge, a rename or a move cannot strand it.
 */
private fun Catalog.realizationsIn(lang: Language): List<RawRealization> =
    areas.flatMap { it.realizations[lang]?.values.orEmpty() }

/** A tagged form's authored article split off its word, as the join splits it. */
private fun Catalog.taggedForms(lang: Language, raw: RawRealization): List<Pair<String?, String>> =
    raw.forms.map { splitArticle(it.text, languages[lang]?.articles.orEmpty()) }

/**
 * Every bare form a card in [lang] may show or grade — `text`, `teaches`, `accepts`, each
 * tagged form without its article — plus each one's bare verb stem ([verbStem]), the
 * lookup's own fallback.
 */
internal fun Catalog.shownForms(lang: Language): Set<String> {
    val prefixes = languages[lang]?.optionalVerbPrefixes.orEmpty()
    val shown = realizationsIn(lang).flatMap { raw ->
        listOf(raw.text) + raw.teaches + raw.accepts + taggedForms(lang, raw).map { it.second }
    }
    return (shown + shown.mapNotNull { verbStem(it, prefixes) }).toSet()
}

/**
 * Every (articled form, bare word) a card in [lang] may say, as speech keys: a realization's
 * own `grammar.gender` in front of each form it shows, and each tagged form with the article
 * it was authored with (`die Lehrerin`).
 */
internal fun Catalog.articledForms(lang: Language): Set<Pair<String, String>> =
    realizationsIn(lang).flatMap { raw ->
        val gender = raw.grammar["gender"]
        val own = if (gender.isNullOrBlank()) emptyList()
                  else (listOf(raw.text) + raw.teaches + raw.accepts).map { gender to it }
        own + taggedForms(lang, raw).mapNotNull { (article, word) -> article?.let { it to word } }
    }.mapTo(mutableSetOf()) { (article, word) ->
        speechKey(spokenTargetForm(article, word, word)) to speechKey(word)
    }

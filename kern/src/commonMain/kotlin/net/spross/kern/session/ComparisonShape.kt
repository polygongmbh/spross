package net.spross.kern.session

import net.spross.kern.model.APOSTROPHES
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.hyphensAndApostrophesStripped
import net.spross.kern.model.nfcNormalized
import net.spross.kern.model.stressFolded

/** The answer language's comparison shape, which [AnswerNormalizer] grades both sides in. */
internal class ComparisonShape(answerLanguage: LanguageInfo) {

    private val digraphFolds: List<Pair<String, String>> = answerLanguage.diacriticDigraphs
        .map { (letter, digraph) -> letter.lowercase() to digraph.lowercase() }

    /** The listed articles that elide (`l'`), without their apostrophe. */
    private val elidedArticles: List<String> = answerLanguage.articles.map { it.lowercase() }.toSet()
        .filter { it.lastOrNull() in APOSTROPHES }.map { it.dropLast(1) }.filter { it.isNotEmpty() }

    fun tokenize(raw: String): List<String> =
        cleaned(elisionSeparated(raw)).split(' ').filter { it.isNotEmpty() }

    /**
     * [raw] with a leading elided article (fr/it `l'`) set apart as its own word, so the article
     * rules see it as they see `la` — the apostrophe pass would otherwise glue it on (`linvitée`).
     */
    private fun elisionSeparated(raw: String): String {
        val trimmed = raw.trimStart()
        val head = trimmed.lowercase()
        for (article in elidedArticles) {
            if (head.length > article.length && head.startsWith(article) && head[article.length] in APOSTROPHES) {
                return trimmed.substring(0, article.length + 1) + " " + trimmed.substring(article.length + 1)
            }
        }
        return raw
    }

    /**
     * The one character pass everything shares, so tokenization can never disagree:
     * NFC, stress marks dropped (uk `пі́вніч` is typed `північ`), lowercase, ß→ss (2 edits — too far for short words' typo budget), the answer
     * language's [LanguageInfo.diacriticDigraphs] (de ä→ae, ö→oe, ü→ue), joiners
     * `-` and the apostrophe class deleted outright ("E-Mail"/"Email", "geht's"/"gehts"), every other
     * non-alphanumeric — punctuation incl. `…—`, and whitespace — becomes a space.
     *
     * The digraph fold runs on both sides like ß→ss does, and for the same reason: it is
     * a full, established ASCII spelling of the letter rather than a slip, so "Kueche"
     * grades [Match.Exact] on a "Küche" card. Folding also LENGTHENS a short word before
     * [allowedTypos] measures it — "für" becomes "fuer" and forgives the slip three
     * letters could not. Dropping a diacritic outright is the other rule and stays out of
     * here on purpose: it is free inside [damerauLevenshtein] only, so it can never reach
     * the exact test and bypass [CatalogAnswerGrader]'s collision check.
     */
    fun cleaned(raw: String): String {
        var lowered = stressFolded(nfcNormalized(raw)).lowercase().replace("ß", "ss")
        for ((letter, digraph) in digraphFolds) lowered = lowered.replace(letter, digraph)
        val out = StringBuilder(lowered.length)
        for (ch in hyphensAndApostrophesStripped(lowered)) {
            when {
                ch.isLetter() || ch.isDigit() -> out.append(ch)
                else -> out.append(' ')
            }
        }
        return out.toString()
    }

    /** Prefix in comparison shape, space-preserving: en `"to "` keeps its trailing space. */
    fun normalizedPrefix(raw: String): String =
        cleaned(raw).replace(AnswerNormalizer.whitespaceRun, " ")
}

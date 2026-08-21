package net.spross.kern.trainer

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * How a GENERATED answer is graded — the one copy the drills that take free text share.
 *
 * A drill has no catalog behind it: the accepted forms are wrapped as one synthetic card, so
 * [Match.OtherWord] can never arise and there is no other concept's word for a slip to be
 * mistaken for. All the strictness is the normalizer's ([AnswerNormalizer.drill]: no article
 * leniency, one slip per word, nothing forgiven inside a digit).
 */
internal fun gradeDrillAnswer(
    input: String,
    accepted: List<String>,
    display: String,
    language: Language,
    cardId: String,
    normalizer: AnswerNormalizer?,
): Match {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return Match.Wrong
    if (normalizer == null) return plainVerdict(trimmed, accepted)
    val card = drillGradingCard(cardId, language, accepted, display)
    return when (val match = normalizer.evaluate(trimmed, card)) {
        Match.Exact -> Match.Exact
        is Match.Typo -> match
        is Match.OtherWord, Match.Wrong -> Match.Wrong
    }
}

/**
 * The accepted forms as one synthetic card. The non-verb kind keeps the verb-prefix option
 * off and an empty `baseAccepted` skips the feminine demotion — a generated answer has
 * neither a base concept nor a conjugation to forgive.
 */
internal fun drillGradingCard(
    cardId: String,
    language: Language,
    accepted: List<String>,
    display: String,
): Card {
    val side = Realization(
        lang = language,
        text = accepted.firstOrNull() ?: display,
        synonyms = accepted.drop(1),
    )
    return Card(
        id = cardId,
        kind = CardKind.Noun,
        area = cardId,
        emoji = null,
        seedIndex = 0,
        components = emptyList(),
        feminineOf = null,
        baseAccepted = emptyList(),
        source = side,
        target = side,
        promptFeminineMarker = false,
    )
}

/** No language info (a preview): a plain case- and punctuation-insensitive comparison. */
private fun plainVerdict(trimmed: String, accepted: List<String>): Match {
    val typed = plainAnswerForm(trimmed)
    return if (accepted.any { plainAnswerForm(it) == typed }) Match.Exact else Match.Wrong
}

/** Comparison shape for the rules that grade without a catalog behind them. */
internal fun plainAnswerForm(raw: String): String = raw.lowercase()
    .map { if (it.isLetterOrDigit()) it else ' ' }
    .joinToString("")
    .split(' ')
    .filter { it.isNotEmpty() }
    .joinToString(" ")

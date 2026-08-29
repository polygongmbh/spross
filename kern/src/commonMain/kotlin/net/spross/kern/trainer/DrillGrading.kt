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
 * A drill has no catalog behind it: the accepted forms are wrapped as one synthetic card.
 * All the strictness is the normalizer's ([AnswerNormalizer.drill]: no article leniency,
 * one slip per word, nothing forgiven inside a digit) — plus one check the catalog path
 * gets from its join and a drill gets from [index]: a slip the typo budget accepted is
 * refused as [Match.OtherWord] when it NAMES a different value ([otherNumber]).
 */
internal fun gradeDrillAnswer(
    input: String,
    accepted: List<String>,
    display: String,
    language: Language,
    cardId: String,
    normalizer: AnswerNormalizer?,
    index: NumberReadingIndex? = null,
): Match {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return Match.Wrong
    if (normalizer == null) return plainVerdict(trimmed, accepted)
    val card = drillGradingCard(cardId, language, accepted, display)
    return when (val match = normalizer.evaluate(trimmed, card)) {
        Match.Exact -> Match.Exact
        is Match.Typo -> index?.let { otherNumber(normalizer, trimmed, match.corrected, accepted, it) } ?: match
        is Match.OtherWord, Match.Wrong ->
            index?.let { otherNumber(normalizer, trimmed, corrected = null, accepted, it) } ?: Match.Wrong
    }
}

/**
 * The value the learner actually wrote, where it is not the one that was asked — or null,
 * and the verdict already reached stands. Consulted on BOTH miss arms: a Typo it turns
 * into a refusal, a Wrong it merely names (`arobaini na saba` for 46 is 47 — two edits,
 * so never a typo, but exactly as worth telling).
 *
 * Two probes against [index], evidence keyed to what was TYPED, never to what was missed:
 * refusing because the EXPECTED reading names a value would refuse every fumbled numeral
 * (`sesemta` for `sesenta`), since a number word is indexed and its fumble is not. Only the
 * typed side can carry proof that a DIFFERENT number was written.
 *
 * 1. The whole answer: if it is a complete reading of some value and the accepted readings
 *    name values of their own, disjointness decides — `ciento setenta y ocho` IS 178, and
 *    `un décimo` (1/10) differs from `undécimo` (11th) only in a space no word split sees.
 *    Readings that agree on a value are the same answer and end the check.
 * 2. Word by word, positionally: where the typed and the matched reading have the same
 *    word count, a differing word that names a value the expected word does not share is
 *    another number inside a compound — `hasi nane` for `hasi nne`. A differing word that
 *    names nothing (a connector, a fumble) never fires; a sentence slips through untouched.
 *    Typo arm only ([corrected] is the form the budget measured against; a Wrong has none).
 */
internal fun otherNumber(
    normalizer: AnswerNormalizer,
    typed: String,
    corrected: String?,
    accepted: List<String>,
    index: NumberReadingIndex,
): Match.OtherWord? {
    fun shape(raw: String): String? =
        normalizer.comparisonForms(raw, verbLeniency = false).firstOrNull()

    fun values(raw: String): Set<NumberIdentity> = shape(raw)?.let(index::values).orEmpty()

    val typedWhole = values(typed)
    if (typedWhole.isNotEmpty()) {
        val expectedWhole = accepted.flatMap(::values).toSet()
        if (expectedWhole.isNotEmpty()) {
            return if ((typedWhole intersect expectedWhole).isEmpty()) refusal(typed, typedWhole) else null
        }
    }

    if (corrected == null) return null
    val typedWords = shape(typed)?.split(' ') ?: return null
    val expectedWords = shape(corrected)?.split(' ') ?: return null
    if (typedWords.size != expectedWords.size) return null
    for (i in typedWords.indices) {
        if (typedWords[i] == expectedWords[i]) continue
        val typedValues = index.values(typedWords[i])
        if (typedValues.isEmpty()) continue
        val expectedValues = index.values(expectedWords[i])
        if (expectedValues.isEmpty()) continue
        if ((typedValues intersect expectedValues).isEmpty()) return refusal(typedWords[i], typedValues)
    }
    return null
}

private fun refusal(word: String, values: Set<NumberIdentity>): Match.OtherWord =
    Match.OtherWord(word = word.trim(), meanings = values.map { it.display }.distinct().sorted())

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

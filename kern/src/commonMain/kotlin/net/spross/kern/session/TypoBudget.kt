package net.spross.kern.session

/** Is [input] within the slips [candidate] forgives, its digits typed exactly? */
internal fun withinBudget(input: String, candidate: String): Boolean {
    if (digitWords(input) != digitWords(candidate)) return false
    if (damerauLevenshtein(input, candidate) > allowedTypos(candidate.count { it != ' ' })) return false
    val typed = input.split(' ')
    val expected = candidate.split(' ')
    // why: the per-word cap needs the words to line up — a dropped or added
    // word leaves the whole-form budget alone to decide.
    if (typed.size != expected.size) return true
    return expected.indices.all { i -> damerauLevenshtein(typed[i], expected[i]) <= wordCap(expected[i]) }
}

private fun wordCap(word: String): Int = (word.length + LETTERS_PER_WORD_SLIP - 1) / LETTERS_PER_WORD_SLIP

private fun digitWords(form: String): List<String> =
    form.split(' ').filter { word -> word.any { it.isDigit() } }

/**
 * One word's slips for [AnswerNormalizer.matchingPrefixWordCount]'s retry-priming rule:
 * the length-scaled formula measured per word, so a short mistyped word
 * never keeps more of the retry field than a retry is meant to prime.
 */
internal fun prefixWordBudget(word: String): Int =
    if (word.any { it.isDigit() }) 0 else allowedTypos(word.length)

/**
 * ~⅙ of letters, but never for words under [MIN_TYPO_LENGTH]:
 * a four-letter word forgives one slip and a long phrase a slip per six letters.
 * That much is safe because [CatalogAnswerGrader] withdraws the credit wherever the
 * typed form is really another concept's word (RealCatalogGradingTest
 * sweeps the shipping catalog for exactly that).
 *
 * A dropped diacritic never needs this budget at all: [damerauLevenshtein]
 * charges nothing for it, so fr "ou" for "où" is a typo even at the floor
 * where the budget is zero — which is the whole point, short accented words
 * being where the floor bit hardest.
 */
internal fun allowedTypos(letters: Int): Int =
    if (letters < MIN_TYPO_LENGTH) 0 else maxOf(1, letters / TYPO_LETTERS_PER_SLIP)

/** Below this many letters an answer is graded exact-only. */
private const val MIN_TYPO_LENGTH = 4

private const val TYPO_LETTERS_PER_SLIP = 6

/** A word's own cap on its share of the budget: a slip per this many letters, rounded up. */
private const val LETTERS_PER_WORD_SLIP = 4

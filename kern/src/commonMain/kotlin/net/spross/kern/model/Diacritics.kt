package net.spross.kern.model

/**
 * Accented Latin vowels that are pure typing-convenience marks, each mapped to its
 * base letter — read by [net.spross.kern.session.AnswerNormalizer]'s drop-case typo
 * leniency and [net.spross.kern.box.BoxSearch]'s base-letter search leniency, so the
 * two can never disagree on which characters are safe to fold.
 *
 * Deliberately excludes characters that mark a genuinely different letter/phoneme
 * rather than a typing convenience: `ç`/`ñ` (es `año`/`ano` is a real minimal pair),
 * Esperanto `ĉ ĝ ĥ ĵ ŝ ŭ` (separate alphabet letters, not accented c/g/h/j/s/u), and
 * Ukrainian `й`/`ї` (distinct Cyrillic letters that happen to NFD-decompose with a
 * combining mark). `ä`/`ö`/`ü` stay listed for [net.spross.kern.box.BoxSearch]'s sake
 * even though German grading handles them via the `ae`/`oe`/`ue` digraph fold instead
 * — the entries are simply inert there once that fold has run.
 */
val ACCENTED_VOWEL_BASE: Map<Char, Char> = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
    'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
)

/** [ch] itself, or its base letter when [ch] is a listed accented vowel. */
fun baseVowel(ch: Char): Char = ACCENTED_VOWEL_BASE[ch] ?: ch

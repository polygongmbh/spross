package net.spross.kern.model

/** Unicode NFC normalization (no common-stdlib support — platform actuals). */
internal expect fun nfcNormalized(text: String): String

/**
 * The comparison key two spellings are matched on: NFC, trimmed, lowercased.
 *
 * Shared so no caller folds a little differently and disagrees with the next one about
 * whether two words are the same word.
 */
internal fun caseFolded(text: String): String = nfcNormalized(text).trim().lowercase()

/**
 * Typewriter, curly, and the modifier letter — one apostrophe class for everything that
 * compares or joins.
 *
 * An INNER apostrophe belongs to the word (uk ім'я, fr s'habiller) and an elided article
 * ends on one, but which codepoint spells it is typography: alphabet files store U+02BC,
 * a keyboard offers U+0027, autocorrect offers U+2019, and all three mean one letter.
 */
internal val APOSTROPHES: Set<Char> = setOf('\u0027', '\u2019', '\u02bc')

/**
 * Apostrophes folded to U+02BC, length-preserving so a folded index still addresses the
 * original string.
 */
internal fun apostropheFolded(text: String): String =
    if (text.none { it in APOSTROPHES }) text
    else text.map { if (it in APOSTROPHES) '\u02bc' else it }.joinToString("")

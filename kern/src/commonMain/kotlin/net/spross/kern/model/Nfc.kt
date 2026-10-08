package net.spross.kern.model

/** Unicode NFC normalization (no common-stdlib support — platform actuals). */
internal expect fun nfcNormalized(text: String): String

/**
 * The comparison key two spellings are matched on: NFC, stress marks dropped, trimmed, lowercased.
 *
 * Shared so no caller folds a little differently and disagrees with the next one about
 * whether two words are the same word.
 */
internal fun caseFolded(text: String): String = stressFolded(nfcNormalized(text)).trim().lowercase()

/**
 * U+0301 COMBINING ACUTE ACCENT where it survives NFC: a stress mark the catalog writes on a
 * vowel no precomposed letter covers (uk `пі́вніч`). It tells the reader where the stress
 * falls and is no part of the spelling, so nobody is expected to type it.
 */
internal const val STRESS_MARK: Char = '\u0301'

/**
 * [text] without its [STRESS_MARK]s — apply to NFC text only, where every acute a letter is
 * spelled with (es `á`, fr `é`) is already composed into that letter and stays.
 */
internal fun stressFolded(text: String): String =
    if (STRESS_MARK !in text) text else text.filterNot { it == STRESS_MARK }

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

/**
 * Hyphens and every apostrophe class removed outright — not folded to one, gone —
 * so a hyphenated or elided spelling and its plain twin become the same key.
 *
 * Grading strips both already (a card's own [text]/[accepts] entries fold onto one
 * accepted answer); search folds the same two characters so a variant kept only for
 * that reason (`e-mail` beside `email`) still earns its keep, and one kept for neither
 * reason is dead weight a lint can catch.
 */
internal fun hyphensAndApostrophesStripped(text: String): String =
    text.filterNot { it == '-' || it in APOSTROPHES }

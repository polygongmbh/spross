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

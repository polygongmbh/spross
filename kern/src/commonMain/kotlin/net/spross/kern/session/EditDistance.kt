package net.spross.kern.session

import net.spross.kern.model.ACCENTED_VOWEL_BASE
import net.spross.kern.model.baseVowel

/**
 * Optimal-string-alignment Damerau-Levenshtein: insert, delete, substitute,
 * and adjacent transposition each cost 1 — except a substitution between two
 * spellings of the same base vowel ([ACCENTED_VOWEL_BASE]), which is free, so a
 * dropped or wrong diacritic costs nothing however short the word. Only the
 * listed typing-convenience accents are free; `ç`, `ñ`, Esperanto `ĉĝĥĵŝŭ` and
 * Ukrainian `й`/`ї` are distinct letters and stay full price (es "ano"/"año").
 * The comparison strings keep their accents, so this reaches the typo path only
 * — a diacritic miss grades [Match.Typo], never [Match.Exact].
 */
internal fun damerauLevenshtein(a: String, b: String): Int {
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val same = a[i - 1] == b[j - 1] || baseVowel(a[i - 1]) == baseVowel(b[j - 1])
            val cost = if (same) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
    }
    return d[a.length][b.length]
}

package net.spross.kern.trainer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guard for the drill-grading claim "the typo budget never bridges two
 * distinct numbers": a pairwise OSA Damerau-Levenshtein sweep over the
 * cardinal generators, on the grading comparison shape (lowercase, ß→ss,
 * joiners `-'’` dropped), asserting no two distinct number WORDS sit within
 * distance 1 — so the capped drill budget (AnswerNormalizer maxTypoBudget = 1)
 * never accepts one number for another. Digit renderings ("21"/"29") ARE one
 * edit apart at any length; the capped normalizer therefore grades
 * digit-bearing forms exact-only (behavior pinned in AnswerNormalizerTests).
 *
 * AUDITED EXCEPTIONS the sweep found and gates explicitly — budget 1 CAN
 * bridge these word pairs, so a capped drill still accepts one for the other:
 * - sw `nne` (4) ↔ `nane` (8), one insertion — and every tens compound
 *   ("kumi na nne" ↔ "kumi na nane", …: 10 pairs over 0..99);
 * - uk `дев'ять` (9) ↔ `десять` (10), one substitution once the apostrophe
 *   is deleted by the comparison pipeline.
 * Any pair NOT on this list fails the sweep; a vanished known pair fails too
 * (the allowlist must not rot).
 */
class TrainerTypoBridgeGuardTests {

    @Test
    fun germanCardinals0To999NeverWithinOneEdit() {
        assertEquals(emptyList(), sweep((0L..999L).map { GermanNumbers.cardinal(it) }))
    }

    @Test
    fun ukrainianCardinals0To99BridgeOnlyTheKnownNineTenPair() {
        val known = sweep((0L..99L).map { UkrainianNumbers.cardinal(it) })
        assertEquals(listOf("\"дев'ять\" ↔ \"десять\""), known)
    }

    @Test
    fun swahiliCardinals0To99BridgeOnlyTheKnownFourEightPairs() {
        val known = sweep((0L..99L).map { SwahiliNumbers.cardinal(it) })
        assertEquals(10, known.size, "expected the ten nne ↔ nane pairs, got $known")
        assertTrue(known.all { "nne" in it && "nane" in it }, "unexpected pair in $known")
    }

    /**
     * All within-one-edit pairs; fails on any pair beyond the audited allowlist,
     * returns the allowlisted ones it found. Distance ≥ length delta, so only
     * near-length pairs need the full OSA.
     */
    private fun sweep(words: List<String>): List<String> {
        val shapes = words.map(::comparisonShape)
        val offenders = mutableListOf<String>()
        val known = mutableListOf<String>()
        for (i in shapes.indices) {
            for (j in i + 1 until shapes.size) {
                if (abs(shapes[i].length - shapes[j].length) > 1) continue
                if (osa(shapes[i], shapes[j]) > 1) continue
                val pair = "\"${words[i]}\" ↔ \"${words[j]}\""
                if (isKnownBridge(shapes[i], shapes[j])) known += pair else offenders += pair
            }
        }
        assertTrue(offenders.isEmpty(), "number words within one edit beyond the allowlist: $offenders")
        return known
    }

    /** Same head words + an allowlisted final-word pair (covers the tens compounds). */
    private fun isKnownBridge(a: String, b: String): Boolean =
        a.substringBeforeLast(' ', "") == b.substringBeforeLast(' ', "") &&
            setOf(a.substringAfterLast(' '), b.substringAfterLast(' ')) in KNOWN_BRIDGES

    /** The AnswerNormalizer comparison shape of a generated word. */
    private fun comparisonShape(word: String): String =
        word.lowercase().replace("ß", "ss").filterNot { it in "-'’" }

    /** Optimal-string-alignment Damerau-Levenshtein (same algorithm grading uses). */
    private fun osa(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    private companion object {
        val KNOWN_BRIDGES = listOf(setOf("nne", "nane"), setOf("девять", "десять"))
    }
}

package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * Guard for the drill-grading claim "the typo budget never bridges two distinct
 * numbers": a pairwise sweep over the cardinal generators asking the REAL drill
 * normalizer (articleLeniency = false, maxTyposPerWord = 1) to grade each number
 * as an answer to every other, asserting it says Wrong. Grading the actual
 * verdict rather than a distance is what makes the sweep hold for the word-wise
 * budget, where a multi-word number ("kumi na nne") may fumble once per word and
 * global distance no longer bounds what is accepted.
 *
 * Digit renderings ("21"/"29") ARE one edit apart; a word carrying a digit
 * therefore grades exact-only (behavior pinned in AnswerNormalizerTests).
 *
 * AUDITED EXCEPTIONS the sweep found and gates explicitly — one slip DOES bridge
 * these word pairs, so a drill still accepts one for the other:
 * - sw `nne` (4) ↔ `nane` (8), one insertion — and every tens compound
 *   ("kumi na nne" ↔ "kumi na nane", …: 10 pairs over 0..99);
 * - uk `дев'ять` (9) ↔ `десять` (10), one substitution once the apostrophe
 *   is deleted by the comparison pipeline;
 * - en `eight` (8) ↔ `eighty` (80), one insertion, and every hundreds
 *   compound ("one hundred eight" ↔ "one hundred eighty");
 * - es `sesenta` (60) ↔ `setenta` (70), one deletion, and every compound
 *   built on them ("sesenta y uno" ↔ "setenta y uno").
 * Any pair NOT on this list fails the sweep; a vanished known pair fails too
 * (the allowlist must not rot).
 */
class TrainerTypoBridgeGuardTests {

    private val catalog = Fixture.catalog()

    @Test
    fun germanCardinals0To999NeverBridge() {
        assertEquals(emptyList(), sweep("de", (0L..999L).map { GermanNumbers.cardinal(it) }))
    }

    @Test
    fun ukrainianCardinals0To99BridgeOnlyTheKnownNineTenPair() {
        val known = sweep("uk", (0L..99L).map { UkrainianNumbers.cardinal(it) })
        assertEquals(listOf("\"дев'ять\" ↔ \"десять\""), known)
    }

    @Test
    fun englishCardinals0To999BridgeOnlyTheKnownEightEightyPairs() {
        val known = sweep("en", (0L..999L).map { EnglishNumbers.cardinal(it) })
        assertEquals(10, known.size, "expected the ten eight ↔ eighty pairs, got $known")
        assertTrue(known.all { "eight\"" in it && "eighty\"" in it }, "unexpected pair in $known")
    }

    @Test
    fun spanishCardinals0To999BridgeOnlyTheKnownSixtySeventyPairs() {
        val known = sweep("es", (0L..999L).map { SpanishNumbers.cardinal(it) })
        assertEquals(100, known.size, "expected the hundred sesenta ↔ setenta pairs, got $known")
        assertTrue(known.all { "sesenta" in it && "setenta" in it }, "unexpected pair in $known")
    }

    @Test
    fun swahiliCardinals0To99BridgeOnlyTheKnownFourEightPairs() {
        val known = sweep("sw", (0L..99L).map { SwahiliNumbers.cardinal(it) })
        assertEquals(10, known.size, "expected the ten nne ↔ nane pairs, got $known")
        assertTrue(known.all { "nne" in it && "nane" in it }, "unexpected pair in $known")
    }

    /**
     * Every pair the drill normalizer accepts one for the other; fails on any pair
     * beyond the audited allowlist, returns the allowlisted ones it found. The
     * word-wise budget spends at most one slip per word, so only pairs within that
     * many edits need grading — the cheap distance filter keeps the sweep O(n²)
     * in comparisons rather than in full gradings.
     */
    private fun sweep(language: String, words: List<String>): List<String> {
        val normalizer = AnswerNormalizer(
            languageInfo(language),
            articleLeniency = false,
            maxTyposPerWord = 1,
        )
        val shapes = words.map(::comparisonShape)
        val offenders = mutableListOf<String>()
        val known = mutableListOf<String>()
        for (i in shapes.indices) {
            for (j in i + 1 until shapes.size) {
                val reach = maxOf(wordCount(shapes[i]), wordCount(shapes[j]))
                if (kotlin.math.abs(shapes[i].length - shapes[j].length) > reach) continue
                if (osa(shapes[i], shapes[j]) > reach) continue
                if (normalizer.evaluate(words[i], card(language, words[j])) == Match.Wrong) continue
                val pair = "\"${words[i]}\" ↔ \"${words[j]}\""
                if (isKnownBridge(shapes[i], shapes[j])) known += pair else offenders += pair
            }
        }
        assertTrue(offenders.isEmpty(), "numbers a drill accepts for one another: $offenders")
        return known
    }

    /**
     * The two readings differ in exactly one word and that word pair is
     * allowlisted — which is what a compound of a known pair looks like,
     * wherever the pair sits ("sesenta y uno" ↔ "setenta y uno").
     */
    private fun isKnownBridge(a: String, b: String): Boolean {
        val left = a.split(' ')
        val right = b.split(' ')
        if (left.size != right.size) return false
        val differing = left.indices.filter { left[it] != right[it] }
        return differing.size == 1 && setOf(left[differing[0]], right[differing[0]]) in KNOWN_BRIDGES
    }

    /** The AnswerNormalizer comparison shape of a generated word. */
    private fun comparisonShape(word: String): String =
        word.lowercase().replace("ß", "ss").filterNot { it in "-'’" }

    private fun wordCount(shape: String): Int = shape.split(' ').count { it.isNotEmpty() }

    /** The synthetic one-answer card drills grade against (TrainerSessionView+Grading). */
    private fun card(language: String, text: String): Card {
        val side = Realization(lang = language, text = text)
        return Card(
            id = "drill", kind = CardKind.Noun, area = "drill", emoji = null, seedIndex = 0,
            components = emptyList(), feminineOf = null,
            source = side, target = side, promptFeminineMarker = false,
        )
    }

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

    /**
     * The fixture catalog's entry, or a bare one for a language it does not
     * carry — a cardinal has no article and no citation prefix, so only the
     * typo budget decides the sweep.
     */
    private fun languageInfo(language: String): LanguageInfo =
        catalog.languages[language] ?: LanguageInfo(language, language, language, "🏳️")

    private companion object {
        val KNOWN_BRIDGES = listOf(
            setOf("nne", "nane"),
            setOf("девять", "десять"),
            setOf("eight", "eighty"),
            setOf("sesenta", "setenta"),
        )
    }
}

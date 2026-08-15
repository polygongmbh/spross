package net.spross.kern.trainer

import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * The pairwise sweep behind the drill-grading claim "the typo budget never bridges two
 * distinct numbers", shared by the cardinal guard ([TrainerTypoBridgeGuardTests]) and the
 * forms guard ([TrainerFormsTypoBridgeGuardTests]).
 *
 * It asks the REAL drill normalizer (articleLeniency = false, maxTyposPerWord = 1) to grade
 * each prompt's readings as an answer to every other prompt's, and fails on any pair it does
 * not call Wrong. Grading the actual verdict rather than a distance is what makes the sweep
 * hold for the word-wise budget, where a multi-word reading ("kumi na nne") may fumble once
 * per word and global distance no longer bounds what is accepted.
 *
 * Digit renderings ("21"/"29") ARE one edit apart; a word carrying a digit therefore grades
 * exact-only (behavior pinned in AnswerNormalizerTests).
 */
internal object TypoBridgeSweep {

    /**
     * One prompt as its drill grades it: every reading accepted for ONE value. Readings of
     * the same prompt are never compared with each other — they are the same number.
     */
    data class Prompt(val readings: List<String>)

    /** One reading per prompt, the cardinal drills' shape. */
    fun single(readings: List<String>): List<Prompt> = readings.map { Prompt(listOf(it)) }

    /**
     * AUDITED EXCEPTIONS the sweep found and gates explicitly — one slip DOES bridge
     * these word pairs, so a drill still accepts one for the other:
     * - sw `nne` (4) ↔ `nane` (8), one insertion;
     * - uk `дев'ять` (9) ↔ `десять` (10), one substitution once the apostrophe
     *   is deleted by the comparison pipeline;
     * - en `eight` (8) ↔ `eighty` (80), one insertion;
     * - es `sesenta` (60) ↔ `setenta` (70), one deletion;
     * - fr `six` (6) ↔ `dix` (10), one substitution — and again welded, because a French
     *   compound hyphenates and the pipeline deletes the hyphen: `soixante-six` ↔
     *   `soixante-dix` and `quatre-vingt-six` ↔ `quatre-vingt-dix` are one word each by
     *   the time they are compared.
     * - it `ventotto` (28) ↔ `centotto` (108), one substitution — two elisions of the
     *   same `otto` onto tens and hundreds that differ in their first letter alone.
     * - eo `ses` (6) ↔ `sep` (7), one substitution — and once more welded into the ten
     *   and the hundred, which are single words there (`sesdek`/`sepdek`,
     *   `sescent`/`sepcent`) and therefore differ in a word the split cannot reach into.
     * Every compound built on one of them bridges too ("kumi na nne" ↔ "kumi na nane",
     * "sesenta y uno" ↔ "setenta y uno"), which is what [isKnownBridge] recognizes.
     */
    val KNOWN_BRIDGES = listOf(
        setOf("nne", "nane"),
        setOf("девять", "десять"),
        setOf("eight", "eighty"),
        setOf("sesenta", "setenta"),
        setOf("six", "dix"),
        setOf("soixantesix", "soixantedix"),
        setOf("quatrevingtsix", "quatrevingtdix"),
        setOf("ventotto", "centotto"),
        setOf("ses", "sep"),
        setOf("sesdek", "sepdek"),
        setOf("sescent", "sepcent"),
    )

    /**
     * Every pair the drill normalizer accepts one for the other; fails on any pair beyond the
     * audited allowlist, returns the allowlisted ones it found — so a vanished known pair
     * fails a caller's assertion too, and the allowlist cannot rot.
     *
     * The word-wise budget spends at most one slip per word, so only readings within that
     * many edits need grading — the cheap distance filter keeps the sweep O(n²) in
     * comparisons rather than in full gradings.
     */
    fun run(
        language: String,
        prompts: List<Prompt>,
        bridges: List<Set<String>> = KNOWN_BRIDGES,
    ): List<String> {
        val normalizer = AnswerNormalizer(
            languageInfo(language),
            articleLeniency = false,
            maxTyposPerWord = 1,
        )
        val owner = prompts.flatMapIndexed { index, prompt -> prompt.readings.map { index } }
        val readings = prompts.flatMap { it.readings }
        val shapes = readings.map(::comparisonShape)
        val offenders = mutableListOf<String>()
        val known = mutableListOf<String>()
        for (i in shapes.indices) {
            for (j in i + 1 until shapes.size) {
                if (owner[i] == owner[j]) continue
                val reach = maxOf(wordCount(shapes[i]), wordCount(shapes[j]))
                if (kotlin.math.abs(shapes[i].length - shapes[j].length) > reach) continue
                if (osa(shapes[i], shapes[j]) > reach) continue
                if (normalizer.evaluate(readings[i], card(language, readings[j])) == Match.Wrong) continue
                val pair = "\"${readings[i]}\" ↔ \"${readings[j]}\""
                if (isKnownBridge(shapes[i], shapes[j], bridges)) known += pair else offenders += pair
            }
        }
        assertTrue(offenders.isEmpty(), "numbers a drill accepts for one another: $offenders")
        return known
    }

    /**
     * Every word the two readings differ in is an allowlisted pair — which is what a
     * compound of a known pair looks like, wherever the pair sits ("sesenta y uno" ↔
     * "setenta y uno"). More than one such word can differ at once, because the budget is
     * spent per word: a decimal reads its digits one at a time, so "nne nukta nne" and
     * "nane nukta nane" carry the same twin twice and bridge on two independent slips.
     * A pair differing in NO word is two prompts with an identical reading, never allowed.
     *
     * An entry may also name the two readings whole, for the twins a word split cannot
     * see: Spanish "un décimo" (1/10) and "undécimo" (11th) differ only in a space.
     */
    private fun isKnownBridge(a: String, b: String, bridges: List<Set<String>>): Boolean {
        if (setOf(a, b) in bridges) return true
        val left = a.split(' ')
        val right = b.split(' ')
        if (left.size != right.size) return false
        val differing = left.indices.filter { left[it] != right[it] }
        return differing.isNotEmpty() && differing.all { setOf(left[it], right[it]) in bridges }
    }

    /** The AnswerNormalizer comparison shape of a generated word. */
    fun comparisonShape(word: String): String =
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

    private val catalog by lazy { Fixture.catalog() }

    /**
     * The fixture catalog's entry, or a bare one for a language it does not
     * carry — a number reading has no article and no citation prefix, so only
     * the typo budget decides the sweep.
     */
    private fun languageInfo(language: String): LanguageInfo =
        catalog.languages[language] ?: LanguageInfo(language, language, language, "🏳️")
}

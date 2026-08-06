package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * The clock's half of [TrainerTypoBridgeGuardTests]: every one of the 1440 times,
 * in every authored language, graded against every OTHER time's accepted set by the
 * REAL drill normalizer (articleLeniency = false, maxTyposPerWord = 1). A reading the
 * drill takes for two different times marks a wrong answer right, so the sweep must
 * come back empty apart from the audited word pairs gated below.
 *
 * What is NOT a collision: the same reading standing for a time and that time plus
 * twelve hours. Every authored clock is a 12-hour cycle, so "quarter to five" IS the
 * right answer to 16:45 and the right answer to 04:45 — the language leaves the cycle
 * open and the drill may not close it. Only same-cycle pairs are graded.
 *
 * Pairs are bucketed by word count and word length before grading: the word-wise
 * budget spends at most one slip per word, so readings that cannot line up word for
 * word can never bridge, and the sweep stays tractable.
 */
class ClockCollisionSweepTests {

    private val catalog = Fixture.catalog()

    @Test
    fun germanClockNeverAcceptsOneTimeForAnother() = sweep("de", gated = emptyList())

    @Test
    fun englishClockNeverAcceptsOneTimeForAnother() = sweep("en", gated = emptyList())

    /** The quarter word and the number four are one transposition apart. */
    @Test
    fun spanishClockBridgesOnlyTheQuarterFourPair() =
        sweep("es", gated = listOf("[cuarto, cuatro]"))

    /** `nne` ↔ `nane` reaches the clock through both the saa hour and the minute. */
    @Test
    fun swahiliClockBridgesOnlyTheKnownFourEightPair() =
        sweep("sw", gated = listOf("[nane, nne]"))

    /** `дев'ять` ↔ `десять` reaches the clock as the minute count and as every hour ordinal case. */
    @Test
    fun ukrainianClockBridgesOnlyTheKnownNineTenPairs() = sweep(
        "uk",
        gated = listOf(
            "[девята, десята]", "[девятої, десятої]", "[девяту, десяту]",
            "[девять, десять]", "[девятій, десятій]",
        ),
    )

    private data class Form(val time: String, val cycle: Int, val text: String, val shape: String)

    private fun sweep(language: String, gated: List<String>) {
        val normalizer = AnswerNormalizer(
            languageInfo(language),
            articleLeniency = false,
            maxTyposPerWord = 1,
        )
        val forms = mutableListOf<Form>()
        for (h in 0..23) {
            for (m in 0..59) {
                val task = Trainer.clock(h, m, language)
                for (text in task.accepted.distinct()) {
                    forms += Form(task.prompt, (h % 12) * 60 + m, text, normalizer.normalize(text))
                }
            }
        }
        val offenders = sortedSetOf<String>()
        val found = sortedSetOf<String>()
        for ((_, bucket) in forms.groupBy { it.shape.count { c -> c == ' ' } }) {
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    if (a.cycle == b.cycle) continue
                    if (!couldBridge(a.shape, b.shape)) continue
                    if (normalizer.evaluate(a.text, card(language, b.text)) == Match.Wrong) continue
                    val pairs = differingWordPairs(a.shape, b.shape)
                    if (pairs.isNotEmpty() && pairs.all { it in KNOWN_BRIDGES }) {
                        found += pairs.map { it.sorted().toString() }
                    } else {
                        offenders += "${a.time} \"${a.text}\" ↔ ${b.time} \"${b.text}\""
                    }
                }
            }
        }
        assertEquals(
            emptyList(), offenders.toList(),
            "$language: readings the drill accepts for two different times",
        )
        // The gate must not rot: a pair that stops colliding is a stale entry.
        assertEquals(gated, found.toList(), "$language: audited pairs the sweep still finds")
    }

    /** Word-for-word, one slip each: lengths may differ by at most one per word. */
    private fun couldBridge(a: String, b: String): Boolean {
        val left = a.split(' ')
        val right = b.split(' ')
        if (left.size != right.size) return false
        return left.indices.all { kotlin.math.abs(left[it].length - right[it].length) <= 1 }
    }

    /** Every word position two readings of equal length disagree in. */
    private fun differingWordPairs(a: String, b: String): List<Set<String>> {
        val left = a.split(' ')
        val right = b.split(' ')
        if (left.size != right.size) return emptyList()
        return left.indices.filter { left[it] != right[it] }.map { setOf(left[it], right[it]) }
    }

    private fun card(language: String, text: String): Card {
        val side = Realization(lang = language, text = text)
        return Card(
            id = "drill", kind = CardKind.Noun, area = "drill", emoji = null, seedIndex = 0,
            components = emptyList(), feminineOf = null,
            source = side, target = side, promptFeminineMarker = false,
        )
    }

    private fun languageInfo(language: String): LanguageInfo =
        catalog.languages[language] ?: LanguageInfo(language, language, language, "🏳️")

    private companion object {
        /**
         * Word pairs one slip apart that a drill therefore takes for each other,
         * audited and gated exactly as [TrainerTypoBridgeGuardTests] gates them for
         * the cardinals. `nne`/`nane` and `дев'ять`/`десять` are that file's pairs
         * reaching the clock as minute counts, saa hours and hour ordinals (the
         * Ukrainian ordinal cases are listed each on their own — the comparison
         * pipeline has already deleted the apostrophe). `cuarto`/`cuatro` is the
         * clock's own: the quarter word and the number four are a transposition
         * apart, so :15 and :04 read alike under a one-slip budget.
         */
        val KNOWN_BRIDGES = listOf(
            setOf("nne", "nane"),
            setOf("cuarto", "cuatro"),
            setOf("девять", "десять"),
            setOf("девята", "десята"),
            setOf("девяту", "десяту"),
            setOf("девятій", "десятій"),
            setOf("девятої", "десятої"),
        )
    }
}

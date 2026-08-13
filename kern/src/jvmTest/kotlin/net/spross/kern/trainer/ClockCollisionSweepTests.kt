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
 * The clock's half of [TrainerTypoBridgeGuardTests]: every one of the 1440 times, in
 * every authored language, graded against every OTHER time's accepted set by the REAL
 * drill normalizer (articleLeniency = false, maxTyposPerWord = 1). A reading the drill
 * takes for two different times marks a wrong answer right, so the sweep must come back
 * empty apart from the audited word pairs gated below.
 *
 * What is NOT a collision: a PERIOD-LESS reading standing for a time and that time plus
 * twelve hours. Every authored clock is a 12-hour cycle, so "quarter to five" IS the
 * right answer to 16:45 and the right answer to 04:45 — the language leaves the cycle
 * open and the drill may not close it. A reading that names the part of the day does
 * close it, and [dayPartReadingsCloseTheTwelveHourCycle] holds it to that.
 *
 * Candidate pairs are found by word count and by the single-deletion neighborhoods of
 * their outer words: under the drill rule two readings bridge only if every aligned word
 * pair is within one edit, and two words are within one edit only if their deletion
 * neighborhoods intersect (transposition included — `cuarto` and `cuatro` both reach
 * `cuato`). Only survivors are graded.
 *
 * What that indexing cannot reach: readings of DIFFERENT word counts, which the
 * normalizer grades on one whole-form budget instead (`AnswerNormalizer.withinBudget`).
 * That path has been probed clean across all five languages, but it is not held here —
 * closing the gap means a second index keyed on the whole shape's deletion neighborhood.
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

    /**
     * The day part is what tells 04:45 from 16:45, so a reading carrying one must be
     * refused for the time twelve hours away — a crossed mapping would teach the
     * learner that the small hours are the afternoon.
     */
    @Test
    fun dayPartReadingsCloseTheTwelveHourCycle() {
        for ((language, pack) in trainerPacks) {
            val parts = pack.clockDayParts
            val normalizer = drillNormalizer(language)
            val offenders = sortedSetOf<String>()
            for (h in 0..11) {
                for (m in 0..59) {
                    val here = Trainer.clock(h, m, language)
                    val twelveOn = Trainer.clock(h + 12, m, language)
                    for ((a, b) in listOf(here to twelveOn, twelveOn to here)) {
                        for (form in a.accepted) {
                            if (parts.none { it in form.lowercase() }) continue
                            if (normalizer.evaluate(form, card(language, b.accepted)) == Match.Wrong) continue
                            offenders += "${a.prompt} \"$form\" accepted at ${b.prompt}"
                        }
                    }
                }
            }
            assertEquals(
                emptyList(), offenders.toList(),
                "$language: day-part readings that still answer the other half of the day " +
                    "(markers: ${parts.sorted()})",
            )
        }
    }

    private data class Probe(
        val time: String,
        val cycle: Int,
        val text: String,
        /** The form itself, or a remainder a leading-word rule could reduce it to. */
        val shape: String,
    )

    private fun sweep(language: String, gated: List<String>) {
        val normalizer = drillNormalizer(language)
        val whole = mutableListOf<Probe>()
        val reduced = mutableListOf<Probe>()
        for (h in 0..23) {
            for (m in 0..59) {
                val task = Trainer.clock(h, m, language)
                for (text in task.accepted.distinct()) {
                    val shape = normalizer.normalize(text)
                    val probe = Probe(task.prompt, (h % 12) * 60 + m, text, shape)
                    whole += probe
                    stripChain(shape).mapTo(reduced) { probe.copy(shape = it) }
                }
            }
        }

        val offenders = sortedSetOf<String>()
        val found = sortedSetOf<String>()
        for ((a, b) in candidates(whole, whole)) {
            if (normalizer.evaluate(a.text, card(language, listOf(b.text))) == Match.Wrong) continue
            val pairs = differingWordPairs(a.shape, b.shape)
            if (pairs.isNotEmpty() && pairs.all { it in KNOWN_BRIDGES }) {
                found += pairs.map { it.sorted().toString() }
            } else {
                offenders += "${a.time} \"${a.text}\" ↔ ${b.time} \"${b.text}\""
            }
        }
        assertEquals(
            emptyList(), offenders.toList(),
            "$language: readings the drill accepts for two different times",
        )
        // The gate must not rot: a pair that stops colliding is a stale entry.
        assertEquals(gated, found.toList(), "$language: audited pairs the sweep still finds")

        // A reading must not answer another time once its leading words are peeled off
        // either — the regression guard for the stray-word rescue the drill path dropped.
        val peeled = sortedSetOf<String>()
        for ((a, b) in candidates(reduced, whole)) {
            if (normalizer.evaluate(a.text, card(language, listOf(b.text))) == Match.Wrong) continue
            peeled += "${a.time} \"${a.text}\" ↔ ${b.time} \"${b.text}\""
        }
        assertEquals(
            emptyList(), peeled.toList(),
            "$language: readings that answer another time once a leading word is dropped",
        )
    }

    /**
     * Cross-cycle pairs worth grading: equal word count, every aligned word within one
     * edit. Forms are indexed by the deletion neighborhoods of their first and last
     * words, which no bridging pair can disagree on, so the join stays near-linear
     * where the plain quadratic would not fit on the fast gate.
     */
    private fun candidates(left: List<Probe>, right: List<Probe>): List<Pair<Probe, Probe>> {
        val index = mutableMapOf<Pair<String, String>, MutableList<Probe>>()
        for (probe in right) {
            for (key in outerKeys(probe.shape)) index.getOrPut(key) { mutableListOf() } += probe
        }
        val out = mutableListOf<Pair<Probe, Probe>>()
        val seen = mutableSetOf<Pair<String, String>>()
        for (a in left) {
            val reached = mutableSetOf<Probe>()
            for (key in outerKeys(a.shape)) index[key]?.let(reached::addAll)
            for (b in reached) {
                if (a.cycle == b.cycle || !couldBridge(a.shape, b.shape)) continue
                if (!seen.add(a.text to b.text)) continue
                out += a to b
            }
        }
        return out
    }

    /** (first word, last word) deletion-neighborhood keys — one entry per combination. */
    private fun outerKeys(shape: String): List<Pair<String, String>> {
        val words = shape.split(' ')
        val head = deletions(words.first())
        val tail = deletions(words.last())
        return head.flatMap { h -> tail.map { t -> h to t } }
    }

    /** The word itself plus every single-character deletion of it. */
    private fun deletions(word: String): Set<String> =
        word.indices.mapTo(mutableSetOf(word)) { word.removeRange(it, it + 1) }

    /** Word-for-word, one slip each: lengths may differ by at most one per word. */
    private fun couldBridge(a: String, b: String): Boolean {
        val left = a.split(' ')
        val right = b.split(' ')
        if (left.size != right.size) return false
        return left.indices.all { kotlin.math.abs(left[it].length - right[it].length) <= 1 }
    }

    /** Every remainder a stray-leading-word rule could reduce a reading to. */
    private fun stripChain(shape: String): List<String> {
        val out = mutableListOf<String>()
        var words = shape.split(' ')
        while (words.size >= 2 && words.first().length <= 4 && words.first().all { it.isLetter() }) {
            words = words.drop(1)
            out += words.joinToString(" ")
        }
        return out
    }

    /** Every word position two readings of equal length disagree in. */
    private fun differingWordPairs(a: String, b: String): List<Set<String>> {
        val left = a.split(' ')
        val right = b.split(' ')
        if (left.size != right.size) return emptyList()
        return left.indices.filter { left[it] != right[it] }.map { setOf(left[it], right[it]) }
    }

    private fun drillNormalizer(language: String) = AnswerNormalizer(
        catalog.languages[language] ?: LanguageInfo(language, language, language, "🏳️"),
        articleLeniency = false,
        maxTyposPerWord = 1,
    )

    private fun card(language: String, forms: List<String>): Card {
        val side = Realization(lang = language, text = forms.first(), synonyms = forms.drop(1))
        return Card(
            id = "drill", kind = CardKind.Noun, area = "drill", emoji = null, seedIndex = 0,
            components = emptyList(), feminineOf = null,
            source = side, target = side, promptFeminineMarker = false,
        )
    }

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

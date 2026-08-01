package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Card

/**
 * The guarantee behind [CatalogAnswerGrader], asserted against the real catalog:
 * no word the catalog teaches is ever a forgiven SLIP of another concept's answer.
 * sw `kufunga` (abschließen) sits one edit from `kufungua` (aufschließen), so the
 * one-card typo budget graded each as the other — the very confusion a learner
 * needs told apart.
 *
 * Relational, not pinned: the pairs come out of the join, so content edits move
 * the sweep instead of breaking it.
 */
class RealCatalogGradingTest {
    private val catalog get() = RealCatalog.catalog

    @Test
    fun noCatalogWordIsEverAForgivenSlipOfAnother() {
        for (target in listOf("en", "es", "sw", "uk")) {
            val cards = catalog.join("de", target)
            val normalizer = AnswerNormalizer(catalog.languages.getValue(target))
            val grader = CatalogAnswerGrader(normalizer, cards)
            val forms = cards.map { normalizer.normalize(it.target.text) }

            var examined = 0
            for ((i, card) in cards.withIndex()) {
                for ((j, other) in cards.withIndex()) {
                    if (i == j || forms[i] == forms[j]) continue
                    // why: §3 keeps the base concept's word lenient on a feminine
                    // card on purpose — that demotion is the one wanted typo.
                    if (other.id == card.feminineOf) continue
                    if (!near(forms[i], forms[j])) continue
                    examined++
                    val verdict = grader.grade(other.target.text, card)
                    assertFalse(
                        verdict is Match.Typo,
                        "de→$target: \"${other.target.text}\" (${other.id}) " +
                            "graded as a typo of ${card.id} (\"${card.target.text}\")",
                    )
                }
            }
            // Non-vacuity: every language does carry near-twins to grade.
            assertTrue(examined > 0, "de→$target swept no near pairs at all")
        }
    }

    /** The named pair, end to end on the shipping catalog. */
    @Test
    fun theOtherWordIsNamedWithItsOwnMeaning() {
        val cards = catalog.join("de", "sw")
        val normalizer = AnswerNormalizer(catalog.languages.getValue("sw"))
        val lock = cards.byTargetText("kufunga")
        val unlock = cards.byTargetText("kufungua")
        // One card at a time the missing "u" is inside the budget — the bug.
        assertIs<Match.Typo>(normalizer.evaluate(lock.target.text, unlock))

        val verdict = CatalogAnswerGrader(normalizer, cards).grade(lock.target.text, unlock)
        assertIs<Match.OtherWord>(verdict)
        assertFalse(verdict.meanings.isEmpty(), "the other word must carry its meaning")
        assertFalse(
            verdict.meanings.any { it == unlock.source.text },
            "naming the prompted meaning would give the answer away: ${verdict.meanings}",
        )
    }

    private fun List<Card>.byTargetText(text: String): Card =
        firstOrNull { it.target.text == text }
            ?: throw AssertionError("no de→sw card answers \"$text\"")

    /**
     * Within reach of any typo budget the formula can hand out (~⅙ of letters),
     * so the sweep grades only the pairs that could bridge — with a margin.
     */
    private fun near(a: String, b: String): Boolean {
        val budget = maxOf(1, maxOf(a.length, b.length) / 6) + 1
        if (kotlin.math.abs(a.length - b.length) > budget) return false
        return distance(a, b, budget) <= budget
    }

    /** Optimal-string-alignment distance, abandoned once it passes [limit]. */
    private fun distance(a: String, b: String, limit: Int): Int {
        var previous = IntArray(b.length + 1) { it }
        var beforePrevious = IntArray(b.length + 1)
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowBest = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, beforePrevious[j - 2] + 1)
                }
                current[j] = value
                rowBest = minOf(rowBest, value)
            }
            if (rowBest > limit) return limit + 1
            beforePrevious = previous
            previous = current
        }
        return previous[b.length]
    }
}

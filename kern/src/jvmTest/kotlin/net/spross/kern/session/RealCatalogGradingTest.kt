package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
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
        for (target in listOf("en", "eo", "es", "fr", "it", "sw", "uk")) {
            val cards = catalog.join("de", target)
            val language = catalog.languages.getValue(target)
            val normalizer = AnswerNormalizer(language)
            val grader = CatalogAnswerGrader(normalizer, cards)
            val forms = cards.map { normalizer.normalize(it.target.text) }
            // A fumbled article, the one stray token the peel reads back: one slip off a
            // listed one, since an article typed correctly is stripped before grading and
            // never reaches the recovery. Null where the language lists none — nothing is
            // peeled there at all, and a token in front of a word is then simply a
            // different string for the ordinary budget to rule on.
            val stray = language.articles.firstOrNull()?.let { it.dropLast(1) + "x" }

            var examined = 0
            var bridged = 0
            for ((i, card) in cards.withIndex()) {
                for ((j, other) in cards.withIndex()) {
                    if (i == j || forms[i] == forms[j]) continue
                    // why: §3 keeps the base concept's word lenient on a feminine
                    // card on purpose — that demotion is the one wanted typo.
                    if (other.id == card.feminineOf) continue
                    // why: a form the prompted card accepts ITSELF is not a confusion to
                    // guard — es `talk` lists `hablar` beside `charlar`, so writing it is
                    // right here and right for `speak`, article fumbled or not.
                    if (normalizer.evaluate(other.target.text, card) == Match.Exact) continue
                    if (!near(forms[i], forms[j])) continue
                    examined++
                    val verdict = grader.grade(other.target.text, card)
                    assertFalse(
                        verdict is Match.Typo,
                        "de→$target: \"${other.target.text}\" (${other.id}) " +
                            "graded as a typo of ${card.id} (\"${card.target.text}\")",
                    )
                    // The same word behind a fumbled article: the normalizer peels the
                    // stray token and grades the rest, so the withdrawal has to see the
                    // remainder rather than the string that was typed.
                    if (stray != null) {
                        val prefixed = "$stray ${other.target.text}"
                        val peeledVerdict = grader.grade(prefixed, card)
                        if (peeledVerdict is Match.OtherWord) bridged++
                        assertFalse(
                            peeledVerdict is Match.Typo,
                            "de→$target: \"$prefixed\" (${other.id}) " +
                                "graded as a typo of ${card.id} (\"${card.target.text}\")",
                        )
                    }
                }
            }
            // Non-vacuity: every language does carry near-twins to grade.
            assertTrue(examined > 0, "de→$target swept no near pairs at all")
            // And the prefixed pass really reaches the recovery, rather than dying on
            // the length pre-filter and proving nothing.
            if (stray != null) {
                assertTrue(bridged > 0, "de→$target never named the word behind the stray token")
            }
        }
    }

    /**
     * The citation form the reveal teaches — `grammar.gender` plus the text — is the
     * spelling a learner copies back, so producing it has to grade Exact. It could not
     * for the es pluralia tantum while they stored a singular `el`/`la` for the
     * convention's sake: the article-mismatch demotion saw `los auriculares` disagree
     * with `el` and marked the one right answer a typo. They carry their real article
     * now, and this is the rule that keeps every gendered card honest, in any language.
     */
    @Test
    fun everyGenderedCardAcceptsTheCitationFormItTeaches() {
        var examined = 0
        for (source in catalog.languages.keys) {
            for (target in catalog.availableTargets(source).map { it.code }) {
                val normalizer = AnswerNormalizer(catalog.languages.getValue(target))
                for (card in catalog.join(source, target)) {
                    val gender = card.target.grammar["gender"] ?: continue
                    // why: an elided article writes onto its noun — the citation an
                    // it/fr reveal teaches is "l'acqua", never a spaced "l' acqua",
                    // and the authored elided variant is what accepts it.
                    val citation =
                        if (gender.endsWith("'")) "$gender${card.target.text}" else "$gender ${card.target.text}"
                    examined++
                    assertEquals(
                        Match.Exact,
                        normalizer.evaluate(citation, card),
                        "$source→$target ${card.id}: \"$citation\" is not graded exact",
                    )
                }
            }
        }
        assertTrue(examined > 0, "no gendered card was swept at all")
    }

    /**
     * The elision ruling, end to end on the shipping catalog (content brief): the
     * tokenizer deletes apostrophes, so an elided article is one token that can never
     * be stripped or read back — the l'-noun therefore authors gender `l'` and carries
     * its elided surface as a variant, and a correct typed answer is never demoted.
     */
    @Test
    fun theElidedItalianArticleNeverDemotesACorrectAnswer() {
        val cards = catalog.join("de", "it")
        val normalizer = AnswerNormalizer(catalog.languages.getValue("it"))
        val water = cards.first { it.id == "water" }
        assertEquals(Match.Exact, normalizer.evaluate("acqua", water))
        // One token via the authored variant; no leading article exists to read back.
        assertEquals(Match.Exact, normalizer.evaluate("l'acqua", water))
        // A spaced article IS readable — and `la` disagrees with the authored `l'`.
        assertIs<Match.Typo>(normalizer.evaluate("la acqua", water))
        // Spaced articles need no variant: stripping and the read-back just work.
        val sugar = cards.first { it.id == "sugar" }
        assertEquals(Match.Exact, normalizer.evaluate("zucchero", sugar))
        assertEquals(Match.Exact, normalizer.evaluate("lo zucchero", sugar))
    }

    /** The same ruling in French: l'eau grades Exact, la eau demotes, le sucre strips. */
    @Test
    fun theElidedFrenchArticleNeverDemotesACorrectAnswer() {
        val cards = catalog.join("de", "fr")
        val normalizer = AnswerNormalizer(catalog.languages.getValue("fr"))
        val water = cards.first { it.id == "water" }
        assertEquals(Match.Exact, normalizer.evaluate("eau", water))
        assertEquals(Match.Exact, normalizer.evaluate("l'eau", water))
        assertIs<Match.Typo>(normalizer.evaluate("la eau", water))
        val sugar = cards.first { it.id == "sugar" }
        assertEquals(Match.Exact, normalizer.evaluate("sucre", sugar))
        assertEquals(Match.Exact, normalizer.evaluate("le sucre", sugar))
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

    /**
     * The screenshot, end to end on the shipping catalog: Swahili lists no article, so
     * "muda nini" keeps its first word and stays a miss — where peeling it left "nini",
     * itself the catalog's word for "was", one slip from "lini".
     */
    @Test
    fun aStrayWordIsNeverEatenInALanguageWithoutArticles() {
        val cards = catalog.join("de", "sw")
        val normalizer = AnswerNormalizer(catalog.languages.getValue("sw"))
        val whenCard = cards.byTargetText("lini")
        assertEquals(Match.Wrong, normalizer.evaluate("muda nini", whenCard))
        assertEquals(Match.Wrong, CatalogAnswerGrader(normalizer, cards).grade("muda nini", whenCard))
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

package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Card

/**
 * [CatalogAnswerGrader] against the real catalog: the known confusion is told apart
 * (sw `kufunga`/`kufungua`), gendered cards accept their citation forms, and the
 * article peel neither eats a stray word nor loosens a language without articles.
 */
class RealCatalogGradingTest {
    private val catalog get() = RealCatalog.catalog

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

    /**
     * The merges the shipping catalog really carries: one Swahili word, two German
     * concepts, each in its own area — and the ear cannot tell them apart because
     * there is nothing to tell apart.
     */
    @Test
    fun aMergedTargetWordNamesEveryMeaningItCarries() {
        val cards = catalog.join("de", "sw")
        val normalizer = AnswerNormalizer(catalog.languages.getValue("sw"))
        val grader = CatalogAnswerGrader(normalizer, cards)
        val bird = cards.first { it.id == "bird" }
        val plane = cards.first { it.id == "plane" }

        assertEquals(listOf("plane"), grader.conceptsSharing("ndege", bird).map { it.id })
        assertEquals(listOf("bird"), grader.conceptsSharing("ndege", plane).map { it.id })
        // And a word only one concept prints carries nothing besides itself.
        assertTrue(grader.conceptsSharing("kufunga", cards.byTargetText("kufunga")).isEmpty())
    }

    /**
     * Case is the difference between two words, and the merge index must keep it.
     * Only a PROMPT form can collide: two concepts that PRINT one word are ambiguous,
     * while one that merely accepts it is not — de `he` takes `sie` because Swahili
     * `yeye` is neither male nor female, and `they` still owns the word it prints.
     */
    @Test
    fun aGermanNounIsNotItsOwnVerb() {
        val cards = catalog.join("en", "de")
        val grader = CatalogAnswerGrader(AnswerNormalizer(catalog.languages.getValue("de")), cards)
        for (card in cards) {
            for (other in grader.conceptsSharing(card.target.text, card)) {
                if (card.target.text !in listOf(other.target.text) + other.target.synonyms) continue
                assertEquals(
                    card.target.text, other.target.text,
                    "${card.id} and ${other.id} were merged across a case difference",
                )
            }
        }
    }

    private fun List<Card>.byTargetText(text: String): Card =
        firstOrNull { it.target.text == text }
            ?: throw AssertionError("no de→sw card answers \"$text\"")

}

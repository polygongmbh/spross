package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/**
 * Catalog-wide produce grading: a form another concept owns is that word, not a
 * slip of the prompted answer. Fixture cards where the join provides the pair,
 * hand-built ones for near-twins the fixture has no reason to carry.
 */
class CatalogAnswerGraderTests {
    private val catalog = Fixture.catalog()
    private val de = AnswerNormalizer(catalog.languages.getValue("de"))
    private val sw = AnswerNormalizer(catalog.languages.getValue("sw"))
    private val uk = AnswerNormalizer(catalog.languages.getValue("uk"))
    private val deToSw = catalog.join("de", "sw")
    private val deToUk = catalog.join("de", "uk")
    private val swToDe = catalog.join("sw", "de")

    private fun joined(cards: List<Card>, id: String): Card = cards.first { it.id == id }

    private fun card(
        id: String,
        source: String,
        target: String,
        kind: CardKind = CardKind.Verb,
        seedIndex: Int = 0,
        lang: String = "sw",
    ): Card = Card(
        id = id, kind = kind, area = "test", emoji = null, seedIndex = seedIndex,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "de", text = source),
        target = Realization(lang = lang, text = target),
        promptFeminineMarker = false,
    )

    @Test
    fun anotherConceptsWordNeverPassesAsATypo() {
        val close = card("close", "schließen", "kufunga", seedIndex = 0)
        val open = card("open", "öffnen", "kufungua", seedIndex = 1)
        // One card at a time, the missing "u" sits inside the budget — the bug.
        assertIs<Match.Typo>(sw.evaluate("kufunga", open))

        val verdict = CatalogAnswerGrader(sw, listOf(close, open)).grade("kufunga", open)
        assertIs<Match.OtherWord>(verdict)
        assertEquals("kufunga", verdict.word)
        assertEquals(listOf("schließen"), verdict.meanings)
    }

    @Test
    fun aWordTheCatalogDoesNotKnowKeepsItsOneCardVerdict() {
        val close = card("close", "schließen", "kufunga")
        val open = card("open", "öffnen", "kufungua", seedIndex = 1)
        val grader = CatalogAnswerGrader(sw, listOf(close, open))
        // A slip nobody else owns stays a forgiven typo …
        assertIs<Match.Typo>(grader.grade("kufunguq", open))
        // … and gibberish stays plainly wrong, with nothing to name.
        assertEquals(Match.Wrong, grader.grade("zzznope", open))
    }

    @Test
    fun theRightAnswerWinsOverAFormTwoConceptsShare() {
        val one = card("one", "zumachen", "kufunga", seedIndex = 0)
        val two = card("two", "schließen", "kufunga", seedIndex = 1)
        val grader = CatalogAnswerGrader(sw, listOf(one, two))
        assertEquals(Match.Exact, grader.grade("kufunga", one))
        assertEquals(Match.Exact, grader.grade("kufunga", two))
    }

    @Test
    fun everyConceptThatOwnsTheWordIsNamedInSeedOrder() {
        val prompted = card("open", "öffnen", "kufungua", seedIndex = 0)
        val shut = card("shut", "zumachen", "kufunga", seedIndex = 2)
        val tie = card("tie", "binden", "kufunga", seedIndex = 1)
        val verdict = CatalogAnswerGrader(sw, listOf(prompted, shut, tie)).grade("kufunga", prompted)
        assertIs<Match.OtherWord>(verdict)
        assertEquals(listOf("binden", "zumachen"), verdict.meanings)
    }

    @Test
    fun theCitationPrefixIsOptionalTowardsVerbsOnly() {
        val grader = CatalogAnswerGrader(sw, deToSw)
        // Fixture: cook is a verb ("kupika"), mouse a noun ("panya").
        val mouse = joined(deToSw, "mouse")
        val stem = grader.grade("pika", mouse)
        assertIs<Match.OtherWord>(stem)
        assertEquals("kupika", stem.word)
        assertEquals(listOf("kochen"), stem.meanings)

        // A noun never lends its text to a "ku"-prefixed answer.
        val noun = card("stem", "Gericht", "pika", kind = CardKind.Noun, seedIndex = 1)
        val other = card("other", "Tür", "mlango", kind = CardKind.Noun, seedIndex = 2)
        assertEquals(Match.Wrong, CatalogAnswerGrader(sw, listOf(noun, other)).grade("kupika", other))
    }

    @Test
    fun aJoinedWordFromAnotherAreaIsNamedWithItsMeaning() {
        val cook = joined(deToSw, "cook")
        val verdict = CatalogAnswerGrader(sw, deToSw).grade("panya", cook)
        assertIs<Match.OtherWord>(verdict)
        assertEquals("panya", verdict.word)
        assertEquals(listOf("Maus"), verdict.meanings)
    }

    @Test
    fun theBaseWordStaysTheFeminineCorrection() {
        val grader = CatalogAnswerGrader(uk, deToUk)
        val waiterF = joined(deToUk, "waiter-f")
        // §3 leniency: the base concept's word demotes to the feminine spelling,
        // it is never re-labeled as another concept's word.
        val verdict = grader.grade("офіціант", waiterF)
        assertIs<Match.Typo>(verdict)
        assertEquals("офіціантка", verdict.corrected)

        // The other way round there is no leniency to protect: name the sibling.
        val named = grader.grade("офіціантка", joined(deToUk, "waiter"))
        assertIs<Match.OtherWord>(named)
        assertEquals(listOf("Kellnerin"), named.meanings)
    }

    /**
     * The normalizer can reach its verdict by peeling a mistyped article, and then the
     * remainder — not the string that was typed — is the form that matched. The owner
     * index has to be asked about it, or a slip behind a fumbled article bridges one
     * concept's word to another with nobody looking.
     */
    @Test
    fun aFumbledArticleNeverBridgesOneWordToAnother() {
        // "Keller" sits one edit from the fixture's "Kellner".
        val cellar = card("cellar", "pishi", "Keller", kind = CardKind.Noun, seedIndex = 99, lang = "de")
        assertIs<Match.Typo>(de.evaluate("dee Kellner", cellar))

        val verdict = CatalogAnswerGrader(de, swToDe + cellar).grade("dee Kellner", cellar)
        assertIs<Match.OtherWord>(verdict)
        assertEquals("Kellner", verdict.word)
    }

    /** And the prompted card keeps a form it accepts itself, fumbled article and all. */
    @Test
    fun theRightAnswerBehindAFumbledArticleStaysThePromptedCardsOwn() {
        val shared = card("shared", "mtumishi", "Kellner", kind = CardKind.Noun, seedIndex = 98, lang = "de")
        val verdict = CatalogAnswerGrader(de, swToDe + shared).grade("dee Kellner", shared)
        assertIs<Match.Typo>(verdict)
        assertEquals("Kellner", verdict.corrected)
    }

    @Test
    fun synonymsAndVariantsOfOtherConceptsCountAsOwned() {
        val grader = CatalogAnswerGrader(uk, deToUk)
        val door = joined(deToUk, "door")
        // uk mouse carries synonym "мишеня" and variant "мишка".
        for (form in listOf("миша", "мишеня", "мишка")) {
            val verdict = grader.grade(form, door)
            assertIs<Match.OtherWord>(verdict)
            assertEquals("миша", verdict.word)
            assertEquals(listOf("Maus"), verdict.meanings)
        }
    }
}

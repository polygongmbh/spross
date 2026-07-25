package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/**
 * Produce-answer grading, configured from the catalog fixture's `languages.json`
 * (articles + verb prefixes) and exercised on joined fixture cards where possible.
 */
class AnswerNormalizerTests {
    private val catalog = Fixture.catalog()
    private val de = AnswerNormalizer(catalog.languages.getValue("de"))
    private val en = AnswerNormalizer(catalog.languages.getValue("en"))
    private val sw = AnswerNormalizer(catalog.languages.getValue("sw"))
    private val uk = AnswerNormalizer(catalog.languages.getValue("uk"))

    private val deToSw = catalog.join("de", "sw")
    private val deToEn = catalog.join("de", "en")
    private val deToUk = catalog.join("de", "uk")
    private val swToDe = catalog.join("sw", "de")

    private fun joined(cards: List<Card>, id: String): Card = cards.first { it.id == id }

    private fun card(
        lang: String,
        text: String,
        kind: CardKind = CardKind.Noun,
        grammar: Map<String, String> = emptyMap(),
    ): Card = Card(
        id = "test/x", kind = kind, area = "test", emoji = null, seedIndex = 0,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "xx", text = "prompt"),
        target = Realization(lang = lang, text = text, grammar = grammar),
        promptFeminineMarker = false,
    )

    @Test
    fun normalizationRules() {
        assertEquals("spülmaschine", de.normalize("  Die Spülmaschine! "))
        assertEquals(de.normalize("Email"), de.normalize("E-Mail"))
        assertEquals("die", de.normalize("die")) // bare article stays
        assertEquals("guten morgen", de.normalize("Guten   Morgen!"))
        assertEquals("strasse", de.normalize("Straße"))
        assertEquals(de.normalize("Fussball"), de.normalize("Fußball"))
        // Ellipsis and em-dash are punctuation → space; uk strips no articles.
        assertEquals("мене звуть", uk.normalize("Мене звуть …"))
        assertEquals("ja genau", de.normalize("ja — genau"))
    }

    @Test
    fun acceptedSetSpansTextSynonymsAndVariants() {
        val mouse = joined(deToUk, "alpha/mouse") // "миша" + synonym "мишеня" + variant "мишка"
        assertTrue(uk.matches("миша", mouse))
        assertTrue(uk.matches("мишеня", mouse))
        assertTrue(uk.matches("мишка", mouse)) // variant: never scheduled, still accepted
        assertFalse(uk.matches("", mouse))
    }

    @Test
    fun sieAndDuVariantsBothAccepted() {
        val phrase = joined(swToDe, "alpha/the-mouse-runs")
        assertEquals(Match.Exact, de.evaluate("Sehen Sie die Maus?", phrase))
        assertEquals(Match.Exact, de.evaluate("Siehst du die Maus?", phrase))
    }

    @Test
    fun verbPrefixOptionalOnBothSides() {
        val cook = joined(deToSw, "alpha/cook") // "kupika"
        assertEquals(Match.Exact, sw.evaluate("pika", cook))
        assertEquals(Match.Exact, sw.evaluate("kupika", cook))
        // Symmetric: a prefix-less catalog form still accepts prefixed input.
        assertEquals(Match.Exact, sw.evaluate("kupika", card("sw", "pika", kind = CardKind.Verb)))
        // The prefix never applies when the target doesn't start with it.
        assertEquals(Match.Wrong, sw.evaluate("ahani", card("sw", "samahani", kind = CardKind.Verb)))
        // German lists no verb prefixes: "kuscheln" ≠ "scheln".
        assertEquals(Match.Wrong, de.evaluate("scheln", card("de", "kuscheln", kind = CardKind.Verb)))
    }

    @Test
    fun englishToPrefixIsOptionalAndSpacePreserving() {
        val cook = joined(deToEn, "alpha/cook") // "to cook"
        assertEquals(Match.Exact, en.evaluate("cook", cook))
        assertEquals(Match.Exact, en.evaluate("to cook", cook))
    }

    @Test
    fun phrasesAreNeverPrefixStripped() {
        val kwaheri = card("sw", "Kwaheri!", kind = CardKind.Phrase)
        assertEquals(Match.Exact, sw.evaluate("kwaheri", kwaheri))
        assertEquals(Match.Wrong, sw.evaluate("aheri", kwaheri)) // "kw" applies to verbs only
    }

    @Test
    fun typoToleranceScalesWithLength() {
        val kuehlschrank = card("de", "Kühlschrank", grammar = mapOf("gender" to "der"))
        // corrected carries the catalog spelling, never the lowercased comparison form
        assertEquals(Match.Typo("Kühlschrank"), de.evaluate("Kuhlschrank", kuehlschrank))
        val spuelmaschine = card("de", "Spülmaschine", grammar = mapOf("gender" to "die"))
        assertEquals(Match.Typo("Spülmaschine"), de.evaluate("Spulmaschine", spuelmaschine))
        assertEquals(Match.Wrong, de.evaluate("Spolmascine", spuelmaschine)) // 2 edits > budget 1
        assertEquals(Match.Typo("friji"), sw.evaluate("firji", card("sw", "friji"))) // transposition
        assertEquals(Match.Wrong, sw.evaluate("kula", card("sw", "kile"))) // short words: exact only
        assertEquals(Match.Exact, sw.evaluate("kula", card("sw", "kula")))
    }

    @Test
    fun esszettFoldsToDoubleSIndependentOfTypoBudget() {
        assertEquals(Match.Exact, de.evaluate("heissen", card("de", "heißen", kind = CardKind.Verb)))
        assertEquals(Match.Exact, de.evaluate("heißen", card("de", "heissen", kind = CardKind.Verb)))
        assertEquals(Match.Exact, de.evaluate("weiss", card("de", "weiß")))
    }

    @Test
    fun articleMismatchDemotesToTypoOnlyWithGenderGrammar() {
        val waiter = joined(swToDe, "alpha/waiter") // "Kellner", gender "der"
        assertEquals(Match.Exact, de.evaluate("der Kellner", waiter))
        assertEquals(Match.Exact, de.evaluate("Kellner", waiter)) // missing article stays exact
        assertEquals(Match.Typo("Kellner"), de.evaluate("die Kellner", waiter))
        assertEquals(Match.Typo("Kellner"), de.evaluate("dee Kellner", waiter)) // stray-word rescue
        // No gender in the grammar → the article is never checked.
        assertEquals(Match.Exact, de.evaluate("das Tisch", card("de", "Tisch")))
    }

    @Test
    fun baseWordOnFeminineCardGradesAsTypoWithFeminineCorrection() {
        val waiterF = joined(swToDe, "alpha/waiter-f") // ♀-marker join; base target "Kellner"
        assertEquals(listOf("Kellner"), waiterF.baseAccepted)
        assertEquals(Match.Exact, de.evaluate("Kellnerin", waiterF))
        assertEquals(Match.Typo("Kellnerin"), de.evaluate("Kellner", waiterF))
        assertEquals(Match.Typo("Kellnerin"), de.evaluate("der Kellner", waiterF))
        assertEquals(Match.Typo("Kellnerin"), de.evaluate("Kelner", waiterF)) // base typo still demotes
        assertEquals(Match.Wrong, de.evaluate("Tisch", waiterF))
        // The audited real-catalog shape: base word distance 2 from feminine, budget 1.
        val waiterFUk = joined(deToUk, "alpha/waiter-f")
        assertEquals(Match.Typo("офіціантка"), uk.evaluate("офіціант", waiterFUk))
        assertEquals(Match.Exact, uk.evaluate("офіціантка", waiterFUk))
    }

    @Test
    fun nonFeminineCardsAndTargetlessBasesCarryNoBaseForms() {
        val waiter = joined(swToDe, "alpha/waiter")
        assertTrue(waiter.baseAccepted.isEmpty())
        assertEquals(Match.Wrong, de.evaluate("Kellnerin", waiter)) // unchanged: no reverse demotion
        // uk never realizes beta/royal — the feminine card has nothing to demote against.
        val royalF = joined(deToUk, "beta/royal-f")
        assertTrue(royalF.baseAccepted.isEmpty())
        assertEquals(Match.Wrong, uk.evaluate("князь", royalF))
    }

    @Test
    fun strayShortLeadingWordIsTypoNotFailure() {
        val panya = joined(deToSw, "alpha/mouse") // "panya"
        assertEquals(Match.Typo("panya"), sw.evaluate("el panya", panya))
        assertEquals(Match.Wrong, sw.evaluate("großes panya", panya)) // long stray word: not forgiven
        assertEquals(Match.Wrong, sw.evaluate("dee", card("sw", "nyumba"))) // never strips the only word
    }

    @Test
    fun leadingArticleIsOptionalOnBothSidesOfAPhrase() {
        val leer = card("de", "Der Kühlschrank ist leer.", kind = CardKind.Phrase)
        assertEquals(Match.Exact, de.evaluate("Der Kühlschrank ist leer", leer))
        assertEquals(Match.Exact, de.evaluate("Kühlschrank ist leer", leer))
        assertEquals(Match.Exact, de.evaluate("der kühlschrank ist leer!", leer))
    }

    @Test
    fun ellipsisPhraseMatchesWithoutTheEllipsis() {
        val name = card("uk", "Мене звуть …", kind = CardKind.Phrase)
        assertEquals(Match.Exact, uk.evaluate("Мене звуть", name))
        assertEquals(Match.Exact, uk.evaluate("мене звуть...", name))
    }

    @Test
    fun articleLeniencyOffRequiresTheAuthoredArticle() {
        val strict = AnswerNormalizer(catalog.languages.getValue("de"), articleLeniency = false)
        val zug = card("de", "der Zug") // synthetic drill card: no gender grammar
        assertEquals(Match.Exact, strict.evaluate("der Zug", zug))
        assertEquals(Match.Wrong, strict.evaluate("die zug", zug)) // wrong article
        assertEquals(Match.Wrong, strict.evaluate("das zug", zug)) // wrong article
        assertEquals(Match.Wrong, strict.evaluate("zug", zug)) // missing article
        // A matching article keeps the typo budget on the rest.
        assertEquals(Match.Typo("der Zug"), strict.evaluate("der Zuk", zug))
        // Strict normalize keeps the article; the lenient default still strips it.
        assertEquals("der zug", strict.normalize("Der Zug!"))
        assertEquals("zug", de.normalize("Der Zug!"))
    }

    @Test
    fun decomposedUnicodeAndArticleInsideSynonymConverge() {
        val door = joined(swToDe, "gamma/door") // NFD "Tür", synonym "die  Türe"
        assertEquals(Match.Exact, de.evaluate("Tür", door))
        assertEquals(Match.Exact, de.evaluate("die Türe", door))
    }
}

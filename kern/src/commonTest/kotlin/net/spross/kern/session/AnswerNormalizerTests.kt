package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Rating
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
        val mouse = joined(deToUk, "mouse") // "миша" + synonym "мишеня" + variant "мишка"
        assertTrue(uk.matches("миша", mouse))
        assertTrue(uk.matches("мишеня", mouse))
        assertTrue(uk.matches("мишка", mouse)) // variant: never scheduled, still accepted
        assertFalse(uk.matches("", mouse))
    }

    @Test
    fun sieAndDuVariantsBothAccepted() {
        val phrase = joined(swToDe, "the-mouse-runs")
        assertEquals(Match.Exact, de.evaluate("Sehen Sie die Maus?", phrase))
        assertEquals(Match.Exact, de.evaluate("Siehst du die Maus?", phrase))
    }

    @Test
    fun verbPrefixOptionalOnBothSides() {
        val cook = joined(deToSw, "cook") // "kupika"
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
        val cook = joined(deToEn, "cook") // "to cook"
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
        assertEquals(Match.Typo("Spülmaschine"), de.evaluate("Spolmascine", spuelmaschine)) // 12 letters: 2 slips
        assertEquals(Match.Wrong, de.evaluate("Spolmascina", spuelmaschine)) // 3 edits > budget 2
        assertEquals(Match.Typo("friji"), sw.evaluate("firji", card("sw", "friji"))) // transposition
        // Four letters is the shortest that forgives anything; three is exact-only.
        assertEquals(Match.Typo("kile"), sw.evaluate("kila", card("sw", "kile")))
        assertEquals(Match.Wrong, sw.evaluate("kula", card("sw", "kile"))) // 2 edits > budget 1
        assertEquals(Match.Wrong, sw.evaluate("mto", card("sw", "mtu")))
        assertEquals(Match.Exact, sw.evaluate("kula", card("sw", "kula")))
        // A long phrase forgives a slip per six letters, not per ten.
        val leer = card("de", "Der Kühlschrank ist leer.", kind = CardKind.Phrase)
        assertEquals(Match.Typo("Der Kühlschrank ist leer."), de.evaluate("Der Külschrenk ist ler.", leer))
    }

    /**
     * With several accepted forms inside the budget the correction is the one the
     * answer came nearest, never the one authored last: sw `white` carries eight
     * stems, so a one-slip attempt at "nyeupe" was corrected to "myeupe".
     */
    @Test
    fun theCorrectionNamesTheNearestAcceptedForm() {
        val fridge = card("de", "Gefrierschrank")
        val plural = fridge.copy(target = fridge.target.copy(variants = listOf("Gefrierschränke")))
        // One slip from the card's own text, two from the later form — both accepted.
        assertEquals(Match.Typo("Gefrierschrank"), de.evaluate("Gefrierschrenk", plural))
        // Equally near: the form the card leads with, not the one authored last.
        val white = card("sw", "nyeupe")
        val stems = white.copy(target = white.target.copy(variants = listOf("cheupe", "myeupe")))
        assertEquals(Match.Typo("nyeupe"), sw.evaluate("kyeupe", stems))
    }

    @Test
    fun esszettFoldsToDoubleSIndependentOfTypoBudget() {
        assertEquals(Match.Exact, de.evaluate("heissen", card("de", "heißen", kind = CardKind.Verb)))
        assertEquals(Match.Exact, de.evaluate("heißen", card("de", "heissen", kind = CardKind.Verb)))
        assertEquals(Match.Exact, de.evaluate("weiss", card("de", "weiß")))
    }

    @Test
    fun articleMismatchDemotesToTypoOnlyWithGenderGrammar() {
        val waiter = joined(swToDe, "waiter") // "Kellner", gender "der"
        assertEquals(Match.Exact, de.evaluate("der Kellner", waiter))
        assertEquals(Match.Exact, de.evaluate("Kellner", waiter)) // missing article stays exact
        assertEquals(Match.Typo("Kellner"), de.evaluate("die Kellner", waiter))
        assertEquals(Match.Typo("Kellner"), de.evaluate("dee Kellner", waiter)) // stray-word rescue
        // No gender in the grammar → the article is never checked.
        assertEquals(Match.Exact, de.evaluate("das Tisch", card("de", "Tisch")))
    }

    @Test
    fun baseWordOnFeminineCardGradesAsTypoWithFeminineCorrection() {
        val waiterF = joined(swToDe, "waiter-f") // ♀-marker join; base target "Kellner"
        assertEquals(listOf("Kellner"), waiterF.baseAccepted)
        assertEquals(Match.Exact, de.evaluate("Kellnerin", waiterF))
        assertEquals(Match.Typo("Kellnerin"), de.evaluate("Kellner", waiterF))
        assertEquals(Match.Typo("Kellnerin"), de.evaluate("der Kellner", waiterF))
        assertEquals(Match.Typo("Kellnerin"), de.evaluate("Kelner", waiterF)) // base typo still demotes
        assertEquals(Match.Wrong, de.evaluate("Tisch", waiterF))
        // The audited real-catalog shape: base word distance 2 from feminine, budget 1.
        val waiterFUk = joined(deToUk, "waiter-f")
        assertEquals(Match.Typo("офіціантка"), uk.evaluate("офіціант", waiterFUk))
        assertEquals(Match.Exact, uk.evaluate("офіціантка", waiterFUk))
    }

    @Test
    fun nonFeminineCardsAndTargetlessBasesCarryNoBaseForms() {
        val waiter = joined(swToDe, "waiter")
        assertTrue(waiter.baseAccepted.isEmpty())
        assertEquals(Match.Wrong, de.evaluate("Kellnerin", waiter)) // unchanged: no reverse demotion
        // uk never realizes beta/royal — the feminine card has nothing to demote against.
        val royalF = joined(deToUk, "royal-f")
        assertTrue(royalF.baseAccepted.isEmpty())
        assertEquals(Match.Wrong, uk.evaluate("князь", royalF))
    }

    @Test
    fun aMistypedLeadingArticleIsTypoNotFailure() {
        val zug = card("de", "Zug")
        assertEquals(Match.Typo("Zug"), de.evaluate("de Zug", zug)) // "de" is one slip from "der"
        assertEquals(Match.Typo("Zug"), de.evaluate("dad Zug", zug))
        assertEquals(Match.Wrong, de.evaluate("grosses Zug", zug)) // long stray word: not forgiven
        assertEquals(Match.Wrong, de.evaluate("rot Zug", zug)) // short, but no article reads like it
        assertEquals(Match.Wrong, de.evaluate("de", card("de", "Zug"))) // never strips the only word
    }

    /**
     * The peel reads a MISTYPED ARTICLE, so a language listing none never peels: sw
     * "muda nini" for "wann" used to lose its first word and come back as a spelling
     * slip of "lini" — while "nini" is a catalog word of its own, one row above.
     */
    @Test
    fun aLanguageWithoutArticlesNeverPeelsALeadingWord() {
        val lini = card("sw", "lini")
        assertEquals(Match.Wrong, sw.evaluate("muda nini", lini))
        val panya = joined(deToSw, "mouse") // "panya"
        assertEquals(Match.Wrong, sw.evaluate("el panya", panya))
        assertEquals(Match.Wrong, uk.evaluate("той миша", joined(deToUk, "mouse")))
    }

    /**
     * The rescue is a vocab-review rule. A drill grades every word, because in a
     * clock reading every word names WHICH time it is — and the rescue recurses,
     * peeling one word per level, so a reading used to decay onto other times'
     * answers ("fünf vor halb sieben" → "halb sieben", 18:25 accepted at 18:30).
     */
    @Test
    fun drillGradingNeverStripsAStrayLeadingWord() {
        val drill = AnswerNormalizer(
            catalog.languages.getValue("de"),
            articleLeniency = false,
            maxTyposPerWord = 1,
        )
        val halbSieben = card("de", "halb sieben", kind = CardKind.Phrase)
        assertEquals(Match.Wrong, drill.evaluate("fünf vor halb sieben", halbSieben))
        assertEquals(Match.Wrong, drill.evaluate("fünf nach halb sieben", halbSieben))
        // Down to a single word, however many levels of peeling it would take.
        assertEquals(Match.Wrong, drill.evaluate("Viertel nach sieben", card("de", "sieben")))
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
    fun drillGradesWordByWordAndNeverBridgesNumbersOrDigits() {
        val drill = AnswerNormalizer(
            catalog.languages.getValue("de"),
            articleLeniency = false,
            maxTyposPerWord = 1,
        )
        val base = card("de", "Ich habe 29 Hefte.", kind = CardKind.Phrase)
        val hefte = base.copy(target = base.target.copy(variants = listOf("Ich habe neunundzwanzig Hefte.")))
        assertEquals(Match.Exact, drill.evaluate("Ich habe 29 Hefte.", hefte))
        assertEquals(Match.Exact, drill.evaluate("Ich habe neunundzwanzig Hefte.", hefte))
        // One digit off is one edit however long the frame — digit words grade exact-only.
        assertEquals(Match.Wrong, drill.evaluate("Ich habe 21 Hefte.", hefte))
        assertEquals(Match.Wrong, drill.evaluate("Ich habe einundzwanzig Hefte.", hefte))
        assertEquals(
            Match.Wrong,
            drill.evaluate("Der Zug fährt um 18:06 Uhr ab.", card("de", "Der Zug fährt um 18:05 Uhr ab.", kind = CardKind.Phrase)),
        )
        // A slip inside a WORD keeps the cap — and every word carries its own.
        assertEquals(
            Match.Typo("Ich habe neunundzwanzig Hefte."),
            drill.evaluate("Ich habe neunundzwanzik Hefte.", hefte),
        )
        assertEquals(
            Match.Typo("Ich habe neunundzwanzig Hefte."),
            drill.evaluate("Ich hebe neunundzwanzik Hefta.", hefte),
        )
        // Two slips in ONE word stay Wrong, however forgiving the rest of the sentence is.
        assertEquals(Match.Wrong, drill.evaluate("Ich habe neunundzwanzk Hefte.", hefte))
        // Long word numbers sit 2 edits apart: Wrong in a drill; the vocab budget is untouched.
        val number = card("de", "einhundertneunundzwanzig")
        assertEquals(Match.Wrong, drill.evaluate("einhunderteinundzwanzig", number))
        assertEquals(Match.Typo("einhundertneunundzwanzig"), de.evaluate("einhunderteinundzwanzig", number))
        // A word too few falls back to the whole-form rule — digits still exact-only.
        assertEquals(Match.Wrong, drill.evaluate("Ich 29 Hefte.", hefte))
    }

    @Test
    fun shortNonDigitWordsGetTheSameCapAsLongerWords() {
        val drill = AnswerNormalizer(
            catalog.languages.getValue("de"),
            articleLeniency = false,
            maxTyposPerWord = 1,
        )
        val phrase = card("de", "Ich kaufe das für dich.", kind = CardKind.Phrase)
        // "für" is 3 letters — the old length-scaled per-word rule forced budget 0
        // regardless of the cap; the cap alone now governs every word, short or long.
        assertEquals(
            Match.Typo("Ich kaufe das für dich."),
            drill.evaluate("Ich kaufe das for dich.", phrase),
        )
        // Two slips in the same short word still exceed the cap.
        assertEquals(Match.Wrong, drill.evaluate("Ich kaufe das fox dich.", phrase))
    }

    @Test
    fun decomposedUnicodeAndArticleInsideSynonymConverge() {
        val door = joined(swToDe, "door") // NFD "Tür", synonym "die  Türe"
        assertEquals(Match.Exact, de.evaluate("Tür", door))
        assertEquals(Match.Exact, de.evaluate("die Türe", door))
    }

    @Test
    fun matchingPrefixWordCountKeepsWholeWordsAlreadyRight() {
        // First word right, second wrong → keep one.
        assertEquals(1, de.matchingPrefixWordCount("Der Gefrierschrank", "Der Kühlschrank"))
        // A forgivable slip inside a kept word still counts as matching.
        assertEquals(2, de.matchingPrefixWordCount("Der Kuhlschrank", "Der Kühlschrank"))
        // Nothing matches → nothing kept.
        assertEquals(0, de.matchingPrefixWordCount("Tisch", "Der Kühlschrank"))
        // Typed more words than the answer has → capped at the answer's length.
        assertEquals(2, de.matchingPrefixWordCount("Der Kühlschrank ist leer", "Der Kühlschrank"))
        // "Der" is 3 letters — the retry-priming rule keeps its own length floor,
        // independent of any drill's maxTyposPerWord (unaffected by that fix).
        assertEquals(0, de.matchingPrefixWordCount("Dre Kühlschrank", "Der Kühlschrank"))
        // Typed fewer words than the answer → capped at what was typed.
        assertEquals(1, de.matchingPrefixWordCount("Der", "Der Kühlschrank"))
        // Empty input matches nothing.
        assertEquals(0, de.matchingPrefixWordCount("", "Der Kühlschrank"))
    }

    @Test
    fun producedRatingIsTheOneRuleEveryPlatformShares() {
        // Exact came back clean.
        assertEquals(Rating.Good, Match.Exact.producedRating())
        // A slip is readable but imperfect — same Hard a finished retype earns.
        assertEquals(Rating.Hard, Match.Typo("Kühlschrank").producedRating())
        // Neither has a rating of its own: both route through reveal, where the
        // eventual retype (Hard) or give-up (Again) decides it — never Good.
        assertEquals(null, Match.OtherWord("wort", listOf("bedeutung")).producedRating())
        assertEquals(null, Match.Wrong.producedRating())
    }
}

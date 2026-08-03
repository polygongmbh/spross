package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.Realization

class CardDisplayTest {

    private val chrome = Chrome.forSource("de")

    private fun realization(
        text: String,
        plural: String? = null,
        synonyms: List<String> = emptyList(),
        gender: String? = null,
    ) = Realization(
        lang = "de",
        text = text,
        synonyms = synonyms,
        grammar = buildMap {
            plural?.let { put("plural", it) }
            gender?.let { put("gender", it) }
        },
    )

    @Test
    fun pluralSentinelsUseChromeStrings() {
        assertEquals("= Pl.", CardDisplay.pluralLine(realization("der Lehrer", plural = "="), chrome))
        assertEquals("nur Pl.", CardDisplay.pluralLine(realization("die Eltern", plural = "only"), chrome))
    }

    @Test
    fun suffixPluralResolvesAgainstTheWord() {
        assertEquals(
            "Pl. die Lehrerinnen",
            CardDisplay.pluralLine(realization("die Lehrerin", plural = "-nen"), chrome),
        )
    }

    @Test
    fun fullWordPluralGetsPrefix() {
        assertEquals(
            "Pl. die Häuser",
            CardDisplay.pluralLine(realization("das Haus", plural = "die Häuser"), chrome),
        )
    }

    /** An authored-but-empty plural is not a form — it used to render a bare "Pl. ". */
    @Test
    fun anEmptyPluralIsNoPluralAtAll() {
        assertNull(CardDisplay.pluralLine(realization("nyumba", plural = ""), chrome))
    }

    @Test
    fun missingPluralAndSynonymsGiveNull() {
        assertNull(CardDisplay.pluralLine(realization("nyumba"), chrome))
        assertNull(CardDisplay.alsoLine(realization("nyumba"), chrome, "nyumba"))
    }

    @Test
    fun alsoLineJoinsTheFamilyBeyondTheFormOnScreen() {
        val word = realization("die Verwaltung", synonyms = listOf("das Amt", "die Behörde"))
        assertEquals("auch: das Amt / die Behörde", CardDisplay.alsoLine(word, chrome, "die Verwaltung"))
    }

    /**
     * The regression: a rotated recognition prompt puts a SYNONYM on screen, and the line
     * used to offer it back as though it were another word — while dropping the citation
     * form the learner had not seen.
     */
    @Test
    fun theFormOnScreenNeverAppearsAmongItsOwnAlternatives() {
        val word = realization("die Verwaltung", synonyms = listOf("das Amt", "die Behörde"))
        assertEquals("auch: die Verwaltung / die Behörde", CardDisplay.alsoLine(word, chrome, "das Amt"))
    }

    @Test
    fun aWordWithNothingLeftToOfferHasNoLine() {
        assertNull(
            CardDisplay.alsoLine(
                realization("das Amt", synonyms = listOf("die Behörde")),
                chrome,
                listOf("das Amt", "die Behörde"),
            ),
        )
    }

    @Test
    fun theArticleComesOffTheGrammar() {
        assertEquals("die", CardDisplay.article(realization("die Küche", gender = "die")))
        assertNull(CardDisplay.article(realization("nyumba")))
    }
}

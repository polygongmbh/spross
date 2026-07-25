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

    @Test
    fun missingPluralAndSynonymsGiveNull() {
        assertNull(CardDisplay.pluralLine(realization("nyumba"), chrome))
        assertNull(CardDisplay.alsoLine(realization("nyumba"), chrome))
    }

    @Test
    fun alsoLineJoinsSynonymFamily() {
        assertEquals(
            "auch: das Amt / die Behörde",
            CardDisplay.alsoLine(
                realization("die Verwaltung", synonyms = listOf("das Amt", "die Behörde")),
                chrome,
            ),
        )
    }

    @Test
    fun genderPassesThrough() {
        assertEquals("die", CardDisplay.gender(realization("die Küche", gender = "die")))
        assertNull(CardDisplay.gender(realization("nyumba")))
    }
}

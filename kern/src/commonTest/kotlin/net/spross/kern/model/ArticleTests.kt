package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The article → gender table every surface reads from kern. */
class ArticleTests {

    @Test
    fun germanArticlesMarkAllThreeGenders() {
        assertEquals(Gender.Masculine, articleGender("der"))
        assertEquals(Gender.Feminine, articleGender("die"))
        assertEquals(Gender.Neuter, articleGender("das"))
    }

    @Test
    fun spanishFoldsOntoTheSameTwoGenders() {
        assertEquals(Gender.Masculine, articleGender("el"))
        assertEquals(Gender.Feminine, articleGender("la"))
    }

    @Test
    fun pluralsAndIndefinitesFollowTheGenderTheyInflect() {
        assertEquals(Gender.Masculine, articleGender("los"))
        assertEquals(Gender.Masculine, articleGender("un"))
        assertEquals(Gender.Feminine, articleGender("las"))
        assertEquals(Gender.Feminine, articleGender("una"))
    }

    @Test
    fun frenchAndItalianFoldOntoTheSameTwoGenders() {
        assertEquals(Gender.Feminine, articleGender("la", "fr"))
        assertEquals(Gender.Feminine, articleGender("la", "it"))
        for (masculine in listOf("il", "lo", "i", "gli")) {
            assertEquals(Gender.Masculine, articleGender(masculine, "it"), masculine)
        }
    }

    @Test
    fun aCollidingFormResolvesByLanguageAloneOrNotAtAll() {
        assertEquals(Gender.Masculine, articleGender("le", "fr"))
        assertEquals(Gender.Feminine, articleGender("le", "it"))
        assertNull(articleGender("le"))
        assertNull(articleGender("le", "de"))
    }

    @Test
    fun anArticleMarkingBothGendersNamesNone() {
        assertNull(articleGender("l'", "fr"))
        assertNull(articleGender("l'", "it"))
        assertNull(articleGender("les", "fr"))
    }

    @Test
    fun authoringCaseIsSlackNotMeaning() {
        assertEquals(Gender.Masculine, articleGender("Der"))
        assertEquals(Gender.Feminine, articleGender("LA"))
    }

    @Test
    fun absentOrUnknownArticleHasNoGender() {
        assertNull(articleGender(null)) // genderless target
        assertNull(articleGender("")) // authored empty
        assertNull(articleGender("the")) // a language this table does not fold
        assertNull(articleGender("ein")) // German's indefinites are not in the table
    }

    private fun noun(lang: String, article: String, gender: String? = null) = Realization(
        lang = lang, text = "x",
        grammar = mapOf("article" to article) + (gender?.let { mapOf("gender" to it) } ?: emptyMap()),
    )

    @Test
    fun theAuthoredGenderOutranksTheArticle() {
        assertEquals(Gender.Feminine, noun("es", "el", gender = "f").gender) // el agua
        assertEquals(Gender.Masculine, noun("fr", "l'", gender = "m").gender) // l'arbre
        assertNull(noun("fr", "l'", gender = "m/f").gender) // l'élève takes both
        assertEquals(Gender.Masculine, noun("es", "el").gender) // nothing authored: the article's
    }

    @Test
    fun aTaggedFormsOwnArticleMarksItsOwnGender() {
        assertEquals(Gender.Feminine, noun("es", "el", gender = "f").shownGender("el")) // el agua
        assertEquals(Gender.Feminine, noun("de", "der").shownGender("die")) // der Lehrer's die Lehrerin
        assertNull(noun("de", "der").shownGender(null))
    }

    @Test
    fun articleShowsOnlyOnTheCanonicalForm() {
        assertEquals("die", shownArticle("die", shownForm = "Kellnerin", targetText = "Kellnerin"))
        // A rotated synonym may carry another gender — the card's article steps aside.
        assertNull(shownArticle("die", shownForm = "Bedienung", targetText = "Kellnerin"))
        assertNull(shownArticle(null, shownForm = "mtu", targetText = "mtu"))
    }
}

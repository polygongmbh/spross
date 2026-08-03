package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The article → gender table every surface used to carry its own copy of. */
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

    @Test
    fun articleShowsOnlyOnTheCanonicalForm() {
        assertEquals("die", shownArticle("die", shownForm = "Kellnerin", targetText = "Kellnerin"))
        // A rotated synonym may carry another gender — the card's article steps aside.
        assertNull(shownArticle("die", shownForm = "Bedienung", targetText = "Kellnerin"))
        assertNull(shownArticle(null, shownForm = "mtu", targetText = "mtu"))
    }
}

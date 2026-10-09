package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitArticleTests {
    @Test
    fun aListedArticleIsSetApartFromItsWord() {
        assertEquals("die" to "Kühlschränke", splitArticle("die Kühlschränke", listOf("der", "die", "das")))
        assertEquals("l'" to "invitée", splitArticle("l'invitée", listOf("la", "l'")))
    }

    @Test
    fun aWordWithoutOneStaysWhole() {
        assertEquals(null to "Diele", splitArticle("Diele", listOf("die")))
        assertEquals(null to "die", splitArticle("die", listOf("die")))
    }
}

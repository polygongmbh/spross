package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.catalog.Fixture

class AreaNamingTests {
    private val naming = AreaNaming(Fixture.catalog(), "de", ownTitle = "Eigene", ownSubtitle = "Deine Wörter")

    @Test
    fun theOwnShelfIsNamedByTheChromeItIsHanded() {
        assertEquals("Eigene", naming.title(OwnWords.AREA))
        assertEquals("Deine Wörter", naming.subtitle(OwnWords.AREA))
        assertEquals(OwnWords.EMOJI, naming.emoji(OwnWords.AREA))
    }

    @Test
    fun anAreaTheCatalogCannotNameStillGetsAReadableTitleAndAPicture() {
        assertEquals("Attic", naming.title("attic"))
        assertNull(naming.subtitle("attic"))
        assertEquals("📦", naming.emoji("attic"))
    }

    @Test
    fun theSearchMatchesOnTheHeadingTheLearnerRead() {
        val searchable = naming.searchable(listOf("attic", OwnWords.AREA))
        assertEquals(listOf("Attic", "Eigene"), searchable.map { it.title })
    }
}

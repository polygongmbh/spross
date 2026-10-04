package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OwnWordDraftTests {

    private fun draft(
        sourceText: String = "",
        targetText: String = "",
        emoji: String = "",
        comment: String = "",
        editing: OwnWord? = null,
        taken: Set<String> = emptySet(),
    ): OwnWord? = OwnWords.fromDraft(
        source = "de", target = "sw",
        sourceText = sourceText, targetText = targetText,
        emoji = emoji, comment = comment,
        editing = editing, taken = taken,
    )

    @Test
    fun aDraftWithEveryFieldBlankIsRefused() {
        assertNull(draft(sourceText = " ", targetText = "  ", emoji = "🏠", comment = "   "))
    }

    @Test
    fun aNewWordIsNamedAfterItsTargetSideAndFallsBackToItsSourceSide() {
        assertEquals("own:nyumba", draft(sourceText = "Haus", targetText = " nyumba ")?.id)
        assertEquals("own:haus", draft(sourceText = " Haus ")?.id)
        assertEquals("own:nyumba-2", draft(targetText = "nyumba", taken = setOf("own:nyumba"))?.id)
    }

    @Test
    fun anEditKeepsItsIdKindAndTheHalvesThisProfileCannotSee() {
        val stored = OwnWord(
            id = "own:nyumba",
            kind = OwnWords.DEFAULT_KIND,
            emoji = null,
            texts = mapOf("de" to "Haus", "sw" to "nyumba", "en" to "house"),
        )
        val fixed = draft(sourceText = "Haus", targetText = "nyumbani", editing = stored,
            taken = setOf(stored.id))
        assertEquals(stored.id, fixed?.id)
        assertEquals(stored.kind, fixed?.kind)
        assertEquals(mapOf("de" to "Haus", "sw" to "nyumbani", "en" to "house"), fixed?.texts)
    }
}

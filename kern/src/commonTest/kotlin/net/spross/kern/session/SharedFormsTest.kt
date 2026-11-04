package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

class SharedFormsTest {
    private val languages = Fixture.catalog().languages

    private fun card(id: String, target: Realization, seedIndex: Int) = Card(
        id = id, kind = CardKind.Phrase, area = "test", emoji = null, seedIndex = seedIndex,
        components = emptyList(), source = Realization("en", id), target = target,
    )

    @Test
    fun aSentenceSharesTheWordItIsWrittenAround() {
        val near = card("near", Realization("sw", "karibu"), 0)
        val welcome = card("welcome", Realization("sw", "Karibu!"), 1)
        val shared = SharedForms.printed(languages.getValue("sw"), listOf(near, welcome))
        assertEquals(listOf("welcome"), shared.concepts("karibu", near).map { it.id })
    }

    @Test
    fun aWordShownWithItsArticleIsNotTheBareOne() {
        val arm = card("arm", Realization("de", "Arm", grammar = mapOf("article" to "der")), 0)
        val poor = card("poor", Realization("de", "arm"), 1)
        val shared = SharedForms.printed(languages.getValue("de"), listOf(arm, poor))
        assertEquals(emptyList(), shared.concepts("arm", poor))
        assertEquals(emptyList(), shared.concepts("Arm", arm))
    }
}

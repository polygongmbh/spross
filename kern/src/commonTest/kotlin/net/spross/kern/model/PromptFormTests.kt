package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which form a turn asks with, and how the other side answers it. */
class PromptFormTests {
    private val f = FormTag.FEMININE

    private fun card(source: Realization, target: Realization) = Card(
        id = "teacher", kind = CardKind.Noun, area = "school", emoji = null, seedIndex = 0,
        components = emptyList(), feminineOf = null, source = source, target = target, promptFeminineMarker = false,
    )

    private val lehrer = Realization("de", "Lehrer", forms = listOf(TaggedForm(f, "Lehrerin")))
    private val profesor = Realization("es", "profesor", forms = listOf(TaggedForm(f, "profesora")))

    private fun prompts(card: Card, role: PresentationRole) =
        (0..12).map { turnPrompt(card, role, ProducePrompt.Source, it) }.toSet()

    @Test
    fun recognitionRotatesThroughTheTaggedForms() {
        assertTrue(PromptForm("Lehrerin", f) in prompts(card(Realization("en", "teacher"), lehrer), PresentationRole.Recognize))
    }

    @Test
    fun productionAsksOnlyForAFormBothSidesHave() {
        assertEquals(setOf(PromptForm("teacher", null)), prompts(card(Realization("en", "teacher"), lehrer), PresentationRole.Produce))
        assertTrue(PromptForm("profesora", f) in prompts(card(profesor, lehrer), PresentationRole.Produce))
    }

    @Test
    fun aSideWithoutTheFormSaysItsCitationMarked() {
        assertEquals(Counterpart("teacher", f), counterpart(Realization("en", "teacher"), f))
        assertEquals(Counterpart("profesora", null), counterpart(profesor, f))
    }
}

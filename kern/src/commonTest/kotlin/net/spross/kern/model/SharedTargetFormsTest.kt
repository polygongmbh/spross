package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedTargetFormsTest {
    private fun card(id: String, source: String, target: Realization, seedIndex: Int) = Card(
        id = id, kind = CardKind.Phrase, area = "test", emoji = null, seedIndex = seedIndex,
        components = emptyList(), source = Realization("de", source), target = target,
    )

    @Test
    fun aSentenceSharesTheWordItIsWrittenAround() {
        val near = card("near", "nah", Realization("sw", "karibu"), 0)
        val welcome = card("welcome", "willkommen", Realization("sw", "Karibu!"), 1)
        val shared = SharedTargetForms(listOf(near, welcome))
        assertEquals(listOf("welcome"), shared.concepts("karibu", near).map { it.id })
    }

    @Test
    fun aBareWordKeepsItsCase() {
        val arm = card("arm", "arm", Realization("de", "Arm"), 0)
        val poor = card("poor", "poor", Realization("de", "arm"), 1)
        assertEquals(emptyList(), SharedTargetForms(listOf(arm, poor)).concepts("arm", poor))
    }
}

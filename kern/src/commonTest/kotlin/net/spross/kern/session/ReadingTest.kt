package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.ProducePrompt

class ReadingTest {

    private val knife = TurnFixture.knife

    @Test
    fun recognitionSaysTheWordThenItsMeaning() {
        val asking = TurnFixture.recognize(knife)
        assertEquals(Saying("kisu", "sw"), asking.promptSaying(saysMeaning = true))
        assertNull(asking.answerSaying(saysMeaning = true))
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal)
        assertEquals(Saying("Messer", "de"), revealed.answerSaying(saysMeaning = true))
        assertNull(revealed.answerSaying(saysMeaning = false))
    }

    @Test
    fun productionSaysTheMeaningThenTheWordEvenWhenAnsweredClean() {
        val asking = TurnFixture.produce(knife)
        assertEquals(Saying("Messer", "de"), asking.promptSaying(saysMeaning = true))
        assertNull(asking.promptSaying(saysMeaning = false))
        val clean = TurnFixture.state(asking, TurnIntent.InputChanged("kisu"))
        assertEquals(Saying("kisu", "sw"), clean.answerSaying(saysMeaning = false))
        // A slip holds on its proper spelling, which is what is said.
        val slipped = TurnFixture.state(asking, TurnIntent.Submit("kisuu"))
        assertEquals(Saying("kisu", "sw"), slipped.answerSaying(saysMeaning = false))
    }

    @Test
    fun aCardAskedByEarSaysTheMeaningItOwesBack() {
        val asking = TurnFixture.produce(knife, ProducePrompt.Sound)
        assertEquals(Saying("kisu", "sw"), asking.promptSaying(saysMeaning = true))
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal)
        assertEquals(Saying("Messer", "de"), revealed.answerSaying(saysMeaning = true))
    }
}

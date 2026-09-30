package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.ProducePrompt

class TurnSayingTest {

    private val knife = TurnFixture.knife

    @Test
    fun recognitionSaysTheWordThenItsMeaning() {
        val asking = TurnFixture.recognize(knife)
        assertEquals(TurnSaying("kisu", "sw"), asking.promptSaying(saysMeaning = true))
        assertNull(asking.answerSaying(saysMeaning = true))
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal)
        assertEquals(TurnSaying("Messer", "de"), revealed.answerSaying(saysMeaning = true))
        assertNull(revealed.answerSaying(saysMeaning = false))
    }

    @Test
    fun productionSaysTheMeaningThenTheWordEvenWhenAnsweredClean() {
        val asking = TurnFixture.produce(knife)
        assertEquals(TurnSaying("Messer", "de"), asking.promptSaying(saysMeaning = true))
        assertNull(asking.promptSaying(saysMeaning = false))
        val clean = TurnFixture.state(asking, TurnIntent.InputChanged("kisu"))
        assertEquals(TurnSaying("kisu", "sw"), clean.answerSaying(saysMeaning = false))
    }

    @Test
    fun aCardAskedByEarSaysTheMeaningItOwesBack() {
        val asking = TurnFixture.produce(knife, ProducePrompt.Sound)
        assertEquals(TurnSaying("kisu", "sw"), asking.promptSaying(saysMeaning = true))
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal)
        assertEquals(TurnSaying("Messer", "de"), revealed.answerSaying(saysMeaning = true))
    }
}

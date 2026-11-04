package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.catalog.AudioFixture
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.Fixture
import net.spross.kern.catalog.MapCatalogSource
import net.spross.kern.model.CardKind

class ReadingTest {

    private val knife = TurnFixture.knife

    @Test
    fun recognitionSaysTheWordThenItsMeaning() {
        val asking = TurnFixture.recognize(knife)
        assertEquals(Saying("kisu", "sw"), asking.reading(null, hasTargetVoice = true).prompt)
        assertNull(asking.reading(null, hasTargetVoice = true).answer)
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal)
        assertEquals(Saying("Messer", "de"), revealed.reading(null, hasTargetVoice = true).answer)
    }

    @Test
    fun productionSaysTheMeaningThenTheWordEvenWhenAnsweredClean() {
        val asking = TurnFixture.produce(knife)
        assertEquals(Saying("Messer", "de"), asking.reading(null, hasTargetVoice = true).prompt)
        val clean = TurnFixture.state(asking, TurnIntent.InputChanged("kisu"))
        assertEquals(Saying("kisu", "sw"), clean.reading(null, hasTargetVoice = true).answer)
        // A slip holds on its proper spelling, which is what is said.
        val slipped = TurnFixture.state(asking, TurnIntent.Submit("kisuu"))
        assertEquals(Saying("kisu", "sw"), slipped.reading(null, hasTargetVoice = true).answer)
    }

    @Test
    fun aCardAskedByEarSaysTheMeaningItOwesBack() {
        val asking = TurnFixture.hear(knife)
        assertEquals(Saying("kisu", "sw"), asking.reading(null, hasTargetVoice = true).prompt)
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal)
        assertEquals(Saying("Messer", "de"), revealed.reading(null, hasTargetVoice = true).answer)
    }

    /**
     * RULE: the meaning is said only beside a word this device can sound — a recording of it or a voice.
     * WHY: hearing both sides paired is the cue; the meaning heard alone binds nothing.
     */
    @Test
    fun aWordThatCannotBeHeardLeavesItsMeaningUnsaid() {
        val catalog = Catalog.load(MapCatalogSource(Fixture.files + AudioFixture.files))
        val unrecorded = TurnFixture.produce(knife)
        assertNull(unrecorded.reading(catalog, hasTargetVoice = false).prompt)
        val door = TurnFixture.card("door", "Tür", "mlango", CardKind.Noun)
        val recorded = TurnFixture.produce(door)
        assertEquals(Saying("Tür", "de"), recorded.reading(catalog, hasTargetVoice = false).prompt)
    }
}

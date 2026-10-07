package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.ProducePrompt
import net.spross.kern.session.AnswerControls.Confirm
import net.spross.kern.session.AnswerControls.GiveUp
import net.spross.kern.session.AnswerControls.Primary
import net.spross.kern.session.AnswerControls.Slot

/** What stands under the review card, by where the turn stands. */
class AnswerControlsTests {

    private val knife = TurnFixture.knife

    @Test
    fun productionIsTypedWithOneSubmitAndABlankRevealHandsOverToTheVerdicts() {
        val asking = TurnFixture.produce(knife).controls
        assertEquals(Slot.Typed("sw"), asking.slot)
        assertEquals(Primary.Submit, asking.primary)
        assertFalse(asking.cantListen)

        val blank = TurnFixture.state(TurnFixture.produce(knife), TurnIntent.Reveal).controls
        assertEquals(Slot.SelfGrade, blank.slot)
        assertNull(blank.primary)
    }

    @Test
    fun recognitionIsNeverTyped() {
        val asking = TurnFixture.recognize(knife).controls
        assertNull(asking.slot)
        assertEquals(Primary.Reveal, asking.primary)
        assertEquals(Slot.SelfGrade, TurnFixture.state(TurnFixture.recognize(knife), TurnIntent.Reveal).controls.slot)
    }

    @Test
    fun aMissKeepsTheFieldOpenForTheRetypeWithAQuietSkip() {
        val missed = TurnFixture.state(TurnFixture.produce(knife), TurnIntent.Submit("mbwa")).controls
        assertEquals(Slot.Typed("sw", editable = true), missed.slot)
        assertNull(missed.primary)
        assertEquals(GiveUp.Skip, missed.giveUp)
        assertNull(missed.confirm, "the retype is the way on")
    }

    @Test
    fun aMissAskedByEarRetypesNothingAndGoesOnWithNext() {
        val missed = TurnFixture.state(TurnFixture.produce(knife, ProducePrompt.Sound), TurnIntent.Submit("Hund")).controls
        assertNull(missed.slot)
        assertEquals(GiveUp.Next, missed.giveUp)
    }

    @Test
    fun aNearMissHoldsUntilTappedAndACleanAnswerOnlyWhereNoBeatRuns() {
        assertEquals(Confirm.Always, TurnFixture.state(TurnFixture.produce(knife), TurnIntent.Submit("kisuu")).controls.confirm)
        assertEquals(Confirm.WhenNoBeat, TurnFixture.state(TurnFixture.produce(knife), TurnIntent.Submit("kisu")).controls.confirm)
    }

    @Test
    fun aCardAskedByEarOffersItsWordInWritingWhileItAsks() {
        val asking = TurnFixture.produce(knife, ProducePrompt.Sound)
        assertTrue(asking.controls.cantListen)
        assertEquals(Slot.Typed("de"), asking.controls.slot, "the meaning is owed")
        assertFalse(TurnFixture.state(asking, TurnIntent.ShowPromptText).controls.cantListen)
    }

    @Test
    fun theWriteOutOwnsTheTurn() {
        val missed = TurnFixture.state(TurnFixture.recognize(knife, firstExposure = true), TurnIntent.Reveal)
        val step = TurnFixture.state(missed, TurnIntent.SelfGrade(SelfGrading.Verdict.Unknown)).controls
        assertEquals(Slot.WriteOut("sw", missed = false), step.slot)
        assertEquals(GiveUp.Skip, step.giveUp)
    }
}

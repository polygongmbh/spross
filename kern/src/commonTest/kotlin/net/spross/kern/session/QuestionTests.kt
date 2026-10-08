package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.ClosingNote
import net.spross.kern.model.FormTag
import net.spross.kern.model.PluralForm
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.TaggedForm

class QuestionTests {

    /** A noun with a plural and a note, asked in an area it shares with another card. */
    private val knife = TurnFixture.knife.copy(
        promptAmbiguous = true,
        target = TurnFixture.knife.target.copy(forms = listOf(TaggedForm(FormTag.PLURAL, "visu")), note = "also a blade"),
    )

    @Test
    fun recognitionAsksWithTheTargetAndOpensOntoTheMeaning() {
        val asking = TurnFixture.recognize(knife).question
        assertEquals("kisu", asking.prompt.text)
        // The cited form carries its plural, article or not; no context cue rides on the target.
        assertEquals(PluralForm.Form("visu"), asking.prompt.plural)
        assertNull(asking.prompt.context)
        assertFalse(asking.opens)

        val revealed = TurnFixture.state(TurnFixture.recognize(knife), TurnIntent.Reveal).question
        assertTrue(revealed.opens)
        assertEquals("Messer", revealed.answer.text)
        assertEquals(ClosingNote.Own("also a blade"), revealed.closing.note)
    }

    @Test
    fun aRotatedSynonymCarriesNoCitationGrammarAndLeavesTheCitationAsAnAlternate() {
        val car = TurnFixture.car.let { it.copy(target = it.target.copy(forms = listOf(TaggedForm(FormTag.PLURAL, "magari")))) }
        val question = TurnFixture.recognize(car).copy(promptForm = "motokaa").question
        assertEquals("motokaa", question.prompt.text)
        assertNull(question.prompt.plural)
        assertEquals(listOf("gari"), question.closing.alternates.map { it.text })
        // The meaning side names what the source also teaches.
        assertEquals("Auto / Wagen", question.answer.text)
    }

    @Test
    fun productionAsksWithTheSourceAndOpensOnlyOnAMiss() {
        val asking = TurnFixture.produce(knife)
        assertEquals("Messer", asking.question.prompt.text)
        assertEquals("test", asking.question.prompt.context)
        assertEquals("kisu", asking.question.answer.text)

        assertFalse(TurnFixture.state(asking, TurnIntent.Submit("kisu")).question.opens)
        assertFalse(TurnFixture.state(asking, TurnIntent.Submit("kisuu")).question.opens)
        assertTrue(TurnFixture.state(asking, TurnIntent.Submit("mbwa")).question.opens)
    }

    @Test
    fun aCardAskedByEarShowsTheSoundUntilNothingIsLeftToWithhold() {
        val asking = TurnFixture.produce(knife, ProducePrompt.Sound)
        assertEquals(Question.Form.Sound, asking.question.prompt.form)
        assertNull(asking.question.prompt.text)
        assertEquals("Messer", asking.question.answer.text)

        val written = TurnFixture.state(asking, TurnIntent.ShowPromptText).question
        assertEquals("kisu", written.prompt.text)
        val revealed = TurnFixture.state(asking, TurnIntent.Reveal).question
        assertEquals("kisu", revealed.prompt.text)
    }

    @Test
    fun everyTargetFormTheCardSaysAloudCarriesASpeakerOnItsSide() {
        val turns = listOf(
            TurnFixture.recognize(knife),
            TurnFixture.produce(knife),
            TurnFixture.produce(knife, ProducePrompt.Sound),
        ).flatMap { listOf(it, TurnFixture.state(it, TurnIntent.Reveal)) }
        for (turn in turns) {
            val reading = turn.reading(saysMeaning = false)
            val question = turn.question
            reading.prompt?.let { assertEquals(it, question.prompt.saying) }
            reading.answer?.let { said -> assertTrue(said == question.prompt.saying || said == question.answer.saying) }
        }
    }
}

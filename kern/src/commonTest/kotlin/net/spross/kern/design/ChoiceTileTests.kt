package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChoiceTileTests {

    private val options = listOf("ч", "ш", "щ", "ж")

    @Test
    fun aWrongPickIsAnnouncedAsWrongAndTheAnswerAsCorrect() {
        val verdicts = options.map { ChoiceTile.of(it, answer = "ч", chosen = "ш").verdict }
        assertEquals(listOf(ChoiceVerdict.Correct, ChoiceVerdict.Wrong, null, null), verdicts)
    }

    @Test
    fun anOwedQuestionAnnouncesNothing() {
        for (option in options) assertNull(ChoiceTile.of(option, answer = "ч", chosen = null).verdict)
    }
}

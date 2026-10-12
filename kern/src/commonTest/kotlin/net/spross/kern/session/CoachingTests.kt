package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.PresentationRole

/** The first round's lines: each stands at its own moment, and none outside that round. */
class CoachingTests {

    @Test
    fun theRecognitionLineStandsOnlyOverAHiddenRecognitionPrompt() {
        assertEquals(CoachLine.Recognize, Coaching.promptLine(true, PresentationRole.Recognize, revealed = false))
        assertNull(Coaching.promptLine(true, PresentationRole.Recognize, revealed = true))
        assertNull(Coaching.promptLine(true, PresentationRole.Produce, revealed = false))
    }

    @Test
    fun theControlsCarryTheWriteOutsLineWhileOneStandsAndTheGradesOtherwise() {
        assertEquals(CoachLine.Write, Coaching.controlsLine(true, writing = true))
        assertEquals(CoachLine.Grade, Coaching.controlsLine(true, writing = false))
    }

    @Test
    fun noLineStandsOnceTheRoundOwesNoCoaching() {
        assertNull(Coaching.promptLine(false, PresentationRole.Recognize, revealed = false))
        assertNull(Coaching.controlsLine(false, writing = false))
    }
}

package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.session.ADVANCE_LIVE_MS

class CardMotionTests {

    /** The outgoing card leaves edge-on to one side as the incoming one lands flat from the other, neither showing its back. */
    @Test
    fun theSwitchTurnsOneCardOutAndTheNextIn() {
        assertFalse(CardMotion.flipShows(CardMotion.flipAngle(0.0, incoming = true)))
        assertEquals(0.0, CardMotion.flipAngle(1.0, incoming = true), absoluteTolerance = 0.0)
        assertEquals(0.0, CardMotion.flipAngle(0.0, incoming = false), absoluteTolerance = 0.0)
        assertFalse(CardMotion.flipShows(CardMotion.flipAngle(1.0, incoming = false)))
        val halfway = listOf(true, false).map { CardMotion.flipAngle(0.5, it) }
        assertTrue(halfway.all { CardMotion.flipShows(it) })
        assertTrue(halfway[0] * halfway[1] < 0, "the two cards turn to opposite sides")
    }

    /** A verdict is fully drawn before the shortest beat can move the card on. */
    @Test
    fun aRevealSettlesBeforeTheLiveBeatEnds() {
        assertTrue(CardMotion.REVEAL_MS < ADVANCE_LIVE_MS)
    }
}

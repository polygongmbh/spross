package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals

/** One turn as said, and where a bedtime ends a run. */
class ListeningBeatsTests {

    private val turn = ListeningTurn(
        cardId = "w", targetForm = "Brot", sourceForm = "bread", spokenArticle = "das",
        recallGapMs = 1, echoGapMs = 2, turnGapMs = 3,
    )

    /**
     * Target, then its meaning in the learner's own language, then the target again;
     * the article rides only the target sayings, and the meaning stays out until it is said.
     */
    @Test
    fun aTurnSaysTheWordItsMeaningAndTheWordAgain() {
        val said = turn.sayings
        assertEquals(listOf("Brot", "bread", "Brot"), said.map { it.form })
        assertEquals(listOf(true, false, true), said.map { it.inTarget })
        assertEquals(listOf("das", null, "das"), said.map { it.article })
        assertEquals(listOf(1L, 2L, 3L), said.map { it.gapMs })
        assertEquals(listOf(false, true, true), said.map { it.revealed })
    }

    /** The seam ends the run only once a bedtime has arrived; a run with none laps on. */
    @Test
    fun onlyAnArrivedBedtimeEndsTheRunAtTheSeam() {
        assertEquals(ListeningSeam.Advance, listeningSeam(null))
        assertEquals(ListeningSeam.Advance, listeningSeam(60_000L))
        assertEquals(ListeningSeam.End, listeningSeam(0L))
    }
}

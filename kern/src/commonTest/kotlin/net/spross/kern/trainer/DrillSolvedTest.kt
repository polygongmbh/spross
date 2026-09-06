package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/** What answering a Sprosse OUT means, on pools of every shape a drill has. */
class DrillSolvedTest {

    private val pools: Map<Int, List<String>?> = mapOf(
        1 to listOf("a", "b"),
        2 to listOf("a", "b", "c"),
        3 to emptyList(),
        4 to null,
    )

    @Test
    fun aSprosseIsClearedOnlyOnceEveryPromptOfItIsSolved() {
        assertEquals(emptySet(), DrillSolved.cleared(setOf("a"), 4, pools::get))
        assertEquals(setOf(1), DrillSolved.cleared(setOf("a", "b"), 4, pools::get))
        assertEquals(setOf(1, 2), DrillSolved.cleared(setOf("a", "b", "c"), 4, pools::get))
    }

    /** An empty pool has answered nothing, and a drawn Sprosse has no pool to answer out. */
    @Test
    fun anEmptyOrGeneratedSprosseIsNeverCleared() {
        assertEquals(setOf(1, 2), DrillSolved.cleared(setOf("a", "b", "c", "z"), 4, pools::get))
    }

    @Test
    fun theLadderIsReadOnlyUpToItsTop() {
        assertEquals(setOf(1), DrillSolved.cleared(setOf("a", "b", "c"), 1, pools::get))
    }
}

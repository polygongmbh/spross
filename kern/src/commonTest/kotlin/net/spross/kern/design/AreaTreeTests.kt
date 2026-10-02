package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaGrowth

class AreaTreeTests {

    @Test
    fun aTreeHangsOneSlotPerMark() {
        for (marks in listOf(1, 7, 62)) assertEquals(marks, AreaTree.grow("kitchen", marks, marks / 3).slots.size)
    }

    @Test
    fun noMarkHangsBelowTheGround() {
        for (area in listOf("kitchen", "travel", "family")) for (marks in listOf(3, 20, 60, 150)) {
            assertTrue(AreaTree.grow(area, marks, marks / 3).slots.all { it.y <= 0.0 }, "$area $marks")
        }
    }

    @Test
    fun theSummaryTreeStandsTallerAsTheAreaGrows() {
        val young = tree("kitchen", met = 3)
        val grown = tree("kitchen", met = 30)
        assertTrue(AreaTree.heroHeight(young, 190.0) < AreaTree.heroHeight(grown, 190.0))
    }

    @Test
    fun everyAreaStandsOnceBackToFront() {
        val trees = List(17) { tree("area$it", met = it * 2) }
        val spots = TreesLayout.place(trees, width = 380.0).spots
        assertEquals(trees.indices.toList(), spots.map { it.index }.sorted())
        assertEquals(spots.map { it.baseline }.sorted(), spots.map { it.baseline })
    }

    private fun tree(area: String, met: Int) =
        AreaGrowth(area, 0, met, 0, 0, 1, 0, false, List(met) { 0.5 })
}

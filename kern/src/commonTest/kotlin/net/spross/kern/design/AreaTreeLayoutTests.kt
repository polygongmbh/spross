package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaTree

class AreaTreeLayoutTests {

    @Test
    fun aTreeHangsOneSlotPerMark() {
        for (marks in listOf(1, 7, 62)) assertEquals(marks, AreaTreeLayout.grow("kitchen", marks).slots.size)
    }

    @Test
    fun noMarkHangsBelowTheGround() {
        for (area in listOf("kitchen", "travel", "family")) for (marks in listOf(3, 20, 60, 150)) {
            assertTrue(AreaTreeLayout.grow(area, marks).slots.all { it.y <= 0.0 }, "$area $marks")
        }
    }

    @Test
    fun theSummaryTreeStandsTallerAsTheAreaGrows() {
        val young = tree("kitchen", mass = 1.0)
        val grown = tree("kitchen", mass = 30.0)
        assertTrue(AreaTreeLayout.heroHeight(young, 190.0) < AreaTreeLayout.heroHeight(grown, 190.0))
    }

    @Test
    fun everyAreaStandsOnceBackToFront() {
        val trees = List(17) { tree("area$it", mass = it * 2.0) }
        val spots = TreesLayout.place(trees, width = 380.0).spots
        assertEquals(trees.indices.toList(), spots.map { it.index }.sorted())
        assertEquals(spots.map { it.baseline }.sorted(), spots.map { it.baseline })
    }

    private fun tree(area: String, mass: Double) =
        AreaTree(area, 4, 10, 2, 1, 0, 0, mass, false, List(17) { 0.5 })
}

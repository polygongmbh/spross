package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaTree

class TreeLayoutTests {

    @Test
    fun aTreeHangsOneSlotPerMark() {
        for (marks in listOf(1, 7, 62)) assertEquals(marks, TreeLayout.grow("kitchen", marks).slots.size)
    }

    @Test
    fun theSameAreaAndCountGrowTheSameTree() {
        val once = TreeLayout.grow("kitchen", 30)
        val again = TreeLayout.grow("kitchen", 30)
        assertEquals(once.limbs, again.limbs)
        assertEquals(once.slots, again.slots)
    }

    @Test
    fun theSummaryTreeStandsTallerAsTheAreaGrowsAndFillsATallerScreen() {
        val young = tree("kitchen", mass = 1.0)
        val grown = tree("kitchen", mass = 30.0)
        assertTrue(TreeLayout.heroHeight(young, 190.0) < TreeLayout.heroHeight(grown, 190.0))
        assertTrue(TreeLayout.heroHeight(grown, 400.0) > TreeLayout.heroHeight(grown, 190.0))
        assertEquals(TreeLayout.heroHeight(young, 400.0) / TreeLayout.heroHeight(grown, 400.0),
            TreeLayout.heroHeight(young, 190.0) / TreeLayout.heroHeight(grown, 190.0), 1e-9)
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

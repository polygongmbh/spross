package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaTree

class TreeGrowthTest {
    private val seed = Mix.seed("kitchen")

    @Test
    fun aTreeHangsOneSlotPerMark() {
        for (marks in listOf(1, 7, 62)) assertEquals(marks, TreeSkeleton.grow(seed, marks).slots.size)
    }

    @Test
    fun theSameAreaAndCountGrowTheSameTree() {
        val once = TreeSkeleton.grow(seed, 30)
        val again = TreeSkeleton.grow(seed, 30)
        assertEquals(once.limbs.map { it.end }, again.limbs.map { it.end })
        assertEquals(once.slots.map { it.point }, again.slots.map { it.point })
    }

    @Test
    fun theSummaryTreeStandsTallerAsTheAreaGrowsAndFillsATallerScreen() {
        val young = tree(mass = 1.0)
        val grown = tree(mass = 30.0)
        assertTrue(ForestLayout.heroHeight(young, 190f) < ForestLayout.heroHeight(grown, 190f))
        assertTrue(ForestLayout.heroHeight(grown, 400f) > ForestLayout.heroHeight(grown, 190f))
        assertEquals(ForestLayout.heroHeight(young, 400f) / ForestLayout.heroHeight(grown, 400f),
            ForestLayout.heroHeight(young, 190f) / ForestLayout.heroHeight(grown, 190f), 1e-4f)
    }

    @Test
    fun theForestStandsEveryAreaOnceBackToFront() {
        val trees = SampleForest.trees(0.6)
        val spots = ForestLayout.plant(trees, width = 1000f, density = 2.625f)
        assertEquals(trees.map { it.area }.toSet(), spots.map { it.planted.tree.area }.toSet())
        assertEquals(trees.size, spots.size)
        assertEquals(spots.map { it.planted.foot.y }.sorted(), spots.map { it.planted.foot.y })
    }

    private fun tree(mass: Double) =
        AreaTree("kitchen", 4, 10, 2, 1, 0, 0, mass, false, List(17) { 0.5 })
}

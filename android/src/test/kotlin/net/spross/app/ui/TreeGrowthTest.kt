package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaTree

class TreeGrowthTest {
    private val seed = Mix.seed("kitchen")

    @Test
    fun aTreeGrowsOnlyFromItsTips() {
        val young = TreeSkeleton.grow(seed, TreeSkeleton.vigor(12), 12)
        val old = TreeSkeleton.grow(seed, TreeSkeleton.vigor(60), 60)
        // Every limb the young tree has already forked from stands in the old one unchanged.
        val finished = young.limbs.filter { limb -> young.limbs.any { it.start == limb.end } }
        assertTrue(finished.isNotEmpty())
        for (limb in finished) {
            assertTrue(old.limbs.any { it.start == limb.start && it.end == limb.end && it.control == limb.control },
                "a finished limb moved as the tree grew")
        }
    }

    @Test
    fun hangingAnotherWordMovesNoneAlreadyHanging() {
        val vigor = TreeSkeleton.vigor(30)
        val fewer = TreeSkeleton.grow(seed, vigor, 20).slots.take(20).map { it.point }
        val more = TreeSkeleton.grow(seed, vigor, 30).slots.take(20).map { it.point }
        assertEquals(fewer, more)
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

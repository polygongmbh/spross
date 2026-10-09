package net.spross.kern.design

import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaGrowth
import net.spross.kern.box.StageCounts
import net.spross.kern.box.TreeTransition

class TreePictureTests {

    @Test
    fun everyWordHangsAsTheMarkOfItsStage() {
        val tree = AreaGrowth("kitchen", StageCounts(fresh = 4, growing = 5, settled = 3, matured = 2), 0, false, List(14) { 0.5 })
        val picture = TreePicture.of(tree, "kitchen", 100.0, 200.0, 80.0)
        fun marks(vararg inks: TreeInk) =
            picture.layers.filter { it.ink in inks }.flatMap { it.shapes }.map { it.rank }.filter { it >= 0 }.toSet().size
        assertEquals(2, marks(TreeInk.FRUIT))
        assertEquals(3, marks(TreeInk.BLOSSOM))
        assertEquals(5, marks(TreeInk.LEAF, TreeInk.LEAF_DEEP))
        assertEquals(4, marks(TreeInk.BUD))
    }

    @Test
    fun aStrongerWordHangsABiggerMark() {
        fun bud(strength: Double): Double {
            val tree = AreaGrowth("kitchen", StageCounts(fresh = 12), 0, false, List(12) { strength })
            val shape = TreePicture.of(tree, "kitchen", 100.0, 200.0, 80.0).layers
                .first { it.ink == TreeInk.BUD }.shapes.first { it.rank == 0 }
            return width(shape.path)
        }
        assertTrue(bud(0.9) > bud(0.1))
    }

    @Test
    fun aYoungTreeDrawsOnlyTheWoodItsMarksHangOn() {
        val grown = AreaTree.grow("kitchen", 2, 0)
        val carrying = grown.carrying()
        assertTrue(grown.slots.all { it.limb in carrying })
        assertTrue(carrying.size < grown.limbs.size)
    }

    @Test
    fun aHungMarkArrivesOutOfNothingAndAnUntouchedOneNeverMoves() {
        val before = AreaGrowth("kitchen", StageCounts(growing = 4), 0, false, List(4) { 0.5 })
        val after = AreaGrowth("kitchen", StageCounts(growing = 4, fresh = 1), 0, true, List(5) { 0.5 })
        val rise = TreeRise(TreeTransition(before, after))
        assertEquals(listOf(4), rise.ranks)
        assertEquals(0.0, rise.scale(4, 0.0), 1e-9)
        assertEquals(1.0, rise.scale(4, 1.0), 1e-9)
        for (progress in listOf(0.0, 0.6, 1.0)) assertEquals(1.0, rise.scale(0, progress))
        assertTrue(rise.risen(0.0) < rise.risen(1.0))
    }

    /** How wide a path reaches across x. */
    private fun width(path: DoubleArray): Double {
        var least = Double.MAX_VALUE; var most = -Double.MAX_VALUE
        fun take(x: Double) { least = min(least, x); most = max(most, x) }
        var i = 0
        while (i < path.size) when (path[i].toInt()) {
            TreePath.MOVE, TreePath.LINE -> { take(path[i + 1]); i += 3 }
            TreePath.QUAD -> { take(path[i + 1]); take(path[i + 3]); i += 5 }
            TreePath.OVAL -> { take(path[i + 1]); take(path[i + 1] + path[i + 3]); i += 5 }
            else -> i += 1
        }
        return most - least
    }
}

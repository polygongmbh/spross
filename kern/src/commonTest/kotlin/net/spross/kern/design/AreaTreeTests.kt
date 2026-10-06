package net.spross.kern.design

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.spross.kern.box.AreaGrowth
import net.spross.kern.box.StageCounts

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
    fun aMarkLeansOutAlongItsWoodNeverBackAgainstIt() {
        for (area in listOf("kitchen", "travel", "family", "work", "food")) for (marks in listOf(20, 60, 150)) {
            val grown = AreaTree.grow(area, marks, 0)
            for (slot in grown.slots) {
                val limb = grown.limbs[slot.limb]
                val dot = cos(slot.angle) * (limb.endX - limb.startX) + sin(slot.angle) * (limb.endY - limb.startY)
                assertTrue(dot > 0, "$area $marks: a mark on limb ${slot.limb} points back down its wood")
            }
        }
    }

    @Test
    fun aLearnerKeepsTheirGardenAndEachLanguageGrowsItsOwn() {
        fun limbs(name: String?, target: String) =
            AreaTree.grow(AreaTree.seed(AreaTree.garden(name, target), "kitchen"), 30, 0).limbs
        assertEquals(limbs("Ada", "de"), limbs(" ada ", "de"))
        assertNotEquals(limbs("Ada", "de"), limbs("Ben", "de"))
        assertNotEquals(limbs("Ada", "de"), limbs("Ada", "es"))
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
        AreaGrowth(area, StageCounts(growing = met), 1, false, List(met) { 0.5 })
}

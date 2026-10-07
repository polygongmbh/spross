package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

class DrillChipRowsTests {

    @Test
    fun aFewChipsShareOneLine() {
        assertEquals(listOf(3), Drill.chipRows(3))
    }

    @Test
    fun moreBreakIntoTwoLinesWithTheOddChipOnTop() {
        assertEquals(listOf(3, 2), Drill.chipRows(5))
    }

    @Test
    fun noChipsDrawNoLine() {
        assertEquals(emptyList(), Drill.chipRows(0))
    }

    @Test
    fun everyDrillWearsAFaceOfItsOwn() {
        assertEquals(Drill.entries.size, Drill.entries.map { it.emoji }.toSet().size)
    }
}

package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals

class ChipRowsTests {

    @Test
    fun aFewChipsShareOneLine() {
        assertEquals(listOf(3), ChipRows.of(3))
    }

    @Test
    fun moreBreakIntoTwoLinesWithTheOddChipOnTop() {
        assertEquals(listOf(3, 2), ChipRows.of(5))
    }

    @Test
    fun noChipsDrawNoLine() {
        assertEquals(emptyList(), ChipRows.of(0))
    }
}

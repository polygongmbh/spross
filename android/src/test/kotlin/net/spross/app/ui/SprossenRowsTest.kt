package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the Sprossen card cuts its chips into lines. The break is drawn rather than
 * discovered, so it is the one thing about the card a test can hold (`docs/drills.md`).
 */
class SprossenRowsTest {

    private fun chips(count: Int) = List(count) { HubChip("🔢", "chip $it") {} }

    @Test
    fun threeChipsOrFewerStandOnOneLine() {
        assertEquals(emptyList(), chipRows(emptyList()))
        assertEquals(listOf(1), chipRows(chips(1)).map { it.size })
        assertEquals(listOf(3), chipRows(chips(3)).map { it.size })
    }

    /** Past three the card breaks into two, the fuller line on top. */
    @Test
    fun moreThanThreeBreakIntoTwoLinesInTheOrderTheyWereOffered() {
        assertEquals(listOf(2, 2), chipRows(chips(4)).map { it.size })
        assertEquals(listOf(3, 2), chipRows(chips(5)).map { it.size })
        assertEquals(listOf(3, 3), chipRows(chips(6)).map { it.size })
        val six = chips(6)
        assertEquals(six, chipRows(six).flatten())
    }
}

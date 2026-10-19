package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

class DrillTests {

    @Test
    fun everyDrillWearsAFaceOfItsOwn() {
        assertEquals(Drill.entries.size, Drill.entries.map { it.emoji }.toSet().size)
    }
}

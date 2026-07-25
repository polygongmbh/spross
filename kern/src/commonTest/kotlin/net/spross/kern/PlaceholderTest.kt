package net.spross.kern

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderTest {
    @Test
    fun greets() {
        assertEquals("SprossKern:smoke", Placeholder.greet("smoke"))
    }
}

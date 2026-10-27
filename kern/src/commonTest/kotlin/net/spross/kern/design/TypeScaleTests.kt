package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeScaleTests {

    @Test
    fun aPhoneKeepsTheReadersSize() {
        val scale = TypeScale(windowWidth = 440.0, windowHeight = 956.0)
        assertEquals(TypeScale.NORMAL, scale.app)
        assertEquals(TypeScale.NORMAL, scale.session)
    }

    @Test
    fun aBigWindowEnlargesTheAppInEitherOrientation() {
        for (scale in listOf(TypeScale(669.0, 951.0), TypeScale(951.0, 669.0))) {
            assertTrue(scale.app > TypeScale.NORMAL)
        }
    }

    @Test
    fun aBigWindowEnlargesASessionMoreThanTheApp() {
        val scale = TypeScale(windowWidth = 669.0, windowHeight = 951.0)
        assertTrue(scale.session > scale.app)
    }
}

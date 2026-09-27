package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a screen about to play a word asks for the volume to be turned up. */
class OutputVolumeTest {

    /**
     * RULE: a muted device and one at its bottom step are low; a device at half volume is not.
     * WHY: the hint is for a word that would play unheard, not a nag at a comfortable level.
     */
    @Test
    fun onlyAVolumeAtOrNearZeroIsLow() {
        assertTrue(isVolumeLow(0.0))
        assertTrue(isVolumeLow(1.0 / 16), "an iPhone's bottom step")
        assertTrue(isVolumeLow(1.0 / 15), "a 15-step Android stream's bottom step")
        assertFalse(isVolumeLow(0.5))
        assertFalse(isVolumeLow(1.0))
    }
}

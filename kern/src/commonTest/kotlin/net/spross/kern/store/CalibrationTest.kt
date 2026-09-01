package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import net.spross.kern.model.BoxConfig

class CalibrationTest {

    private val state = StoreFixture.state()

    /** A document written under another calibration answers to this build's numbers. */
    @Test
    fun productCalibrationOverridesAStoredConfig() {
        val stale = BoxConfig(
            sessionCap = 9,
            desiredRetention = 0.95,
            maximumIntervalDays = 30,
            consolidatedStability = 3.0,
            stepsSeconds = listOf(60L),
        )
        assertNotEquals(BoxConfig.product(), stale)
        val loaded = state.copy(config = stale).withProductCalibration()
        assertEquals(BoxConfig.product(), loaded.config)
    }

    /** Only the config is the build's; everything else in the document is the learner's. */
    @Test
    fun calibrationLeavesTheRestOfTheBoxIntact() {
        val loaded = state.copy(config = BoxConfig(sessionCap = 9)).withProductCalibration()
        assertEquals(state, loaded)
    }

    @Test
    fun decodedBoxKeepsItsStoredConfigUntilCalibrated() {
        val stale = state.copy(config = BoxConfig(sessionCap = 9))
        val decoded = StoreCodec.decode(StoreCodec.encode(stale))
        assertEquals(9, decoded.config.sessionCap)
        val rejoined = decoded.join(StoreFixture.cards, StoreFixture.stamp)
        assertEquals(BoxConfig.product(), rejoined.withProductCalibration().config)
    }
}

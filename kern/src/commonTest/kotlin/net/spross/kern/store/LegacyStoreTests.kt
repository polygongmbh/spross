package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine

/** The one-way door from the v1 boxes a device still holds into the store that replaces them. */
class LegacyStoreTests {

    private val state = StoreFixture.state()

    private fun converted(state: net.spross.kern.box.BoxState): StoredBox =
        LegacyStore.convert(mapOf("uk" to StoreCodec.encode(state))).boxes.getValue("uk")

    @Test
    fun aV1BoxArrivesAsTheSchedulesItsLogsImply() {
        val stored = converted(state)
        // The v1 document stored phase, memory and lapses; replaying its logs gives them back.
        assertEquals(state.scheduling, stored.scheduling)
        assertEquals(state.enqueued, stored.enqueued)
        assertEquals(state.ownWords, stored.ownWords)
        assertEquals(state.reportedIssues, stored.reportedIssues)
        assertEquals(state.lastExportAt, stored.lastExportAt)
    }

    @Test
    fun aWordSuspendedBeforeItWasEverAskedArrivesAsAHusk() {
        val suspended = BoxEngine.setSuspended(state, "fixture-phrase", true, Box.day1)
        val sched = converted(suspended).scheduling.getValue("fixture-phrase")
        assertTrue(sched.suspended)
        assertTrue(sched.log.isEmpty())
    }

    /**
     * The leech rule removed on 2026-09-01 auto-suspended anything with two lapses; its mark
     * is lifted on the way over, read off the count v1 stored rather than a replayed one.
     */
    @Test
    fun aLeechEraSuspensionIsLiftedOnTheWayOver() {
        val leech = Box.inject(
            state,
            Box.sched("fixture-phrase", dueMillis = Box.day1, lastReviewMillis = Box.day1,
                      lapses = 2, suspended = true),
        )
        assertFalse(converted(leech).scheduling.getValue("fixture-phrase").suspended)

        val ownChoice = Box.inject(
            state,
            Box.sched("fixture-phrase", dueMillis = Box.day1, lastReviewMillis = Box.day1,
                      lapses = 1, suspended = true),
        )
        assertTrue(converted(ownChoice).scheduling.getValue("fixture-phrase").suspended)
    }

    @Test
    fun everyLanguageOnDiskComesAcrossAtOnce() {
        val sw = Box.state(listOf(Box.word(1))).copy(
            joinStamp = net.spross.kern.model.JoinStamp("de", "sw", "fixture"),
        )
        val boxes = LegacyStore.convert(
            mapOf("uk" to StoreCodec.encode(state), "sw" to StoreCodec.encode(sw)),
        )
        assertEquals(setOf("uk", "sw"), boxes.boxes.keys)
    }
}

package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating

/**
 * Join-filter inventory: schedules and enqueued entries of non-joining cards turn
 * inert on a source switch — never pruned — and revive on switch-back. The phrase
 * unlock gate reads component schedules by key, so phrases stay unlocked.
 */
class SourceSwitchTests {
    private val now = Box.day1

    // w02 has no source realization under "en": it joins only the de profile.
    private val w01 = Box.word(1)
    private val w02 = Box.word(2)
    private val w03 = Box.word(3)
    private val p1 = Box.phrase("p1", components = listOf("w01", "w02"))
    private val deJoin = listOf(w01, w02, w03, p1)
    private val enJoin = listOf(w01, w03, p1)
    private val enStamp = JoinStamp("en", "sw", "fixture")

    private fun studied(): BoxState {
        var state = Box.state(deJoin)
        state = Box.inject(state, Box.sched("w01", dueMillis = now - 60_000, lastReviewMillis = Box.plusDays(now, -1.0)))
        state = Box.inject(state, Box.sched("w02", dueMillis = now - 30_000, lastReviewMillis = Box.plusDays(now, -1.0)))
        return state
    }

    @Test
    fun nonJoiningSchedulesTurnInertAndReviveOnSwitchBack() {
        val de = studied()
        assertEquals(2, BoxEngine.statistics(de, now, Box.TZ).activeCount)
        assertEquals(listOf(Box.produce("w01"), Box.produce("w02")), BoxEngine.dueNow(de, now))

        val en = BoxEngine.rejoin(de, enJoin, enStamp)
        assertEquals(enStamp, en.joinStamp)
        assertEquals(1, BoxEngine.statistics(en, now, Box.TZ).activeCount)
        assertEquals(listOf(Box.produce("w01")), BoxEngine.dueNow(en, now))
        assertFalse(BoxEngine.exposureCards(en, now, limit = 10).any { it.id == "w02" })
        // Inert, not pruned: the raw schedule survives untouched.
        assertEquals(de.scheduling, en.scheduling)

        val back = BoxEngine.rejoin(en, deJoin, Box.stamp)
        assertEquals(2, BoxEngine.statistics(back, now, Box.TZ).activeCount)
        assertEquals(listOf(Box.produce("w01"), Box.produce("w02")), BoxEngine.dueNow(back, now))
    }

    @Test
    fun answerOnNonJoiningKeyIsStaleNoop() {
        val en = BoxEngine.rejoin(studied(), enJoin, enStamp)
        val outcome = BoxEngine.answer(en, Box.produce("w02"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.StaleUnit, outcome.status)
        assertEquals(en, outcome.state)

        // Back under the de join the very same key applies again.
        val back = BoxEngine.rejoin(en, deJoin, Box.stamp)
        val applied = BoxEngine.answer(back, Box.produce("w02"), Rating.Good, now, Box.TZ)
        assertEquals(AnswerStatus.Applied, applied.status)
    }

    @Test
    fun phrasesStayUnlockedAcrossSourceSwitch() {
        val de = studied() // w01 + w02 in Review at stability 10 ≥ 2.0
        assertEquals(listOf(Box.produce("p1")), Box.candidates(de).unlockedPhrases)

        // Under en, component w02 does not join — but the gate reads its produce
        // schedule BY KEY, so p1 stays unlocked.
        val en = BoxEngine.rejoin(de, enJoin, enStamp)
        assertEquals(listOf(Box.produce("p1")), Box.candidates(en).unlockedPhrases)
    }

    @Test
    fun enqueuedEntriesSurviveInertAndRevive() {
        var de = Box.state(deJoin)
        de = BoxEngine.enqueue(de, listOf("w02"))
        assertEquals(listOf("w02"), de.enqueued)

        val en = BoxEngine.rejoin(de, enJoin, enStamp)
        assertEquals(listOf("w02"), en.enqueued) // kept, just not eligible
        assertFalse(Box.produce("w02") in Box.candidates(en).newUnits)

        val back = BoxEngine.rejoin(en, deJoin, Box.stamp)
        assertTrue(Box.candidates(back).newUnits.first() == Box.produce("w02"))
    }
}

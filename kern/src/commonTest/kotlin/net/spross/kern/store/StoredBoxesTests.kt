package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.box.Box
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating

/** The store as a whole: writing one language back, restoring, and the cross-language days. */
class StoredBoxesTests {

    private val uk = StoreFixture.state()
    private val sw = Box.state(listOf(Box.word(1)))
        .let { Box.inject(it, Box.sched("w01", dueMillis = Box.day1, lastReviewMillis = Box.day1, logCount = 3)) }
        .let { it.copy(joinStamp = JoinStamp("de", "sw", "fixture")) }

    @Test
    fun writingALanguageBackLeavesTheOthersAlone() {
        val both = StoredBoxes.EMPTY.with(uk).with(sw)
        assertEquals(setOf("uk", "sw"), both.boxes.keys)
        assertEquals(uk.scheduling, both.boxes.getValue("uk").scheduling)
    }

    @Test
    fun restoringReplacesWhatItCarriesAndKeepsTheRest() {
        val held = StoredBoxes.EMPTY.with(uk).with(sw)
        val imported = StoredBoxes.EMPTY.with(Box.state(listOf(Box.word(2))).copy(joinStamp = uk.joinStamp))

        val restored = held.restoring(imported)
        assertEquals(setOf("uk", "sw"), restored.boxes.keys)
        assertEquals(emptyMap(), restored.boxes.getValue("uk").scheduling) // replaced
        assertEquals(sw.scheduling, restored.boxes.getValue("sw").scheduling) // untouched
    }

    @Test
    fun aRestoreOpensOnTheLanguageAnsweredLast() {
        val word = Box.word(1)
        val later = Box.answered(Box.state(listOf(word)), word.id, Rating.Good, Box.plusSeconds(Box.day1, 86_400))
            .copy(joinStamp = JoinStamp("de", "sw", "fixture"))
        assertEquals("sw", StoredBoxes.EMPTY.with(uk).with(later).lastStudied())
        assertEquals(null, StoredBoxes.EMPTY.lastStudied())
    }

    /** A day earns the streak whichever language it was spent on — but not twice. */
    @Test
    fun theOtherLanguagesDaysLeaveThisOneOut() {
        val both = StoredBoxes.EMPTY.with(uk).with(sw)
        assertEquals(mapOf("2026-07-01" to 3), both.answerDaysExcept("uk", Box.TZ))
        assertEquals(mapOf("2026-07-01" to 3), both.answerDaysExcept("sw", Box.TZ))
    }
}

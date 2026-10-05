package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.store.SaveScope
import net.spross.kern.store.StoredBox

/** BoxEngine.open: a pair's box as a launch or a language switch finds it. */
class OpenBoxTests {
    private val cards = listOf(Box.word(1), Box.word(2))

    @Test
    fun nothingSavedIsBootstrappedAndOwesTheDiskItsDocument() {
        val opened = BoxEngine.open(saved = null, cards = cards, joinStamp = Box.stamp)

        assertEquals(SaveScope.BOX_AND_SNAPSHOTS, opened.save)
        assertEquals(cards.map { it.id }.toSet(), opened.box.cards.keys)
        assertTrue(opened.box.scheduling.isEmpty())
    }

    @Test
    fun aSavedBoxIsJoinedWithItsProgressAndOwesOnlyTheSnapshots() {
        val answered = Box.inject(Box.state(cards), Box.sched("w01", dueMillis = Box.day1, lastReviewMillis = Box.day1))
        val opened = BoxEngine.open(StoredBox.of(answered), cards, Box.stamp)

        assertEquals(SaveScope.SNAPSHOTS, opened.save)
        assertEquals(answered.scheduling, opened.box.scheduling)
        assertEquals(cards.map { it.id }.toSet(), opened.box.cards.keys)
    }
}

package net.spross.kern.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Role

class WatchSnapshotBuilderTests {

    private val fem = Snap.card(
        "wf", 1, emoji = "👩", sourceText = "Kellner", targetText = "Kellnerin",
        synonyms = listOf("Serviererin"), variants = listOf("Bedienung"),
        gender = "die", feminineMarker = true,
    )

    @Test
    fun produceEntryIsFullyPreResolved() {
        val due = Box.plusSeconds(Box.day1, 600)
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched(
                "wf", phase = CardPhase.Learning, stability = 1.5,
                dueMillis = due, lastReviewMillis = Box.day1,
            ),
        )
        val doc = WatchSnapshotBuilder.doc(state, Box.day1)
        val entry = doc.entries.single()

        assertEquals("wf|produce", entry.unitKey)
        assertEquals("Kellner ♀", entry.prompt)
        assertEquals("Kellnerin", entry.answer)
        assertEquals(listOf("Kellnerin", "Serviererin", "Bedienung"), entry.accepted)
        assertEquals("👩", entry.emoji)
        assertEquals("die", entry.articleTint)
        assertEquals(due, entry.due)
        assertEquals(1.5, entry.stability)
    }

    @Test
    fun produceEmojiIsHiddenAfterLearning() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched("wf", phase = CardPhase.Review, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        assertNull(WatchSnapshotBuilder.doc(state, Box.day1).entries.single().emoji)
    }

    @Test
    fun recognizeEntryRevealsDecoratedSourceAndTakesNoTyping() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched(
                "wf", role = Role.Recognize, form = "Kellnerin", phase = CardPhase.Learning,
                stability = 0.5, dueMillis = Box.day1, lastReviewMillis = Box.day1,
            ),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single()

        assertEquals(Box.recognize("wf", "Kellnerin"), entry.unitKey)
        assertEquals("Kellnerin", entry.prompt)
        assertEquals("Kellner ♀", entry.answer)
        assertTrue(entry.accepted.isEmpty())
        assertNull(entry.emoji)
        assertEquals("die", entry.articleTint)
    }

    @Test
    fun entriesDedupeByCardKeepingTheWeakestUnit() {
        val card = Snap.card("wd", 1, synonyms = listOf("syn"))
        var state = Snap.state(listOf(card))
        state = Box.inject(
            state,
            Box.sched("wd", stability = 10.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        state = Box.inject(
            state,
            Box.sched(
                "wd", role = Role.Recognize, form = "syn", phase = CardPhase.Relearning,
                stability = 1.0, dueMillis = Box.day1, lastReviewMillis = Box.day1,
            ),
        )
        val doc = WatchSnapshotBuilder.doc(state, Box.day1)
        assertEquals(listOf(Box.recognize("wd", "syn")), doc.entries.map { it.unitKey })
    }

    @Test
    fun entriesAreCappedAt60ByExposureRank() {
        val cards = (1..65).map { Box.word(it) }
        var state = Snap.state(cards)
        for (n in 1..65) {
            state = Box.inject(
                state,
                Box.sched(
                    cards[n - 1].id, stability = n.toDouble(),
                    dueMillis = Box.day1, lastReviewMillis = Box.day1,
                ),
            )
        }
        val doc = WatchSnapshotBuilder.doc(state, Box.day1)

        assertEquals(60, doc.entries.size)
        assertEquals("w01|produce", doc.entries.first().unitKey)
        assertTrue(doc.entries.none { it.unitKey >= "w61|produce" })
    }

    @Test
    fun suspendedAndNonJoiningUnitsAreExcluded() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched("wf", suspended = true, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        state = Box.inject(state, Box.sched("zz", dueMillis = Box.day1, lastReviewMillis = Box.day1))
        assertTrue(WatchSnapshotBuilder.doc(state, Box.day1).entries.isEmpty())
    }

    @Test
    fun schemaVersionAndGeneratedArePinned() {
        val doc = WatchSnapshotBuilder.doc(Snap.state(emptyList()), Box.day1)
        assertEquals(2, doc.schemaVersion)
        assertEquals(Box.day1, doc.generated)
    }

    @Test
    fun buildEmitsDeterministicJson() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched("wf", dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        assertEquals(
            WatchSnapshotBuilder.build(state, Box.day1),
            WatchSnapshotBuilder.build(state, Box.day1),
        )
        assertTrue(WatchSnapshotBuilder.build(state, Box.day1).startsWith("{\"entries\":"))
    }
}

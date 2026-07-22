package net.spross.kern.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.model.CardPhase

class WatchSnapshotBuilderTests {

    // fnv1a64("wf") is EVEN: next role is recognize at odd log counts, produce at even.
    private val fem = Snap.card(
        "wf", 1, emoji = "👩", sourceText = "Kellner", targetText = "Kellnerin",
        synonyms = listOf("Serviererin"), variants = listOf("Bedienung"),
        gender = "die", feminineMarker = true,
    )

    @Test
    fun entryCarriesBothSidesWithNextRoleResolvedFromLogCount() {
        val due = Box.plusSeconds(Box.day1, 600)
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched(
                "wf", phase = CardPhase.Learning, stability = 1.5,
                dueMillis = due, lastReviewMillis = Box.day1, logCount = 2,
            ),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single()

        assertEquals("wf", entry.cardId)
        assertEquals("Kellner", entry.sourceText) // bare — femMarker carries the badge
        assertEquals(true, entry.femMarker)
        assertEquals("Kellnerin", entry.targetText)
        assertEquals(listOf("Kellnerin", "Serviererin", "Bedienung"), entry.accepted)
        assertEquals("produce", entry.nextRole)
        assertEquals("👩", entry.emoji) // produce + learning → visible
        assertEquals("die", entry.articleTint)
        assertEquals(due, entry.due)
        assertEquals(1.5, entry.stability)
        // Rotation at count 2: index (2/2 + hash%2forms) % 2 = 1 → the synonym.
        assertEquals("Serviererin", entry.promptForm)
    }

    @Test
    fun recognizeEntryHidesEmojiAndPromptsTheRotatedForm() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched(
                "wf", phase = CardPhase.Learning, stability = 0.5,
                dueMillis = Box.day1, lastReviewMillis = Box.day1, logCount = 1,
            ),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single()

        assertEquals("recognize", entry.nextRole)
        assertNull(entry.emoji) // never on recognition measurement: it depicts the answer
        assertEquals("Kellnerin", entry.promptForm) // count 1 → canonical form
        assertTrue(entry.accepted.isNotEmpty()) // reveal shows the full family
    }

    @Test
    fun produceEmojiIsHiddenAfterLearning() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(
            state,
            Box.sched("wf", phase = CardPhase.Review, dueMillis = Box.day1, lastReviewMillis = Box.day1, logCount = 2),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single()
        assertEquals("produce", entry.nextRole)
        assertNull(entry.emoji)
    }

    // Verifier finding: due-first ranking — a currently-due card outranks any
    // non-due one regardless of its exposure tier.
    @Test
    fun dueCardsOutrankNonDueOnesAcrossTiers() {
        var state = Snap.state(listOf(Snap.card("w01", 1), Snap.card("w02", 2)))
        state = Box.inject(
            state,
            Box.sched(
                "w01", phase = CardPhase.Learning, stability = 0.5,
                dueMillis = Box.plusSeconds(Box.day1, 600), lastReviewMillis = Box.day1,
            ),
        )
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Review, stability = 9.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )
        // Learning (tier 2) would beat Review (tier 3), but only w02 is due NOW.
        val ids = WatchSnapshotBuilder.doc(state, Box.day1).entries.map { it.cardId }
        assertEquals(listOf("w02", "w01"), ids)
    }

    @Test
    fun capNeverEvictsDueCardsForNonDueOnes() {
        val cards = (1..65).map { Box.word(it) }
        var state = Snap.state(cards)
        for (n in 1..3) { // non-due learning cards — lower tier, must not crowd due out
            state = Box.inject(
                state,
                Box.sched(
                    cards[n - 1].id, phase = CardPhase.Learning, stability = 0.5,
                    dueMillis = Box.plusSeconds(Box.day1, 600), lastReviewMillis = Box.day1,
                ),
            )
        }
        for (n in 4..65) { // 62 due review cards
            state = Box.inject(
                state,
                Box.sched(cards[n - 1].id, stability = n.toDouble(), dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }
        val doc = WatchSnapshotBuilder.doc(state, Box.day1)

        assertEquals(60, doc.entries.size)
        // All 60 slots go to due cards (weakest stability first); none to w01–w03.
        assertEquals((4..63).map { "w" + it.toString().padStart(2, '0') }, doc.entries.map { it.cardId })
    }

    @Test
    fun suspendedAndNonJoiningCardsAreExcluded() {
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

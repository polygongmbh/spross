package net.spross.kern.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardPhase

class WatchSnapshotBuilderTests {

    // fnv1a64("wf") is EVEN: recognize at count 0 and odd counts ≥ 3; produce at 1 and even.
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
        assertEquals("produce", entry.nextRole)
        assertEquals("👩", entry.emoji) // produce + learning → visible
        assertNull(entry.revealEmoji) // already upfront, so nothing is held back
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
                dueMillis = Box.day1, lastReviewMillis = Box.day1, logCount = 3,
            ),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single()

        assertEquals("recognize", entry.nextRole)
        assertNull(entry.emoji) // never on recognition measurement: it depicts the answer
        assertEquals("👩", entry.revealEmoji) // but the reveal has nothing left to give away
        assertEquals("Serviererin", entry.promptForm) // count 3 rotation → the synonym
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
        assertEquals("👩", entry.revealEmoji)
    }

    // The picture rides on the key that names when it may be seen, so a surface
    // reading `emoji` alone can never show a held-back one early.
    @Test
    fun theTwoEmojiKeysAreNeverBothSet() {
        var state = Snap.state(listOf(fem, Snap.card("plain", 2)))
        for (count in listOf(0, 1, 2, 3, 4)) {
            state = Box.inject(
                state,
                Box.sched(
                    "wf", phase = CardPhase.Learning, stability = 1.0,
                    dueMillis = Box.day1, lastReviewMillis = Box.day1, logCount = count,
                ),
            )
            val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single { it.cardId == "wf" }
            assertTrue(
                entry.emoji == null || entry.revealEmoji == null,
                "log count $count offered the picture twice",
            )
            assertEquals("👩", entry.emoji ?: entry.revealEmoji, "log count $count lost the picture")
        }
    }

    @Test
    fun aCardWithoutAPictureShipsNeitherKey() {
        val state = scheduled(Snap.card("plain", 1), Snap.card("other", 2))
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single { it.cardId == "plain" }
        assertNull(entry.emoji)
        assertNull(entry.revealEmoji)
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

    // The watch shuffles what it is given, so a distractor read on the
    // CANDIDATE's role instead of the entry's would put source meanings and
    // target words in the same question.
    @Test
    fun distractorsSitOnTheEntrysOwnOptionSide() {
        val cards = (1..6).map { Snap.card("w0$it", it, sourceText = "de-$it", targetText = "sw-$it") }
        var state = Snap.state(cards)
        for ((index, card) in cards.withIndex()) {
            state = Box.inject(
                state,
                Box.sched(
                    card.id, stability = (index + 1).toDouble(),
                    dueMillis = Box.day1, lastReviewMillis = Box.day1,
                    // Alternating log counts → a mix of produce and recognize entries.
                    logCount = index,
                ),
            )
        }
        val entries = WatchSnapshotBuilder.doc(state, Box.day1).entries
        assertTrue(entries.any { it.nextRole == "produce" } && entries.any { it.nextRole == "recognize" })

        for (entry in entries) {
            val prefix = if (entry.nextRole == "recognize") "de-" else "sw-"
            assertTrue(
                entry.distractors.isNotEmpty() && entry.distractors.all { it.startsWith(prefix) },
                "${entry.nextRole} entry ${entry.cardId} got ${entry.distractors}",
            )
            assertFalse(entry.distractors.contains(if (prefix == "de-") entry.sourceText else entry.targetText))
        }
    }

    // why: the JSON flavor omits defaults, so an empty shortlist drops out of
    // the wire (the watch reads it as absent) but a real one must be there.
    @Test
    fun distractorsReachTheWire() {
        var state = Snap.state(listOf(Snap.card("w01", 1), Snap.card("w02", 2)))
        for (id in listOf("w01", "w02")) {
            state = Box.inject(state, Box.sched(id, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        }
        assertTrue(WatchSnapshotBuilder.build(state, Box.day1).contains("\"distractors\":["))
    }

    @Test
    fun aLoneCardHasNoDistractorsToOffer() {
        var state = Snap.state(listOf(fem))
        state = Box.inject(state, Box.sched("wf", dueMillis = Box.day1, lastReviewMillis = Box.day1))
        assertTrue(WatchSnapshotBuilder.doc(state, Box.day1).entries.single().distractors.isEmpty())
    }

    // why: ENTRY_CAP is a wire budget, not a statement about which words may stand
    // next to an answer — off-cap cards are the only ones sharing the probe's area
    // here, so seeing them proves the option pool outlives the cap.
    @Test
    fun optionsComeFromEveryLearnedCardNotOnlyTheCappedEntries() {
        val probe = Snap.card("probe", 0, area = "kitchen", targetText = "sw-probe")
        val filler = (1..WatchSnapshotBuilder.ENTRY_CAP).map {
            Snap.card("f$it", it, area = "hall", targetText = "sw-f$it")
        }
        val offCap = (1..3).map { Snap.card("x$it", 100 + it, area = "kitchen", targetText = "sw-x$it") }
        var state = Snap.state(listOf(probe) + filler + offCap)
        state = Box.inject(state, Box.sched("probe", stability = 0.5, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        for (card in filler) {
            state = Box.inject(
                state,
                Box.sched(card.id, stability = 1.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }
        for (card in offCap) {
            state = Box.inject(
                state,
                Box.sched(card.id, stability = 99.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }
        val entries = WatchSnapshotBuilder.doc(state, Box.day1).entries
        assertEquals(WatchSnapshotBuilder.ENTRY_CAP, entries.size)
        assertFalse(entries.any { it.cardId.startsWith("x") })
        val offered = entries.single { it.cardId == "probe" }.distractors
        assertEquals(setOf("sw-x1", "sw-x2", "sw-x3"), offered.take(3).toSet())
    }

    // why: a sentence longer than a tile can hold arrives shrunk past reading, and
    // a four-way pick between sentences is exposure rather than recall — the phone
    // keeps teaching it, the watch simply never sees it.
    @Test
    fun aTextTooLongForATileNeverReachesTheWatch() {
        val long = "a".repeat(WatchSnapshotBuilder.MAX_TEXT_CHARS + 1)
        val state = scheduled(
            Snap.card("fits", 1, targetText = "a".repeat(WatchSnapshotBuilder.MAX_TEXT_CHARS)),
            Snap.card("toolong", 2, targetText = long),
        )
        val entries = WatchSnapshotBuilder.doc(state, Box.day1).entries
        assertEquals(listOf("fits"), entries.map { it.cardId })
    }

    // The cap gates the option POOL too: a distractor that overflows its tile
    // breaks the question exactly as badly as an answer that does.
    @Test
    fun anOverlongCardIsNeverOfferedAsSomebodyElsesTile() {
        val state = scheduled(
            Snap.card("probe", 1, targetText = "t-probe"),
            Snap.card("short", 2, targetText = "t-short"),
            Snap.card("toolong", 3, targetText = "b".repeat(WatchSnapshotBuilder.MAX_TEXT_CHARS + 1)),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single { it.cardId == "probe" }
        assertEquals(listOf("t-short"), entry.distractors)
    }

    @Test
    fun theSourceSideIsMeasuredJustAsTheTargetIs() {
        val state = scheduled(
            Snap.card("ok", 1),
            Snap.card("wordy", 2, sourceText = "c".repeat(WatchSnapshotBuilder.MAX_TEXT_CHARS + 1)),
        )
        val entries = WatchSnapshotBuilder.doc(state, Box.day1).entries
        assertEquals(listOf("ok"), entries.map { it.cardId })
    }

    // `recognitionPromptForm` rotates onto a synonym, so a synonym that cannot be
    // rendered keeps its whole card off the watch rather than breaking one review.
    @Test
    fun anOverlongSynonymKeepsItsCardOff() {
        val state = scheduled(
            Snap.card("plain", 1),
            Snap.card(
                "rotates", 2,
                synonyms = listOf("d".repeat(WatchSnapshotBuilder.MAX_TEXT_CHARS + 1)),
            ),
        )
        val entries = WatchSnapshotBuilder.doc(state, Box.day1).entries
        assertEquals(listOf("plain"), entries.map { it.cardId })
    }

    @Test
    fun aBoundStemIsOfferedWithoutItsDashAndStillTaughtWithIt() {
        val state = scheduled(
            Snap.card("good", 1, kind = CardKind.Adjective, targetText = "-zuri"),
            Snap.card("bad", 2, kind = CardKind.Adjective, targetText = "-baya"),
        )
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single { it.cardId == "good" }
        assertEquals("produce", entry.nextRole)
        assertEquals("zuri", entry.optionForm)
        assertEquals("-zuri", entry.targetText)
        assertEquals(listOf("baya"), entry.distractors)
    }

    @Test
    fun aVerbIsOfferedWithoutTheCitationPrefixTheLanguageDeclares() {
        val state = scheduled(
            Snap.card("cook", 1, kind = CardKind.Verb, targetText = "kupika"),
            Snap.card("eat", 2, kind = CardKind.Verb, targetText = "kula"),
        )
        val doc = WatchSnapshotBuilder.doc(state, Box.day1, mapOf("sw" to listOf("ku", "kw")))
        val entry = doc.entries.single { it.cardId == "cook" }
        assertEquals("pika", entry.optionForm)
        assertEquals(listOf("la"), entry.distractors)
    }

    @Test
    fun aWordOfferedAsItIsTaughtShipsNoOptionForm() {
        val state = scheduled(Snap.card("w01", 1), Snap.card("w02", 2))
        val entry = WatchSnapshotBuilder.doc(state, Box.day1).entries.single { it.cardId == "w01" }
        assertNull(entry.optionForm)
    }

    private fun scheduled(vararg cards: Card) =
        cards.fold(Snap.state(cards.toList())) { state, card ->
            Box.inject(state, Box.sched(card.id, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        }

    @Test
    fun schemaVersionAndGeneratedArePinned() {
        val doc = WatchSnapshotBuilder.doc(Snap.state(emptyList()), Box.day1)
        assertEquals(5, doc.schemaVersion)
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

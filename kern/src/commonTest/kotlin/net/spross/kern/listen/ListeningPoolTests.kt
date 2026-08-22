package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.AudioFixture
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.Fixture
import net.spross.kern.catalog.MapCatalogSource
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/**
 * Which of the learner's words a listening run may say — the pool, its audibility filter, and
 * the whole-sayable-join rule that keeps the mode endless rather than lapping a thin box.
 *
 * Most cases run with both voices installed, which is the ordinary device: then audibility is
 * settled and what is left is the pool rule itself. The Swahili shape — recordings but no
 * synthesizer — gets its own case over the shipped audio fixtures.
 */
class ListeningPoolTests {

    private val catalog: Catalog = Catalog.load(MapCatalogSource(Fixture.files + AudioFixture.files))

    private fun report(
        box: BoxState,
        hasTargetVoice: Boolean,
        hasSourceVoice: Boolean,
    ): ListeningPool.Report =
        ListeningPool.report(catalog, box, "de", "sw", hasTargetVoice, hasSourceVoice)

    private fun spoken(box: BoxState): ListeningPool.Report = report(box, true, true)

    private fun ids(report: ListeningPool.Report): List<String> = report.candidates.map { it.card.id }

    private fun id(n: Int): String = "w" + n.toString().padStart(2, '0')

    /** A box whose first [scheduled] words carry a schedule, out of [total] joined. */
    private fun box(total: Int, scheduled: Int): BoxState {
        var state = Box.state((1..total).map { Box.word(it) })
        for (n in 1..scheduled) {
            state = Box.inject(
                state,
                Box.sched("w" + n.toString().padStart(2, '0'), dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }
        return state
    }

    /**
     * RULE: a suspended card is in the pool.
     * WHY: the leech rule auto-suspends at two lapses, so the words that stick worst are
     * exactly the ones `Inventory.active` drops — and those are the words the hour is for.
     * Suspension takes a word out of the box's queue; it never said stop meeting the word.
     */
    @Test
    fun aSuspendedLeechIsStillInThePool() {
        var state = box(total = 20, scheduled = 20)
        state = Box.inject(
            state,
            Box.sched("w03", dueMillis = Box.day1, lastReviewMillis = Box.day1, lapses = 2, suspended = true),
        )

        val leech = spoken(state).candidates.single { it.card.id == "w03" }

        assertTrue(leech.suspended)
        // The pool reads the schedule's stability, the one figure the draw ladders on.
        assertEquals(10.0, leech.stability)
    }

    /**
     * RULE: a candidate needs audio on the target side AND the source side.
     * WHY: a turn plays the word, then its meaning, then the word again. One of those beats
     * arriving as silence teaches nothing, so half-sayable is not sayable.
     */
    @Test
    fun bothHalvesOfATurnMustBeAudible() {
        // The shipped audio fixture: de has "Tür"/"kochen", sw has "mlango". Nothing else can
        // be said on a device with neither voice.
        val cards = listOf(
            recorded(1, source = "Tür", target = "mlango"),
            recorded(2, source = "kochen", target = "mlango"),
            recorded(3, source = "unaufsagbar", target = "mlango"),
            recorded(4, source = "Tür", target = "haisemeki"),
        )
        var state = Box.state(cards)
        for (card in cards) {
            state = Box.inject(state, Box.sched(card.id, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        }

        // By SET: which words survive is this rule's business, where each lands is the deal's.
        assertEquals(setOf("r1", "r2"), ids(report(state, false, hasSourceVoice = false)).toSet())
        // A voice on either side alone is not enough — the other beat is still silent.
        assertEquals(setOf("r1", "r2", "r3"), ids(report(state, false, hasSourceVoice = true)).toSet())
        assertEquals(setOf("r1", "r2", "r4"), ids(report(state, true, hasSourceVoice = false)).toSet())
    }

    /**
     * RULE: the pool is the whole sayable join — every scheduled word AND every unseen one,
     * thin box or settled box alike.
     * WHY: this is the endless mode. A learner a few words in hears a stream of new words
     * rather than lapping the handful they hold, and a learner with a full vocabulary hears
     * their own words in it — the deal does the steering.
     */
    @Test
    fun thePoolIsTheWholeSayableJoin() {
        val thin = spoken(box(total = 30, scheduled = 3))
        assertEquals(30, thin.candidates.size)
        assertEquals(3, thin.candidates.count { it.scheduled })
        assertEquals(27, thin.candidates.count { !it.scheduled })

        val settled = spoken(box(total = 40, scheduled = 17))
        assertEquals(40, settled.candidates.size)
        assertEquals(17, settled.candidates.count { it.scheduled })
        assertEquals(23, settled.candidates.count { !it.scheduled })

        assertEquals((1..30).map(::id).toSet(), ids(thin).toSet())
    }

    /**
     * RULE: a locked phrase is never in the pool.
     * WHY: the pool reuses `Growth.isIntroducible`, so a phrase waits for its components
     * here exactly as it waits everywhere else — hearing it before they have landed is the
     * wall of unparsed sound the unlock gate exists to prevent.
     */
    @Test
    fun aLockedPhraseIsNotInThePool() {
        val cards = (1..3).map { Box.word(it) } + Box.phrase("p01", components = listOf("w01", "w02"))
        var state = Box.state(cards)
        state = Box.inject(state, Box.sched("w01", dueMillis = Box.day1, lastReviewMillis = Box.day1))

        assertFalse("p01" in ids(spoken(state)))
    }

    /**
     * RULE: `available` is the pool being non-empty.
     * WHY: it gates the entry card, and the pool is already the whole sayable join — so
     * whatever survives IS everything there is to hear, and a short pool simply laps, which
     * is what a playlist does anyway.
     */
    @Test
    fun availabilityIsThePoolHavingAnythingToSay() {
        assertTrue(spoken(box(total = 4, scheduled = 1)).available)
        assertFalse(spoken(Box.state(emptyList())).available)
    }

    /**
     * RULE: an empty box plays the catalog from `seedIndex` 0 upward, in order.
     * WHY: the beginner case, and the point of dealing an order at all. Every candidate of an
     * untouched box is unscheduled, so they are ONE lane — and a lane of new words plays in
     * strict catalog order, which is the curriculum the seed index already encodes. A lottery
     * here meets *Fernseher* before *ich*.
     */
    @Test
    fun anEmptyBoxPlaysTheCatalogFromItsFirstWord() {
        assertEquals((1..30).map(::id), ids(spoken(box(total = 30, scheduled = 0))))
    }

    /**
     * RULE: packed words are heard inside the opening turns, and ahead of the other new ones.
     * WHY: packing is the learner saying *these words next*; the study round honors it and the
     * widget honors it, so the mode with the least friction cannot be the one that ignores it.
     * Their own lane runs a rung faster, which puts them in the first handful of turns without
     * making them a block.
     */
    @Test
    fun packedWordsAreHeardInTheOpeningTurnsAheadOfTheOtherNewOnes() {
        val packed = listOf("w50", "w60", "w70")
        val state = box(total = 100, scheduled = 0).copy(enqueued = packed)

        val played = ids(spoken(state))

        assertEquals("w50", played.first(), "a packed word opens the run")
        assertTrue(played.take(6).containsAll(packed), "packed words late: ${played.take(6)}")
        // The packed lane runs a rung ahead: each of its words lands before the plain new
        // word of the same rank, so all three are out while the catalog is on its third.
        for ((rank, word) in packed.withIndex()) {
            assertTrue(played.indexOf(word) < played.indexOf(id(rank + 1)), "$word is late")
        }
    }

    /**
     * RULE: every lane is reached inside the opening stretch of one lap.
     * WHY: the regression the deal exists to prevent. A plain sort by priority would empty
     * rung 6, then rung 5, then spend the whole run inside a rung-4 block of every unseen word
     * in the catalog — the settled and the shaky-suspended words would never be reached in a
     * session at all. Dealing each lane evenly means every one of them reaches the ear.
     */
    @Test
    fun everyLaneIsReachedInsideTheOpeningStretch() {
        var state = box(total = 200, scheduled = 0)
        // Five words on each rung of the stability ladder, plus a packed lane and 170 unseen.
        for ((rung, stability) in listOf(0.0, 3.0, 5.0, 7.0, 9.0, 20.0).withIndex()) {
            for (n in 1..5) {
                state = Box.inject(
                    state,
                    Box.sched(
                        id(rung * 5 + n), stability = stability,
                        dueMillis = Box.day1, lastReviewMillis = Box.day1,
                    ),
                )
            }
        }
        state = state.copy(enqueued = listOf("w40", "w41", "w42"))

        val candidates = spoken(state).candidates
        val lane = { c: ListeningCandidate -> Triple(c.scheduled, c.queued, listeningPriority(c)) }

        assertEquals(8, candidates.map(lane).toSet().size, "the fixture must cover eight lanes")
        assertEquals(
            candidates.map(lane).toSet(),
            candidates.take(20).map(lane).toSet(),
            "a lane went unheard in the opening twenty turns",
        )
    }

    /**
     * RULE: inside a lane, new words keep catalog order and scheduled ones do not.
     * WHY: the catalog is a curriculum for words never met, so new ones follow it. For words
     * already held it is a liability — seed neighbors are often related concepts, and hearing
     * them in the same sequence every run teaches a word from its neighbor rather than on its
     * own. Hashing the id de-correlates them exactly as `Inventory.dueOrder` does.
     */
    @Test
    fun scheduledWordsBreakCatalogOrderWhileNewOnesKeepIt() {
        var state = box(total = 60, scheduled = 0)
        for (n in 1..20) {
            state = Box.inject(
                state,
                Box.sched(id(n), stability = 0.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }

        val played = ids(spoken(state))
        val held = played.filter { it in (1..20).map(::id) }
        val fresh = played.filter { it in (21..60).map(::id) }

        assertEquals((21..60).map(::id), fresh)
        assertEquals((1..20).map(::id).toSet(), held.toSet())
        assertTrue(held != held.sorted(), "the scheduled lane is still in catalog order")
    }

    /** A card whose two forms are ones the shipped audio fixture really has recordings for. */
    private fun recorded(n: Int, source: String, target: String): Card = Card(
        id = "r$n",
        kind = CardKind.Noun,
        area = "area1",
        emoji = null,
        seedIndex = n,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = source),
        target = Realization(lang = "sw", text = target),
        promptFeminineMarker = false,
    )
}

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
        seed: Long = Box.day1,
    ): ListeningPool.Report =
        ListeningPool.report(catalog, box, "de", "sw", hasTargetVoice, hasSourceVoice, seed)

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
     * WHY: a suspended word is exactly the kind `Inventory.active` drops — and those are
     * the words the hour is for.
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
     * RULE: an empty box's basics — the earliest `LISTENING_BASICS_WORDS` concepts — lead the
     * whole lap, ahead of everything past them, whichever order they land in among themselves.
     * WHY: the beginner case. A first session should still open on greetings and thanks rather
     * than a word forty shelves in, but the basics do not need a fixed sequence among
     * themselves to do that — only `newWordOrder`'s bucket split, not `catalogOrder`, decides
     * this now.
     */
    @Test
    fun anEmptyBoxLeadsWithItsBasicsButNotInAFixedOrder() {
        val played = ids(spoken(box(total = 80, scheduled = 0)))
        // seedIndex is 1-based here, and the split is seedIndex < LISTENING_BASICS_WORDS (50).
        val basics = (1..49).map(::id)
        val rest = (50..80).map(::id)

        assertEquals(basics.toSet(), played.take(basics.size).toSet(), "the basics fill the opening stretch")
        assertTrue(played.take(basics.size) != basics, "a lottery may meet Fernseher before ich")
        assertEquals(rest.toSet(), played.drop(basics.size).toSet(), "nothing outside the basics is lost")
    }

    /**
     * RULE: packed words are heard inside the opening turns, and ahead of the other new ones.
     * WHY: packing is the learner saying *these words next*; the study round honors it and the
     * widget honors it, so the mode with the least friction cannot be the one that ignores it.
     * Their own lane runs a Sprosse faster, which puts them in the first handful of turns without
     * making them a block.
     */
    @Test
    fun packedWordsAreHeardInTheOpeningTurnsAheadOfTheOtherNewOnes() {
        val packed = listOf("w50", "w60", "w70")
        val state = box(total = 100, scheduled = 0).copy(enqueued = packed)

        val played = ids(spoken(state))

        assertEquals("w50", played.first(), "a packed word opens the run")
        assertTrue(played.take(6).containsAll(packed), "packed words late: ${played.take(6)}")
        // The packed lane runs a Sprosse ahead of the plain new one, so its own first word
        // beats the plain lane's regardless of which plain word that turns out to be.
        assertTrue(
            played.indexOf("w50") < played.indexOf((played - packed.toSet()).first()),
            "the packed lane's opener is late",
        )
    }

    /**
     * RULE: every lane is reached inside the opening stretch of one lap.
     * WHY: the regression the deal exists to prevent. A plain sort by priority would empty
     * Sprosse 6, then Sprosse 5, then spend the whole run inside a Sprosse-4 block of every unseen word
     * in the catalog — the settled and the shaky-suspended words would never be reached in a
     * session at all. Dealing each lane evenly means every one of them reaches the ear.
     */
    @Test
    fun everyLaneIsReachedInsideTheOpeningStretch() {
        var state = box(total = 200, scheduled = 0)
        // Five words on each Sprosse of the stability ladder, plus a packed lane and 170 unseen.
        for ((sprosse, stability) in listOf(0.0, 3.0, 7.0, 15.0, 25.0, 35.0).withIndex()) {
            for (n in 1..5) {
                state = Box.inject(
                    state,
                    Box.sched(
                        id(sprosse * 5 + n), stability = stability,
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
     * RULE: a box shaped like a well-used one — a few shaky words, a modest middle band, and a
     * large block of matured (`MATURED_STABILITY`+) ones — still hears its settling band (10 to
     * 30 days) well before the matured block, on average.
     * WHY: the regression this ladder exists to fix. A flat per-day step floored a word at just
     * ten days, so a settling word and a matured one shared the same slowest-dealt lane and a
     * box with far more of the latter than shaky words spent almost the whole lap on it. The
     * wider ceilings give the settling range its own, faster lane instead.
     */
    @Test
    fun aMatureBoxStillLeadsWithItsSettlingBandOverTheMaturedOne() {
        var state = box(total = 130, scheduled = 0)
        val settling = (1..20).map(::id)
        val matured = (21..120).map(::id)
        for (wordId in settling) {
            state = Box.inject(
                state,
                Box.sched(wordId, stability = 18.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }
        for (wordId in matured) {
            state = Box.inject(
                state,
                Box.sched(wordId, stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }

        val played = ids(spoken(state))
        val settlingMeanIndex = settling.map(played::indexOf).average()
        val maturedMeanIndex = matured.map(played::indexOf).average()

        assertTrue(
            settlingMeanIndex < maturedMeanIndex,
            "settling band ($settlingMeanIndex) did not lead the matured one ($maturedMeanIndex)",
        )
    }

    /**
     * RULE: inside a lane, only the PACKED queue keeps catalog order — scheduled words and
     * plain new ones both break it, though the new lane still leads with its basics as a group.
     * WHY: the catalog is a curriculum for words never met, but a fixed sequence inside that
     * curriculum is what pinned a single word to the front of every sweep until growth reached
     * it. Packing is the one case a fixed order is the learner's own ask, so it alone keeps
     * `catalogOrder`. Hashing the rest de-correlates them exactly as `Inventory.dueOrder` does.
     */
    @Test
    fun onlyThePackedQueueKeepsCatalogOrder() {
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
        val freshBasics = fresh.filter { it in (21..49).map(::id) }
        val freshRest = fresh.filter { it in (50..60).map(::id) }

        assertEquals((1..20).map(::id).toSet(), held.toSet())
        assertEquals((21..60).map(::id).toSet(), fresh.toSet())
        assertTrue(held != held.sorted(), "the scheduled lane is still in catalog order")
        assertTrue(fresh != (21..60).map(::id), "the new lane is still in catalog order")
        // The basics-vs-rest split survives the shuffle even though neither half is sorted.
        assertEquals(fresh.take(freshBasics.size).toSet(), freshBasics.toSet(), "basics did not lead the rest")
    }

    /**
     * RULE: the same box dealt with the same seed repeats; dealt with a different one, its
     * scheduled lane reshuffles.
     * WHY: the apps re-sweep the pool on every foreground and hand in the current instant, so
     * a learner who listens more than once a day must not hear the identical sequence every
     * time — but a single report is still a pure function of the box and the seed it names,
     * never a live clock read.
     */
    @Test
    fun theScheduledLaneReshufflesBetweenTwoDealingsOfTheSameBox() {
        var state = box(total = 20, scheduled = 0)
        for (n in 1..20) {
            state = Box.inject(
                state,
                Box.sched(id(n), stability = 0.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
            )
        }

        val first = ids(report(state, true, true, seed = Box.day1))
        val again = ids(report(state, true, true, seed = Box.day1))
        val later = ids(report(state, true, true, seed = Box.plusDays(Box.day1, 1.0)))

        assertEquals(first, again, "the same seed must deal the same order")
        assertTrue(first != later, "a different seed never reshuffled the order")
    }

    /**
     * RULE: the same box dealt with the same seed repeats; dealt with a different one, the new
     * lane reshuffles too — including inside its basics.
     * WHY: this is the fix `newWordOrder` exists for. An unlearned box used to lead every
     * single sweep with the exact same earliest unseen word until growth reached it; the basics
     * still lead as a group, but which of them leads changes from one dealing to the next.
     */
    @Test
    fun theNewLaneReshufflesBetweenTwoDealingsOfTheSameBox() {
        val state = box(total = 80, scheduled = 0)

        val first = ids(report(state, true, true, seed = Box.day1))
        val again = ids(report(state, true, true, seed = Box.day1))
        val later = ids(report(state, true, true, seed = Box.plusDays(Box.day1, 1.0)))

        assertEquals(first, again, "the same seed must deal the same order")
        assertTrue(first != later, "a different seed never reshuffled the order")
        assertTrue(first.first() != later.first(), "the same word led every dealing")
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

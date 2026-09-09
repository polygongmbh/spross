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
        // The pool reads the box's own bar, the one fact the ladder stands on.
        assertTrue(leech.growing)
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
     * RULE: the pool is the sayable join short of the grown words — every scheduled word still
     * growing AND every unseen one, thin box or settled box alike.
     * WHY: this is the endless mode. A learner a few words in hears a stream of new words
     * rather than lapping the handful they hold, and a learner with a full vocabulary hears
     * their own words in it — the deal does the steering.
     */
    @Test
    fun thePoolIsTheSayableJoinShortOfTheGrownWords() {
        val thin = spoken(box(total = 30, scheduled = 3)).candidates.distinct()
        assertEquals(30, thin.size)
        assertEquals(3, thin.count { it.scheduled })
        assertEquals(27, thin.count { !it.scheduled })

        val settled = spoken(box(total = 40, scheduled = 17)).candidates.distinct()
        assertEquals(40, settled.size)
        assertEquals(17, settled.count { it.scheduled })
        assertEquals(23, settled.count { !it.scheduled })

        assertEquals((1..30).map(::id).toSet(), thin.map { it.card.id }.toSet())
    }

    /**
     * RULE: a fully grown word is not in the pool.
     * WHY: it is what the box already calls done (`Statistics.isConsolidated`), and an hour of
     * listening is for what is not. Left in, a well-used box — where the grown words outnumber
     * everything else — would spend its evening on the words it trusts most.
     */
    @Test
    fun aGrownWordIsNotInThePool() {
        var state = box(total = 20, scheduled = 0)
        state = Box.inject(state, Box.sched("w01", stability = 18.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        state = Box.inject(state, Box.sched("w02", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        val played = ids(spoken(state))

        assertTrue("w01" in played, "a growing word is still heard")
        assertFalse("w02" in played, "a grown word was dealt")
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

        assertTrue(played.take(6).containsAll(packed), "packed words late: ${played.take(6)}")
        // The packed lane runs a Sprosse ahead of the plain new one, so its own opener
        // beats the plain lane's regardless of which plain word that turns out to be.
        assertTrue(
            played.indexOf("w70") < played.indexOf((played - packed.toSet()).first()),
            "the packed lane's opener is late",
        )
    }

    /**
     * RULE: inside the packed lane, the most recently packed word leads — not pack order,
     * and not catalog order.
     * WHY: growth already introduces packed words most-recent-first (`Growth.enqueuedEligible`)
     * — what a learner just packed on top of an older queue is their freshest ask, and the two
     * surfaces would disagree if listening kept reading the queue by catalog position instead.
     */
    @Test
    fun thePackedLanePlaysMostRecentlyPackedFirst() {
        val packed = listOf("w50", "w60", "w70")
        val state = box(total = 100, scheduled = 0).copy(enqueued = packed)

        val played = ids(spoken(state)).filter { it in packed }

        assertEquals(listOf("w70", "w60", "w50"), played)
    }

    /** A box of [shaky] words short of the growing bar and [growing] ones past it, nothing unseen. */
    private fun ladderBox(shaky: Int, growing: Int): BoxState {
        var state = box(total = shaky + growing, scheduled = 0)
        for (n in 1..shaky + growing) {
            state = Box.inject(
                state,
                Box.sched(
                    id(n), stability = if (n <= shaky) 0.0 else 10.0,
                    dueMillis = Box.day1, lastReviewMillis = Box.day1,
                ),
            )
        }
        return state
    }

    /** The turns between one hearing of [wordId] and the next, over the whole deal. */
    private fun returnGaps(played: List<String>, wordId: String): List<Int> =
        played.withIndex().filter { it.value == wordId }.map { it.index }.zipWithNext { a, b -> b - a }

    /**
     * RULE: every shaky word plays before any growing word does.
     * WHY: the hour is for what is slipping. A learner with plenty of words short of the bar
     * used to hear a word the box already trusted within the first handful of turns, because
     * every lane was dealt a fixed slice of the run whatever it held.
     */
    @Test
    fun theShakyWordsPlayOutBeforeAGrowingOneIsHeard() {
        val played = ids(spoken(ladderBox(shaky = 10, growing = 10)))

        assertEquals((1..10).map(::id).toSet(), played.take(10).toSet(), "a growing word led a shaky one")
    }

    /**
     * RULE: once the shaky words are out, they come back among the growing ones — and a
     * smaller shaky lane brings each of its words back sooner than a larger one.
     * WHY: a long session should keep reinforcing what is slipping rather than drift into the
     * words the box trusts; and the fewer words are slipping, the more each of them deserves.
     */
    @Test
    fun theShakyWordsComeBackAmongTheGrowingOnesSoonerWhenFewer() {
        val few = ids(spoken(ladderBox(shaky = 5, growing = 30)))
        val many = ids(spoken(ladderBox(shaky = 60, growing = 30)))

        val afterOpening = few.drop(5)
        assertTrue(afterOpening.any { it in (1..5).map(::id) }, "no shaky word came back")
        assertTrue(afterOpening.any { it in (6..35).map(::id) }, "no growing word was reached")

        val fewGap = (1..5).map(::id).flatMap { returnGaps(few, it) }.average()
        val manyGap = (1..60).map(::id).flatMap { returnGaps(many, it) }.average()
        assertTrue(fewGap < manyGap, "five shaky words ($fewGap) did not return sooner than sixty ($manyGap)")
    }

    /**
     * RULE: no word is said again within `LISTENING_RETURN_FLOOR_TURNS` turns.
     * WHY: a lane of one or two words would otherwise be every other turn all evening; the
     * turns it cannot fill go to the next lane instead, which is the new words here.
     */
    @Test
    fun noWordComesBackInsideTheFloor() {
        var state = box(total = 100, scheduled = 0)
        state = Box.inject(state, Box.sched("w01", stability = 0.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        state = Box.inject(state, Box.sched("w02", stability = 0.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        val played = ids(spoken(state))

        for (wordId in listOf("w01", "w02")) {
            val gaps = returnGaps(played, wordId)
            assertTrue(gaps.size > 1, "$wordId never came back")
            assertTrue(gaps.all { it >= LISTENING_RETURN_FLOOR_TURNS }, "$wordId echoed: $gaps")
        }
    }

    /**
     * RULE: unseen words take `LISTENING_NEW_SHARE` of the turns from the first one on.
     * WHY: audio is the cheapest exposure a new word can get, so breadth rides alongside the
     * shaky words rather than waiting for them — but as a slice, not a Sprosse, so three
     * hundred unseen words do not crowd out the twenty that are slipping.
     */
    @Test
    fun unseenWordsTakeTheirShareFromTheFirstTurn() {
        var state = box(total = 130, scheduled = 0)
        for (n in 1..30) {
            state = Box.inject(state, Box.sched(id(n), stability = 0.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        }

        val opening = spoken(state).candidates.take(50)
        val unseen = opening.count { !it.scheduled }

        assertTrue(unseen in 18..22, "$unseen of the first 50 turns were unseen words")
        assertTrue(opening.take(3).any { !it.scheduled }, "the first new word waited")
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

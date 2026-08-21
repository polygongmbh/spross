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
 * the top-up that keeps a thin box from looping four words.
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
        assertEquals(2, leech.lapses)
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

        assertEquals(listOf("r1", "r2"), ids(report(state, hasTargetVoice = false, hasSourceVoice = false)))
        // A voice on either side alone is not enough — the other beat is still silent.
        assertEquals(listOf("r1", "r2", "r3"), ids(report(state, hasTargetVoice = false, hasSourceVoice = true)))
        assertEquals(listOf("r1", "r2", "r4"), ids(report(state, hasTargetVoice = true, hasSourceVoice = false)))
    }

    /**
     * RULE: unseen words top a thin pool up to [LISTENING_POOL_FLOOR], in seed order.
     * WHY: the same "fill a short round out" move the session composer makes. A learner three
     * words in would otherwise hear those three words for the whole walk, and listening is
     * where breadth of exposure is cheapest.
     */
    @Test
    fun unseenWordsTopUpAThinPool() {
        val report = spoken(box(total = 30, scheduled = 3))

        assertEquals(LISTENING_POOL_FLOOR, report.candidates.size)
        assertEquals(3, report.candidates.count { it.scheduled })
        // Seed order, and the top-up picks up straight after the scheduled words.
        assertEquals((1..LISTENING_POOL_FLOOR).map { "w" + it.toString().padStart(2, '0') }, ids(report))
    }

    /**
     * RULE: a pool already at the floor takes no unseen words.
     * WHY: the top-up answers a box with nothing to play, not an appetite for new words —
     * listening is exposure to what the learner is working on, and an endless drip of unseen
     * vocabulary would drown it.
     */
    @Test
    fun aFullPoolTakesNoUnseenWords() {
        val report = spoken(box(total = 40, scheduled = LISTENING_POOL_FLOOR + 5))

        assertEquals(LISTENING_POOL_FLOOR + 5, report.candidates.size)
        assertTrue(report.candidates.all { it.scheduled })
    }

    /**
     * RULE: a locked phrase never tops the pool up.
     * WHY: the top-up reuses `Growth.isIntroducible`, so a phrase waits for its components
     * here exactly as it waits everywhere else — hearing it before they have landed is the
     * wall of unparsed sound the unlock gate exists to prevent.
     */
    @Test
    fun aLockedPhraseDoesNotTopThePoolUp() {
        val cards = (1..3).map { Box.word(it) } + Box.phrase("p01", components = listOf("w01", "w02"))
        var state = Box.state(cards)
        state = Box.inject(state, Box.sched("w01", dueMillis = Box.day1, lastReviewMillis = Box.day1))

        assertFalse("p01" in ids(spoken(state)))
    }

    /**
     * RULE: `available` is the pool being non-empty.
     * WHY: it gates the entry card, and the top-up has already grown the pool as far as the
     * content allows — so whatever survives IS everything there is to hear, and a short pool
     * simply laps, which is what a playlist does anyway.
     */
    @Test
    fun availabilityIsThePoolHavingAnythingToSay() {
        assertTrue(spoken(box(total = 4, scheduled = 1)).available)
        assertFalse(spoken(Box.state(emptyList())).available)
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

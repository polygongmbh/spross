package net.spross.kern.listen

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

/** The playlist itself: what it draws next, what it replays, and what it never touches. */
class ListeningRunTests {

    private fun candidates(n: Int): List<ListeningCandidate> = (1..n).map {
        ListeningCandidate(Box.word(it), difficulty = 5.0, lapses = 0, suspended = false, scheduled = true)
    }

    private fun run(pool: List<ListeningCandidate>, rng: Random): ListeningRunState =
        ListeningRun.reduce(ListeningRun.idle(pool), ListeningIntent.Start, rng).state

    /** The ids of [turns] consecutive turns, the opening one first. */
    private fun heard(pool: List<ListeningCandidate>, turns: Int, seed: Int): List<String> {
        val rng = Random(seed)
        var state = run(pool, rng)
        val ids = mutableListOf<String>()
        repeat(turns) {
            ids += assertNotNull(state.turn).cardId
            state = ListeningRun.reduce(state, ListeningIntent.Advance, rng).state
        }
        return ids
    }

    /**
     * RULE: a word just heard stays out of the draw for [RECENCY_WINDOW] turns.
     * WHY: weighting says what is worth hearing, the ring says what is worth hearing AGAIN
     * YET. Without it a leech-heavy pool says the same four words for an hour, which is the
     * one way a playlist can be worse than silence.
     */
    @Test
    fun theRecencyRingNeverRepeatsInsideItsWindow() {
        val ids = heard(candidates(20), turns = 200, seed = 7)

        for (index in ids.indices) {
            val window = ids.subList(maxOf(0, index - RECENCY_WINDOW), index)
            assertFalse(ids[index] in window, "${ids[index]} repeated inside the window at $index")
        }
    }

    /**
     * RULE: a pool smaller than the window laps instead of running dry.
     * WHY: the ring is a preference, never a gate — a learner with three sayable words still
     * gets a run, it just rotates. What it must never do is hand back two of the same word in
     * a row, so the hold shrinks to `pool − 1` rather than emptying the draw.
     */
    @Test
    fun aPoolSmallerThanTheWindowLapsCleanly() {
        val ids = heard(candidates(3), turns = 60, seed = 3)

        assertEquals(setOf("w01", "w02", "w03"), ids.toSet())
        for (index in 1 until ids.size) {
            assertFalse(ids[index] == ids[index - 1], "the same word twice in a row at $index")
        }
    }

    /**
     * RULE: a one-word pool repeats that word.
     * WHY: the floor case of the same rule — there is nothing else to say, and falling silent
     * would be worse than saying it again.
     */
    @Test
    fun aSingleWordPoolKeepsSayingIt() {
        assertEquals(List(5) { "w01" }, heard(candidates(1), turns = 5, seed = 1))
    }

    /**
     * RULE: a hundred turns leave the box exactly as they found it.
     * WHY: nothing is answered, so nothing is booked — and it is structural rather than
     * promised: the run state carries no box at all, so there is no path from a turn to a
     * schedule. Hearing an unseen word does not introduce it either; introduction is the
     * first answer.
     */
    @Test
    fun theRunBooksNothing() {
        val before = box(total = 20, scheduled = 8)
        val catalog: Catalog = Catalog.load(MapCatalogSource(Fixture.files + AudioFixture.files))
        val pool = ListeningPool.report(catalog, before, "de", "sw", true, true)
        val rng = Random(11)

        var state = run(pool.candidates, rng)
        repeat(100) { state = ListeningRun.reduce(state, ListeningIntent.Advance, rng).state }

        assertEquals(101, state.played)
        assertEquals(before, box(total = 20, scheduled = 8))
        assertEquals(
            pool.candidates.map { it.card.id },
            ListeningPool.report(catalog, before, "de", "sw", true, true).candidates.map { it.card.id },
        )
    }

    /**
     * RULE: a turn carries both forms, the target-side article, and all three beats.
     * WHY: a run must be playable from the turn alone — anything an app had to derive is a
     * decision the two platforms would make differently, and this one is inaudible when it
     * goes wrong.
     */
    @Test
    fun aTurnCarriesEveryBeatAndBothForms() {
        val bread = ListeningCandidate(
            card = gendered("bread", source = "das Brot", target = "mkate", article = "das"),
            difficulty = 5.0, lapses = 0, suspended = false, scheduled = true,
        )
        val turn = assertNotNull(run(listOf(bread), Random(1)).turn)

        assertEquals("bread", turn.cardId)
        assertEquals("mkate", turn.targetForm)
        assertEquals("das Brot", turn.sourceForm)
        assertEquals("das", turn.spokenArticle)
        assertEquals(RECALL_GAP_HELD_MS, turn.recallGapMs)
        // The echo and the breath between turns reuse the two recall gaps rather than
        // minting beats of their own — a turn is one varying pause plus the two it follows.
        assertEquals(ECHO_GAP_MS, turn.echoGapMs)
        assertEquals(TURN_GAP_MS, turn.turnGapMs)
        assertEquals(RECALL_GAP_FRESH_MS, ECHO_GAP_MS)
        assertEquals(RECALL_GAP_HELD_MS, TURN_GAP_MS)
    }

    /**
     * RULE: a card with no authored gender speaks no article.
     * WHY: most target languages have none, and an article invented for them would be taught
     * as fact.
     */
    @Test
    fun aCardWithoutAGenderSpeaksNoArticle() {
        assertNull(assertNotNull(run(candidates(1), Random(1)).turn).spokenArticle)
    }

    /**
     * RULE: Repeat replays the current turn and draws nothing.
     * WHY: "again" is asked for when the word did not land — a fresh draw would be the run
     * answering a different question. The replay is an EFFECT because the state does not
     * move, and a driver watching for a changed turn would go deaf on exactly this button.
     */
    @Test
    fun repeatReplaysTheSameTurnWithoutDrawing() {
        val rng = Random(5)
        val started = run(candidates(20), rng)

        val again = ListeningRun.reduce(started, ListeningIntent.Repeat, rng)

        assertEquals(started.turn, again.state.turn)
        assertEquals(started.played, again.state.played)
        assertEquals(listOf(ListeningEffect.Play(assertNotNull(started.turn))), again.effects)
    }

    /**
     * RULE: pausing stops the sound; resuming replays the current turn from its first beat.
     * WHY: the run holds beats, not a playhead, so there is no middle of a word to resume
     * into — and a word cut in half is worth hearing whole.
     */
    @Test
    fun pausingStopsTheSoundAndResumingReplaysTheTurn() {
        val rng = Random(5)
        val started = run(candidates(20), rng)

        val paused = ListeningRun.reduce(started, ListeningIntent.TogglePause, rng)
        assertTrue(paused.state.paused)
        assertEquals(listOf(ListeningEffect.Stop), paused.effects)

        val resumed = ListeningRun.reduce(paused.state, ListeningIntent.TogglePause, rng)
        assertFalse(resumed.state.paused)
        assertEquals(listOf(ListeningEffect.Play(assertNotNull(started.turn))), resumed.effects)
        assertEquals(started.played, resumed.state.played)
    }

    /**
     * RULE: Advance does nothing while paused.
     * WHY: the beat chain is stopped, but the audio the pause interrupted may still report
     * itself finished — and that stray completion must not walk the playlist on behind a
     * learner who asked for silence.
     */
    @Test
    fun advanceIsANoOpWhilePaused() {
        val rng = Random(5)
        val paused = ListeningRun.reduce(run(candidates(20), rng), ListeningIntent.TogglePause, rng).state

        val stayed = ListeningRun.reduce(paused, ListeningIntent.Advance, rng)

        assertEquals(paused, stayed.state)
        assertEquals(emptyList(), stayed.effects)
    }

    /**
     * RULE: Skip draws the next word and lifts a pause.
     * WHY: reaching for the next word is a request to hear it; a skip that left the run silent
     * would read as a broken button.
     */
    @Test
    fun skipDrawsTheNextWordAndLiftsAPause() {
        val rng = Random(5)
        val paused = ListeningRun.reduce(run(candidates(20), rng), ListeningIntent.TogglePause, rng).state

        val skipped = ListeningRun.reduce(paused, ListeningIntent.Skip, rng)

        assertFalse(skipped.state.paused)
        assertEquals(paused.played + 1, skipped.state.played)
        assertTrue(skipped.effects.single() is ListeningEffect.Play)
    }

    /**
     * RULE: closing stops the sound and ends the run, keeping the turn.
     * WHY: a sound-only surface that leaves audio playing after it is dismissed has taken the
     * device over; the turn stays because the screen may still be animating away with it.
     */
    @Test
    fun closingStopsTheSound() {
        val rng = Random(5)
        val started = run(candidates(20), rng)

        val closed = ListeningRun.reduce(started, ListeningIntent.Close, rng)

        assertFalse(closed.state.active)
        assertEquals(started.turn, closed.state.turn)
        assertEquals(listOf(ListeningEffect.Stop), closed.effects)
    }

    /**
     * RULE: an empty pool opens on silence rather than failing.
     * WHY: the pool is rebuilt from a live box and a live device — a voice can vanish between
     * the entry card and the run, and a playlist with nothing to play is an empty screen, not
     * a crash.
     */
    @Test
    fun anEmptyPoolOpensOnSilence() {
        val opened = ListeningRun.reduce(ListeningRun.idle(emptyList()), ListeningIntent.Start, Random(1))

        assertNull(opened.state.turn)
        assertEquals(listOf(ListeningEffect.Stop), opened.effects)
    }

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

    private fun gendered(id: String, source: String, target: String, article: String): Card = Card(
        id = id,
        kind = CardKind.Noun,
        area = "area1",
        emoji = null,
        seedIndex = 1,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = source),
        target = Realization(lang = "sw", text = target, grammar = mapOf("gender" to article)),
        promptFeminineMarker = false,
    )
}

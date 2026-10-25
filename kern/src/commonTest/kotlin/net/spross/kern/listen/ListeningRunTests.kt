package net.spross.kern.listen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

/** The playlist itself: what it plays next, what it replays, and what it never touches. */
class ListeningRunTests {

    private fun candidates(n: Int): List<ListeningCandidate> = (1..n).map {
        ListeningCandidate(Box.word(it), arrived = false, suspended = false, scheduled = true, queued = false, queuedRank = 0)
    }

    private fun run(pool: List<ListeningCandidate>): ListeningRunState =
        ListeningRun.reduce(ListeningRun.idle(pool), ListeningIntent.Start).state

    /** The ids of [turns] consecutive turns, the opening one first. */
    private fun heard(pool: List<ListeningCandidate>, turns: Int): List<String> {
        var state = run(pool)
        val ids = mutableListOf<String>()
        repeat(turns) {
            ids += assertNotNull(state.turn).cardId
            state = ListeningRun.reduce(state, ListeningIntent.Advance).state
        }
        return ids
    }

    /**
     * RULE: past the playlist's end the run deals the same words again, never opening on the
     * word it just closed with.
     * WHY: a run outlasting its deal should hear a fresh order rather than the old one again,
     * and what it must never do is hand back the same word twice in a row.
     */
    @Test
    fun aRunPastItsDealDealsTheSameWordsAgain() {
        val pool = candidates(6)
        val ids = heard(pool, turns = 12)

        assertEquals(pool.map { it.card.id }.toSet(), ids.drop(6).toSet())
        assertNotEquals(ids[5], ids[6])
    }

    /**
     * RULE: a run plays the pool in the order it was handed, turn for turn.
     * WHY: the ordering decision lives in `listeningOrder` and nowhere else. A run that
     * re-sorted or re-rolled would be a second opinion about the playlist, and the run would
     * stop being reproducible from the box alone.
     */
    @Test
    fun aRunPlaysThePoolInTheOrderItWasHanded() {
        // Deliberately NOT catalog order: the walk must not quietly repair it.
        val pool = listOf(7, 2, 9, 1).map {
            ListeningCandidate(Box.word(it), arrived = false, suspended = false, scheduled = true, queued = false, queuedRank = 0)
        }

        assertEquals(listOf("w07", "w02", "w09", "w01"), heard(pool, turns = 4))
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
        val pool = ListeningPool.report(catalog, before, "de", "sw", true, true, Box.day1)

        var state = run(pool.candidates)
        repeat(100) { state = ListeningRun.reduce(state, ListeningIntent.Advance).state }

        assertEquals(101, state.played)
        assertEquals(before, box(total = 20, scheduled = 8))
        assertEquals(
            pool.candidates.map { it.card.id },
            ListeningPool.report(catalog, before, "de", "sw", true, true, Box.day1).candidates.map { it.card.id },
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
            arrived = false, suspended = false, scheduled = true, queued = false, queuedRank = 0,
        )
        val turn = assertNotNull(run(listOf(bread)).turn)

        assertEquals("bread", turn.cardId)
        assertEquals("mkate", turn.targetForm)
        assertEquals("das Brot", turn.sourceForm)
        assertEquals("das", turn.spokenArticle)
        assertEquals(RECALL_GAP_HELD_MS, turn.recallGapMs)
        assertEquals(ECHO_GAP_MS, turn.echoGapMs)
        assertEquals(TURN_GAP_MS, turn.turnGapMs)
    }

    /**
     * RULE: a card with no authored gender speaks no article.
     * WHY: most target languages have none, and an article invented for them would be taught
     * as fact.
     */
    @Test
    fun aCardWithoutAGenderSpeaksNoArticle() {
        assertNull(assertNotNull(run(candidates(1)).turn).spokenArticle)
    }

    /**
     * RULE: Back restarts the turn on air; pressed again before the run moves on, it steps to
     * the turn before, and keeps stepping.
     * WHY: the word just missed is the likelier ask, and a press on a turn already restarted
     * can only mean the one before it. The restart is an EFFECT because the turn does not
     * change, and a driver watching for a changed turn would go deaf on exactly this button.
     */
    @Test
    fun backRestartsTheTurnThenStepsBehindIt() {
        var state = run(candidates(20))
        repeat(3) { state = ListeningRun.reduce(state, ListeningIntent.Advance).state }

        val restarted = ListeningRun.reduce(state, ListeningIntent.Back)
        assertEquals(listOf(ListeningEffect.Play(assertNotNull(state.turn))), restarted.effects)

        val once = ListeningRun.reduce(restarted.state, ListeningIntent.Back).state
        val twice = ListeningRun.reduce(once, ListeningIntent.Back).state
        assertEquals(listOf("w03", "w02"), listOf(once, twice).map { assertNotNull(it.turn).cardId })
        assertEquals("w03", assertNotNull(ListeningRun.reduce(twice, ListeningIntent.Advance).state.turn).cardId)
    }

    /**
     * RULE: Back at the head of a run restarts it, having nothing before it.
     */
    @Test
    fun backAtTheHeadRestartsIt() {
        val restarted = ListeningRun.reduce(run(candidates(3)), ListeningIntent.Back).state
        assertEquals("w01", assertNotNull(ListeningRun.reduce(restarted, ListeningIntent.Back).state.turn).cardId)
    }

    /**
     * RULE: pausing stops the sound; resuming replays the current turn from its first beat.
     * WHY: the run holds beats, not a playhead, so there is no middle of a word to resume
     * into — and a word cut in half is worth hearing whole.
     */
    @Test
    fun pausingStopsTheSoundAndResumingReplaysTheTurn() {
        val started = run(candidates(20))

        val paused = ListeningRun.reduce(started, ListeningIntent.TogglePause)
        assertTrue(paused.state.paused)
        assertEquals(listOf(ListeningEffect.Stop), paused.effects)

        val resumed = ListeningRun.reduce(paused.state, ListeningIntent.TogglePause)
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
        val paused = ListeningRun.reduce(run(candidates(20)), ListeningIntent.TogglePause).state

        val stayed = ListeningRun.reduce(paused, ListeningIntent.Advance)

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
        val paused = ListeningRun.reduce(run(candidates(20)), ListeningIntent.TogglePause).state

        val skipped = ListeningRun.reduce(paused, ListeningIntent.Skip)

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
        val started = run(candidates(20))

        val closed = ListeningRun.reduce(started, ListeningIntent.Close)

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
        val opened = ListeningRun.reduce(ListeningRun.idle(emptyList()), ListeningIntent.Start)

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
        source = Realization(lang = "de", text = source),
        target = Realization(lang = "sw", text = target, grammar = mapOf("article" to article)),
    )
}

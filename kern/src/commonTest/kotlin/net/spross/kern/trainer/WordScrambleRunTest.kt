package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/** The word run: what an answer is worth, where the ladder takes it, and what a close books. */
class WordScrambleRunTest {

    private val cards = listOf(
        ScrambleFixture.word("window", "Fenster", seed = 1),
        ScrambleFixture.word("cook", "kochen", CardKind.Verb, seed = 2),
        ScrambleFixture.word("fast", "schnell", CardKind.Adjective, seed = 3),
        ScrambleFixture.word("rainbow", "Regenbogen", seed = 4),
        ScrambleFixture.word("bike", "Fahrrad", seed = 5, synonyms = listOf("Velo"), variants = listOf("Farrad")),
    )

    private fun config() = WordScrambleRunConfig(
        report = WordScrambleAvailability.report(ScrambleFixture.box(cards)),
        normalizer = ScrambleFixture.normalizer,
    )

    private fun open(level: Int = 1, seed: Int = 4) =
        WordScrambleRun.openAt(config(), level, Random(seed))

    private fun reduce(state: WordScrambleRunState, intent: WordScrambleIntent, seed: Int = 9) =
        WordScrambleRun.reduce(state, intent, Random(seed))

    /** A question standing on its own, for the rules that are about grading rather than the draw. */
    private fun task(cardId: String, display: String, accepted: List<String>) = WordScrambleTask(
        cardId = cardId,
        language = ScrambleFixture.TARGET,
        level = 1,
        scrambled = WordScrambleMasking.scramble(display, 1, Random(1)),
        accepted = accepted,
        display = display,
        gloss = "en-$cardId",
    )

    /** The opening question is the Sprosse it was asked for, mixed the way that Sprosse mixes. */
    @Test
    fun aRunOpensOnTheSprosseItWasAskedFor() {
        val task = assertNotNull(open().task)
        assertEquals(1, task.level)
        assertEquals(1, task.scrambled.fixedLeading)
        assertEquals(1, task.scrambled.fixedTrailing)
        assertEquals(task.display, task.accepted.first())
        assertEquals("en-${task.cardId}", task.gloss)
    }

    /** Finishing the word IS the answer: the live approve books it and arms the beat. */
    @Test
    fun aFinishedSpellingApprovesItself() {
        val state = open()
        val typed = reduce(state, WordScrambleIntent.InputChanged(state.task!!.display))
        assertEquals(TurnFeedback.Correct, typed.state.feedback)
        assertTrue(typed.effects.any { it == DrillEffect.ArmAdvance(AdvanceTier.Live) })
    }

    /** Every form the catalog authored for the word counts — a synonym and a variant alike. */
    @Test
    fun theCardsOwnSynonymsAndVariantsAreAccepted() {
        val config = config()
        val bike = task("bike", "Fahrrad", listOf("Fahrrad", "Velo", "Farrad"))
        assertEquals(Match.Exact, WordScrambleRun.grade("Fahrrad", bike, config))
        assertEquals(Match.Exact, WordScrambleRun.grade("Velo", bike, config))
        assertEquals(Match.Exact, WordScrambleRun.grade("Farrad", bike, config))
        assertEquals(Match.Wrong, WordScrambleRun.grade("Auto", bike, config))
    }

    /** A look-up is a miss, and a miss drops the Sprosse the run stood on. */
    @Test
    fun aRevealBooksAMissAndDropsTheSprosse() {
        val state = open(level = 2)
        assertEquals(2, state.level)
        val revealed = reduce(state, WordScrambleIntent.Reveal).state
        assertEquals(TurnFeedback.Revealed, revealed.feedback)
        assertTrue(revealed.showsAnswer)
        val booked = reduce(revealed, WordScrambleIntent.ConfirmPending).state
        assertEquals(1, booked.level)
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
        assertEquals(0, booked.streak)
    }

    /** Clean wins carry the Sprosse; the wins banked below stay behind with it. */
    @Test
    fun cleanSpellingsCarryTheSprosse() {
        var state = open()
        repeat(WordScrambleRun.WINS_TO_ADVANCE) {
            val task = assertNotNull(state.task)
            state = reduce(state, WordScrambleIntent.Submit(task.display)).state
            assertEquals(TurnFeedback.Correct, state.feedback)
            state = reduce(state, WordScrambleIntent.ConfirmPending).state
        }
        assertEquals(2, state.level)
        assertEquals(0, state.winsAtLevel)
        assertEquals(2, assertNotNull(state.task).level)
    }

    /**
     * A word spelled clean is not asked again at that Sprosse — but the SAME word at a harder
     * mixing is a question of its own, so the pool refills as the ladder rises.
     */
    @Test
    fun aWordSpelledCleanIsRetiredAtThatSprosseOnly() {
        var state = open()
        val first = assertNotNull(state.task).cardId
        state = reduce(state, WordScrambleIntent.Submit(state.task!!.display)).state
        state = reduce(state, WordScrambleIntent.ConfirmPending).state
        assertTrue(DrillSolved.wordKey(1, first) in state.solved)
        assertFalse(DrillSolved.wordKey(2, first) in state.solved)
        // A look-up keeps the run at the foot of the ladder, where that word is now spent.
        repeat(6) { round ->
            val task = assertNotNull(state.task, "the pool ran dry after $round rounds")
            if (task.level == 1) assertFalse(task.cardId == first, "asked ${task.cardId} again")
            state = reduce(state, WordScrambleIntent.Reveal).state
            state = reduce(state, WordScrambleIntent.ConfirmPending).state
        }
    }

    /** Closing books a pending answer exactly as the tap would, and an untouched run reports nothing. */
    @Test
    fun closingBooksWhatWeiterWouldAndNothingMore() {
        assertNull(WordScrambleRun.close(open()).summary)

        val state = open()
        val accepted = reduce(state, WordScrambleIntent.Submit(state.task!!.display)).state
        val closed = WordScrambleRun.close(accepted)
        val summary = assertNotNull(closed.summary)
        assertEquals(1, summary.done)
        assertEquals(1, summary.bestStreak)
        assertFalse(summary.newRecord, "the drill keeps no record store")
        assertTrue(closed.state.finished)
    }
}

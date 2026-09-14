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
        assertEquals(listOf(task.display), task.accepted, "the form drawn IS the accepted set")
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

    /**
     * The letters handed over are the question, so the form they spell is the whole answer:
     * another word of the same card spells something else and is refused like any other word.
     */
    @Test
    fun onlyTheFormTheLettersSpellIsAccepted() {
        val config = config()
        val bike = task("bike", "Fahrrad", listOf("Fahrrad"))
        assertEquals(Match.Exact, WordScrambleRun.grade("Fahrrad", bike, config))
        assertEquals(Match.Wrong, WordScrambleRun.grade("Velo", bike, config), "a synonym is another word")
        assertEquals(Match.Wrong, WordScrambleRun.grade("Auto", bike, config))
    }

    /**
     * The slips forgiven scale with the word: one on a short spelling, more on a long one.
     * A learner who mistypes twice in fifteen letters has read the letters; one who mistypes
     * twice in five has not.
     */
    @Test
    fun theTyposForgivenScaleWithTheWordsLength() {
        val config = config()
        val short = task("cook", "kochen", listOf("kochen"))
        assertEquals(Match.Typo("kochen"), WordScrambleRun.grade("kochem", short, config))
        assertEquals(Match.Wrong, WordScrambleRun.grade("kochemm", short, config))

        val long = task("speed", "Geschwindigkeit", listOf("Geschwindigkeit"))
        assertEquals(Match.Typo("Geschwindigkeit"), WordScrambleRun.grade("Geschwundigkeit", long, config))
        assertEquals(Match.Typo("Geschwindigkeit"), WordScrambleRun.grade("Geschwundigkeid", long, config))
        assertEquals(Match.Wrong, WordScrambleRun.grade("Geschwundugkeid", long, config))
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
        assertFalse(summary.newRecord, "the drill keeps no streak record")
        assertTrue(closed.state.finished)
    }

    // MARK: - The ladder

    /** The Sprosse is a LENGTH FLOOR: whatever it asks carries the letters that Sprosse wants. */
    @Test
    fun aSprosseNeverAsksAWordShorterThanItsFloor() {
        val report = config().report
        for (start in 1..report.maxLevel) {
            var state = open(level = start)
            repeat(WordScrambleRun.WINS_TO_ADVANCE) {
                val task = state.task ?: return@repeat
                assertTrue(
                    task.display.count { it.isLetter() } >= report.lettersAt(task.level),
                    "Sprosse ${task.level} asked \"${task.display}\"",
                )
                state = answer(state, clean = true)
            }
        }
    }

    // MARK: - What the store keeps

    /** A Sprosse climbed on clean spellings alone is booked, and the next run opens above it. */
    @Test
    fun aSprosseClimbedCleanOpensTheNextRunAboveIt() {
        var state = open()
        repeat(WordScrambleRun.WINS_TO_ADVANCE) { state = answer(state, clean = true) }
        val closed = WordScrambleRun.close(state)
        assertEquals(setOf(1), closed.clearedSprossen)
        assertEquals(2, closed.bestLevel)

        val resumed = WordScrambleRunConfig(config().report, ScrambleFixture.normalizer, closed.clearedSprossen)
        assertEquals(2, resumed.entryLevel)
        assertEquals(2, WordScrambleRun.open(resumed, Random(3)).level)
    }

    /**
     * An almost costs the RUN nothing — the streak stands, the banked win stands, the Sprosse
     * holds — and costs the STORE the Sprosse: it is climbed here and never booked, so the next
     * run opens on it again.
     */
    @Test
    fun anAlmostKeepsTheRunAndForfeitsTheSprosse() {
        var state = answer(open(), clean = true)
        assertEquals(1, state.winsAtLevel)
        state = answer(state, clean = false)
        assertEquals(AnswerOutcome.Almost, state.outcomes.last())
        assertEquals(2, state.streak, "an almost is no miss")
        assertEquals(1, state.level, "and no demotion")
        assertEquals(1, state.winsAtLevel, "the banked win stands")

        repeat(WordScrambleRun.WINS_TO_ADVANCE) { if (state.level == 1) state = answer(state, clean = true) }
        assertTrue(state.level > 1, "the run climbs as it always did")
        val closed = WordScrambleRun.close(state)
        assertEquals(emptySet(), closed.clearedSprossen, "but the Sprosse is not the store's")
        assertEquals(1, WordScrambleRunConfig(config().report, null, closed.clearedSprossen).entryLevel)
    }

    /** A miss takes the Sprosse's booking with it, even at the foot where there is nothing to drop to. */
    @Test
    fun aMissForfeitsTheSprosseItFallsOn() {
        var state = reduce(open(), WordScrambleIntent.Reveal).state
        state = reduce(state, WordScrambleIntent.ConfirmPending).state
        assertEquals(1, state.level, "the foot of the ladder has nothing below it")

        repeat(WordScrambleRun.WINS_TO_ADVANCE) { if (state.level == 1) state = answer(state, clean = true) }
        assertTrue(state.level > 1)
        assertEquals(emptySet(), WordScrambleRun.close(state).clearedSprossen)
    }

    /** Answer whatever stands — exactly, or with one letter wrong — and book it. */
    private fun answer(state: WordScrambleRunState, clean: Boolean): WordScrambleRunState {
        val task = assertNotNull(state.task)
        val text = if (clean) task.display else task.display.dropLast(1) + "x"
        val typed = reduce(state, WordScrambleIntent.Submit(text)).state
        return reduce(typed, WordScrambleIntent.ConfirmPending).state
    }
}

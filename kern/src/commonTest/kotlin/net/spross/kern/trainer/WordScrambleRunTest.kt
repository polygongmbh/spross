package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind
import net.spross.kern.model.ClosingNote
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.AnswerControls
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
        ScrambleFixture.word("bike", "Fahrrad", seed = 5, teaches = listOf("Velo"), accepts = listOf("Farrad")),
    ) + ScrambleFixture.filler(count = 11, letters = 8, fromSeed = 100)

    private fun config() = WordScrambleRunConfig(
        report = WordScrambleAvailability.report(ScrambleFixture.box(cards)),
        normalizer = ScrambleFixture.normalizer,
    )

    private fun open(sprosse: Int = 1, seed: Int = 4) =
        WordScrambleRun.openAt(config(), sprosse, Random(seed))

    private fun reduce(state: WordScrambleRunState, intent: WordScrambleIntent, seed: Int = 9) =
        WordScrambleRun.reduce(state, intent, Random(seed))

    /** A question standing on its own, for the rules that are about grading rather than the draw. */
    private fun task(cardId: String, display: String, accepted: List<String>) = WordScrambleTask(
        cardId = cardId,
        language = ScrambleFixture.TARGET,
        sprosse = 1,
        scrambled = WordScrambleMasking.scramble(display, 1, Random(1)),
        accepted = accepted,
        display = display,
        gloss = "en-$cardId",
    )

    /** The opening question is the Sprosse it was asked for, mixed the way that Sprosse mixes. */
    @Test
    fun aRunOpensOnTheSprosseItWasAskedFor() {
        val task = assertNotNull(open().task)
        assertEquals(1, task.sprosse)
        assertEquals(1, task.scrambled.fixedLeading)
        assertEquals(listOf(task.display), task.accepted, "the form drawn IS the accepted set")
        assertEquals("en-${task.cardId}", task.gloss)
    }

    /** Finishing the word IS the answer: the live approve books it and arms the beat. */
    @Test
    fun aFinishedSpellingApprovesItself() {
        val state = open()
        val typed = reduce(state, WordScrambleIntent.InputChanged(state.task!!.display))
        assertEquals(TurnFeedback.Correct, typed.state.feedback)
        assertTrue(typed.effects.any { it == DrillEffect.ArmAdvance(AdvanceBeat.Live) })
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
        val state = open(sprosse = 2)
        assertEquals(2, state.sprosse)
        val revealed = reduce(state, WordScrambleIntent.Reveal).state
        assertEquals(TurnFeedback.Revealed, revealed.feedback)
        assertTrue(revealed.showsAnswer)
        val booked = reduce(revealed, WordScrambleIntent.ConfirmPending).state
        assertEquals(1, booked.sprosse)
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
        assertEquals(0, booked.answerStreak)
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
        assertEquals(2, state.sprosse)
        assertEquals(0, state.winsAtSprosse)
        assertEquals(2, assertNotNull(state.task).sprosse)
    }

    /** A drill's field: one submit while owed, then a miss holds what was written until tapped, and a second miss offers the way out. */
    @Test
    fun theFieldHoldsAMissUntilTappedAndASecondMissOffersTheWayOut() {
        val state = open()
        val asking = assertNotNull(state.controls)
        assertEquals(AnswerControls.Primary.Submit, asking.primary)
        val missed = reduce(state, WordScrambleIntent.Reveal).state
        val held = assertNotNull(missed.controls)
        assertNull(held.primary)
        assertFalse((held.slot as AnswerControls.Slot.Typed).editable)
        assertEquals(AnswerControls.Confirm.Always, held.confirm)
        assertFalse(held.stop)
        val again = reduce(reduce(missed, WordScrambleIntent.ConfirmPending).state, WordScrambleIntent.Reveal).state
        assertTrue(assertNotNull(again.controls).stop)
    }

    /**
     * The gloss is what the scramble never said, so a clean spelling grows it on a card that stays closed:
     * the word already stands in the learner's own text.
     */
    @Test
    fun aCleanSpellingIsGlossedOnAClosedCard() {
        val state = open()
        val task = assertNotNull(state.task)
        val answered = assertNotNull(reduce(state, WordScrambleIntent.Submit(task.display)).state.question)
        assertFalse(answered.opens)
        assertTrue(answered.growsNote)
        assertEquals(ClosingNote.Own(task.gloss), answered.closing.note)
        assertTrue(assertNotNull(reduce(state, WordScrambleIntent.Reveal).state.question).opens)
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
            if (task.sprosse == 1) assertFalse(task.cardId == first, "asked ${task.cardId} again")
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
        assertEquals(1, summary.bestAnswerStreak)
        assertFalse(summary.newRecord, "the drill keeps no streak record")
        assertTrue(closed.state.finished)
    }

    // MARK: - The ladder

    /** The Sprosse is a LENGTH FLOOR: whatever it asks carries the letters that Sprosse wants. */
    @Test
    fun aSprosseNeverAsksAWordShorterThanItsFloor() {
        val report = config().report
        for (start in 1..report.maxSprosse) {
            var state = open(sprosse = start)
            repeat(WordScrambleRun.WINS_TO_ADVANCE) {
                val task = state.task ?: return@repeat
                assertTrue(
                    task.display.count { it.isLetter() } >= report.lettersAt(task.sprosse),
                    "Sprosse ${task.sprosse} asked \"${task.display}\"",
                )
                state = answer(state, clean = true)
            }
        }
    }

    /** The ladder stops at the longest Sprosse the pool fills, and answering that one out ends the run. */
    @Test
    fun theLadderStopsAtItsTop() {
        val top = config().report.maxSprosse
        var state = open(sprosse = top)
        repeat(cards.size + 1) {
            if (state.finished) return@repeat
            state = answer(state, clean = true)
            assertEquals(top, state.sprosse, "no Sprosse past the top")
        }
        assertTrue(state.finished)
        assertNull(state.task)
    }

    // MARK: - What the store keeps

    /** A Sprosse climbed on clean spellings alone is booked for the store. */
    @Test
    fun aSprosseClimbedCleanIsBooked() {
        var state = open()
        repeat(WordScrambleRun.WINS_TO_ADVANCE) { state = answer(state, clean = true) }
        val closed = WordScrambleRun.close(state)
        assertEquals(setOf(1), closed.clearedSprossen)
        assertEquals(2, closed.bestSprosse)
    }

    /** The run's first slip keeps every Sprosse after it from the store, and the ones before it booked. */
    @Test
    fun aSlipEndsTheRunsClearing() {
        var state = open()
        repeat(WordScrambleRun.WINS_TO_ADVANCE) { state = answer(state, clean = true) }
        state = answer(state, clean = false)
        while (state.sprosse == 2) state = answer(state, clean = true)
        assertTrue(state.sprosse > 2, "the run climbs off Sprosse 2")
        assertEquals(setOf(1), WordScrambleRun.close(state).clearedSprossen)
    }

    private fun resumed(vararg cleared: Int) = WordScrambleRun.open(
        WordScrambleRunConfig(config().report, ScrambleFixture.normalizer, cleared.toSet()),
        Random(3),
    )

    /**
     * A resumed run opens at the foot and passes each Sprosse the store holds on one clean
     * answer — booking nothing new for it — while one the store lacks asks the full count.
     */
    @Test
    fun aResumedRunFastClimbsWhatTheStoreHolds() {
        var state = resumed(1, 2)
        assertEquals(1, state.sprosse)
        state = answer(state, clean = true)
        assertEquals(2, state.sprosse)
        state = answer(state, clean = true)
        assertEquals(3, state.sprosse)
        assertEquals(0, state.newSprossen, "a Sprosse the store held is nothing new")
        state = answer(state, clean = true)
        assertEquals(3, state.sprosse, "a Sprosse the store lacks asks the full count")
    }

    /** The first slip ends the fast climb: from there on a held Sprosse asks the full count too. */
    @Test
    fun aSlipEndsTheFastClimb() {
        var state = reduce(resumed(1, 2), WordScrambleIntent.Reveal).state
        state = reduce(state, WordScrambleIntent.ConfirmPending).state
        repeat(WordScrambleRun.WINS_TO_ADVANCE) { state = answer(state, clean = true) }
        assertEquals(2, state.sprosse)
        state = answer(state, clean = true)
        assertEquals(2, state.sprosse, "held, and still asking the full count")
    }

    /**
     * An almost costs the RUN nothing — the answer streak stands, the banked win stands, the Sprosse
     * holds. What it costs the store is [aSlipEndsTheRunsClearing]'s.
     */
    @Test
    fun anAlmostCostsTheRunNothing() {
        var state = answer(open(), clean = true)
        assertEquals(1, state.winsAtSprosse)
        state = answer(state, clean = false)
        assertEquals(AnswerOutcome.Almost, state.outcomes.last())
        assertEquals(2, state.answerStreak, "an almost is no miss")
        assertEquals(1, state.sprosse, "and no demotion")
        assertEquals(1, state.winsAtSprosse, "the banked win stands")
    }

    /** Answer whatever stands — exactly, or with one letter wrong — and book it. */
    private fun answer(state: WordScrambleRunState, clean: Boolean): WordScrambleRunState {
        val task = assertNotNull(state.task)
        val text = if (clean) task.display else task.display.dropLast(1) + "x"
        val typed = reduce(state, WordScrambleIntent.Submit(text)).state
        return reduce(typed, WordScrambleIntent.ConfirmPending).state
    }
}

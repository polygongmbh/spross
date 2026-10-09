package net.spross.kern.trainer

import net.spross.kern.session.Saying
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.ClosingNote
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.AnswerControls
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.TurnFeedback

/** The sentence run: what a placement is worth, where the ladder takes it, what a close books. */
class SentenceScrambleRunTest {

    private val words = listOf(
        ScrambleFixture.word("mouse", "Maus", seed = 1),
        ScrambleFixture.word("run", "laufen", seed = 2),
    )

    private val phrases = listOf(
        ScrambleFixture.phrase("runs", "die Maus läuft.", listOf("mouse", "run"), seed = 10),
        ScrambleFixture.phrase("sleeps", "die Maus schläft dort.", listOf("mouse", "run"), seed = 11),
        ScrambleFixture.phrase("eats", "die Maus frisst.", listOf("mouse", "run"), seed = 12),
        ScrambleFixture.phrase("waits", "die Maus wartet hier.", listOf("mouse", "run"), seed = 13),
        ScrambleFixture.phrase("slow", "die Maus läuft sehr langsam.", listOf("mouse", "run"), seed = 14),
        ScrambleFixture.phrase("asks", "läuft die Maus?", listOf("mouse", "run"), seed = 15),
    )

    /** Two phrases to a band: three Sprossen over the six phrases, the first of them all three words. */
    private fun report(bandSize: Int = 2) = SentenceScrambleAvailability.report(ScrambleFixture.box(words + phrases))
        .copy(bandSize = bandSize)

    private fun config(bandSize: Int = 2) = SentenceScrambleRunConfig(report(bandSize))

    private fun open(sprosse: Int = 1, seed: Int = 5, bandSize: Int = 2) =
        SentenceScrambleRun.openAt(config(bandSize), sprosse, Random(seed))

    private fun answered(state: SentenceScrambleRunState, correctly: Boolean = true) =
        reduce(arrange(state, correctly), SentenceScrambleIntent.ConfirmPending).state

    private fun reduce(
        state: SentenceScrambleRunState,
        intent: SentenceScrambleIntent,
        seed: Int = 11,
    ) = SentenceScrambleRun.reduce(state, intent, Random(seed))

    /** Place every atom in the order the phrase was authored in. */
    private fun arrange(state: SentenceScrambleRunState, correctly: Boolean): SentenceScrambleRunState {
        val task = assertNotNull(state.task)
        val order = task.canonical.let { if (correctly) it else it.reversed() }
        return order.fold(state) { carried, atom ->
            reduce(carried, SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == atom.id })).state
        }
    }

    /** The deal is the phrase's own atoms, in an order that is not the phrase's. */
    @Test
    fun theAtomsAreDealtOutOfOrder() {
        val task = assertNotNull(open().task)
        assertEquals(task.canonical.map { it.id }.sorted(), task.shuffled.map { it.id }.sorted())
        assertFalse(ScrambleGrading.isSolved(task.shuffled, task.canonical), "dealt in its own order")
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, task.words)
        assertEquals("en-${task.cardId}", task.gloss)
        assertEquals(ScrambleTokenizer.joined(task.canonical), task.display.removeSuffix("."))
    }

    /** A miss drops a band, and the band below asks genuinely easier phrases. */
    @Test
    fun aMissDropsToTheEasierBand() {
        val report = config().report
        for (seed in 1..8) {
            val state = open(sprosse = 2, seed = seed)
            assertTrue(assertNotNull(state.task).cardId in report.phrasesAt(2).map { it.card.id })
            val missed = answered(state, correctly = false)
            assertEquals(1, missed.sprosse)
            assertTrue(assertNotNull(missed.task).cardId in report.phrasesAt(1).map { it.card.id })
        }
    }

    /**
     * A question mark is dealt and placed like any other chip: riding its word it would name
     * the last one, and the sentence's own full stop is dropped for the same reason.
     */
    @Test
    fun aMarkIsAChipToPlaceAndTheFullStopIsGone() {
        val asks = config().report.phrases.single { it.card.id == "asks" }
        assertEquals(listOf("läuft", "die", "Maus", "?"), asks.atoms.map { it.text })
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, asks.words)
        val slow = config().report.phrases.single { it.card.id == "slow" }
        assertEquals(listOf("die", "Maus", "läuft", "sehr", "langsam"), slow.atoms.map { it.text })
        assertEquals("die Maus läuft sehr langsam.", slow.card.target.text, "the reveal keeps it")
    }

    /** Committing the last atom IS the answer; a right arrangement books it and arms the beat. */
    @Test
    fun thePlacementThatCompletesTheArrangementIsTheAnswer() {
        var state = open()
        val task = assertNotNull(state.task)
        state = reduce(state, SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == 0 }))
            .state
        assertEquals(TurnFeedback.Neutral, state.feedback, "an incomplete arrangement decides nothing")
        assertEquals(task.size - 1, state.remaining)

        val done = arrange(open(), correctly = true)
        assertEquals(TurnFeedback.Correct, done.feedback)
        assertTrue(done.complete)
        assertEquals(ScrambleTokenizer.joined(done.task!!.canonical), done.arranged)
    }

    /** A wrong order opens the card on the authored one rather than waiting to be permuted. */
    @Test
    fun aWrongArrangementRevealsTheAuthoredOrder() {
        val wrong = arrange(open(), correctly = false)
        assertEquals(TurnFeedback.Revealed, wrong.feedback)
        assertTrue(wrong.showsAnswer)
        val booked = reduce(wrong, SentenceScrambleIntent.ConfirmPending).state
        assertEquals(listOf(AnswerOutcome.Wrong), booked.outcomes)
        assertEquals(emptyList(), booked.placed, "the next question starts empty")
    }

    /** The placement that grades, and the task it graded. */
    private fun graded(correctly: Boolean): Pair<SentenceScrambleTask, SentenceScrambleReduction> {
        val state = open()
        val task = assertNotNull(state.task)
        val order = task.canonical.let { if (correctly) it else it.reversed() }
        val place = { atom: ScrambleAtom -> SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == atom.id }) }
        val waiting = order.dropLast(1).fold(state) { carried, atom -> reduce(carried, place(atom)).state }
        return task to reduce(waiting, place(order.last()))
    }

    /**
     * Either way an arrangement ends on the sound of the phrase as authored, never as it was
     * put together — and the beat arms on a clean one alone.
     */
    @Test
    fun anArrangementSaysTheAuthoredPhrase() {
        val (task, right) = graded(correctly = true)
        assertEquals(Saying(task.display, "de"), right.state.reading.answer)
        assertTrue(DrillEffect.ArmAdvance(AdvanceBeat.Explicit) in right.effects)
        val (wrongTask, wrong) = graded(correctly = false)
        assertEquals(Saying(wrongTask.display, "de"), wrong.state.reading.answer)
        assertTrue(wrong.effects.none { it is DrillEffect.ArmAdvance })
    }

    /** An atom can be taken back while the arrangement is still the learner's to give. */
    @Test
    fun anAtomComesBackWhileTheArrangementIsOpen() {
        var state = open()
        val task = assertNotNull(state.task)
        state = reduce(state, SentenceScrambleIntent.PlaceAtom(0)).state
        state = reduce(state, SentenceScrambleIntent.PlaceAtom(1)).state
        assertTrue(state.isPlaced(0))
        state = reduce(state, SentenceScrambleIntent.ReturnAtom(0)).state
        assertFalse(state.isPlaced(0))
        assertEquals(listOf(task.shuffled[1]), state.placedAtoms)
        // The same atom twice, and an atom nobody dealt, change nothing.
        assertEquals(state, reduce(state, SentenceScrambleIntent.PlaceAtom(1)).state)
        assertEquals(state, reduce(state, SentenceScrambleIntent.PlaceAtom(task.size + 3)).state)
    }

    /** Once the question is decided the arrangement stands — nothing may be taken back out of it. */
    @Test
    fun aDecidedArrangementIsNotRearranged() {
        val done = arrange(open(), correctly = true)
        assertEquals(done, reduce(done, SentenceScrambleIntent.ReturnAtom(0)).state)
    }

    /** A phrase arranged clean is never asked again — its order does not change with the Sprosse. */
    @Test
    fun aPhraseArrangedCleanIsRetired() {
        var state = open()
        val first = assertNotNull(state.task).cardId
        state = arrange(state, correctly = true)
        state = reduce(state, SentenceScrambleIntent.ConfirmPending).state
        assertTrue(DrillSolved.sentenceKey(first) in state.solved)
        assertFalse(assertNotNull(state.task).cardId == first)
    }

    /** The ladder climbs band by band and ends on its summary once the top band is answered out. */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        var state = open()
        repeat(phrases.size + 2) {
            if (state.finished) return@repeat
            state = answered(state)
            assertTrue(state.sprosse <= state.config.report.maxSprosse, "Sprosse ${state.sprosse} holds nothing")
        }
        assertTrue(state.finished)
        assertNull(state.task)
    }

    /** Wins enough to climb, on the top band, keep the run there on what the band has left. */
    @Test
    fun theTopBandIsWhereTheLadderStops() {
        var state = open(bandSize = phrases.size)
        repeat(SentenceScrambleRun.WINS_TO_ADVANCE) { state = answered(state) }
        assertEquals(1, state.sprosse)
        assertEquals(1, state.bestSprosse)
        assertNotNull(state.task, "the band still has phrases to ask")
    }

    /** Closing books a pending answer as the tap would, and an untouched run reports nothing. */
    @Test
    fun closingBooksWhatWeiterWouldAndNothingMore() {
        assertNull(SentenceScrambleRun.close(open()).summary)

        val done = arrange(open(), correctly = true)
        val closed = SentenceScrambleRun.close(done)
        val summary = assertNotNull(closed.summary)
        assertEquals(1, summary.done)
        assertEquals(1, summary.bestAnswerStreak)
        assertFalse(summary.newRecord, "the drill keeps no streak record")
        assertTrue(closed.state.finished)
    }

    // MARK: - What the store keeps

    /** Every Sprosse climbed off clean is booked, and none the run still stands on. */
    @Test
    fun aSprosseClimbedCleanIsBooked() {
        var state = open()
        repeat(phrases.size - 1) { state = answered(state) }
        val closed = SentenceScrambleRun.close(state)
        assertEquals(3, closed.bestSprosse)
        assertEquals(setOf(1, 2), closed.clearedSprossen)
    }

    /** A resumed run opens at the foot and passes a band the store holds on one clean arrangement. */
    @Test
    fun aResumedRunFastClimbsWhatTheStoreHolds() {
        val resumed = SentenceScrambleRunConfig(report(bandSize = 3), cleared = setOf(1))
        val state = SentenceScrambleRun.open(resumed, Random(7))
        assertEquals(1, state.sprosse)
        assertEquals(2, answered(state).sprosse)
    }

    /** A clean arrangement grows the meaning alone; only a missed one opens onto the authored order. */
    @Test
    fun theCardSetsTheOrderAgainOnlyWhereItWasMissed() {
        val clean = assertNotNull(arrange(open(), correctly = true).question)
        val missed = assertNotNull(arrange(open(), correctly = false).question)
        val task = assertNotNull(open().task)
        assertFalse(clean.opens)
        assertTrue(clean.growsNote)
        assertEquals(ClosingNote.Own(task.gloss), clean.closing.note)
        assertTrue(missed.opens)
        assertEquals(task.display, missed.answer.text)
    }

    /** Placing the last piece is the answer, so no primary action asks for it. */
    @Test
    fun anArrangementNeedsNoPrimaryAction() {
        val controls = assertNotNull(open().controls)
        assertEquals(AnswerControls.Slot.Arrangement, controls.slot)
        assertNull(controls.primary)
    }

    /** An alternative word order from `orders` is accepted but flags [alternativeMatch]. */
    @Test
    fun anAlternativeOrderIsAcceptedAndFlagged() {
        val canonical = listOf(ScrambleAtom(0, "gehen"), ScrambleAtom(1, "Sie"), ScrambleAtom(2, "geradeaus"))
        val placed = listOf(canonical[1], canonical[0], canonical[2])
        val altAtoms = listOf(ScrambleAtom(0, "Sie"), ScrambleAtom(1, "gehen"), ScrambleAtom(2, "geradeaus"))
        assertFalse(ScrambleGrading.matchesCanonical(placed, canonical))
        assertTrue(ScrambleGrading.isSolved(placed, canonical, listOf(altAtoms)))

        val ordered = words + listOf(
            ScrambleFixture.phrase("formal", "Gehen Sie geradeaus.",
                listOf("mouse"), seed = 20, orders = listOf("Sie gehen geradeaus.")),
        )
        val report = SentenceScrambleAvailability.report(ScrambleFixture.box(ordered))
        val phrase = report.phrases.single { it.card.id == "formal" }
        assertEquals(1, phrase.alternativeOrders.size)
    }

    /** A phrase with one comma accepts its halves swapped, marks staying at the sentence's edges. */
    @Test
    fun aSingleCommaSwapsItsHalves() {
        fun swapped(text: String) = ScrambleCommaSwap.of(ScrambleTokenizer.atoms(text))?.let(ScrambleTokenizer::joined)
        assertEquals("help me, Mom!", swapped("Mom, help me!"))
        assertEquals("¡ayúdame, mamá!", swapped("¡mamá, ayúdame!"))
        assertEquals("¿dónde estás, papá?", swapped("papá, ¿dónde estás?"))
        assertNull(swapped("Wait, think, go!"))
        assertNull(swapped("Hello there!"))
    }

    /** An order is authored plain, yet it must land on the chips the learner is dealt. */
    @Test
    fun anOrderMapsOntoTheChipsWithTheirStressAndComma() {
        val ordered = words + listOf(
            ScrambleFixture.phrase("stress", "Мамо́, допоможи́ мені́!", listOf("mouse"), seed = 21,
                orders = listOf("Мамо, мені допоможи!")),
            ScrambleFixture.phrase("front", "I hurt myself yesterday.", listOf("mouse"), seed = 22,
                orders = listOf("Yesterday, I hurt myself.")),
        )
        val report = SentenceScrambleAvailability.report(ScrambleFixture.box(ordered))
        assertEquals(2, report.phrases.single { it.card.id == "stress" }.alternativeOrders.size)
        val front = report.phrases.single { it.card.id == "front" }.alternativeOrders
        assertEquals(listOf("yesterday", "I", "hurt", "myself"), front.first().map { it.text })
    }
}

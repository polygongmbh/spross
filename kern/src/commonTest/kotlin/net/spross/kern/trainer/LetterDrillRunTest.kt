package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The letter run: which Sprosse it opens on, what a tile and a typed glyph earn, the
 * dictation verdict ladder, and what a close leaves behind (which is figures and nothing else
 * — D12, the drill books no review and keeps no record).
 *
 * The ladder, the draw and the ramp step are kern's own and pinned elsewhere; what is
 * asserted here is the run that steps through them.
 */
class LetterDrillRunTest {

    private val xx = LanguageInfo(
        code = LetterDrillFixture.LANGUAGE,
        name = "Xx",
        englishName = "Xx",
        flag = "🏳️",
    )

    private val gapWords = LetterDrillFixture.alphabet.entries
        .associate { it.ref to LetterDrillFixture.example(it) }

    private fun report(
        growing: Int,
        dictation: List<LetterDrill.DictationCandidate> = emptyList(),
        refs: List<String> = LetterDrillFixture.allRefs,
    ) = LetterDrillAvailability.Report(
        language = LetterDrillFixture.LANGUAGE,
        alphabet = LetterDrillFixture.alphabet,
        promptableRefs = refs,
        dictationCandidates = dictation,
        gapWords = gapWords,
        arrivedCards = growing,
    )

    private fun config(
        report: LetterDrillAvailability.Report,
        cards: List<Card> = emptyList(),
        grader: Boolean = true,
        cleared: Set<Int> = emptySet(),
    ) = LetterDrillRunConfig(
        report = report,
        cards = cards.associateBy { it.id },
        dictationGrader = if (!grader) {
            null
        } else {
            CatalogAnswerGrader(
                AnswerNormalizer(xx, articleLeniency = false),
                cards,
            )
        },
        cleared = cleared,
    )

    private fun reduce(state: LetterDrillRunState, intent: LetterDrillIntent, rng: Random) =
        LetterDrillRun.reduce(state, intent, rng)

    private fun dictationTask(card: Card) = LetterDrillTask(
        format = LetterFormat.Dictation,
        language = LetterDrillFixture.LANGUAGE,
        answerRef = card.id,
        promptText = card.target.text,
        promptKind = LetterPromptKind.Word,
        promptSlug = card.id,
        promptGlyph = null,
        choices = null,
        gapText = null,
        accepted = listOf(card.target.text),
        display = card.target.text,
        gloss = card.source.text,
    )

    // MARK: - Where a run opens

    /**
     * The entry Sprosse comes from the words the learner already holds, capped one format below
     * whatever this device can reach — nobody starts by taking dictation.
     */
    @Test
    fun aRunOpensOnTheFormatTheLearnersWordsHaveEarned() {
        assertEquals(LetterFormat.ChoiceEasy, report(growing = 0).openingFormat(emptySet()))
        val held = report(growing = 72, dictation = LetterDrillFixture.dictationCandidates())
        assertEquals(LetterFormat.Typed, held.openingFormat(emptySet()))
    }

    /**
     * A format some run answered out is climbed past on the way in, above whatever the
     * vocabulary already skips — the atlas' record, kept for the tile and typed formats.
     */
    @Test
    fun aRunOpensAboveTheSprossenEarlierRunsAnsweredOut() {
        val fresh = report(growing = 0)
        assertEquals(3, fresh.openingSprosse(setOf(1, 2)))
        assertEquals(LetterFormat.ChoiceConfusable, fresh.openingFormat(setOf(1, 2)))
        assertTrue(fresh.formatCleared(LetterFormat.ChoiceEasy, setOf(1, 2)))
        assertFalse(fresh.formatCleared(LetterFormat.ChoiceConfusable, setOf(3, 4)))
        assertEquals(3, LetterDrillRun.open(config(fresh, cleared = setOf(1, 2)), Random(3)).sprosse)

        val held = report(growing = 72, dictation = LetterDrillFixture.dictationCandidates())
        assertEquals(6, held.openingSprosse(setOf(1, 2)), "the vocabulary already stands above them")
        assertEquals(8, held.openingSprosse(setOf(6, 7)))
        // Everything answered out still opens a run, on the top Sprosse.
        assertEquals(7, fresh.openingSprosse((1..7).toSet()))
    }

    /** A Sprosse climbed off clean is what the close files; a slip on one keeps it out. */
    @Test
    fun aCloseFilesTheSprossenTheRunClimbedOffClean() {
        val rng = Random(41)
        var state = LetterDrillRun.openAt(config(report(growing = 0)), 1, rng)
        assertTrue(LetterDrillRun.close(state).clearedSprossen.isEmpty())
        while (state.task != null && state.sprosse <= 2) state = answeredRight(state, rng)
        assertEquals(setOf(1, 2), LetterDrillRun.close(state).clearedSprossen)

        var slipped = LetterDrillRun.openAt(config(report(growing = 0)), 1, rng)
        slipped = reduce(slipped, LetterDrillIntent.Reveal, rng).state
        slipped = reduce(slipped, LetterDrillIntent.ConfirmPending, rng).state
        while (slipped.task != null && slipped.sprosse <= 1) slipped = answeredRight(slipped, rng)
        assertTrue(1 !in LetterDrillRun.close(slipped).clearedSprossen, "a miss on Sprosse 1 keeps it out")
    }

    /** Dictation draws from the box, which grows, so no climb through it is ever filed. */
    @Test
    fun dictationIsNeverFiled() {
        val rng = Random(43)
        val held = report(growing = 72, dictation = LetterDrillFixture.dictationCandidates())
        var state = LetterDrillRun.openAt(config(held, LetterDrillFixture.dictationCandidates().map { it.card }), 8, rng)
        while (state.task != null && state.sprosse <= 8) state = answeredRight(state, rng)
        assertTrue(LetterDrillRun.close(state).clearedSprossen.none { it >= 8 })
    }

    @Test
    fun aSprosseForcedAboveTheCeilingOpensInsideIt() {
        val rng = Random(3)
        val typed = LetterDrillRun.openAt(config(report(growing = 0)), 9, rng)
        assertEquals(7, typed.sprosse)
        assertEquals(LetterFormat.Typed, typed.format)

        val dictating = LetterDrillRun.openAt(
            config(
                report(growing = 0, dictation = LetterDrillFixture.dictationCandidates()),
                LetterDrillFixture.dictationCards(),
            ),
            9,
            rng,
        )
        assertEquals(9, dictating.sprosse)
        assertEquals(LetterFormat.Dictation, dictating.format)
    }

    // MARK: - Tiles

    @Test
    fun aTileIsOneAttemptAndACleanHitArmsTheBeat() {
        val rng = Random(5)
        val state = LetterDrillRun.openAt(config(report(growing = 0)), 1, rng)
        val task = assertNotNull(state.task)
        assertEquals(LetterFormat.ChoiceEasy, task.format)
        assertTrue(task.choices.orEmpty().contains(task.display))

        val hit = reduce(state, LetterDrillIntent.Choose(task.display), rng)
        assertEquals(TurnFeedback.Correct, hit.state.feedback)
        assertEquals(task.display, hit.state.chosen)
        assertEquals(
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ArmAdvance(AdvanceBeat.Explicit),
            ),
            hit.effects,
        )
        // A second tap would be a retry, and the ramp has no verdict for that.
        assertEquals(hit.state, reduce(hit.state, LetterDrillIntent.Choose("m"), rng).state)
    }

    @Test
    fun aWrongTileStandsTheAnswerUpAndBooksAMiss() {
        val rng = Random(7)
        var state = LetterDrillRun.openAt(config(report(growing = 72)), 5, rng)
        assertEquals(LetterFormat.ChoiceConfusable, state.format)
        val wrong = assertNotNull(state.task!!.choices).first { it != state.task!!.display }
        state = reduce(state, LetterDrillIntent.Choose(wrong), rng).state
        assertEquals(TurnFeedback.Revealed, state.feedback)
        assertTrue(state.showsAnswer)

        state = reduce(state, LetterDrillIntent.ConfirmPending, rng).state
        assertEquals(listOf(AnswerOutcome.Wrong), state.outcomes)
        assertEquals(0, state.answerStreak)
        assertEquals(1, state.missRun)
        assertEquals(4, state.sprosse, "a miss steps the Sprosse back down")
    }

    // MARK: - Typed and dictated

    /** The card carries the answer, so the field stays EMPTY — nothing primes it. */
    @Test
    fun revealingLeavesTheFieldEmpty() {
        val rng = Random(11)
        val state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)
        assertEquals(LetterFormat.Typed, state.format)
        assertTrue(state.typing)
        val revealed = reduce(state, LetterDrillIntent.Reveal, rng)
        assertEquals(
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)),
            revealed.effects,
        )
        assertTrue(revealed.state.showsAnswer)
        // Nothing typed is the same ask, so the check button and Enter agree; once the
        // answer is in, nothing is owed and a stray blank changes nothing.
        val blank = reduce(state, LetterDrillIntent.Submit("   "), rng)
        assertEquals(revealed.state, blank.state)
        assertEquals(revealed.effects, blank.effects)
        assertEquals(revealed.state, reduce(revealed.state, LetterDrillIntent.Submit(" "), rng).state)
    }

    /** Finishing the answer IS the answer: an exact one approves itself, with no Check tap. */
    @Test
    fun anExactAnswerApprovesItselfWhileTyping() {
        val rng = Random(14)
        val state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)
        val task = assertNotNull(state.task)
        val done = reduce(state, LetterDrillIntent.InputChanged(task.display), rng)
        assertEquals(TurnFeedback.Correct, done.state.feedback)
        assertTrue(DrillEffect.ArmAdvance(AdvanceBeat.Live) in done.effects)
        // Typing past the answer takes the approval back, so a longer word never books it.
        val past = reduce(done.state, LetterDrillIntent.InputChanged(task.display + "x"), rng)
        assertEquals(TurnFeedback.Neutral, past.state.feedback)
        assertTrue(DrillEffect.CancelAdvance in past.effects)
    }

    /** A slip approves nothing live — it pauses on the explicit check, like every typed drill. */
    @Test
    fun aDictationSlipWaitsForTheCheck() {
        val rng = Random(15)
        val rainbow = LetterDrillFixture.card("rainbow", "Regenbogen")
        val state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)
            .copy(config = config(report(growing = 72), listOf(rainbow)), task = dictationTask(rainbow))
        val slip = reduce(state, LetterDrillIntent.InputChanged("Regenbogem"), rng).state
        assertEquals(TurnFeedback.Neutral, slip.feedback)
        assertEquals(
            TurnFeedback.Almost("Regenbogen", AlmostReason.Typo),
            reduce(slip, LetterDrillIntent.Submit("Regenbogem"), rng).state.feedback,
        )
        assertEquals(
            TurnFeedback.Correct,
            reduce(slip, LetterDrillIntent.InputChanged("Regenbogen"), rng).state.feedback,
        )
    }

    /** A tile question has no field, so a keystroke there changes nothing. */
    @Test
    fun aTileQuestionIgnoresKeystrokes() {
        val rng = Random(16)
        val state = LetterDrillRun.openAt(config(report(growing = 0)), 1, rng)
        val typed = reduce(state, LetterDrillIntent.InputChanged(state.task!!.display), rng)
        assertEquals(state, typed.state)
        assertTrue(typed.effects.isEmpty())
    }

    /**
     * The dictation ladder: exact, then a slip, then the miss — which is where the
     * catalog-wide grader withdraws typo credit for a word that is somebody else's, and where
     * another form of the very card lands too: dictation asks the word that PLAYED.
     */
    @Test
    fun dictationCreditsOnlyTheWordThatPlayed() {
        val mouse = LetterDrillFixture.card("mouse", "миша", teaches = listOf("мишка"))
        val closed = LetterDrillFixture.card("close", "kufunga")
        val opened = LetterDrillFixture.card("open", "kufungua")
        val rainbow = LetterDrillFixture.card("rainbow", "Regenbogen")
        val cards = listOf(mouse, closed, opened, rainbow)
        val grader = config(report(growing = 72), cards).dictationGrader

        assertEquals(
            Match.Exact,
            LetterDrillRun.grade("миша", dictationTask(mouse), mouse, grader),
        )
        assertEquals(
            Match.Wrong,
            LetterDrillRun.grade("мишка", dictationTask(mouse), mouse, grader),
        )
        // A variant one slip from the played form is still another form, never an almost.
        val color = LetterDrillFixture.card("color", "Farbenlehre", teaches = listOf("Farbenlehren"))
        val spelled = config(report(growing = 72), listOf(color)).dictationGrader
        assertEquals(
            Match.Typo("Farbenlehre"),
            spelled!!.grade("Farbenlehren", LetterDrill.dictationGradingCard(color, dictationTask(color))),
        )
        assertEquals(
            Match.Wrong,
            LetterDrillRun.grade("Farbenlehren", dictationTask(color), color, spelled),
        )
        assertEquals(
            Match.Typo("Regenbogen"),
            LetterDrillRun.grade("Regenbogem", dictationTask(rainbow), rainbow, grader),
        )
        assertEquals(
            Match.Wrong,
            LetterDrillRun.grade("kufungua", dictationTask(closed), closed, grader),
        )
    }

    /** Missing card or grader is defensive, never asserted: the glyph rule takes over. */
    @Test
    fun aDictationWithoutAGraderFallsBackToTheGlyphRule() {
        val mouse = LetterDrillFixture.card("mouse", "миша")
        val task = dictationTask(mouse)
        assertEquals(Match.Exact, LetterDrillRun.grade("миша", task, mouse, null))
        assertEquals(Match.Wrong, LetterDrillRun.grade("мишка", task, mouse, null))
        assertEquals(Match.Exact, LetterDrillRun.grade("миша", task, null, null))
    }

    /** An almost hold waits for a tap and moves the Sprosse neither way. */
    @Test
    fun anAlmostAnswerHoldsTheSprosseAndExtendsTheStreak() {
        val rng = Random(17)
        val state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)
        val held = state.copy(feedback = TurnFeedback.Almost("миша", AlmostReason.Typo))
        assertTrue(held.answerAccepted)
        assertTrue(held.showsAnswer, "a slip leaves a spelling worth seeing")
        val booked = reduce(held, LetterDrillIntent.ConfirmPending, rng).state
        assertEquals(listOf(AnswerOutcome.Almost), booked.outcomes)
        assertEquals(6, booked.sprosse)
        assertEquals(1, booked.answerStreak)
        assertEquals(0, booked.missRun)
    }

    @Test
    fun oneCleanWinIsEnoughOnceAVocabularyIsGrowing() {
        val rng = Random(19)
        var state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)
        state = reduce(state, LetterDrillIntent.Submit(state.task!!.display), rng).state
        state = reduce(state, LetterDrillIntent.AdvanceElapsed, rng).state
        assertEquals(7, state.sprosse)

        var slow = LetterDrillRun.openAt(config(report(growing = 0)), 6, rng)
        slow = reduce(slow, LetterDrillIntent.Submit(slow.task!!.display), rng).state
        slow = reduce(slow, LetterDrillIntent.AdvanceElapsed, rng).state
        assertEquals(6, slow.sprosse, "the classic two wins per Sprosse below a held vocabulary")
        assertEquals(1, slow.winsAtSprosse)
    }

    // MARK: - The way out, and the end

    /** Nothing left to ask ends the run on its summary, never on a blank card. */
    @Test
    fun aRunWhoseSamplingDriesUpEndsRatherThanBlanks() {
        val rng = Random(29)
        val dried = config(report(growing = 0, refs = emptyList()))
        assertNull(LetterDrillRun.open(dried, rng).task)

        var state = LetterDrillRun.openAt(config(report(growing = 0)), 1, rng)
        state = state.copy(config = dried)
        state = reduce(state, LetterDrillIntent.Choose(state.task!!.display), rng).state
        state = reduce(state, LetterDrillIntent.ConfirmPending, rng).state
        assertNull(state.task)
        assertTrue(state.finished)
        assertEquals(1, state.done)
    }

    // MARK: - Asking each prompt once

    /** The question on screen, answered right the way its format takes an answer. */
    private fun answeredRight(state: LetterDrillRunState, rng: Random): LetterDrillRunState {
        val task = assertNotNull(state.task)
        val intent = if (task.choices == null) {
            LetterDrillIntent.Submit(task.display)
        } else {
            LetterDrillIntent.Choose(task.display)
        }
        return reduce(reduce(state, intent, rng).state, LetterDrillIntent.ConfirmPending, rng).state
    }

    /**
     * One promptable letter is one question per FORMAT, so answering it right empties both
     * Sprossen of that format at once: the run climbs past them rather than asking `m` again,
     * and the same letter is a question again where the next format asks it another way.
     */
    @Test
    fun aFormatAnsweredOutIsClimbedPast() {
        val rng = Random(41)
        val one = config(report(growing = 0, refs = listOf("m")))
        var state = LetterDrillRun.openAt(one, 1, rng)
        assertEquals("em", state.task?.promptText)

        state = answeredRight(state, rng)
        assertEquals(3, state.sprosse, "both easy Sprossen ask the same one question")
        assertEquals(LetterFormat.ChoiceConfusable, state.format)
        assertEquals(0, state.winsAtSprosse, "the wins stay behind with the Sprosse that earned them")
        assertEquals("em", state.task?.promptText, "a tile format and a typed one are two questions")
        assertFalse(state.finished)
    }

    /** A ladder answered out ends the run on its summary, never on a repeat. */
    @Test
    fun aLadderAnsweredOutEndsTheRun() {
        val rng = Random(43)
        var state = LetterDrillRun.openAt(config(report(growing = 0, refs = listOf("m"))), 1, rng)
        while (!state.finished) state = answeredRight(state, rng)
        assertNull(state.task)
        assertEquals(3, state.done, "one question per format: two tile formats and the typed one")
    }

    /**
     * The card opens on a clean answer too, the way the word scramble's does:
     * the spelling was the question and the meaning never was.
     */
    @Test
    fun aCleanSpellingIsGlossedToo() {
        val rng = Random(17)
        val state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)
        assertFalse(state.showsAnswer, "nothing to show while the turn is still open")
        assertTrue(
            state.copy(feedback = TurnFeedback.Correct).showsAnswer,
            "a letter spelled right is still worth glossing",
        )
    }

    // MARK: - Closing

    @Test
    fun closingBooksAPendingAnswerAndKeepsNoRecord() {
        val rng = Random(31)
        val state = LetterDrillRun.openAt(config(report(growing = 72)), 6, rng)

        val untouched = LetterDrillRun.close(state)
        assertNull(untouched.summary)
        assertTrue(DrillEffect.Silence in untouched.effects)

        val almost = LetterDrillRun.close(state.copy(feedback = TurnFeedback.Almost("м", AlmostReason.Typo)))
        assertEquals(listOf(AnswerOutcome.Almost), almost.state.outcomes)
        assertEquals(1, almost.summary?.done)
        assertEquals(false, almost.summary?.newRecord, "the letter drill keeps no record store")
        assertEquals(6, almost.state.sprosse, "closing may not upgrade an almost answer")

        // A revealed answer nobody confirmed is not accepted, so closing books nothing.
        assertNull(LetterDrillRun.close(state.copy(feedback = TurnFeedback.Revealed)).summary)
    }
}

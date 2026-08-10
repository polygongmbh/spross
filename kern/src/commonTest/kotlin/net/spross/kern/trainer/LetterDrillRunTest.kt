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
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerTone
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/**
 * The letter run: which rung it opens on, what a tile and a typed glyph earn, the three-step
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
        consolidated: Int,
        dictation: List<LetterDrill.DictationCandidate> = emptyList(),
        refs: List<String> = LetterDrillFixture.allRefs,
    ) = LetterDrillAvailability.Report(
        language = LetterDrillFixture.LANGUAGE,
        alphabet = LetterDrillFixture.alphabet,
        promptableRefs = refs,
        dictationCandidates = dictation,
        gapWords = gapWords,
        consolidatedCards = consolidated,
    )

    private fun config(
        report: LetterDrillAvailability.Report,
        cards: List<Card> = emptyList(),
        grader: Boolean = true,
    ) = LetterDrillRunConfig(
        report = report,
        cards = cards.associateBy { it.id },
        dictationGrader = if (!grader) {
            null
        } else {
            CatalogAnswerGrader(
                AnswerNormalizer(xx, articleLeniency = false, maxTyposPerWord = 1),
                cards,
            )
        },
    )

    private fun reduce(state: LetterDrillRunState, intent: LetterDrillIntent, rng: Random) =
        LetterDrillRun.reduce(state, intent, rng)

    private fun dictationTask(card: Card) = LetterDrillTask(
        stage = LetterStage.Dictation,
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
     * The entry rung comes from the words the learner already holds, capped one stage below
     * whatever this device can reach — nobody starts by taking dictation.
     */
    @Test
    fun aRunOpensOnTheStageTheLearnersWordsHaveEarned() {
        val fresh = report(consolidated = 0)
        assertEquals(1, fresh.entryLevel)
        assertEquals(LetterStage.ChoiceEasy, fresh.entryStage)
        assertEquals(LetterDrill.MAX_LEVEL_WITHOUT_DICTATION, fresh.maxLevel)
        assertEquals(2, fresh.winsToAdvance)

        assertEquals(3, report(consolidated = 24).entryLevel)
        assertEquals(LetterStage.ChoiceConfusable, report(consolidated = 24).entryStage)

        val held = report(consolidated = 72, dictation = LetterDrillFixture.dictationCandidates())
        assertEquals(6, held.entryLevel)
        assertEquals(LetterStage.Typed, held.entryStage)
        assertEquals(LetterDrill.MAX_LEVEL_WITH_DICTATION, held.maxLevel)
        assertEquals(1, held.winsToAdvance, "a consolidated vocabulary earns a rung in one win")
    }

    /** Dictation exists only above the floor, and the ramp stops one rung short of it below. */
    @Test
    fun theCeilingFollowsWhetherThereIsEnoughToDictate() {
        val cards = LetterDrillFixture.dictationCards()
        val floor = LetterDrillAvailability.DICTATION_FLOOR
        val enough = report(72, LetterDrillFixture.dictationCandidates(cards.take(floor)))
        val short = report(72, LetterDrillFixture.dictationCandidates(cards.take(floor - 1)))
        assertTrue(enough.dictationAvailable)
        assertFalse(short.dictationAvailable)
        assertEquals(9, enough.maxLevel)
        assertEquals(7, short.maxLevel)
    }

    @Test
    fun aRungForcedAboveTheCeilingOpensInsideIt() {
        val rng = Random(3)
        val typed = LetterDrillRun.openAt(config(report(consolidated = 0)), 9, rng)
        assertEquals(7, typed.level)
        assertEquals(LetterStage.Typed, typed.stage)

        val dictating = LetterDrillRun.openAt(
            config(
                report(consolidated = 0, dictation = LetterDrillFixture.dictationCandidates()),
                LetterDrillFixture.dictationCards(),
            ),
            9,
            rng,
        )
        assertEquals(9, dictating.level)
        assertEquals(LetterStage.Dictation, dictating.stage)
    }

    // MARK: - Tiles

    @Test
    fun aTileIsOneAttemptAndACleanHitArmsTheBeat() {
        val rng = Random(5)
        val state = LetterDrillRun.openAt(config(report(consolidated = 0)), 1, rng)
        val task = assertNotNull(state.task)
        assertEquals(LetterStage.ChoiceEasy, task.stage)
        assertTrue(task.choices.orEmpty().contains(task.display))

        val hit = reduce(state, LetterDrillIntent.Choose(task.display), rng)
        assertEquals(TurnFeedback.Correct, hit.state.feedback)
        assertEquals(task.display, hit.state.chosen)
        assertEquals(
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ArmAdvance(AdvanceTier.Explicit),
            ),
            hit.effects,
        )
        // A second tap would be a retry, and the ramp has no verdict for that.
        assertEquals(hit.state, reduce(hit.state, LetterDrillIntent.Choose("m"), rng).state)
    }

    @Test
    fun aWrongTileStandsTheAnswerUpAndBooksAMiss() {
        val rng = Random(7)
        var state = LetterDrillRun.openAt(config(report(consolidated = 72)), 5, rng)
        assertEquals(LetterStage.ChoiceConfusable, state.stage)
        val wrong = assertNotNull(state.task!!.choices).first { it != state.task!!.display }
        state = reduce(state, LetterDrillIntent.Choose(wrong), rng).state
        assertEquals(TurnFeedback.Revealed, state.feedback)
        assertTrue(state.showsAnswer)

        state = reduce(state, LetterDrillIntent.ConfirmPending, rng).state
        assertEquals(listOf(AnswerTone.Wrong), state.outcomes)
        assertEquals(0, state.streak)
        assertEquals(1, state.missRun)
        assertEquals(4, state.level, "a miss steps the rung back down")
    }

    // MARK: - Typed and dictated

    /** The card carries the answer, so the field stays EMPTY — nothing primes it. */
    @Test
    fun revealingLeavesTheFieldEmpty() {
        val rng = Random(11)
        val state = LetterDrillRun.openAt(config(report(consolidated = 72)), 6, rng)
        assertEquals(LetterStage.Typed, state.stage)
        assertTrue(state.typing)
        val revealed = reduce(state, LetterDrillIntent.Reveal, rng)
        assertEquals(
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)),
            revealed.effects,
        )
        assertTrue(revealed.state.showsAnswer)
    }

    @Test
    fun aTypedGlyphGradesExactWithNoTypoBudget() {
        val rng = Random(13)
        val state = LetterDrillRun.openAt(config(report(consolidated = 72)), 6, rng)
        val drawn = assertNotNull(state.task)
        assertEquals(LetterVerdict.Clean, LetterDrillRun.verdict(drawn.display, drawn, null, null))
        assertEquals(LetterVerdict.Wrong, LetterDrillRun.verdict("zz", drawn, null, null))

        // Case folds; a one-glyph answer never gets a slip budget, whatever the stage.
        val cyrillic = drawn.copy(accepted = listOf("ч"), display = "ч")
        assertEquals(LetterVerdict.Clean, LetterDrillRun.verdict("Ч", cyrillic, null, null))
        assertEquals(LetterVerdict.Wrong, LetterDrillRun.verdict("c", cyrillic, null, null))
    }

    /**
     * The dictation ladder, IN ORDER: exact, then a form the card itself teaches (amber, naming
     * what actually played), then a slip, then the miss — which is where the catalog-wide
     * grader withdraws typo credit for a word that is somebody else's.
     */
    @Test
    fun theDictationVerdictLadderPutsATaughtFormAheadOfTheGradersOwnVerdict() {
        val mouse = LetterDrillFixture.card("mouse", "миша", synonyms = listOf("мишка"))
        val closed = LetterDrillFixture.card("close", "kufunga")
        val opened = LetterDrillFixture.card("open", "kufungua")
        val rainbow = LetterDrillFixture.card("rainbow", "Regenbogen")
        val cards = listOf(mouse, closed, opened, rainbow)
        val grader = config(report(consolidated = 72), cards).dictationGrader

        assertEquals(
            LetterVerdict.Clean,
            LetterDrillRun.verdict("миша", dictationTask(mouse), mouse, grader),
        )
        assertEquals(
            LetterVerdict.Heard("миша"),
            LetterDrillRun.verdict("мишка", dictationTask(mouse), mouse, grader),
        )
        assertEquals(
            LetterVerdict.Typo("Regenbogen"),
            LetterDrillRun.verdict("Regenbogem", dictationTask(rainbow), rainbow, grader),
        )
        assertEquals(
            LetterVerdict.Wrong,
            LetterDrillRun.verdict("kufungua", dictationTask(closed), closed, grader),
        )
    }

    /** Missing card or grader is defensive, never asserted: the glyph rule takes over. */
    @Test
    fun aDictationWithoutAGraderFallsBackToTheGlyphRule() {
        val mouse = LetterDrillFixture.card("mouse", "миша")
        val task = dictationTask(mouse)
        assertEquals(LetterVerdict.Clean, LetterDrillRun.verdict("миша", task, mouse, null))
        assertEquals(LetterVerdict.Wrong, LetterDrillRun.verdict("мишка", task, mouse, null))
        assertEquals(LetterVerdict.Clean, LetterDrillRun.verdict("миша", task, null, null))
    }

    /** Both amber holds wait for a tap, give the field back, and move the rung neither way. */
    @Test
    fun anAmberAnswerHoldsTheRungAndExtendsTheStreak() {
        val rng = Random(17)
        val state = LetterDrillRun.openAt(config(report(consolidated = 72)), 6, rng)
        for (reason in listOf(AlmostReason.Typo, AlmostReason.Heard)) {
            val held = state.copy(feedback = TurnFeedback.Almost("миша", reason))
            assertTrue(held.answerAccepted)
            assertTrue(held.showsAnswer, "a slip and a heard-instead both leave a spelling worth seeing")
            val booked = reduce(held, LetterDrillIntent.ConfirmPending, rng).state
            assertEquals(listOf(AnswerTone.Tough), booked.outcomes)
            assertEquals(6, booked.level)
            assertEquals(1, booked.streak)
            assertEquals(0, booked.missRun)
        }
    }

    @Test
    fun oneCleanWinIsEnoughOnceAVocabularyHasConsolidated() {
        val rng = Random(19)
        var state = LetterDrillRun.openAt(config(report(consolidated = 72)), 6, rng)
        state = reduce(state, LetterDrillIntent.Submit(state.task!!.display), rng).state
        state = reduce(state, LetterDrillIntent.AdvanceElapsed, rng).state
        assertEquals(7, state.level)

        var slow = LetterDrillRun.openAt(config(report(consolidated = 0)), 6, rng)
        slow = reduce(slow, LetterDrillIntent.Submit(slow.task!!.display), rng).state
        slow = reduce(slow, LetterDrillIntent.AdvanceElapsed, rng).state
        assertEquals(6, slow.level, "the classic two wins per rung below a held vocabulary")
        assertEquals(1, slow.winsAtLevel)
    }

    // MARK: - The way out, and the end

    @Test
    fun theSecondMissInARowOffersTheWayOut() {
        val rng = Random(23)
        var state = LetterDrillRun.openAt(config(report(consolidated = 72)), 6, rng)
        assertFalse(reduce(state, LetterDrillIntent.Reveal, rng).state.offersFinish)
        state = reduce(state, LetterDrillIntent.Reveal, rng).state
        state = reduce(state, LetterDrillIntent.ConfirmPending, rng).state
        assertTrue(reduce(state, LetterDrillIntent.Reveal, rng).state.offersFinish)
    }

    /** Nothing left to ask ends the run on its summary, never on a blank card. */
    @Test
    fun aRunWhoseSamplingDriesUpEndsRatherThanBlanks() {
        val rng = Random(29)
        val dried = config(report(consolidated = 0, refs = emptyList()))
        assertNull(LetterDrillRun.open(dried, rng).task)

        var state = LetterDrillRun.openAt(config(report(consolidated = 0)), 1, rng)
        state = state.copy(config = dried)
        state = reduce(state, LetterDrillIntent.Choose(state.task!!.display), rng).state
        state = reduce(state, LetterDrillIntent.ConfirmPending, rng).state
        assertNull(state.task)
        assertTrue(state.finished)
        assertEquals(1, state.done)
    }

    // MARK: - Closing

    @Test
    fun closingBooksAPendingAnswerAndKeepsNoRecord() {
        val rng = Random(31)
        val state = LetterDrillRun.openAt(config(report(consolidated = 72)), 6, rng)

        val untouched = LetterDrillRun.close(state)
        assertNull(untouched.summary)
        assertTrue(DrillEffect.Silence in untouched.effects)

        val amber = LetterDrillRun.close(state.copy(feedback = TurnFeedback.Almost("м", AlmostReason.Heard)))
        assertEquals(listOf(AnswerTone.Tough), amber.state.outcomes)
        assertEquals(1, amber.summary?.done)
        assertEquals(false, amber.summary?.newRecord, "the letter drill keeps no record store")
        assertEquals(6, amber.state.level, "closing may not upgrade an amber answer")

        // A revealed answer nobody confirmed is not accepted, so closing books nothing.
        assertNull(LetterDrillRun.close(state.copy(feedback = TurnFeedback.Revealed)).summary)
    }

    @Test
    fun theCounterCountsAnswersThatWereNotMisses() {
        val state = LetterDrillRun.openAt(config(report(consolidated = 0)), 1, Random(37)).copy(
            outcomes = listOf(AnswerTone.Right, AnswerTone.Tough, AnswerTone.Wrong),
            done = 3,
            bestStreak = 5,
        )
        assertEquals(2, state.cleanCount)
        assertEquals(StreakTier.Cheer, LetterDrillRun.close(state).summary!!.tier)
    }
}

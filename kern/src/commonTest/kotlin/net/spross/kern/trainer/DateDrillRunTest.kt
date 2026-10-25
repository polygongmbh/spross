package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.QuestionHint
import net.spross.kern.session.Saying

/**
 * What the calendar brings to the shared run ([PairDrillRunTest] steps through that): the
 * warm-up tiles, the value check, the pattern word, the parse a reversed run climbs into, and
 * which Sprossen can be answered out. The ladder, the draw and the task shapes are [DateDrillTests]'.
 */
class DateDrillRunTest {

    private val swahili = DateDrillFixture.swahiliContent

    private fun config(reverse: Boolean = false) = DateDrillRunConfig(
        content = DateDrillFixture.germanContent,
        reverse = reverse,
        fast = false,
        normalizer = AnswerNormalizer.drill(if (reverse) DateDrillFixture.english else DateDrillFixture.german),
    )

    private fun swahiliConfig(reverse: Boolean = false) = DateDrillRunConfig(
        content = swahili,
        reverse = reverse,
        fast = false,
        normalizer = AnswerNormalizer.drill(if (reverse) DateDrillFixture.german else DateDrillFixture.swahili),
    )

    private fun open(reverse: Boolean = false, sprosse: Int = 1) =
        DateDrillRun.openAt(config(reverse), sprosse, Random(7))

    private fun PairDrillRunState<DateDrillTask>.reduce(intent: PairDrillIntent) =
        DateDrillRun.reduce(this, intent, Random(7))

    /** The run stepped by one whole answer, the way the platform steps it. */
    private fun PairDrillRunState<DateDrillTask>.answered(text: String): PairDrillRunState<DateDrillTask> =
        reduce(PairDrillIntent.Submit(text)).state.reduce(PairDrillIntent.ConfirmPending).state

    private val PairDrillRunState<DateDrillTask>.patternWord: String?
        get() = (question.hint as? QuestionHint.NewWord)?.word

    @Test
    fun aRunOpensOnTheWarmUpTiles() {
        val run = DateDrillRun.open(config(), Random(7))
        assertEquals(DateTaskKind.NameChoice, run.task.kind)
        assertTrue(run.owesAnswer)
    }

    // MARK: - Grading

    /**
     * The assembled Sprossen carry the numbers drill's value check word by word, which is
     * what the languages whose numerals sit one edit apart need: sw `nane` (8) written for
     * `nne` (4) is one insertion, well inside the drill's slip a word, so without the check
     * a learner would be told they had merely fumbled the day they got wrong. A fumble that
     * names NO value stays the forgiven slip it was.
     */
    @Test
    fun aNumeralThatNamesAnotherValueIsRefusedNotForgiven() {
        val task = DateDrillTasks.dayMonth(swahili, 4, 2)
        assertEquals("tarehe nne Machi", task.display)
        val match = DateDrillRun.grade("tarehe nane Machi", task, swahiliConfig())
        assertIs<Match.OtherWord>(match)
        assertEquals("nane", match.word, "the refusal names the numeral, not the whole line")
        assertIs<Match.Typo>(DateDrillRun.grade("tarehe nnr Machi", task, swahiliConfig()))
    }

    // MARK: - The word a pattern adds

    /**
     * The first-sight rule, the place-value hint's: the word is handed over for the card
     * that owes it and never again, whatever else the run goes on to draw.
     */
    @Test
    fun thePatternWordIsShownOnceAndThenNeverAgain() {
        var run = DateDrillRun.openAt(swahiliConfig(), sprosse = 4, Random(7))
        while (run.task.kind != DateTaskKind.DayAndMonth) run = run.answered(run.task.display)
        assertEquals("tarehe", run.patternWord)
        run = run.answered(run.task.display)
        while (run.task.kind != DateTaskKind.DayAndMonth) run = run.answered(run.task.display)
        assertNull(run.patternWord, "the word was already met")
    }

    /** Reversed the card carries the reading, which says the word already — so no hint at all. */
    @Test
    fun aReversedRunIsHandedNoPatternWord() {
        var run = DateDrillRun.openAt(swahiliConfig(reverse = true), sprosse = 4, Random(7))
        repeat(12) {
            assertNull(run.patternWord)
            run = run.answered(run.task.display)
        }
    }

    // MARK: - The reversed direction

    /**
     * Reverse is a DIRECTION, not a shorter ladder: a reversed run climbs off the nineteen
     * names into the parse, where the card carries the reading and the answer is the date
     * written in digits — which is also why such a run does not run out.
     */
    @Test
    fun aReversedRunClimbsOffTheNamesIntoTheParse() {
        var run = open(reverse = true, sprosse = 3)
        repeat(30) {
            if (run.task.digits) return@repeat
            run = run.answered(run.task.display)
        }
        assertTrue(run.task.digits, "the run never reached a date to parse")
        assertTrue(run.sprosse > 3)
        assertEquals("en", run.answerLanguage, "the digits are written in the learner's own format")
        assertEquals(Match.Exact, DateDrillRun.grade(run.task.display, run.task, config(reverse = true)))
        assertNotEquals(Match.Exact, DateDrillRun.grade("the third of June", run.task, config(reverse = true)))
    }

    /** A reversed run says a name it is answered with, never the digits a date is owed in. */
    @Test
    fun aReversedRunSaysItsNamesButNotItsDigits() {
        fun PairDrillRunState<DateDrillTask>.saidAnswer() =
            reduce(PairDrillIntent.Submit(task.display)).state.reading(saysMeaning = true).answer
        var run = open(reverse = true)
        assertEquals(false, run.task.digits)
        assertEquals(Saying(run.task.display, "en"), run.saidAnswer())
        run = open(reverse = true, sprosse = 3)
        repeat(30) {
            if (run.task.digits) return@repeat
            run = run.answered(run.task.display)
        }
        assertTrue(run.task.digits, "the run never reached a date to parse")
        assertNull(run.saidAnswer())
    }

    // MARK: - Answering out

    /**
     * The name Sprossen enumerate and can be answered out; an assembled Sprosse is drawn,
     * so however long a run stands on it, it is never cleared. The Sprossen nest, so the
     * months alone clear nothing while weekdays and months together clear two.
     */
    @Test
    fun onlyTheNameSprossenCanBeAnsweredOut() {
        val content = DateDrillFixture.germanContent
        fun keys(kind: DateTaskKind) = DateDrillTasks.pool(content, kind, false).map { DrillSolved.key(it) }
        val weekdays = keys(DateTaskKind.Weekday).toSet()
        val months = keys(DateTaskKind.Month).toSet()
        val tiles = DateDrillChoices.pool(content, false).map { DrillSolved.key(it) }.toSet()

        assertEquals(emptySet(), DateDrill.cleared(content, false, months))
        assertEquals(setOf(2, 3), DateDrill.cleared(content, false, weekdays + months))
        assertEquals(setOf(1, 2, 3), DateDrill.cleared(content, false, weekdays + months + tiles))

        var run = open(sprosse = 4)
        repeat(40) { run = run.answered(run.task.display) }
        val closed = DateDrillRun.close(run, standingRecord = 0)
        assertTrue(closed.clearedSprossen.none { it >= 4 }, "an assembled Sprosse was cleared")
    }
}

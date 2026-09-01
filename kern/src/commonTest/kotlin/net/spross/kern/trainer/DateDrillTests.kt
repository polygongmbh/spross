package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ladder and the task shapes, on [DateDrillFixture]'s two hand-built pairs: the full
 * seven-rung en→de ladder and the year-less de→uk one. The run that steps through them is
 * [DateDrillRunTest]'s.
 */
class DateDrillTests {

    private val german = DateDrillFixture.germanContent
    private val ukrainian = DateDrillFixture.ukrainianContent

    private val bareKinds = listOf(DateTaskKind.Weekday, DateTaskKind.Month)
    private val fullLadder = DateTaskKind.entries.toList()

    // MARK: - The ladder

    /** The year rung exists only where the answer side reads a year; reverse keeps the names. */
    @Test
    fun theLadderIsAsTallAsTheContentCanCarry() {
        assertEquals(7, DateDrill.maxLevel(german, reverse = false))
        assertEquals(6, DateDrill.maxLevel(ukrainian, reverse = false))
        assertEquals(3, DateDrill.maxLevel(german, reverse = true))
        assertEquals(3, DateDrill.maxLevel(ukrainian, reverse = true))
    }

    /**
     * Each rung asks ONLY what it introduces — three weekday wins must never carry the run
     * past a rung whose own question it never met — and the top rung mixes everything below.
     */
    @Test
    fun eachRungAsksItsOwnKindAndTheTopMixes() {
        for ((index, kind) in fullLadder.withIndex()) {
            assertEquals(listOf(kind), DateDrill.kinds(german, index + 1, reverse = false))
        }
        assertEquals(fullLadder, DateDrill.kinds(german, 7, reverse = false))
        assertEquals(fullLadder.dropLast(1), DateDrill.kinds(ukrainian, 6, reverse = false))
        assertEquals(listOf(DateTaskKind.Weekday), DateDrill.kinds(german, 1, reverse = true))
        assertEquals(listOf(DateTaskKind.Month), DateDrill.kinds(german, 2, reverse = true))
        assertEquals(bareKinds, DateDrill.kinds(german, 3, reverse = true))
        assertEquals(fullLadder, DateDrill.kinds(german, 99, reverse = false), "clamped to the top")
        assertEquals(listOf(DateTaskKind.Weekday), DateDrill.kinds(german, 0, reverse = false))
    }

    @Test
    fun fastIsOfferedOnlyOnceThisLaddersTopRungHasBeenReached() {
        assertFalse(DateDrill.fastUnlocked(6, german, reverse = false))
        assertTrue(DateDrill.fastUnlocked(7, german, reverse = false))
        assertTrue(DateDrill.fastUnlocked(6, ukrainian, reverse = false), "the short ladder tops at 6")
        assertTrue(DateDrill.fastUnlocked(3, german, reverse = true))
    }

    /** Three clean wins a rung, or one where fast was earned — on this pair's own ceiling. */
    @Test
    fun theRampMovesOnThisPairsOwnCeiling() {
        assertEquals(3, DateDrill.winsToAdvance(fast = false))
        assertEquals(1, DateDrill.winsToAdvance(fast = true))
        var step = DrillRamp.RungStep(1, 0)
        repeat(3) {
            step = DateDrill.step(german, false, step.level, step.winsAtLevel,
                correct = true, clean = true, fast = false)
        }
        assertEquals(2, step.level)
        assertEquals(2, DateDrill.step(german, false, 1, 0, correct = true, clean = true, fast = true).level)
        val top = DateDrill.step(ukrainian, false, 6, 2, correct = true, clean = true, fast = false)
        assertEquals(6, top.level, "the short ladder's ramp stops at its own top")
    }

    // MARK: - Task shapes

    @Test
    fun aBareNameTaskCarriesEveryTaughtAndAcceptedForm() {
        val saturday = DateDrillTasks.pool(german, DateTaskKind.Weekday, reverse = false)[5]
        assertEquals("Saturday", saturday.promptText)
        assertEquals(listOf("Samstag", "Sonnabend"), saturday.accepted)
        assertEquals("Samstag", saturday.display)
        assertEquals("5", saturday.id)
        assertEquals(12, DateDrillTasks.pool(german, DateTaskKind.Month, reverse = false).size)
    }

    /** Reverse is a direction, not another ladder: the same names, asked the other way. */
    @Test
    fun reverseSwapsThePromptAndTheAcceptedSide() {
        val back = DateDrillTasks.pool(german, DateTaskKind.Weekday, reverse = true)[5]
        assertEquals("Samstag", back.promptText)
        assertEquals(listOf("Saturday"), back.accepted)
        assertEquals("Saturday", back.display)
    }

    /** The day is the pack's reading, prompted language-neutrally — never authored, never digits. */
    @Test
    fun theDayRungReadsThePacksOrdinal() {
        val task = DateDrillTasks.day(german, 3)
        assertEquals("3.", task.promptText)
        assertEquals("dritte", task.display)
        assertContains(task.accepted, "dritten")
        assertEquals(31, DateDrillTasks.pool(german, DateTaskKind.DayOfMonth, reverse = false).size)
    }

    /** Pattern variants cross-multiply with the day's readings — the accusative rides along. */
    @Test
    fun anAssembledDayAndMonthCrossMultipliesItsParts() {
        val task = DateDrillTasks.dayMonth(german, 3, 5)
        assertEquals("der dritte Juni", task.display)
        assertContains(task.accepted, "den dritten Juni")
        assertEquals("6/3", task.promptText, "the prompt wears the SOURCE's numeric format")
        assertEquals("3.6", task.id)
        assertContains(DateDrillTasks.dayMonth(german, 3, 0).accepted, "der dritte Jänner")
    }

    /** Ukrainian's `dateForm` replaces the citation form inside a date — and only it grades. */
    @Test
    fun aDateFormDeclinesTheMonthInsideADate() {
        val task = DateDrillTasks.dayMonth(ukrainian, 3, 2)
        assertEquals(listOf("третього березня"), task.accepted)
        assertEquals("3.3.", task.promptText, "the de ordinal dot survives the year's removal")
    }

    @Test
    fun aFullDateAsksWithTheSourceAbbreviationAndAcceptsTheSynonymWeekday() {
        val task = DateDrillTasks.fullDate(german, 5, 3, 5)
        assertEquals("Sat, 6/3", task.promptText)
        assertEquals("Samstag, der dritte Juni", task.display)
        assertContains(task.accepted, "Sonnabend, der dritte Juni")
        assertContains(task.accepted, "Samstag, den dritten Juni")
        assertEquals("5:3.6", task.id)
    }

    /** Once a year fixes the date the weekday is a fact: computed, never drawn. */
    @Test
    fun aDatedCardComputesItsWeekday() {
        assertEquals(1, DateDrillTasks.weekdayIndex(2026, 3, 3), "2026-03-03 is a Tuesday")
        assertEquals(3, DateDrillTasks.weekdayIndex(2024, 2, 29), "the leap day exists and is a Thursday")
        assertEquals(5, DateDrillTasks.weekdayIndex(2000, 1, 1), "the century leap rule holds")
        val task = DateDrillTasks.fullDateWithYear(german, 3, 2, 2026)
        val year = Trainer.pack("de").year(2026)
        assertEquals("Dienstag, der dritte März ${year.display}", task.display)
        assertEquals("Tue, 3/3/2026", task.promptText)
        assertEquals("3.3.2026", task.id)
        assertContains(task.accepted, "Dienstag, den dritten März ${year.display}")
    }

    // MARK: - Sampling

    @Test
    fun theSameSeedDrawsTheSameRun() {
        fun run(): List<String> {
            val rng = Random(7)
            return (1..20).map {
                assertNotNull(DateDrill.sample(german, 4, false, null, emptySet(), rng)).id
            }
        }
        assertEquals(run(), run())
        assertTrue(run().toSet().size > 1, "the run asked one question twenty times")
    }

    /** One resample, not a loop: the repeat needs two unlucky draws to survive. */
    @Test
    fun theLastAnswerIsResampledOnce() {
        fun hits(avoid: String?) = (1..400).count {
            DateDrill.sample(german, 1, false, avoid, emptySet(), Random(it.toLong()))?.id == "0"
        }
        assertTrue(hits("0") < hits(null), "avoidId bought nothing")
    }

    @Test
    fun aSolvedQuestionIsNeverAskedAgain() {
        val solved = (0..5).map { "${DateTaskKind.Weekday}:$it" }.toSet()
        repeat(20) {
            val task = assertNotNull(DateDrill.sample(german, 1, false, null, solved, Random(it.toLong())))
            assertEquals("6", task.id)
        }
    }

    /** A spent rung is climbed past, and the rung above is booked like any other. */
    @Test
    fun aSpentRungIsClimbedPast() {
        val weekdaysOut = (0..6).map { "${DateTaskKind.Weekday}:$it" }.toSet()
        val draw = DateDrill.draw(german, 1, false, null, weekdaysOut, Random(7))
        assertEquals(2, draw.level)
        assertEquals(DateTaskKind.Month, assertNotNull(draw.task).kind)

        val daysOut = (1..31).map { "${DateTaskKind.DayOfMonth}:$it" }.toSet()
        val past = DateDrill.draw(german, 3, false, null, daysOut, Random(7))
        assertEquals(4, past.level)
        assertEquals(DateTaskKind.DayAndMonth, assertNotNull(past.task).kind)
    }

    /** A generated rung is spent when a whole run of draws lands on solved questions. */
    @Test
    fun aSpentGeneratedRungIsClimbedPast() {
        val oneMonth = german.copy(months = german.months.take(1))
        val solved = (1..31).map { "${DateTaskKind.DayAndMonth}:$it.1" }.toSet()
        val draw = DateDrill.draw(oneMonth, 4, false, null, solved, Random(7))
        assertEquals(5, draw.level)
        assertEquals(DateTaskKind.FullDate, assertNotNull(draw.task).kind)
    }

    /** The whole ladder answered out is the null draw that ends a run on its summary. */
    @Test
    fun aLadderAnsweredOutDrawsNothing() {
        val everything = ((0..6).map { "${DateTaskKind.Weekday}:$it" } +
            (0..11).map { "${DateTaskKind.Month}:$it" }).toSet()
        val draw = DateDrill.draw(german, 1, true, null, everything, Random(7))
        assertNull(draw.task)
    }

    @Test
    fun theTopRungMixesTheKindsBelowIt() {
        val kinds = (1..60).mapNotNull {
            DateDrill.sample(german, 7, false, null, emptySet(), Random(it.toLong()))?.kind
        }.toSet()
        assertTrue(kinds.size >= 4, "the mixed rung kept asking one kind: $kinds")
    }

    // MARK: - Reference

    @Test
    fun theReferenceTableShowsBothSidesAndTheDateOnlyForms() {
        val groups = DateDrill.reference(ukrainian)
        assertEquals(listOf(DateTaskKind.Weekday, DateTaskKind.Month), groups.map { it.kind })
        val march = groups[1].rows[2]
        assertEquals("März", march.source)
        assertEquals("березень", march.target)
        assertEquals("березня", march.dateForm)
        val saturday = DateDrill.reference(german)[0].rows[5]
        assertEquals(listOf("Sonnabend"), saturday.synonyms)
        assertEquals("Sa", saturday.abbr)
    }

    @Test
    fun theDirectionSettlesTheLanguages() {
        assertEquals("de", DateDrill.answerLanguage(german, reverse = false))
        assertEquals("en", DateDrill.answerLanguage(german, reverse = true))
        assertEquals("en", DateDrill.promptLanguage(german, reverse = false))
        assertEquals("de", DateDrill.promptLanguage(german, reverse = true))
    }
}

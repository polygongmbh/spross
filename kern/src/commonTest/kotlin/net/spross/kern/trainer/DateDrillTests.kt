package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ladder and the task shapes, on [DateDrillFixture]'s two hand-built pairs: the full
 * six-Sprosse en→de ladder and the year-less de→uk one. The run that steps through them is
 * [DateDrillRunTest]'s.
 */
class DateDrillTests {

    private val german = DateDrillFixture.germanContent
    private val ukrainian = DateDrillFixture.ukrainianContent
    private val english = DateDrillFixture.englishContent

    private val bareKinds = listOf(DateTaskKind.Weekday, DateTaskKind.Month)
    private val fullLadder = DateTaskKind.entries.toList()

    /** The ladder above the warm-up: everything a Sprosse still carries once it is left behind. */
    private val written = fullLadder.drop(1)

    // MARK: - The ladder

    /** The year Sprosse exists only where the answer side reads a year; reverse keeps the names. */
    @Test
    fun theLadderIsAsTallAsTheContentCanCarry() {
        assertEquals(7, DateDrill.maxLevel(german, reverse = false))
        assertEquals(6, DateDrill.maxLevel(ukrainian, reverse = false))
        assertEquals(3, DateDrill.maxLevel(german, reverse = true))
        assertEquals(3, DateDrill.maxLevel(ukrainian, reverse = true))
    }

    /**
     * A Sprosse carries what it introduces and everything below it; the top one is all of it.
     * All but the warm-up, which every Sprosse above it leaves behind.
     */
    @Test
    fun eachSprosseAddsItsKindToTheOnesBelow() {
        val warmUp = listOf(DateTaskKind.NameChoice)
        assertEquals(warmUp, DateDrill.kinds(german, 1, reverse = false))
        for ((index, kind) in written.withIndex()) {
            val kinds = DateDrill.kinds(german, index + 2, reverse = false)
            assertEquals(written.take(index + 1), kinds, "a written Sprosse carries no tiles")
            assertEquals(kind, kinds.last(), "the Sprosse introduces its own kind")
        }
        assertEquals(written.dropLast(1), DateDrill.kinds(ukrainian, 6, reverse = false))
        assertEquals(warmUp, DateDrill.kinds(german, 1, reverse = true))
        assertEquals(bareKinds, DateDrill.kinds(german, 3, reverse = true))
        assertEquals(written, DateDrill.kinds(german, 99, reverse = false), "clamped to the top")
        assertEquals(warmUp, DateDrill.kinds(german, 0, reverse = false))
    }

    @Test
    fun fastIsOfferedOnlyOnceThisLaddersTopSprosseHasBeenReached() {
        assertFalse(DateDrill.fastUnlocked(6, german, reverse = false))
        assertTrue(DateDrill.fastUnlocked(7, german, reverse = false))
        assertTrue(DateDrill.fastUnlocked(6, ukrainian, reverse = false), "the short ladder tops at 6")
        assertTrue(DateDrill.fastUnlocked(3, german, reverse = true))
    }

    /** Three clean wins a Sprosse, or one where fast was earned — on this pair's own ceiling. */
    @Test
    fun theRampMovesOnThisPairsOwnCeiling() {
        assertEquals(3, DateDrill.winsToAdvance(fast = false))
        assertEquals(1, DateDrill.winsToAdvance(fast = true))
        var step = DrillRamp.SprosseStep(1, 0)
        repeat(3) {
            step = DateDrill.step(german, false, step.level, step.winsAtLevel,
                correct = true, clean = true, fast = false)
        }
        assertEquals(2, step.level)
        assertEquals(2, DateDrill.step(german, false, 1, 0, correct = true, clean = true, fast = true).level)
        val top = DateDrill.step(ukrainian, false, 6, 2, correct = true, clean = true, fast = false)
        assertEquals(7, top.level, "the number climbs past the short ladder's last named Sprosse")
        assertEquals(
            DateDrill.kinds(ukrainian, 6, reverse = false),
            DateDrill.kinds(ukrainian, top.level, reverse = false),
            "and asks the top Sprosse's questions up there",
        )
    }

    // MARK: - Task shapes

    @Test
    fun aBareNameTaskCarriesEveryTaughtAndAcceptedForm() {
        val saturday = DateDrillTasks.pool(german, DateTaskKind.Weekday, reverse = false)[5]
        assertEquals("Saturday", saturday.promptText)
        assertEquals(listOf("Samstag", "Sonnabend"), saturday.accepted)
        assertEquals("Samstag", saturday.display)
        assertEquals("5", saturday.id)
        assertNull(saturday.choices, "a written Sprosse offers nothing to tap")
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
    fun theDaySprosseReadsThePacksOrdinal() {
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

    /**
     * A language that genuinely says its date two ways teaches both rather than teaching
     * one and merely tolerating the other: the taught assemblies turn with the day, while
     * the accept-only one grades and never reaches the reveal.
     */
    @Test
    fun aPatternSynonymTakesItsTurnOnTheReveal() {
        val odd = DateDrillTasks.dayMonth(english, 1, 6)
        val even = DateDrillTasks.dayMonth(english, 2, 6)
        assertEquals("July first", odd.display)
        assertEquals("the second of July", even.display)
        assertContains(odd.accepted, "the first of July")
        assertContains(odd.accepted, "first of July")
        assertFalse(
            DateDrillTasks.dayMonth(english, 3, 6).display.startsWith("third"),
            "an accept-only assembly never leads the reveal",
        )
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
                assertNotNull(DateDrill.sample(german, 5, false, null, emptySet(), rng)).id
            }
        }
        assertEquals(run(), run())
        assertTrue(run().toSet().size > 1, "the run asked one question twenty times")
    }

    /** One resample, not a loop: the repeat needs two unlucky draws to survive. */
    @Test
    fun theLastAnswerIsResampledOnce() {
        fun hits(avoid: String?) = (1..400).count {
            DateDrill.sample(german, 2, false, avoid, emptySet(), Random(it.toLong()))?.id == "0"
        }
        assertTrue(hits("${DateTaskKind.Weekday}:0") < hits(null), "avoid bought nothing")
    }

    @Test
    fun aSolvedQuestionIsNeverAskedAgain() {
        val solved = (0..5).map { "${DateTaskKind.Weekday}:$it" }.toSet()
        repeat(20) {
            val task = assertNotNull(DateDrill.sample(german, 2, false, null, solved, Random(it.toLong())))
            assertEquals("6", task.id)
        }
    }

    /** A Sprosse with something left keeps it: the day Sprosse answered out still has its names. */
    @Test
    fun aSprosseKeepsWhatTheSprossenBelowItStillHold() {
        val daysOut = (1..31).map { "${DateTaskKind.DayOfMonth}:$it" }.toSet()
        val draw = DateDrill.draw(german, 4, false, null, daysOut, Random(7))
        assertEquals(4, draw.level)
        assertNotEquals(DateTaskKind.DayOfMonth, assertNotNull(draw.task).kind)
    }

    /** A Sprosse with NOTHING left is climbed past, and the Sprosse above is booked like any other. */
    @Test
    fun aSpentSprosseIsClimbedPast() {
        val namesOut = ((0..6).map { "${DateTaskKind.Weekday}:$it" } +
            (0..11).map { "${DateTaskKind.Month}:$it" }).toSet()
        val draw = DateDrill.draw(german, 3, false, null, namesOut, Random(7))
        assertEquals(4, draw.level)
        assertEquals(DateTaskKind.DayOfMonth, assertNotNull(draw.task).kind)
    }

    /** A generated Sprosse is spent when a whole run of draws lands on solved questions. */
    @Test
    fun aSpentGeneratedSprosseIsClimbedPast() {
        val oneMonth = german.copy(months = german.months.take(1))
        val below = (0..6).map { "${DateTaskKind.Weekday}:$it" } +
            listOf("${DateTaskKind.Month}:0") +
            (1..31).map { "${DateTaskKind.DayOfMonth}:$it" }
        val solved = (below + (1..31).map { "${DateTaskKind.DayAndMonth}:$it.1" }).toSet()
        val draw = DateDrill.draw(oneMonth, 5, false, null, solved, Random(7))
        assertEquals(6, draw.level)
        assertEquals(DateTaskKind.FullDate, assertNotNull(draw.task).kind)
    }

    /** The whole ladder answered out is the null draw that ends a run on its summary. */
    @Test
    fun aLadderAnsweredOutDrawsNothing() {
        val everything = ((0..6).map { "${DateTaskKind.Weekday}:$it" } +
            (0..11).map { "${DateTaskKind.Month}:$it" } +
            (0..6).map { "${DateTaskKind.NameChoice}:w$it" } +
            (0..11).map { "${DateTaskKind.NameChoice}:m$it" }).toSet()
        val draw = DateDrill.draw(german, 1, true, null, everything, Random(7))
        assertNull(draw.task)
    }

    /** Every Sprosse mixes what it carries — and leads with what it introduced. */
    @Test
    fun aSprosseMixesTheKindsBelowItAndFavorsItsOwn() {
        val drawn = (1..200).mapNotNull {
            DateDrill.sample(german, 7, false, null, emptySet(), Random(it.toLong()))?.kind
        }
        assertEquals(written.toSet(), drawn.toSet(), "the Sprosse stopped asking a kind it carries")
        val newest = drawn.count { it == DateTaskKind.FullDateWithYear }
        assertTrue(newest > drawn.size / 4, "the Sprosse buried its own question: $newest of ${drawn.size}")
    }

    // MARK: - The warm-up

    /** Four tiles, the answer among them, and the three others out of its own half. */
    @Test
    fun theWarmUpOffersFourTilesFromTheAnswersOwnGroup() {
        val weekdays = german.weekdays.map { it.target.text }.toSet()
        val months = german.months.map { it.target.text }.toSet()
        val drawn = (1..40).map {
            assertNotNull(DateDrill.sample(german, 1, false, null, emptySet(), Random(it.toLong())))
        }
        for (task in drawn) {
            assertEquals(DateTaskKind.NameChoice, task.kind)
            val tiles = assertNotNull(task.choices)
            assertEquals(DateDrillChoices.COUNT, tiles.size)
            assertEquals(tiles.size, tiles.toSet().size, "a name stood on two tiles")
            assertContains(tiles, task.display)
            val group = if (task.display in weekdays) weekdays else months
            assertTrue(tiles.all { it in group }, "company from the other half: $tiles")
        }
        assertTrue(
            drawn.any { it.display in weekdays } && drawn.any { it.display in months },
            "the warm-up asks the whole calendar, not one half of it",
        )
    }

    /** Tapping a name and writing it are two questions, so neither answers the other out. */
    @Test
    fun theWarmUpKeepsSolvedKeysOfItsOwn() {
        val tapped = assertNotNull(DateDrill.sample(german, 1, false, null, emptySet(), Random(7)))
        assertTrue(DrillSolved.key(tapped).startsWith("${DateTaskKind.NameChoice}:"))
        val writtenOut = ((0..6).map { "${DateTaskKind.Weekday}:$it" } +
            (0..11).map { "${DateTaskKind.Month}:$it" }).toSet()
        assertNotNull(
            DateDrill.sample(german, 1, false, null, writtenOut, Random(7)),
            "the written names answered out took the tiles with them",
        )
    }

    /** Reversed, the tiles are the learner's own names — the direction reaches them too. */
    @Test
    fun aReversedWarmUpOffersTheLearnersOwnNames() {
        val own = (german.weekdays + german.months).map { it.source.text }.toSet()
        val task = assertNotNull(DateDrill.sample(german, 1, true, null, emptySet(), Random(3)))
        assertTrue(assertNotNull(task.choices).all { it in own })
        assertContains(task.accepted, task.display)
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

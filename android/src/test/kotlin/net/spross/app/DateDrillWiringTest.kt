package net.spross.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.catalog.DateNames
import net.spross.kern.catalog.DatePattern
import net.spross.kern.catalog.DatePatterns
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.DateDrillRun
import net.spross.kern.trainer.DateDrillRunConfig

/**
 * What the APP does with kern's dates run — [DrillWiringTest]'s fourth section, in a file
 * of its own because that one stands at the size cap. Its boundary holds here too: the
 * rules themselves (the ramp, the draw, the verdict ladder, when the way out is offered)
 * belong to `:kern:jvmTest`; nothing here re-tests them.
 */
class DateDrillWiringTest {

    /** The platform half, recorded: everything a flow hands outside the run. */
    private class Platform {
        val tones = mutableListOf<ToneKind>()
        var focusReleases = 0
        var silences = 0
        var screenReader = false
    }

    private fun names(vararg pairs: Pair<String, String>): List<DateNames> =
        pairs.map { (text, abbr) -> DateNames(text, abbr = abbr) }

    private val enWeekdays = names(
        "Monday" to "Mon", "Tuesday" to "Tue", "Wednesday" to "Wed", "Thursday" to "Thu",
        "Friday" to "Fri", "Saturday" to "Sat", "Sunday" to "Sun",
    )

    private val deWeekdays = names(
        "Montag" to "Mo", "Dienstag" to "Di", "Mittwoch" to "Mi", "Donnerstag" to "Do",
        "Freitag" to "Fr", "Samstag" to "Sa", "Sonntag" to "So",
    )

    private val enMonths = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ).map { DateNames(it) }

    private val deMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    ).map { DateNames(it) }

    private val calendars = DateDrillContent(
        source = "en",
        target = "de",
        weekdays = enWeekdays.zip(deWeekdays).mapIndexed { i, (s, t) -> DateEntry(i, s, t) },
        months = enMonths.zip(deMonths).mapIndexed { i, (s, t) -> DateEntry(i, s, t) },
        numeric = "{m}/{d}/{y}",
        patterns = DatePatterns(
            dayMonth = DatePattern("der {day} {month}"),
            date = DatePattern("{weekday}, der {day} {month}"),
            dateWithYear = DatePattern("{weekday}, der {day} {month} {year}"),
        ),
    )

    private fun dates(platform: Platform, reverse: Boolean = false, seed: Int = 5) =
        DateDrillFlow(
            start = DateDrillRun.open(
                DateDrillRunConfig(
                    content = calendars,
                    reverse = reverse,
                    fast = false,
                    // A run with no language info grades plainly — enough to drive the wiring.
                    normalizer = null,
                ),
                Random(seed),
            ),
            rng = Random(seed),
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )

    /**
     * Writing the reading out IS the answer — the review loop's rule, which the atlas run
     * carries and this one carries the same way.
     */
    @Test
    fun finishingTheReadingArmsTheLiveBeatWithoutACheckTap() {
        val platform = Platform()
        val flow = dates(platform)
        flow.type(flow.state.task.display)
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceTier.Live, flow.armedBeat)
    }

    /** Typing PAST a finished reading takes the green with it, so it is never booked. */
    @Test
    fun backingOutOfAFinishedReadingDropsTheBeat() {
        val flow = dates(Platform())
        flow.type(flow.state.task.display)
        flow.type(flow.state.task.display + "x")
        assertEquals(TurnFeedback.Neutral, flow.state.feedback)
        assertNull(flow.armedBeat)
    }

    /** The way out belongs to the SECOND miss in a row, not to the first. */
    @Test
    fun theWayOutIsOfferedOnTheSecondMissInARow() {
        val flow = dates(Platform())
        flow.primary()
        assertEquals(TurnFeedback.Revealed, flow.state.feedback)
        assertTrue(!flow.state.offersFinish, "one miss is not yet a run worth leaving")
        flow.confirm()
        flow.primary()
        assertTrue(flow.state.offersFinish)
    }

    @Test
    fun aClosedDatesRunReportsItsFiguresAndTheRungItReached() {
        val untouched = dates(Platform()).close(standingRecord = 0)
        assertNull(untouched.summary)

        val flow = dates(Platform())
        flow.type(flow.state.task.display)
        val closed = flow.close(standingRecord = 0)
        val summary = assertNotNull(closed.summary)
        // The pending clean answer books on the way out, exactly as the tap would.
        assertEquals(1, summary.done)
        assertTrue(summary.newRecord, "a first streak beats a standing record of none")
        assertEquals(flow.state.bestLevel, closed.bestLevel)
    }
}

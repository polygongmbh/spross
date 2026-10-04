package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.spross.kern.trainer.DrillSuggestion.BoxFacts
import net.spross.kern.trainer.DrillSuggestion.Ladder
import net.spross.kern.trainer.DrillSuggestion.Reason
import net.spross.kern.trainer.DrillSuggestion.Standing

/** Which drill Home names, and how long the name holds. */
class DrillSuggestionTests {

    private val tz = "Europe/Berlin"

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(2026, 9, day, hour, minute).toInstant(TimeZone.of(tz)).toEpochMilliseconds()

    private val now = at(20, 19)
    private val young = BoxFacts(settledWords = 0, newScript = false)

    private fun standing(drill: Drill, ranOn: Int? = null, ladder: Ladder? = Ladder(5, 9)) =
        Standing(drill, ranOn?.let { at(it, 10) }, ladder)

    private fun suggest(standings: List<Standing>, facts: BoxFacts = young, time: Long = now) =
        DrillSuggestion.suggest(standings, facts, time, tz, "uk")

    // MARK: - Which drill

    @Test
    fun aMasteredLadderDropsOutHoweverLongAgoItRan() {
        val pick = suggest(
            listOf(
                standing(Drill.Countries, ranOn = null, ladder = Ladder(0, 9)),
                standing(Drill.Dates, ranOn = 20),
            ),
        )
        assertEquals(Drill.Dates, pick?.drill)
        assertNull(suggest(listOf(standing(Drill.Countries, ladder = Ladder.cleared((1..9).toSet(), 9)))))
    }

    @Test
    fun aDrillNeverRunLeadsDrillsRunLatelyInEverySlotOfTheDay() {
        val standings = listOf(
            standing(Drill.Countries, ranOn = 20),
            standing(Drill.Dates, ranOn = null),
            standing(Drill.Letters, ranOn = 19),
        )
        val grown = BoxFacts(settledWords = 400, newScript = false)
        for (hour in 0..23) {
            val pick = suggest(standings, grown, at(20, hour))
            assertEquals(Drill.Dates, pick?.drill, "at $hour:00")
            assertEquals(Reason.NeverRun, pick?.reason)
        }
    }

    @Test
    fun aNewScriptLeadsWithLettersWhileTheBoxIsYoung() {
        val all = Drill.entries.map { standing(it) }
        assertEquals(
            DrillSuggestion.Pick(Drill.Letters, Reason.NewScript, -1),
            suggest(all, BoxFacts(settledWords = 0, newScript = true)),
        )
        val pick = suggest(all, BoxFacts(settledWords = 0, newScript = false))
        assertEquals(Drill.Numbers, pick?.drill)
        assertEquals(Reason.EarlyNumbers, pick?.reason)
    }

    @Test
    fun settledWordsSteerTowardTheWordScramble() {
        val threeDaysAgo = Drill.entries.map { standing(it, ranOn = 17) }
        val pick = suggest(threeDaysAgo, BoxFacts(settledWords = 150, newScript = true))
        assertEquals(Drill.WordScramble, pick?.drill)
        assertEquals(Reason.WordsSettled, pick?.reason)
    }

    @Test
    fun withNothingSteeringTheReasonIsHowLongAgoItRan() {
        val grown = BoxFacts(settledWords = 400, newScript = false)
        val pick = suggest(listOf(standing(Drill.Countries, ranOn = 15), standing(Drill.Dates, ranOn = 20)), grown)
        assertEquals(DrillSuggestion.Pick(Drill.Countries, Reason.NotLately, 5), pick)
        val today = suggest(listOf(standing(Drill.Countries, ranOn = 20)), grown)
        assertEquals(Reason.Variety, today?.reason)
    }

    @Test
    fun onlyTheOfferedDrillsAreCandidates() {
        assertNull(suggest(emptyList()))
    }

    // MARK: - How long it holds

    @Test
    fun thePickHoldsThroughASlotAndTurnsOverAcrossDays() {
        val even = listOf(standing(Drill.Countries, ranOn = 1), standing(Drill.Dates, ranOn = 1))
        val grown = BoxFacts(settledWords = 400, newScript = false)
        assertEquals(suggest(even, grown, at(20, 19)), suggest(even, grown, at(20, 19, 45)))
        val picks = (20..27).flatMap { day -> listOf(9, 14, 19).map { suggest(even, grown, at(day, it))?.drill } }
        assertEquals(setOf(Drill.Countries, Drill.Dates), picks.toSet())
    }

    // MARK: - The ladders it reads

    @Test
    fun numbersAreMasteredOnceEveryExerciseStoodAboveItsTop() {
        val tops = listOf(NumbersExercise.Counting, NumbersExercise.Clock, NumbersExercise.Forms)
            .associateWith { Numbers.maxSprosse(it.reading!!) }
        assertFalse(Ladder.numbers(tops, "de").mastered)
        assertTrue(Ladder.numbers(tops.mapValues { it.value + 1 }, "de").mastered)
        assertFalse(Ladder.numbers(emptyMap(), "de").mastered)
    }
}

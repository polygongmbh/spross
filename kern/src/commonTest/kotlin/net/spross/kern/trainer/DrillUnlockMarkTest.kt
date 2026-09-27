package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/** An unlock is marked once, and only where a padlock was there to lose. */
class DrillUnlockMarkTest {

    private val clock = DrillUnlockMark.row(NumbersExercise.Clock)
    private val counting = DrillUnlockMark.row(NumbersExercise.Counting)
    private val fast = DrillUnlockMark.row(DrillModifier.Fast)

    @Test
    fun aRowLastShownPadlockedAndOpenNowIsMarked() {
        assertEquals(setOf(clock), DrillUnlockMark.marked(setOf(clock, fast), setOf(counting, clock)))
    }

    @Test
    fun aRowNeverShownPadlockedIsNotMarked() {
        // A first visit has no record; a row open from the start never had a padlock.
        assertEquals(emptySet(), DrillUnlockMark.marked(emptySet(), setOf(counting, clock)))
        assertEquals(emptySet(), DrillUnlockMark.marked(setOf(fast), setOf(counting)))
    }

    @Test
    fun rowsOfDifferentKindsNeverShareAName() {
        val names = NumbersExercise.entries.map(DrillUnlockMark::row) +
            DrillModifier.entries.map(DrillUnlockMark::row) +
            LetterStage.entries.map(DrillUnlockMark::row)
        assertEquals(names.size, names.toSet().size)
    }
}

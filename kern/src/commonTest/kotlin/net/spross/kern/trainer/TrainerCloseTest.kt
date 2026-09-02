package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.TurnFeedback

/**
 * What a closed run leaves behind — the two store writes and the figures the page that started
 * it wears as one tile. The tier ladder and the clean/answered counter are shared vocabulary
 * ([DrillRunSummary]), so the letter run reads them back the same way.
 */
class TrainerCloseTest {

    private fun numbers(language: String = "de") = TrainerMode(DrillVariant.Numbers, language)

    @Test
    fun anUntouchedRunStoresNothing() {
        val closed = TrainerRun.close(TrainerRun.open(numbers(), Random(41)), 5, emptyMap())
        assertNull(closed.summary)
        assertEquals(emptyMap<String, Int>(), closed.progressBookings)
        assertTrue(DrillEffect.Silence in closed.effects)
    }

    @Test
    fun closingBooksAPendingAnswerAndNeverUpgradesIt() {
        val rng = Random(43)
        val state = TrainerRun.open(numbers(), rng)
        val almost = TrainerRun.close(
            state.copy(feedback = TurnFeedback.Almost("sieben", AlmostReason.Typo)),
            0,
            emptyMap(),
        )
        assertEquals(listOf(AnswerOutcome.Almost), almost.state.outcomes)
        assertEquals(1, almost.summary?.done)
        assertEquals(1, almost.state.currentLevel, "almost moves the Sprosse neither way")

        // A hint-assisted clean answer is almost too, exactly as the explicit tap would book it.
        val hinted = TrainerRun.close(
            state.copy(feedback = TurnFeedback.Correct, hintUsed = true),
            0,
            emptyMap(),
        )
        assertEquals(listOf(AnswerOutcome.Almost), hinted.state.outcomes)

        // A revealed answer nobody confirmed is not accepted, so closing books nothing.
        val revealed = TrainerRun.close(state.copy(feedback = TurnFeedback.Revealed), 0, emptyMap())
        assertNull(revealed.summary)
    }

    @Test
    fun theRecordFallsOnlyToAStrictlyLongerStreak() {
        val played = TrainerRun.open(numbers(), Random(47)).copy(core = DrillRunCore(done = 6, bestStreak = 8))
        assertTrue(TrainerRun.close(played, 7, emptyMap()).summary!!.newRecord)
        assertFalse(TrainerRun.close(played, 8, emptyMap()).summary!!.newRecord)
        // Re-closing a resumed summary can never double-claim.
        assertFalse(TrainerRun.close(played, 9, emptyMap()).summary!!.newRecord)
    }

    /**
     * Every variant the run ASKED books its high-water — not only the one it ended on, and never
     * one it never drew, because an unasked Sprosse was never stood on.
     */
    @Test
    fun everyAskedVariantBooksTheHighestSprosseItStoodOn() {
        val mode = TrainerMode(listOf(DrillVariant.Numbers, DrillVariant.Clock), "sw", emptySet())
        val played = TrainerRun.open(mode, Random(53)).copy(
            core = DrillRunCore(done = 9, bestStreak = 4),
            // The Sprosse fell back to 3, but the ladder rewards reaching 5.
            levels = mapOf(DrillVariant.Numbers to 3, DrillVariant.Clock to 1),
            bestLevels = mapOf(DrillVariant.Numbers to 5),
        )
        assertEquals(
            mapOf("Numbers.sw" to 5),
            TrainerRun.close(played, 0, mapOf("Numbers.sw" to 3)).progressBookings,
        )
        // A Sprosse already earned is not fresh progress.
        assertEquals(
            emptyMap<String, Int>(),
            TrainerRun.close(played, 0, mapOf("Numbers.sw" to 5)).progressBookings,
        )
    }

    // MARK: - What the page behind the run reads

    @Test
    fun theTierLadderTurnsOnTwoFiveAndTen() {
        fun tier(streak: Int) = DrillRunSummary(done = 1, bestStreak = streak, newRecord = false).tier
        assertEquals(StreakTier.Sprout, tier(0))
        assertEquals(StreakTier.Sprout, tier(1))
        assertEquals(StreakTier.Effort, tier(2))
        assertEquals(StreakTier.Effort, tier(4))
        assertEquals(StreakTier.Cheer, tier(5))
        assertEquals(StreakTier.Cheer, tier(9))
        assertEquals(StreakTier.Trophy, tier(10))
    }

    /** A variant with one Sprosse has no Sprosse to report; the emoji leads only in a mixed run. */
    @Test
    fun theScoreLineOnlyReportsASprosseThereIsSomethingToClimb() {
        val one = TrainerRun.open(numbers(), Random(61))
        assertTrue(one.showsSprosse)
        assertFalse(one.severalVariants)
        val mixed = TrainerRun.open(
            TrainerMode(listOf(DrillVariant.Numbers, DrillVariant.Clock), "de", emptySet()),
            Random(61),
        )
        assertTrue(mixed.severalVariants)
        // A run carrying no frame has no sentence ladder to show.
        assertEquals(1, numbers().maxLevel(DrillVariant.Phrases))
    }
}

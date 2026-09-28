package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AnswerNormalizer

/**
 * When an endless run pauses to ask whether to go on ([DrillPacing]), and what going on keeps.
 * Driven through the slot run, which every drill's booking shares the rule with.
 */
class DrillPacingTest {

    private val de = LanguageInfo(code = "de", name = "Deutsch", englishName = "German", flag = "🇩🇪")
    private val normalizer = AnswerNormalizer(de, articleLeniency = false, maxTyposPerWord = 1)
    private val mode = NumbersMode(NumbersExercise.Counting, "de")

    private fun reduce(state: NumbersRunState, intent: NumbersIntent, rng: Random) =
        NumbersRun.reduce(state, intent, normalizer, rng).state

    private fun right(state: NumbersRunState, rng: Random): NumbersRunState =
        reduce(reduce(state, NumbersIntent.Submit(state.currentTask.accepted.first()), rng), NumbersIntent.ConfirmPending, rng)

    private fun miss(state: NumbersRunState, rng: Random): NumbersRunState =
        reduce(reduce(state, NumbersIntent.Reveal, rng), NumbersIntent.ConfirmPending, rng)

    private fun rights(state: NumbersRunState, count: Int, rng: Random): NumbersRunState =
        (1..count).fold(state) { run, _ -> right(run, rng) }

    @Test
    fun aLongStretchWithNothingNewPausesOnTheCount() {
        val rng = Random(3)
        val run = rights(NumbersRun.open(mode, 0, rng), DrillPacing.STRETCH, rng)
        assertEquals(DrillPauseReason.Count, run.pause)
    }

    @Test
    fun aBeatenRecordOrANewSprossePausesAsImproved() {
        val rng = Random(5)
        val record = rights(NumbersRun.open(mode, 4, rng), DrillPacing.IMPROVED_AFTER, rng)
        assertEquals(DrillPauseReason.Improved, record.pause)

        val cleared = (1..DrillPacing.IMPROVED_AFTER).fold(DrillRunCore()) { core, _ -> core.book(true, true, null) }
            .paced(level = 2, newSprossen = 1, endless = true)
        assertEquals(DrillPauseReason.Improved, cleared.pacing.pause)
    }

    @Test
    fun mostOfTheLastFewMissedPausesAsStruggling() {
        val rng = Random(7)
        var run = rights(NumbersRun.open(mode, 0, rng), 3, rng)
        repeat(3) { run = miss(run, rng) }
        assertEquals(DrillPauseReason.Struggling, run.pause)
    }

    @Test
    fun keepPracticingGoesOnWithTheSameRunAndAFreshStretch() {
        val rng = Random(11)
        val paused = rights(NumbersRun.open(mode, 0, rng), DrillPacing.STRETCH, rng)
        val resumed = reduce(paused, NumbersIntent.KeepPracticing, rng)
        assertNull(resumed.pause)
        assertEquals(paused.index, resumed.index)
        assertEquals(paused.streak, resumed.streak)
        assertEquals(paused.solved, resumed.solved)
        assertEquals(paused.levels, resumed.levels)
        assertNull(right(resumed, rng).pause)
    }
}

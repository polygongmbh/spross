package net.spross.kern.session

import net.spross.kern.model.Rating
import net.spross.kern.session.SelfGrading.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfGradingTests {

    private val word = 6 // a typical single-word prompt

    @Test
    fun `the learner's verdict is never overruled by the clock`() {
        // The whole point of keeping a button for each: a fast answer the learner
        // knows was shaky stays Hard, and a slow one they knew stays a pass.
        for (elapsed in listOf(1L, 200L, 5_000L, 600_000L)) {
            assertEquals(Rating.Again, SelfGrading.rating(Verdict.Unknown, elapsed, word))
            assertEquals(Rating.Hard, SelfGrading.rating(Verdict.Tough, elapsed, word))
        }
    }

    @Test
    fun `only a word that came instantly earns Easy`() {
        val budget = SelfGrading.instantBudgetMs(word)
        assertEquals(Rating.Easy, SelfGrading.rating(Verdict.Knew, budget - 1, word))
        assertEquals(Rating.Easy, SelfGrading.rating(Verdict.Knew, budget, word))
        assertEquals(Rating.Good, SelfGrading.rating(Verdict.Knew, budget + 1, word))
    }

    @Test
    fun `an unmeasured recall never earns Easy`() {
        assertEquals(Rating.Good, SelfGrading.rating(Verdict.Knew, 0, word))
        assertEquals(Rating.Good, SelfGrading.rating(Verdict.Knew, -1, word))
    }

    @Test
    fun `a longer prompt gets longer to be read before recall counts as slow`() {
        val phrase = 40
        assertTrue(SelfGrading.instantBudgetMs(phrase) > SelfGrading.instantBudgetMs(word))
        // The same elapsed time is instant for a phrase and slow for a short word.
        val elapsed = SelfGrading.instantBudgetMs(word) + 100
        assertEquals(Rating.Good, SelfGrading.rating(Verdict.Knew, elapsed, word))
        assertEquals(Rating.Easy, SelfGrading.rating(Verdict.Knew, elapsed, phrase))
    }
}

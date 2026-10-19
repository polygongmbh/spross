package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.CountryRunFixture.open

/**
 * What the atlas brings to the shared run ([PairDrillRunTest] steps through that): how a typed
 * name is graded and when a flag would give the answer away.
 * The ladder, the draw and the task shapes are [CountryDrillTests]'.
 */
class CountryDrillRunTest {

    /** A flag beside a known-language name gives nothing away; reversed, it would name the learner's own country. */
    @Test
    fun aReversedRunHoldsTheGiveawayFlagBack() {
        assertFalse(open().task.emojiIsGiveaway)
        val reversed = open(reverse = true)
        assertEquals("Deutschland", reversed.task.display)
        assertTrue(reversed.task.emojiIsGiveaway)
    }

    /** A name that is not this one's is a miss, however close the ladder's other rows are. */
    @Test
    fun anotherCountrysNameIsAMissAndNotASlip() {
        val reduction = CountryDrillRun.reduce(open(), PairDrillIntent.Submit("Uhispania"), Random(7))
        assertEquals(TurnFeedback.Revealed, reduction.state.feedback)
    }

    /**
     * The STRICT drill grader, on both platforms and in both directions: no article
     * leniency. The atlas authors the article INSIDE the name where a language wants one
     * ("die Schweiz") and the bare form beside it, so an article it did not author is one
     * the learner invented.
     */
    @Test
    fun anArticleTheAtlasDidNotAuthorIsNotForgiven() {
        val reversed = open(reverse = true)
        val config = CountryRunFixture.config(reverse = true)
        assertEquals(Match.Exact, CountryDrillRun.grade("Deutschland", reversed.task, config))
        assertEquals(Match.Wrong, CountryDrillRun.grade("das Deutschland", reversed.task, config))
    }

    /** No language info (a preview): a plain case- and punctuation-insensitive comparison. */
    @Test
    fun aPreviewWithNoLanguageInfoStillGradesPlainly() {
        val config = CountryRunFixture.config(graded = false)
        val task = open(graded = false).task
        assertEquals(Match.Exact, CountryDrillRun.grade("  ujerumani!  ", task, config))
        assertEquals(Match.Wrong, CountryDrillRun.grade("Ujerumami", task, config))
    }
}

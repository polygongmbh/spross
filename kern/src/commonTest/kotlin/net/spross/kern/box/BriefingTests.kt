package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Realization

/**
 * What a brief may tell an outside conversation partner, and what it may not.
 * The catalog is the join fixture's, so area headings and language names are real ones.
 */
class BriefingTests {

    private val catalog = Fixture.catalog()

    private fun brief(state: BoxState, name: String? = null) = Briefings.of(state, catalog, name)

    /** Landed words stand in one block, the ones still coming in the other. */
    @Test
    fun sortsWordsByHowFarTheyHaveCome() {
        var state = Box.state((1..3).map { Box.word(it, area = "alpha") })
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        state = Box.inject(state, Box.sched("w02", stability = 3.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        state = Box.inject(
            state,
            Box.sched("w03", phase = CardPhase.Learning, stability = 1.0, dueMillis = Box.day1, lastReviewMillis = Box.day1),
        )

        val briefing = brief(state)

        assertEquals(listOf("t1"), briefing.free.single().words)
        assertEquals(listOf("t2", "t3"), briefing.inPlay.map { it.target }.sorted())
        assertTrue(briefing.hasWords)
    }

    /** A word out of rotation is one the learner said they did not want to meet. */
    @Test
    fun leavesSuspendedWordsOut() {
        var state = Box.state(listOf(Box.word(1, area = "alpha"), Box.word(2, area = "alpha")))
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))
        state = Box.inject(
            state,
            Box.sched("w02", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1, suspended = true),
        )

        val text = brief(state).text

        assertContains(text, "t1")
        assertFalse("t2" in text, "a suspended word reached the brief")
    }

    /** Everything the box has not opened stays unnamed — only the allowance is listed. */
    @Test
    fun namesNoWordItHasNotOpened() {
        var state = Box.state((1..30).map { Box.word(it, area = "alpha") })
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        val briefing = brief(state)
        val expected = Growth.newCandidates(state, Briefings.NEW_LIMIT, Briefings.NEW_LIMIT)
            .newCards.map { state.cards.getValue(it).target.text }

        assertEquals(expected, briefing.newWords.map { it.target })
        assertTrue(briefing.newWords.size < 29, "the brief listed the whole shelf as new")
    }

    /** A box with nothing introduced briefs nobody. */
    @Test
    fun saysNothingAboutAnUntouchedBox() {
        val briefing = brief(Box.state((1..3).map { Box.word(it, area = "alpha") }))

        assertFalse(briefing.hasWords)
    }

    /** The article is half of what knowing a noun means, so the brief writes it. */
    @Test
    fun writesTargetNounsWithTheirArticle() {
        val card = Box.word(1, area = "alpha").let {
            it.copy(target = Realization(lang = "sw", text = "Amt", grammar = mapOf("gender" to "das")))
        }
        var state = Box.state(listOf(card))
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        assertEquals(listOf("das Amt"), brief(state).free.single().words)
    }

    /** Both of the learner's languages are named, in the language the brief is written in. */
    @Test
    fun namesBothLanguagesInEnglish() {
        var state = Box.state(listOf(Box.word(1, area = "alpha")))
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        val text = brief(state, name = "Anna").text

        assertContains(text, "Anna knows German and is learning Swahili.")
        assertContains(text, "Speak Swahili.")
        assertContains(text, "Alpha: t1")
    }

    /** Without a name the brief still reads as a sentence about somebody. */
    @Test
    fun standsWithoutALearnerName() {
        var state = Box.state(listOf(Box.word(1, area = "alpha")))
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        assertContains(brief(state, name = "  ").text, "The learner knows German")
    }

    /** What the brief asks for at the end is what [Harvest] reads back. */
    @Test
    fun closesWithABlockTheBoxCanReadBack() {
        var state = Box.state((1..3).map { Box.word(it, area = "alpha") })
        state = Box.inject(state, Box.sched("w01", stability = 40.0, dueMillis = Box.day1, lastReviewMillis = Box.day1))

        val block = brief(state).text.substringAfterLast("```spross").substringBefore("```").trim()
        val answer = "Sure — here is what came up.\n\n```spross\n" +
            block.replace("t2", "mgeni").replace("g2", "Gast") + "\n```\nSee you tomorrow!"

        assertEquals(listOf(BriefWord("mgeni", "Gast")), Harvest.read(answer, state))
    }
}

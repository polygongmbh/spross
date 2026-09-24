package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling

/** Which words a brief may name, and what the conversation may hand back ([Harvest]). */
class BriefingTests {

    private val catalog = Fixture.catalog()

    private fun state(cards: List<Card>): BoxState = Box.state(cards)

    private fun matured(cardId: String): CardScheduling =
        Box.sched(
            cardId,
            stability = MATURED_STABILITY * 2,
            dueMillis = Box.plusDays(Box.day1, 30.0),
            lastReviewMillis = Box.day1,
        )

    private fun learning(cardId: String, suspended: Boolean = false): CardScheduling =
        Box.sched(
            cardId,
            phase = CardPhase.Learning,
            stability = 1.0,
            dueMillis = Box.day1,
            lastReviewMillis = Box.day1,
            suspended = suspended,
        )

    private fun brief(state: BoxState, name: String? = "Ada"): Briefing =
        Briefings.of(state, catalog, name)

    /** What a partner is told to go gently on, versus what they may build a sentence out of. */
    @Test
    fun maturedIsWhatIsKnownAndTheRestIsStillInProgress() {
        var box = state(listOf(Box.word(1), Box.word(2)))
        box = Box.inject(box, matured("w01"))
        box = Box.inject(box, learning("w02"))

        val brief = brief(box)
        assertEquals(listOf("t1"), brief.matured.flatMap { it.words })
        assertEquals(listOf("t2"), brief.learning.map { it.target })
    }

    /** A fence is not a list: what is suspended or never introduced is nowhere in the brief. */
    @Test
    fun suspendedAndUnscheduledCardsAreNamedNowhere() {
        var box = state(listOf(Box.word(1), Box.word(2), Box.word(3)))
        box = Box.inject(box, learning("w01", suspended = true))
        box = Box.inject(box, matured("w02").copy(suspended = true))

        val brief = brief(box)
        assertEquals(emptyList(), brief.matured.flatMap { it.words })
        assertEquals(emptyList(), brief.learning.map { it.target })
        // w03 was never introduced, so it can only turn up as something to teach next.
        assertTrue(brief.newWords.any { it.target == "t3" })
    }

    /** The box's most personal content stays on the device, on every list. */
    @Test
    fun ownWordsLeaveTheDeviceNowhere() {
        var box = state(listOf(Box.word(1, area = OwnWords.AREA), Box.word(2, area = OwnWords.AREA)))
        box = Box.inject(box, matured("w01"))
        box = Box.inject(box, learning("w02"))

        val brief = brief(box)
        assertEquals(emptyList(), brief.matured.flatMap { it.words })
        assertEquals(emptyList(), brief.learning.map { it.target })
        assertEquals(emptyList(), brief.newWords.map { it.target })
        assertFalse(Briefings.available(box))
    }

    /** A box with nothing introduced briefs nobody; one word in is a conversation. */
    @Test
    fun availableFollowsWhetherAnyWordIsIntroduced() {
        val fresh = state(listOf(Box.word(1)))
        assertFalse(Briefings.available(fresh))
        assertTrue(Briefings.available(Box.inject(fresh, learning("w01"))))
    }

    /** What is next is an offer, and a learner already juggling enough is not made one. */
    @Test
    fun aBusyLearnerIsOfferedNoNewWords() {
        val cards = (1..Briefings.LEARNING_BUSY + 5).map { Box.word(it) }
        var box = state(cards)
        for (n in 1..Briefings.LEARNING_BUSY) box = Box.inject(box, learning(Box.word(n).id))

        assertEquals(emptyList(), brief(box).newWords)

        val quieter = Box.inject(state(cards), learning("w01"))
        val newWords = brief(quieter).newWords
        assertTrue(newWords.isNotEmpty())
        assertTrue(newWords.size <= Briefings.NEW_LIMIT)
    }

    /** A sown word is the learner's own ask: named on its own list, even past busy, and nowhere else. */
    @Test
    fun sownWordsLeadEvenForABusyLearner() {
        val cards = (1..Briefings.LEARNING_BUSY + 5).map { Box.word(it) }
        var box = state(cards)
        for (n in 1..Briefings.LEARNING_BUSY) box = Box.inject(box, learning(Box.word(n).id))
        box = BoxEngine.enqueue(box, listOf(Box.word(Briefings.LEARNING_BUSY + 3).id))

        val brief = brief(box)
        assertEquals(listOf("t${Briefings.LEARNING_BUSY + 3}"), brief.sown.map { it.target })
        assertEquals(emptyList(), brief.newWords)

        val quieter = BoxEngine.enqueue(state(listOf(Box.word(1), Box.word(2))), listOf("w02"))
        val offered = brief(quieter)
        assertEquals(listOf("t2"), offered.sown.map { it.target })
        assertFalse(offered.newWords.any { it.target == "t2" })
    }

    /** Sown words set the opening story's topic; without any, the words in progress do. */
    @Test
    fun theStoryRevolvesAroundSownWords() {
        val box = Box.inject(state(listOf(Box.word(1), Box.word(2))), learning("w01"))
        assertFalse("words I chose" in brief(box).text)

        val sown = brief(BoxEngine.enqueue(box, listOf("w02"))).text
        assertTrue("story around the words I chose" in sown)
        assertTrue("t2 (" in sown)
        assertTrue("\nSTART HERE" in sown, "the opening turn keeps its left edge")
    }

    /** The loop closes: the fence the brief prints is one [Harvest] reads back. */
    @Test
    fun theHarvestFenceRoundTrips() {
        val box = Box.inject(state(listOf(Box.word(1), Box.word(2))), learning("w01"))
        val brief = brief(box)
        val example = brief.newWords.first()

        val read = Harvest.read(brief.text, box)
        assertTrue(read.any { it.word == example }, "no $example in ${read.map { it.word }}")
    }
}

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
        assertEquals(emptyList(), brief.sown.map { it.target })
    }

    /** The box's most personal content stays on the device, on every list. */
    @Test
    fun ownWordsLeaveTheDeviceNowhere() {
        val own = (1..3).map { Box.word(it, area = OwnWords.AREA) }
        var box = BoxEngine.enqueue(state(own), listOf("w03"))
        box = Box.inject(box, matured("w01"))
        box = Box.inject(box, learning("w02"))

        val brief = brief(box)
        assertEquals(emptyList(), brief.matured.flatMap { it.words })
        assertEquals(emptyList(), brief.learning.map { it.target })
        assertEquals(emptyList(), brief.sown.map { it.target })
        assertFalse(Briefings.available(box))
    }

    /** A box with nothing introduced briefs nobody; one word in is a conversation. */
    @Test
    fun availableFollowsWhetherAnyWordIsIntroduced() {
        val fresh = state(listOf(Box.word(1)))
        assertFalse(Briefings.available(fresh))
        assertTrue(Briefings.available(Box.inject(fresh, learning("w01"))))
    }

    /** What is next is the learner's own ask: the sown words, and no word of the app's choosing. */
    @Test
    fun onlySownWordsAreNamedAsNext() {
        val box = BoxEngine.enqueue(state(listOf(Box.word(1), Box.word(2), Box.word(3))), listOf("w03"))

        val brief = brief(box)
        assertEquals(listOf("t3"), brief.sown.map { it.target })
        assertFalse("t2" in brief.text)
    }

    /** A sown phrase is named before its words have unlocked it — a talk waits on no round. */
    @Test
    fun aSownPhraseIsNamedWhileStillLocked() {
        val box = BoxEngine.enqueue(
            state(listOf(Box.word(1), Box.word(2), Box.phrase("p1", components = listOf("w01", "w02")))),
            listOf("p1"),
        )
        assertTrue(brief(box).sown.any { it.target == "p1" })
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
        val started = Box.inject(state(listOf(Box.word(1), Box.word(2))), learning("w01"))
        val box = BoxEngine.enqueue(started, listOf("w02"))
        val brief = brief(box)
        val example = brief.sown.first()

        val read = Harvest.read(brief.text, box)
        assertTrue(read.any { it.word == example }, "no $example in ${read.map { it.word }}")
    }
}

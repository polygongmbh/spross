package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.catalog.OppositePair
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.session.Match

/** What the opposites drill may ask of a box, how its bands fall, and what grades right. */
class OppositesRunTest {

    private val word = ScrambleFixture::word

    private val cards = listOf(
        word("white", "weiß", CardKind.Adjective, 1, emptyList(), emptyList()),
        word("black", "schwarz", CardKind.Adjective, 2, emptyList(), emptyList()),
        word("old", "alt", CardKind.Adjective, 3, emptyList(), emptyList()),
        word("new", "neu", CardKind.Adjective, 4, emptyList(), emptyList()),
        word("young", "jung", CardKind.Adjective, 5, emptyList(), emptyList()),
        word("to-move-out", "ausziehen", CardKind.Verb, 6, emptyList(), emptyList()),
        word("to-move-in", "einziehen", CardKind.Verb, 7, emptyList(), emptyList()),
        word("to-take-off", "ausziehen", CardKind.Verb, 8, emptyList(), emptyList()),
        word("to-put-on", "anziehen", CardKind.Verb, 9, emptyList(), emptyList()),
        word("to-buy", "kaufen", CardKind.Verb, 10, emptyList(), emptyList()),
        word("to-sell", "verkaufen", CardKind.Verb, 11, emptyList(), emptyList()),
        word("up", "oben", CardKind.Adjective, 12, emptyList(), emptyList()),
        word("down", "oben", CardKind.Adjective, 13, emptyList(), emptyList()),
    )

    private val pairs = listOf(
        OppositePair("white", "black"),
        OppositePair("new", "old"),
        OppositePair("young", "old"),
        OppositePair("to-move-in", "to-move-out"),
        OppositePair("to-put-on", "to-take-off"),
        OppositePair("to-buy", "to-sell"),
        OppositePair("up", "down"),
    )

    /** Every card arrived except those in [unmet], which the box knows of and never scheduled. */
    private fun report(unmet: Set<String> = emptySet()): OppositesAvailability.Report {
        var state = Box.state(cards)
        for (card in cards.filter { it.id !in unmet }) {
            state = Box.inject(
                state,
                Box.sched(card.id, stability = ScrambleFixture.SETTLED, dueMillis = Box.plusDays(Box.day1, 3.0), lastReviewMillis = Box.day1),
            )
        }
        return OppositesAvailability.report(state, pairs)
    }

    private fun prompt(form: String, report: OppositesAvailability.Report = report()) =
        report.prompts.singleOrNull { it.form == form }

    private fun answers(form: String) = assertNotNull(prompt(form)).opposites.map { it.text }.toSet()

    @Test
    fun aPairIsAskedOnlyOnceBothSidesHaveArrived() {
        assertEquals(null, prompt("kaufen", report(unmet = setOf("to-sell"))))
        assertEquals(null, prompt("verkaufen", report(unmet = setOf("to-sell"))))
        assertEquals(setOf("verkaufen"), answers("kaufen"))
    }

    @Test
    fun aPairWrittenAlikeAsksNothing() {
        assertEquals(null, prompt("oben"))
    }

    /** Two concepts written alike are one prompt, and every opposite of either answers it. */
    @Test
    fun aMergedFormAcceptsTheOppositeOfEverySense() {
        assertEquals(setOf("einziehen", "anziehen"), answers("ausziehen"))
        assertEquals(setOf("neu", "jung"), answers("alt"))
    }

    /** An opposite the learner has not met yet still answers — the reveal is where they meet it. */
    @Test
    fun anUnmetSenseStillAnswers() {
        val unmet = report(unmet = setOf("to-take-off", "to-put-on"))
        assertEquals(setOf("einziehen", "anziehen"), assertNotNull(prompt("ausziehen", unmet)).opposites.map { it.text }.toSet())
    }

    @Test
    fun theBandsAreAdjectivesThenVerbsThenSeveralOpposites() {
        assertEquals(OppositesAvailability.ADJECTIVES, prompt("weiß")?.sprosse)
        assertEquals(OppositesAvailability.VERBS, prompt("kaufen")?.sprosse)
        assertEquals(OppositesAvailability.SEVERAL, prompt("ausziehen")?.sprosse)
    }

    private fun config() = OppositesRunConfig(report(), ScrambleFixture.normalizer)

    /** Answers right until [form] comes up — the collision band is the top of the ladder. */
    private fun task(form: String): OppositesTask {
        var state = OppositesRun.open(config(), Random(1))
        repeat(40) {
            val task = state.task ?: error("\"$form\" was never drawn")
            if (task.prompt == form) return task
            state = answered(state, task, it)
        }
        error("\"$form\" was never drawn")
    }

    private fun answered(state: OppositesRunState, task: OppositesTask, seed: Int): OppositesRunState {
        val typed = OppositesRun.reduce(state, OppositesIntent.InputChanged(task.answers.first().text), Random(seed)).state
        return OppositesRun.reduce(typed, OppositesIntent.ConfirmPending, Random(seed)).state
    }

    @Test
    fun anyOppositeGradesRight() {
        val task = task("ausziehen")
        assertEquals(Match.Exact, OppositesRun.grade("anziehen", task, config()))
        assertEquals(Match.Exact, OppositesRun.grade("einziehen", task, config()))
    }

    /** The prompt sits inside its opposite's typo budget, and is still never a slip of it. */
    @Test
    fun writingThePromptBackIsAMiss() {
        val task = task("ausziehen")
        assertEquals(Match.Wrong, OppositesRun.grade("ausziehen", task, config()))
    }

    @Test
    fun aRunThatAnswersEveryPromptEnds() {
        var state = OppositesRun.open(config(), Random(3))
        repeat(40) {
            val task = state.task ?: return@repeat
            state = answered(state, task, it)
        }
        assertTrue(state.finished)
        assertFalse(OppositesRun.close(state).summary == null)
    }
}

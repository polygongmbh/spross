package net.spross.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback
import net.spross.kern.trainer.ScrambleTokenizer
import net.spross.kern.trainer.SentenceScrambleAvailability
import net.spross.kern.trainer.SentenceScrambleRun
import net.spross.kern.trainer.SentenceScrambleRunConfig
import net.spross.kern.trainer.WordScrambleAvailability
import net.spross.kern.trainer.WordScrambleRun
import net.spross.kern.trainer.WordScrambleRunConfig

/**
 * What the APP does with kern's two scramble runs — the word spelled from mixed letters and
 * the phrase put back in order. The rules themselves belong to `:kern:jvmTest`, as in
 * [DrillWiringTest]; the harness is the same [DrillPlatform].
 */
class ScrambleWiringTest {

    // MARK: - The word scramble

    private fun scramble(platform: DrillPlatform, seed: Int = 11): WordScrambleFlow {
        val report = WordScrambleAvailability.Report(
            listOf("chumba", "kitabu", "mlango", "dirisha", "meza")
                .mapIndexed { index, word ->
                    WordScrambleAvailability.Spelling(grownWord("word$index", word), listOf(word))
                },
        )
        return WordScrambleFlow(
            // A run with no language info grades plainly — enough to drive the wiring.
            start = WordScrambleRun.open(WordScrambleRunConfig(report, normalizer = null), Random(seed)),
            rng = Random(seed),
            clearedKey = TrainerStore.wordScrambleKey("sw"),
            onTone = { platform.tones += it },
            onReleaseFocus = { platform.focusReleases += 1 },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )
    }

    /** Writing the word out IS the answer — the typed drills' rule, on mixed letters. */
    @Test
    fun finishingTheSpellingArmsTheBeatWithoutACheckTap() {
        val platform = DrillPlatform()
        val flow = scramble(platform)
        flow.type(assertNotNull(flow.state.task).display)
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceBeat.Live, flow.armedBeat)
    }

    @Test
    fun aClosedWordScrambleReportsItsFiguresAndNoRecord() {
        assertNull(scramble(DrillPlatform()).close().summary)

        val flow = scramble(DrillPlatform())
        flow.type(assertNotNull(flow.state.task).display)
        val summary = assertNotNull(flow.close().summary)
        // The pending clean answer books on the way out, exactly as the tap would.
        assertEquals(1, summary.done)
        // This drill keeps no record store, so nothing it does can beat one.
        assertTrue(!summary.newRecord)
    }

    // MARK: - The sentence scramble

    private fun phrase(id: String, text: String) = SentenceScrambleAvailability.Phrase(
        card = grownWord(id, text).copy(kind = CardKind.Phrase),
        atoms = ScrambleTokenizer.atoms(text),
    )

    private fun sentences(platform: DrillPlatform, seed: Int = 3): SentenceScrambleFlow {
        val report = SentenceScrambleAvailability.Report(
            listOf(
                phrase("greet", "habari za asubuhi"),
                phrase("thanks", "asante sana rafiki"),
                phrase("ask", "unaitwa nani leo"),
            ),
        )
        return SentenceScrambleFlow(
            start = SentenceScrambleRun.open(SentenceScrambleRunConfig(report), Random(seed)),
            rng = Random(seed),
            clearedKey = TrainerStore.sentenceScrambleKey("sw"),
            onTone = { platform.tones += it },
            onSilence = { platform.silences += 1 },
            screenReaderOn = { platform.screenReader },
        )
    }

    /** The LAST word placed is the answer: there is no check tap to send. */
    @Test
    fun committingTheLastAtomGradesTheArrangement() {
        val platform = DrillPlatform()
        val flow = sentences(platform)
        val task = assertNotNull(flow.state.task)
        task.canonical.forEach { atom ->
            flow.place(task.shuffled.indexOfFirst { it.id == atom.id })
        }
        assertEquals(TurnFeedback.Correct, flow.state.feedback)
        assertEquals(listOf(ToneKind.Correct), platform.tones)
        assertEquals(AdvanceBeat.Explicit, flow.armedBeat)
    }

    /** A slip of the finger costs a tap rather than the question. */
    @Test
    fun anAtomGoesBackWhileTheOrderIsStillOwed() {
        val flow = sentences(DrillPlatform())
        flow.place(0)
        assertEquals(1, flow.state.placed.size)
        flow.take(0)
        assertTrue(flow.state.placed.isEmpty())
        assertTrue(!flow.state.isPlaced(0), "the chip is back in the bank")
    }
}

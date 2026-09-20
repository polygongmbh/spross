package net.spross.app

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.WordScrambleAvailability
import net.spross.kern.trainer.WordScrambleClose
import net.spross.kern.trainer.WordScrambleIntent
import net.spross.kern.trainer.WordScrambleRun
import net.spross.kern.trainer.WordScrambleRunConfig
import net.spross.kern.trainer.WordScrambleRunState

/**
 * One word-scramble run as this platform holds it — the twin of [LetterDrillFlow], over
 * kern's own [WordScrambleRun].
 *
 * Everything decidable is kern's: which word is drawn, how much of its spelling the Sprosse
 * leaves standing, what a typed answer earns. What is left here is [DrillFlow]'s: the
 * field's text and the armed beat.
 *
 * No review is ever booked: the box is READ for the words it has consolidated and never
 * written, and the run keeps no streak record — spelling a word back out of its own letters
 * is not the recall the schedule measures. What DOES outlive the run is the ladder it
 * climbed, filed under [clearedKey].
 */
class WordScrambleFlow(
    start: WordScrambleRunState,
    rng: Random,
    /**
     * Where the Sprossen this run clears are filed, and where it read the ones it opened
     * above ([TrainerStore.wordScrambleKey]) — one string, so the two sides cannot drift.
     */
    val clearedKey: String,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : ProgressDrillFlow<WordScrambleRunState, WordScrambleIntent>(
    start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn,
) {
    /** Leaving: kern books a pending answer exactly as the tap would, then reports. */
    fun close(): WordScrambleClose =
        WordScrambleRun.close(state).also { land(it.state, it.effects) }

    override fun reduce(state: WordScrambleRunState, intent: WordScrambleIntent, rng: Random) =
        WordScrambleRun.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun inputChanged(text: String) = WordScrambleIntent.InputChanged(text)

    override fun submit(text: String) = WordScrambleIntent.Submit(text)

    override fun confirmPending() = WordScrambleIntent.ConfirmPending

    override fun advanceElapsedIntent() = WordScrambleIntent.AdvanceElapsed
}

/**
 * A run, or null where this box holds too few grown words to mix — the chip gates on the
 * same report, so a null here is a closed door rather than a screen.
 *
 * The normalizer is the STRICT drill one for the language being learned, which is the side
 * the spelling is owed on. A profile whose catalog names no such language grades plainly.
 *
 * The run opens on the Sprosse the store's mask leaves lowest ([WordScrambleRunConfig.entryLevel]),
 * so a ladder climbed clean is never asked for twice.
 */
fun AppModel.newWordScramble(
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): WordScrambleFlow? {
    val state = box ?: return null
    val report = WordScrambleAvailability.report(state)
    if (!report.drillAvailable) return null
    val info = catalog?.languages?.get(state.joinStamp.target)
    val key = TrainerStore.wordScrambleKey(state.joinStamp.target)
    val config = WordScrambleRunConfig(
        report,
        info?.let { AnswerNormalizer.drill(it) },
        trainer.store.cleared(key),
    )
    return WordScrambleFlow(
        start = WordScrambleRun.open(config, rng),
        rng = rng,
        clearedKey = key,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

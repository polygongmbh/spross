package net.spross.app

import kotlin.random.Random
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.SentenceScrambleAvailability
import net.spross.kern.trainer.SentenceScrambleClose
import net.spross.kern.trainer.SentenceScrambleIntent
import net.spross.kern.trainer.SentenceScrambleRun
import net.spross.kern.trainer.SentenceScrambleRunConfig
import net.spross.kern.trainer.SentenceScrambleRunState

/**
 * One sentence-scramble run as this platform holds it — the twin of [WordScrambleFlow], over
 * kern's own [SentenceScrambleRun].
 *
 * There is no field and no submit: moving the last atom into place IS the answer, the way a
 * finished spelling is on the typed drills, so this is the one [DrillFlow] that hands kern
 * neither a keystroke nor a check and leaves both unimplemented. Everything decidable is
 * kern's — the deal, the ladder of lengths, the grading by position — and what is left here
 * is the armed beat.
 *
 * No review is ever booked: the box is READ for the phrases it has unlocked and never
 * written, and the run keeps no streak record — arrangement is not recall. What DOES outlive
 * the run is the ladder it climbed, filed under [clearedKey].
 */
class SentenceScrambleFlow(
    start: SentenceScrambleRunState,
    rng: Random,
    /**
     * Where the Sprossen this run clears are filed, and where it read the ones it opened
     * above ([TrainerStore.sentenceScrambleKey]) — one string, so the two sides cannot drift.
     */
    val clearedKey: String,
    onTone: (ToneKind) -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : ProgressDrillFlow<SentenceScrambleRunState, SentenceScrambleIntent>(
    // Nothing to release: this drill has no field, so no pause can be waiting behind a keyboard.
    start, rng, onTone, onReleaseFocus = {}, onSilence = onSilence, screenReaderOn = screenReaderOn,
) {
    /** A bank slot tapped: the atom joins the end of the arrangement, and the last one grades. */
    fun place(index: Int) = dispatch(SentenceScrambleIntent.PlaceAtom(index))

    /** An answer-row slot tapped: the atom goes back, while the order is still owed. */
    fun take(index: Int) = dispatch(SentenceScrambleIntent.ReturnAtom(index))

    /** Leaving: kern books a pending arrangement exactly as the tap would, then reports. */
    fun close(): SentenceScrambleClose =
        SentenceScrambleRun.close(state).also { land(it.state, it.effects) }

    override fun reduce(state: SentenceScrambleRunState, intent: SentenceScrambleIntent, rng: Random) =
        SentenceScrambleRun.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun confirmPending() = SentenceScrambleIntent.ConfirmPending

    override fun advanceElapsedIntent() = SentenceScrambleIntent.AdvanceElapsed
}

/**
 * A run, or null where this box has unlocked too few long-enough phrases — the chip gates on
 * the same report, so a null here is a closed door rather than a screen.
 *
 * Nothing is graded against a language here: the answer is a permutation of atoms kern itself
 * dealt, so the run needs no normalizer. The language being learned is read for the ladder's
 * storage key alone — every phrase the run deals names its own.
 *
 * The run opens on the Sprosse the store's mask leaves lowest
 * ([SentenceScrambleRunConfig.entryLevel]), so a ladder climbed clean is never asked for twice.
 */
fun AppModel.newSentenceScramble(
    onTone: (ToneKind) -> Unit = {},
    rng: Random = Random.Default,
): SentenceScrambleFlow? {
    val state = box ?: return null
    val report = SentenceScrambleAvailability.report(state)
    if (!report.drillAvailable) return null
    val key = TrainerStore.sentenceScrambleKey(state.joinStamp.target)
    return SentenceScrambleFlow(
        start = SentenceScrambleRun.open(
            SentenceScrambleRunConfig(report, trainer.store.cleared(key)),
            rng,
        ),
        rng = rng,
        clearedKey = key,
        onTone = onTone,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

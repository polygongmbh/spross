package net.spross.app

import kotlin.random.Random
import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.OppositesAvailability
import net.spross.kern.trainer.OppositesClose
import net.spross.kern.trainer.OppositesIntent
import net.spross.kern.trainer.OppositesRun
import net.spross.kern.trainer.OppositesRunConfig
import net.spross.kern.trainer.OppositesRunState

/**
 * One opposites run as this platform holds it — the twin of [WordScrambleFlow], over kern's
 * own [OppositesRun]. What is left here is [DrillFlow]'s: the field's text and the armed beat.
 * No review is ever booked; what outlives the run is the ladder, filed for [language].
 */
class OppositesFlow(
    start: OppositesRunState,
    rng: Random,
    /** The language drilled, which its close files under ([OppositesClose.bookings]). */
    val language: Language,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : DrillFlow<OppositesRunState, OppositesIntent>(
    start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn,
) {
    /** Leaving: kern books a pending answer exactly as the tap would, then reports. */
    fun close(): OppositesClose =
        OppositesRun.close(state).also { land(it.state, it.effects) }

    override fun reduce(state: OppositesRunState, intent: OppositesIntent, rng: Random) =
        OppositesRun.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun inputChanged(text: String) = OppositesIntent.InputChanged(text)

    override fun submit(text: String) = OppositesIntent.Submit(text)

    override fun confirmPending() = OppositesIntent.ConfirmPending

    override fun advanceElapsedIntent() = OppositesIntent.AdvanceElapsed

    override fun keepPracticingIntent() = OppositesIntent.KeepPracticing
}

/**
 * A run, or null where this box holds too few opposite pairs — the chip gates on the same
 * report, so a null here is a closed door rather than a screen.
 */
fun AppModel.newOpposites(
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): OppositesFlow? {
    val state = box ?: return null
    val report = OppositesAvailability.report(state, catalog?.oppositePairs.orEmpty())
    if (!report.drillAvailable) return null
    val info = catalog?.languages?.get(state.joinStamp.target)
    val key = TrainerStore.oppositesKey(state.joinStamp.target)
    val config = OppositesRunConfig(
        report,
        info?.let { AnswerNormalizer.drill(it) },
        trainer.store.cleared(key),
    )
    return OppositesFlow(
        start = OppositesRun.open(config, rng),
        rng = rng,
        language = state.joinStamp.target,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

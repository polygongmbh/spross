package net.spross.app

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.LetterDrillClose
import net.spross.kern.trainer.LetterDrillIntent
import net.spross.kern.trainer.LetterDrillRun
import net.spross.kern.trainer.LetterDrillRunConfig
import net.spross.kern.trainer.LetterDrillRunState

/**
 * One letter run as this platform holds it — the twin of [NumbersFlow], over kern's own
 * [LetterDrillRun].
 *
 * Everything decidable is kern's: the ladder, the draw, the ramp step, the three-way
 * verdict a dictated word can earn. What is left here is [DrillFlow]'s — the field's text
 * and the armed beat — and a keystroke means nothing until it is submitted, so this is the
 * one drill that offers kern none. No review is ever booked (D12): the box is READ, for the
 * pacing figures and the dictation pool, and never written.
 */
class LetterDrillFlow(
    start: LetterDrillRunState,
    rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : DrillFlow<LetterDrillRunState, LetterDrillIntent>(
    start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn,
) {
    /** One attempt per tile — a second tap after the answer is in would be a retry. */
    fun choose(glyph: String) = dispatch(LetterDrillIntent.Choose(glyph))

    /** Leaving: kern books a pending answer exactly as the tap would, then reports. */
    fun close(): LetterDrillClose =
        LetterDrillRun.close(state).also { land(it.state, it.effects) }

    override fun reduce(state: LetterDrillRunState, intent: LetterDrillIntent, rng: Random) =
        LetterDrillRun.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun index(state: LetterDrillRunState) = state.index

    override fun finished(state: LetterDrillRunState) = state.finished

    override fun owesAnswer(state: LetterDrillRunState) = state.owesAnswer

    override fun submit(text: String) = LetterDrillIntent.Submit(text)

    override fun confirmPending() = LetterDrillIntent.ConfirmPending

    override fun advanceElapsedIntent() = LetterDrillIntent.AdvanceElapsed
}

/**
 * A run, or null where this device can ask nothing at all — the overview's start button
 * gates on the same predicate, so a null here is a closed door rather than a screen.
 */
fun AppModel.newLetterDrill(
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): LetterDrillFlow? {
    val report = trainer.letters ?: return null
    if (!report.drillAvailable) return null
    val state = box ?: return null
    val info = catalog?.languages?.get(report.language) ?: return null
    val config = LetterDrillRunConfig(
        report = report,
        cards = state.cards,
        // why: the STRICT drill normalizer (no article leniency, a slip per word) with the
        // whole join in view — a per-word budget alone accepts `kufungua` for `kufunga`,
        // and only the catalog-wide grader withdraws that credit.
        dictationGrader = CatalogAnswerGrader(
            AnswerNormalizer.drill(info),
            state.cards.values.toList(),
        ),
    )
    return LetterDrillFlow(
        start = LetterDrillRun.open(config, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

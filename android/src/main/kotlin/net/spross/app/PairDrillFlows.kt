package net.spross.app

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.CountryDrillRun
import net.spross.kern.trainer.CountryDrillRunConfig
import net.spross.kern.trainer.CountryDrillTask
import net.spross.kern.trainer.DateDrillRun
import net.spross.kern.trainer.DateDrillRunConfig
import net.spross.kern.trainer.DateDrillTask
import net.spross.kern.trainer.PairDrill
import net.spross.kern.trainer.PairDrillRun
import net.spross.kern.trainer.PairDrillRunConfig
import net.spross.kern.trainer.PairDrillTask

/**
 * The run the atlas page opens, or null before the catalog has landed.
 * [sprosse] is the page's call ([net.spross.kern.trainer.NumbersMode.entrySprosse] or a tap);
 * [standingRecord] and [cleared] are what the page's store holds — what a pause for improving
 * is measured against.
 */
fun AppModel.newCountryDrill(
    reverse: Boolean,
    fast: Boolean,
    sprosse: Int,
    standingRecord: Int,
    cleared: Set<Int>,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): TypedDrillFlow<CountryDrillTask>? {
    val content = atlas ?: return null
    val normalizer = drillNormalizer(content.source, content.target, reverse) ?: return null
    val config = CountryDrillRunConfig(content, reverse, fast, normalizer, standingRecord, cleared)
    return pairDrillFlow(CountryDrillRun, config, sprosse, onTone, onReleaseFocus, rng)
}

/** The run the dates page opens, or null before the catalog has landed — [newCountryDrill]'s twin. */
fun AppModel.newDateDrill(
    reverse: Boolean,
    fast: Boolean,
    sprosse: Int,
    standingRecord: Int,
    cleared: Set<Int>,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): TypedDrillFlow<DateDrillTask>? {
    val content = dates ?: return null
    val normalizer = drillNormalizer(content.source, content.target, reverse) ?: return null
    val config = DateDrillRunConfig(content, reverse, fast, normalizer, standingRecord, cleared)
    return pairDrillFlow(DateDrillRun, config, sprosse, onTone, onReleaseFocus, rng)
}

/**
 * The STRICT drill grader, built for the language the answer is owed in — which is the
 * learner's OWN language on a reversed run, so the direction is settled by kern rather than
 * assumed to be the target.
 */
private fun AppModel.drillNormalizer(source: String, target: String, reverse: Boolean): AnswerNormalizer? =
    catalog?.languages?.get(PairDrill.answerLanguage(source, target, reverse))?.let(AnswerNormalizer::drill)

private fun <T : PairDrillTask> AppModel.pairDrillFlow(
    run: PairDrillRun<T>,
    config: PairDrillRunConfig<T>,
    sprosse: Int,
    onTone: (ToneKind) -> Unit,
    onReleaseFocus: () -> Unit,
    rng: Random,
) = TypedDrillFlow(
    start = run.openAt(config, sprosse, rng),
    run = run,
    rng = rng,
    onTone = onTone,
    onReleaseFocus = onReleaseFocus,
    onSilence = { pronouncer.stop() },
    screenReaderOn = { pronouncer.readsScreenAloud },
)

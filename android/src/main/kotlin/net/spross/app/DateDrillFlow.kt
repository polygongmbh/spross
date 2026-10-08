package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.DateDrill
import net.spross.kern.trainer.DateDrillIntent
import net.spross.kern.trainer.DateDrillRun
import net.spross.kern.trainer.DateDrillRunConfig
import net.spross.kern.trainer.DateDrillRunState

/**
 * One dates run as this platform holds it — the fourth sibling of [NumbersFlow],
 * [LetterDrillFlow] and [CountryDrillFlow], over kern's own [DateDrillRun].
 *
 * Everything decidable is kern's: which question a Sprosse may ask, which date is drawn, what
 * a typed reading earns, how far one answer moves the ramp, which beat is armed. What is
 * left here is the text standing in the field and the beat itself.
 *
 * Stateless like all its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's calendars, not the learner's own words. The one
 * thing that outlives a run is the furthest Sprosse it stood on, which the page that started
 * it files ([TrainerStore.bookSprosse]).
 */
class DateDrillFlow(
    start: DateDrillRunState,
    rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : TypedDrillFlow<DateDrillRunState, DateDrillIntent>(
    start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn,
) {
    /** The tile the warm-up question was answered off, cleared with the question itself. */
    override var chosen by mutableStateOf<String?>(null)
        private set

    /**
     * A tapped tile: submitted as the text it carries, so kern grades it against the same
     * accepted set a written answer meets and nothing here decides what a tap is worth.
     */
    override fun choose(text: String) {
        chosen = text
        super.choose(text)
    }

    override fun onQuestionChanged() {
        chosen = null
    }

    override fun reduce(state: DateDrillRunState, intent: DateDrillIntent, rng: Random) =
        DateDrillRun.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun closeRun(state: DateDrillRunState, standingRecord: Int): DrillEnd<DateDrillRunState> {
        val closed = DateDrillRun.close(state, standingRecord)
        return DrillEnd(
            closed.state,
            closed.effects,
            TypedDrillClose(closed.summary, closed.bookings()),
        )
    }

    override fun inputChanged(text: String) = DateDrillIntent.InputChanged(text)

    override fun submit(text: String) = DateDrillIntent.Submit(text)

    override fun confirmPending() = DateDrillIntent.ConfirmPending

    override fun advanceElapsedIntent() = DateDrillIntent.AdvanceElapsed

    override fun keepPracticingIntent() = DateDrillIntent.KeepPracticing

    /**
     * The run as the shared typed-drill screen reads it. A dates question carries no
     * picture, so the leading slot stays empty and the prompt — a name, or a dated line in
     * the prompt side's digits — stands where the country's name would.
     */
    override fun view(): TypedDrillView = TypedDrillView(
        index = state.index,
        sprosse = state.sprosse,
        answerStreak = state.answerStreak,
        bestAnswerStreak = state.bestAnswerStreak,
        outcomes = state.outcomes,
        tally = state.tally,
        question = state.question,
    )
}

/**
 * The run the dates page opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one, built for the language the answer is owed in —
 * which is the learner's OWN language on a reversed run, so the direction is settled here
 * rather than assumed to be the target.
 */
fun AppModel.newDateDrill(
    reverse: Boolean,
    fast: Boolean,
    /** The Sprosse the run opens on — the page's call ([net.spross.kern.trainer.NumbersMode.entrySprosse] or a tap). */
    sprosse: Int,
    /** The answer-streak record the page's store holds — what a pause for improving is measured against. */
    standingRecord: Int,
    /** The Sprossen answered out in this direction — a new one is what a pause for improving names. */
    cleared: Set<Int>,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): DateDrillFlow? {
    val content = dates ?: return null
    val info = catalog?.languages?.get(DateDrill.answerLanguage(content, reverse)) ?: return null
    val config = DateDrillRunConfig(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = AnswerNormalizer.drill(info),
        standingRecord = standingRecord,
        cleared = cleared,
    )
    return DateDrillFlow(
        start = DateDrillRun.openAt(config, sprosse, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

package net.spross.app

import kotlin.random.Random
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.CountryDrill
import net.spross.kern.trainer.CountryDrillIntent
import net.spross.kern.trainer.CountryDrillRun
import net.spross.kern.trainer.CountryDrillRunConfig
import net.spross.kern.trainer.CountryDrillRunState

/**
 * One atlas run as this platform holds it — the third sibling of [TrainerFlow] and
 * [LetterDrillFlow], over kern's own [CountryDrillRun].
 *
 * Everything decidable is kern's: which question a Sprosse may ask, which row is drawn, what a
 * typed name earns, how far one answer moves the ramp, which beat is armed. What is left
 * here is the text standing in the field and the beat itself.
 *
 * The atlas asks nothing off tiles, so a question is only ever written.
 *
 * Stateless like both its siblings: no review is ever booked and the box is never read at
 * all — the material is the catalog's atlas, not the learner's own words. The one thing
 * that outlives a run is the furthest Sprosse it stood on, which the page that started it
 * files ([TrainerStore.bookSprosse]).
 */
class CountryDrillFlow(
    start: CountryDrillRunState,
    rng: Random,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    onSilence: () -> Unit = {},
    screenReaderOn: () -> Boolean = { false },
) : TypedDrillFlow<CountryDrillRunState, CountryDrillIntent>(
    start, rng, onTone, onReleaseFocus, onSilence, screenReaderOn,
) {
    override fun reduce(state: CountryDrillRunState, intent: CountryDrillIntent, rng: Random) =
        CountryDrillRun.reduce(state, intent, rng).let { DrillStep(it.state, it.effects) }

    override fun closeRun(state: CountryDrillRunState, standingRecord: Int): DrillEnd<CountryDrillRunState> {
        val closed = CountryDrillRun.close(state, standingRecord)
        return DrillEnd(
            closed.state,
            closed.effects,
            TypedDrillClose(closed.summary, closed.bestLevel, closed.clearedSprossen),
        )
    }

    override fun index(state: CountryDrillRunState) = state.index

    override fun finished(state: CountryDrillRunState) = state.finished

    override fun owesAnswer(state: CountryDrillRunState) = state.owesAnswer

    override fun inputChanged(text: String) = CountryDrillIntent.InputChanged(text)

    override fun submit(text: String) = CountryDrillIntent.Submit(text)

    override fun reveal() = CountryDrillIntent.Reveal

    override fun confirmPending() = CountryDrillIntent.ConfirmPending

    override fun advanceElapsedIntent() = CountryDrillIntent.AdvanceElapsed

    /**
     * The run as the shared typed-drill screen reads it. The one rule about a picture — when
     * a withheld flag comes back — is the card's; this only hands over what kern drew.
     */
    override fun view(chrome: Chrome): TypedDrillView = TypedDrillView(
        index = state.index,
        level = state.level,
        streak = state.streak,
        bestStreak = state.bestStreak,
        outcomes = state.outcomes,
        tally = state.tally,
        feedback = state.feedback,
        showsAnswer = state.showsAnswer,
        offersFinish = state.offersFinish,
        otherWord = state.otherWord,
        answerLanguage = state.answerLanguage,
        prompt = TypedDrillPrompt(
            ask = chrome.countryAsk(state.task.kind),
            text = state.task.promptText,
            // A flag is written in no language, so it is tagged with none.
            language = if (state.task.promptText == null) null else state.promptLanguage,
            display = state.task.display,
            gloss = state.task.gloss,
            emoji = state.task.promptEmoji,
            emojiIsGiveaway = state.task.emojiIsGiveaway,
        ),
    )
}

/**
 * The run the atlas page opens, or null before the catalog has landed.
 *
 * The normalizer is the STRICT drill one, built for the language the answer is owed in —
 * which is the learner's OWN language on a reversed run, so the direction is settled here
 * rather than assumed to be the target.
 */
fun AppModel.newCountryDrill(
    reverse: Boolean,
    fast: Boolean,
    /** The Sprosse the run opens on — the page's call ([net.spross.kern.trainer.TrainerMode.entrySprosse] or a tap). */
    level: Int,
    onTone: (ToneKind) -> Unit = {},
    onReleaseFocus: () -> Unit = {},
    rng: Random = Random.Default,
): CountryDrillFlow? {
    val content = atlas ?: return null
    val info = catalog?.languages?.get(CountryDrill.answerLanguage(content, reverse)) ?: return null
    val config = CountryDrillRunConfig(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = AnswerNormalizer.drill(info),
    )
    return CountryDrillFlow(
        start = CountryDrillRun.openAt(config, level, rng),
        rng = rng,
        onTone = onTone,
        onReleaseFocus = onReleaseFocus,
        onSilence = { pronouncer.stop() },
        screenReaderOn = { pronouncer.readsScreenAloud },
    )
}

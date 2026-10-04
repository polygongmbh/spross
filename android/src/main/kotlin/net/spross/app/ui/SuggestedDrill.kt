package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.TrainerStore
import net.spross.app.offers
import net.spross.app.trainerHubOffered
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.model.Language
import net.spross.kern.trainer.CountryDrill
import net.spross.kern.trainer.DateDrill
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.DrillSuggestion
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.SentenceScrambleAvailability
import net.spross.kern.trainer.WordScrambleAvailability

/**
 * The one drill Home names (`docs/drills.md` § The suggestion), which leads the day's card
 * wherever it is named ([DrillLeadCard]).
 *
 * WHICH drill, WHEN and WHY are kern's [DrillSuggestion]; this side reads the stores it is
 * asked about and words the answer.
 */
/** Kern's pick for [standing], while one is named. */
@Composable
fun rememberSuggestedDrill(model: AppModel, standing: HomeStanding?): DrillSuggestion.Pick? =
    // why: the facts and both scramble reports are walks over the box — asked once per box
    // and offer, never once per frame.
    remember(model.box, standing?.offer, model.dates) { model.suggestedDrill(standing) }

/** Kern's pick among the chips this profile offers, while one is named. */
private fun AppModel.suggestedDrill(standing: HomeStanding?): DrillSuggestion.Pick? {
    val state = box ?: return null
    val offer = standing?.offer ?: return null
    if (!trainerHubOffered || !DrillSuggestion.shown(offer)) return null
    val language = state.joinStamp.target
    val store = trainer.store
    val standings = Drill.entries.filter { offers(it) }.map { drill ->
        DrillSuggestion.Standing(
            drill,
            store.lastRun(DrillSuggestion.lastRunKey(drill, language)),
            ladder(drill, language, state.joinStamp.source, dates),
        )
    }
    return DrillSuggestion.suggest(
        standings,
        DrillSuggestion.BoxFacts.of(state),
        System.currentTimeMillis(),
        TimeZone.getDefault().id,
        language,
    )
}

/** What each drill has filed of its ladder, in the shape kern weighs it. The letter drill files none. */
private fun AppModel.ladder(
    drill: Drill,
    language: Language,
    source: Language,
    dates: DateDrillContent?,
): DrillSuggestion.Ladder? {
    val store = trainer.store
    val state = box ?: return null
    // A cleared ladder is read the way its run opens: forward.
    fun cleared(key: String, top: Int) =
        DrillSuggestion.Ladder.cleared(store.cleared(NumbersMode.clearedKey(key, false)), top)
    return when (drill) {
        Drill.Numbers -> DrillSuggestion.Ladder.numbers(store.ladder(language), language)
        Drill.Letters -> null
        Drill.Countries -> cleared(TrainerStore.countriesKey(source, language), CountryDrill.MAX_LEVEL)
        Drill.Dates -> dates?.let { cleared(TrainerStore.datesKey(source, language), DateDrill.maxLevel(it, false)) }
        Drill.WordScramble ->
            cleared(TrainerStore.wordScrambleKey(language), WordScrambleAvailability.report(state).maxLevel)
        Drill.SentenceScramble ->
            cleared(TrainerStore.sentenceScrambleKey(language), SentenceScrambleAvailability.report(state).maxLevel)
    }
}

internal fun reason(chrome: Chrome, pick: DrillSuggestion.Pick): String = when (pick.reason) {
    DrillSuggestion.Reason.NewScript -> chrome.homeSuggestionReasonNewScript
    DrillSuggestion.Reason.EarlyNumbers -> chrome.homeSuggestionReasonEarlyNumbers
    DrillSuggestion.Reason.WordsSettled -> chrome.homeSuggestionReasonWordsSettled
    DrillSuggestion.Reason.NeverRun -> chrome.homeSuggestionReasonNeverRun
    DrillSuggestion.Reason.NotLately -> chrome.homeSuggestionReasonNotLately.format(pick.daysSinceRun)
    DrillSuggestion.Reason.Variety -> chrome.homeSuggestionReasonVariety
}

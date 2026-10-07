package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.offers
import net.spross.app.trainerHubOffered
import net.spross.kern.session.HomeStanding
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.DrillLadders
import net.spross.kern.trainer.DrillSuggestion

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
            DrillLadders.ladder(
                drill,
                state.joinStamp.source,
                language,
                state,
                catalog?.oppositePairs.orEmpty(),
                dates,
                store,
            ),
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

internal fun reason(chrome: Chrome, pick: DrillSuggestion.Pick): String = when (pick.reason) {
    DrillSuggestion.Reason.NewScript -> chrome.homeSuggestionReasonNewScript
    DrillSuggestion.Reason.EarlyNumbers -> chrome.homeSuggestionReasonEarlyNumbers
    DrillSuggestion.Reason.WordsSettled -> chrome.homeSuggestionReasonWordsSettled
    DrillSuggestion.Reason.NeverRun -> chrome.homeSuggestionReasonNeverRun
    DrillSuggestion.Reason.NotLately -> chrome.homeSuggestionReasonNotLately.format(pick.daysSinceRun)
    DrillSuggestion.Reason.Variety -> chrome.homeSuggestionReasonVariety
}

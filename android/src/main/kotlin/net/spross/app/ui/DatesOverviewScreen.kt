package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.dateSprosse
import net.spross.kern.trainer.DateDrill

/**
 * The Dates entry: the calendar as the two languages name it, and the place its drill is
 * started from.
 *
 * The page itself is [TypedDrillOverview], shared with the atlas; what is here is the
 * calendar's own words, its own ladder and the table under it.
 *
 * Unlike the atlas the ladder here is not one fixed list — its height is the pair's own
 * and the direction's (`docs/drills.md`) — so the rows are drawn from kern's own
 * [DateDrill.kinds] per Sprosse rather than from a list of fixed length.
 */
@Composable
fun DatesOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    // The join is the registry: no calendars for this pair, no page — and the chip that
    // opens it gates on the same predicate, so this is a closed door rather than a screen.
    val content = model.dates ?: return
    val standing = model.trainer.dates
    TypedDrillOverview(
        model = model,
        ladder = TypedDrillLadder(
            title = chrome.datesTitle.format(model.languageName(content.target)),
            standing = standing,
            fastHint = chrome.trainerModifierFastHint.format(DateDrill.winsToAdvance(fast = false)),
            reverseHint = { reverse ->
                // Two sentences, not one: turned round the calendar asks for a DATE, which is
                // written in digits rather than in either language.
                val line = if (reverse) chrome.datesReverseHintBack else chrome.datesReverseHint
                reverseHint(model, line, content.source, content.target, reverse)
            },
            ceiling = { reverse -> DateDrill.maxLevel(content, reverse) },
            fastOpen = { reverse -> DateDrill.fastUnlocked(standing.bestSprosse, content, reverse) },
            // The wordings are keyed by KIND, not by row: the ladder has no fixed length.
            sprosse = { sprosse, reverse -> chrome.dateSprosse(DateDrill.kinds(content, sprosse, reverse)) },
            start = model::startDateDrill,
        ),
    ) {
        DateReferenceSection(model, content, chrome)
    }
}

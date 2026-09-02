package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.dateSprosse
import net.spross.app.dateSprosseHint
import net.spross.kern.trainer.DateDrill

/**
 * The "Datum" entry: the calendar as the two languages name it, and the place its drill is
 * started from.
 *
 * The page itself is [TypedDrillOverview], shared with the atlas; what is here is the
 * calendar's own words, its own ladder and the table under it.
 *
 * Unlike its siblings the ladder here is not one fixed list: how tall it is depends on
 * what the pair's content carries (no `dateWithYear` pattern, no year Sprosse) and which way
 * round the run asks (reversed, only the name Sprossen stand). The rows are therefore drawn
 * from kern's own [DateDrill.kinds] per Sprosse.
 */
@Composable
fun DatesOverviewScreen(model: AppModel) {
    val chrome = model.chrome
    // The join is the registry: no calendars for this pair, no page — and the chip that
    // opens it gates on the same predicate, so this is a closed door rather than a screen.
    val content = model.dates ?: return
    val best = model.werkstatt.datesBest
    TypedDrillOverview(
        model = model,
        ladder = TypedDrillLadder(
            title = chrome.datesTitle.format(model.languageName(content.target)),
            pace = chrome.datesPace,
            bestNote = if (best > 0) chrome.datesBest.format(best) else null,
            fastHint = chrome.datesFastHint,
            reverseHint = { reverse ->
                reverseHint(model, chrome.datesReverseHint, content.source, content.target, reverse)
            },
            ceiling = { reverse -> DateDrill.maxLevel(content, reverse) },
            fastOpen = { reverse -> DateDrill.fastUnlocked(best, content, reverse) },
            sprosse = { sprosse, reverse ->
                // The wordings are keyed by KIND, not by row: the ladder has no fixed length.
                val kinds = DateDrill.kinds(content, sprosse, reverse)
                LadderSprosse(chrome.dateSprosse(kinds), chrome.dateSprosseHint(kinds))
            },
            start = model::startDateDrill,
        ),
    ) {
        DateReferenceSection(model, content, chrome)
    }
}

package net.spross.app.ui

import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.TodayReport
import net.spross.kern.box.TomorrowNote
import net.spross.kern.session.HeadlineKind
import net.spross.kern.session.OfferPartKind
import net.spross.kern.session.SessionHeadline
import net.spross.kern.session.SessionOffer
import net.spross.kern.trainer.DayLead

/** Which of Home's four cards the day is standing on. */
enum class HomeCard {
    /** The box could not be read at all; nothing else on the screen means anything. */
    Failure,

    /** There is a round to sit down to. */
    Session,

    /** The day has done most of its work, and the named drill leads ([DayLead.Drill]). */
    Drill,

    /** Nothing due — worked or merely clear, which the card itself distinguishes. */
    Done,
}

/**
 * Which card the day shows: a failure outranks everything (the counts behind it are
 * meaningless), and otherwise kern's [DayLead] says.
 */
fun homeCard(failed: Boolean, lead: DayLead): HomeCard = when {
    failed -> HomeCard.Failure
    lead == DayLead.Round -> HomeCard.Session
    lead == DayLead.Drill -> HomeCard.Drill
    else -> HomeCard.Done
}

/** The separator between the spelled-out parts of an offer or a tally. */
private const val PART_JOIN = " · "

/**
 * The words for the phrasing kern picked. Which kind and which variant is the ROUND's shape
 * ([SessionOffer.headline]) — the same answer on both platforms and across launches — so
 * this only looks the phrasing up, and never re-rolls it.
 */
fun headlineText(chrome: Chrome, headline: SessionHeadline): String {
    val variants = when (headline.kind) {
        HeadlineKind.Reviews -> chrome.headlineReviews
        HeadlineKind.WarmUp -> chrome.headlineWarmUp
        HeadlineKind.NewSet -> chrome.headlineNewSet
        HeadlineKind.StreakReminder -> chrome.headlineStreak
    }
    return variants[headline.variant % variants.size]
}

/**
 * What the round holds, spelled out — "15 Checks · 2 Neue".
 * Which counts it names and in which order is the offer's own rule
 * ([SessionOffer.summaryParts]); a round that names nothing says so in one plain phrase
 * rather than printing zeros.
 */
fun offerSummary(chrome: Chrome, offer: SessionOffer): String {
    val allParts = offer.summaryParts()
    val parts = allParts.map { part ->
        when (part.kind) {
            OfferPartKind.Reviews -> countLine(chrome.homeTallyReviewsOne, chrome.homeTallyReviews, part.count)
            OfferPartKind.Ahead -> countLine(chrome.homeTallyAheadOne, chrome.homeTallyAhead, part.count)
            OfferPartKind.NewCards ->
                if (allParts.size == 1) {
                    chrome.homeTallyNewWordsOnly.format(part.count)
                } else {
                    countLine(chrome.homeTallyNewCardsOne, chrome.homeTallyNewCards, part.count)
                }
        }
    }
    return if (parts.isEmpty()) chrome.homeTallySomeCards else parts.joinToString(PART_JOIN)
}

/**
 * What the day bought, or null on a day that was not worked — an unworked day has a state,
 * not a tally, and [TodayReport.tallyParts] answers that with an empty list.
 */
fun todayTally(chrome: Chrome, report: TodayReport): String? {
    val parts = report.tallyParts().map { part ->
        when (part.kind) {
            TallyPartKind.Introduced -> countLine(chrome.homeTallyNewCardsOne, chrome.homeTallyNewCards, part.count)
            TallyPartKind.Reviewed -> countLine(chrome.homeTallyReviewsOne, chrome.homeTallyReviews, part.count)
            TallyPartKind.Settled -> countLine(chrome.tallySettledOne, chrome.tallySettled, part.count)
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(PART_JOIN)
}

/** What a done day leaves the learner with — which of the three is kern's ([tomorrowNote]). */
fun tomorrowText(chrome: Chrome, note: TomorrowNote, due: Int): String = when (note) {
    TomorrowNote.Queued -> chrome.homeDoneQueued
    TomorrowNote.Empty -> chrome.homeDoneTomorrowFresh
    TomorrowNote.Due -> countLine(chrome.homeDoneTomorrowDueOne, chrome.homeDoneTomorrowDue, due)
}

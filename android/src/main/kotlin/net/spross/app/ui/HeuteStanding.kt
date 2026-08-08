package net.spross.app.ui

import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.TodayReport
import net.spross.kern.box.TomorrowNote
import net.spross.kern.box.endOfTomorrow
import net.spross.kern.box.tomorrowNote
import net.spross.kern.session.OfferPartKind
import net.spross.kern.session.SessionHeadline
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind
import net.spross.kern.session.SessionOffers

/** Which of Heute's four cards the day is standing on. */
enum class HeuteCard {
    /** The box could not be read at all; nothing else on the screen means anything. */
    Failure,

    /** There is a round to sit down to. */
    Session,

    /** Nothing due — worked or merely clear, which the card itself distinguishes. */
    Done,

    /** Nothing is packed yet: the way on is the box, not a round. */
    EmptyBox,
}

/**
 * Which card the day shows, in strict precedence.
 *
 * A failure outranks everything (the counts behind it are meaningless), an offer outranks
 * a done state, and "done" only ever reaches a box with something in it — an empty box is
 * not caught up, it has never been packed.
 */
fun heuteCard(failed: Boolean, offerKind: SessionOfferKind, activeCards: Int): HeuteCard = when {
    failed -> HeuteCard.Failure
    offerKind != SessionOfferKind.Nothing -> HeuteCard.Session
    activeCards > 0 -> HeuteCard.Done
    else -> HeuteCard.EmptyBox
}

/**
 * Everything Heute asks the box for at one instant, asked once.
 *
 * The screen recomposes on every tap that touches state, and each of these is a walk over
 * the box; reading them together keeps the day's card, its tally and its fine print
 * describing the same moment rather than three moments a frame apart.
 */
data class HeuteStanding(
    val offer: SessionOffer,
    val today: TodayReport,
    val tomorrow: TomorrowNote,
    /** What falls due inside tomorrow — kern's horizon, never a local-midnight rederivation. */
    val tomorrowDue: Int,
    val canPracticeMore: Boolean,
) {
    companion object {
        fun of(state: BoxState, nowEpochMillis: Long, tzId: String): HeuteStanding {
            val horizon = endOfTomorrow(nowEpochMillis, tzId).toEpochMilliseconds()
            val due = BoxEngine.dueNow(state, horizon).size
            return HeuteStanding(
                offer = SessionOffers.offer(state, nowEpochMillis, tzId),
                today = BoxEngine.today(state, nowEpochMillis, tzId),
                tomorrow = tomorrowNote(SessionOffers.packedWordsPending(state), due),
                tomorrowDue = due,
                canPracticeMore = SessionOffers.canPracticeMore(state, nowEpochMillis, tzId),
            )
        }
    }
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
        SessionOfferKind.Reviews -> chrome.headlineReviews
        SessionOfferKind.WarmUp -> chrome.headlineWarmUp
        // Kern folds the empty round's kind into FreshSet before it gets here; naming it
        // anyway keeps every path off a missing phrasing.
        SessionOfferKind.FreshSet, SessionOfferKind.Nothing -> chrome.headlineFreshSet
    }
    return variants[headline.variant % variants.size]
}

/**
 * What the round holds, spelled out — "15 Wiederholungen · 2 Frischlinge".
 * Which counts it names and in which order is the offer's own rule
 * ([SessionOffer.summaryParts]); a round that names nothing says so in one plain phrase
 * rather than printing zeros.
 */
fun offerSummary(chrome: Chrome, offer: SessionOffer): String {
    val parts = offer.summaryParts().map { part ->
        when (part.kind) {
            OfferPartKind.Reviews -> countLine(chrome.dayReviewsOne, chrome.dayReviews, part.count)
            OfferPartKind.Ahead -> countLine(chrome.dayAheadOne, chrome.dayAhead, part.count)
            OfferPartKind.Fresh -> countLine(chrome.dayNewCardsOne, chrome.dayNewCards, part.count)
        }
    }
    return if (parts.isEmpty()) chrome.sessionSomeCards else parts.joinToString(PART_JOIN)
}

/**
 * What the day bought, or null on a day that was not worked — an unworked day has a state,
 * not a tally, and [TodayReport.tallyParts] answers that with an empty list.
 */
fun todayTally(chrome: Chrome, report: TodayReport): String? {
    val parts = report.tallyParts().map { part ->
        when (part.kind) {
            TallyPartKind.Reviews -> countLine(chrome.dayReviewsOne, chrome.dayReviews, part.count)
            TallyPartKind.Introduced -> countLine(chrome.dayNewCardsOne, chrome.dayNewCards, part.count)
            TallyPartKind.Consolidated -> chrome.dayConsolidated.format(part.count)
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(PART_JOIN)
}

/** What a done day leaves the learner with — which of the three is kern's ([tomorrowNote]). */
fun tomorrowText(chrome: Chrome, note: TomorrowNote, due: Int): String = when (note) {
    TomorrowNote.Packed -> chrome.tomorrowPacked
    TomorrowNote.Fresh -> chrome.tomorrowFresh
    TomorrowNote.Due -> chrome.tomorrowDue.format(due)
}

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
import net.spross.kern.session.HeadlineKind
import net.spross.kern.session.OfferPartKind
import net.spross.kern.session.SessionHeadline
import net.spross.kern.session.SessionOffer
import net.spross.kern.session.SessionOfferKind
import net.spross.kern.session.SessionOffers

/** Which of Heute's three cards the day is standing on. */
enum class HeuteCard {
    /** The box could not be read at all; nothing else on the screen means anything. */
    Failure,

    /** There is a round to sit down to. */
    Session,

    /** Nothing due — worked or merely clear, which the card itself distinguishes. */
    Done,
}

/**
 * Which card the day shows, in strict precedence.
 *
 * A failure outranks everything (the counts behind it are meaningless), and an offer outranks
 * a done state.
 */
fun heuteCard(failed: Boolean, offerKind: SessionOfferKind): HeuteCard = when {
    failed -> HeuteCard.Failure
    offerKind != SessionOfferKind.Nothing -> HeuteCard.Session
    else -> HeuteCard.Done
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
    /** The line the day's card leads with — clock-dependent, so it is read at this instant too. */
    val headline: SessionHeadline,
    val today: TodayReport,
    val tomorrow: TomorrowNote,
    /** What falls due inside tomorrow — kern's horizon, never a local-midnight rederivation. */
    val tomorrowDue: Int,
    val canPracticeMore: Boolean,
) {
    companion object {
        /**
         * [canPracticeMore] is handed in rather than asked for: it composes a whole
         * round, and the model already took that answer for the same box
         * (`AppModel.canPracticeExtra`) — asking again composes it twice.
         */
        fun of(
            state: BoxState,
            nowEpochMillis: Long,
            tzId: String,
            canPracticeMore: Boolean,
        ): HeuteStanding {
            val horizon = endOfTomorrow(nowEpochMillis, tzId).toEpochMilliseconds()
            // why: the SIZE of the pile, so nothing composes its order — the shuffle
            // keys and the sort behind `dueNow` are thrown away for an integer.
            val due = BoxEngine.dueCount(state, horizon)
            val offer = SessionOffers.offer(state, nowEpochMillis, tzId)
            return HeuteStanding(
                offer = offer,
                headline = offer.headline(nowEpochMillis, tzId),
                today = BoxEngine.today(state, nowEpochMillis, tzId),
                tomorrow = tomorrowNote(SessionOffers.packedWordsPending(state), due),
                tomorrowDue = due,
                canPracticeMore = canPracticeMore,
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
        HeadlineKind.Reviews -> chrome.headlineReviews
        HeadlineKind.WarmUp -> chrome.headlineWarmUp
        HeadlineKind.FreshSet -> chrome.headlineFreshSet
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
            OfferPartKind.Reviews -> countLine(chrome.dayReviewsOne, chrome.dayReviews, part.count)
            OfferPartKind.Ahead -> countLine(chrome.dayAheadOne, chrome.dayAhead, part.count)
            OfferPartKind.Fresh ->
                if (allParts.size == 1) {
                    chrome.dayNewWordsOnly.format(part.count)
                } else {
                    countLine(chrome.dayNewCardsOne, chrome.dayNewCards, part.count)
                }
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

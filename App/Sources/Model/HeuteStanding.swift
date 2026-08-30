import Foundation
import SprossKern

/// Every kern answer the Heute screen puts on the page, taken together.
///
/// Each of these is a walk of the box and three of them compose a whole round,
/// so a screen that asked for them one at a time — from computed properties, in
/// a body SwiftUI re-evaluates freely — composed the day's session three times
/// and counted the backlog four more before it could draw a single frame.
///
/// They are read together, so they are computed together: once, whenever
/// something that could move them moves (`AppModel.refreshStats`).
struct HeuteStanding {
    /// Today's round as kern classified it.
    let offer: SessionOffer
    /// What the learner did today — reviews, first meetings, words consolidated.
    let today: TodayReport?
    /// Whether there is a round to sit down to. Due work counts even where the
    /// composed round cannot carry it, so a capped backlog never reads as "nothing".
    let sessionAvailable: Bool
    /// Whether a round the learner ASKS for would yield anything.
    let canPracticeMore: Bool
    /// Whether words the learner packed are still waiting to enter a round.
    let hasPackedWords: Bool
    /// Cards that will be due by tomorrow evening — the preview on the done state.
    let tomorrowDue: Int
    /// The day this standing was taken, so a screen left open past midnight
    /// can tell that it has (`AppModel.refreshIfDayTurned`).
    let day: String

    /// Nothing loaded: the shape Heute draws before a box exists.
    // why: computed, not stored — it carries Kern values, which are not Sendable,
    // and a stored static would have to be.
    static var none: HeuteStanding {
        HeuteStanding(
            offer: SessionOffer(kind: .nothing, reviews: 0, dueHeldBack: 0, ahead: 0, fresh: 0,
                                shortRound: 0, doneToday: 0, streakExposed: false),
            today: nil, sessionAvailable: false, canPracticeMore: false,
            hasPackedWords: false, tomorrowDue: 0, day: "")
    }

    static func of(box: BoxState, nowEpochMillis: Int64, tzId: String) -> HeuteStanding {
        HeuteStanding(
            offer: SessionOffers.shared.offer(state: box, nowEpochMillis: nowEpochMillis, tzId: tzId),
            today: BoxEngine.shared.today(state: box, nowEpochMillis: nowEpochMillis, tzId: tzId),
            sessionAvailable: SessionOffers.shared.sessionAvailable(
                state: box, nowEpochMillis: nowEpochMillis, tzId: tzId),
            canPracticeMore: SessionOffers.shared.canPracticeMore(
                state: box, nowEpochMillis: nowEpochMillis, tzId: tzId),
            hasPackedWords: SessionOffers.shared.packedWordsPending(state: box),
            tomorrowDue: Int(BoxEngine.shared.dueCount(
                state: box,
                nowEpochMillis: endOfTomorrow(nowEpochMillis: nowEpochMillis, tzId: tzId)
                    .toEpochMilliseconds())),
            day: dayKey(nowEpochMillis: nowEpochMillis, tzId: tzId))
    }
}

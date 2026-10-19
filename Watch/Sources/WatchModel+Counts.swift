import Foundation

/// The home screen's counts, asked at a moment the caller's timeline names.
extension WatchModel {
    /// The clock is the caller's, so a view that redraws on a timeline counts
    /// the cards that came due while nobody touched the watch.
    func roundCount(at now: Date) -> Int {
        snapshot?.roundEntries(now: now).count ?? 0
    }

    func tomorrowDueCount(at now: Date) -> Int {
        snapshot?.tomorrowDueCount(now: now, calendar: calendar) ?? 0
    }

    /// The moments the counts can change without a sync — each card's due
    /// moment — led by `now`, since a timeline renders its first date even
    /// when it lies ahead.
    func countChanges(after now: Date) -> [Date] {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        let dues = (snapshot?.entries ?? [])
            .map(\.due)
            .filter { $0 > nowMillis }
            .sorted()
            .map { Date(timeIntervalSince1970: Double($0) / 1000) }
        return [now] + dues
    }
}

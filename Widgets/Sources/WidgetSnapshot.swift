import Foundation

/// Decode-only mirror of Kern's `WidgetSnapshotBuilder` JSON, written by the
/// app on every persist. The widget extension links no Kotlin (no catalog in
/// its bundle, tight extension memory cap) — everything it renders is
/// pre-resolved phone-side; only `dueCount(now:)`, `averageRetrievability(now:)`
/// and the streak walk run here at render time.
struct WidgetSnapshot: Codable {

    /// One pre-resolved exposure row (TARGET-side text; ♀ baked into
    /// `sourceText`; `articleTint` doubles as the article word).
    struct Entry: Codable {
        var cardId: String
        var text: String
        var sourceText: String
        var emoji: String?
        var articleTint: String?
    }

    /// One active card schedule — render-time stat input.
    struct CardInfo: Codable {
        var cardId: String
        /// Epoch milliseconds.
        var due: Int64
        var stability: Double
        /// Epoch milliseconds of the last review (or the card's add date).
        var lastReview: Int64
        /// Whether the card sits in the Review phase (retrievability input).
        var review: Bool
    }

    struct Day: Codable {
        var reviews: Int
        var introduced: Int
        var activeCount: Int
    }

    var schemaVersion: Int
    var entries: [Entry]
    var cards: [CardInfo]
    /// Trailing ~70 days, keyed by ISO `yyyy-MM-dd`.
    var dailyStats: [String: Day]

    // MARK: - Render-time stats

    func dueCount(now: Date) -> Int {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return cards.filter { $0.due <= nowMillis }.count
    }

    /// FSRS-6 trainable decay default (`w20`). DELIBERATE duplication of the
    /// Kern constant (kern-design.md §7): the widget cannot link the engine,
    /// so the retrievability power curve is re-implemented here verbatim.
    static let w20 = 0.1542

    /// Mean FSRS retrievability over Review-phase cards, elapsed measured
    /// from each card's last review — mirrors Kern `Statistics`/`Fsrs`:
    /// R(t, S) = (1 + factor · t / S)^(−w20), factor = 0.9^(−1/w20) − 1,
    /// S floored at 0.001, t at 0. Nil when no card is in Review yet.
    func averageRetrievability(now: Date) -> Double? {
        let review = cards.filter(\.review)
        guard !review.isEmpty else { return nil }
        let decay = -Self.w20
        let factor = pow(0.9, 1.0 / decay) - 1.0
        let nowMillis = Double(now.timeIntervalSince1970 * 1000)
        let sum = review.reduce(0.0) { acc, card in
            let elapsedDays = max(0.0, (nowMillis - Double(card.lastReview)) / 86_400_000)
            let s = max(card.stability, 0.001)
            return acc + pow(1.0 + factor * elapsedDays / s, decay)
        }
        return sum / Double(review.count)
    }

    /// Streak walk — DELIBERATE duplication of Kern `Statistics.streak`
    /// (kern-design.md §7). Walk back from today: today without reviews
    /// neither breaks the streak nor consumes forgiveness (the day isn't
    /// over); afterwards exactly ONE 0-review day is forgiven, the next miss
    /// ends the streak. Forgiven days do not increment the count.
    func streak(now: Date, timeZone: TimeZone = .current) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        var count = 0
        var forgivenessLeft = 1
        var day = calendar.startOfDay(for: now)
        var isToday = true
        while true {
            let reviews = dailyStats[Self.dayKey(day, calendar: calendar)]?.reviews ?? 0
            if reviews > 0 {
                count += 1
            } else if !isToday {
                if forgivenessLeft > 0 { forgivenessLeft -= 1 } else { break }
            }
            isToday = false
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day)
            else { break }
            day = previous
        }
        return count
    }

    /// Kern day keys are ISO `yyyy-MM-dd` regardless of the device calendar.
    private static func dayKey(_ date: Date, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d",
                      parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}

/// Reads the app-written snapshot from the shared App-Group container
/// (`box/widget-snapshot.json`, next to the box documents).
enum WidgetSnapshotReader {
    static let appGroup = "group.net.spross.app"

    static func load() -> WidgetSnapshot? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup) else { return nil }
        let url = container.appendingPathComponent("box", isDirectory: true)
            .appendingPathComponent("widget-snapshot.json")
        guard let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data),
              snapshot.schemaVersion == 1 else { return nil }
        return snapshot
    }
}

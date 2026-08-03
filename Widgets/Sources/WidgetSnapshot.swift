import Foundation

/// Decode-only mirror of Kern's `WidgetSnapshotBuilder` JSON, written by the
/// app on every persist. The widget extension links no Kotlin (no catalog in
/// its bundle, tight extension memory cap) — everything it renders is
/// pre-resolved phone-side; only `dueCount(now:)` and the streak walk run here
/// at render time.
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

    /// One active card schedule — the render-time due-count input.
    struct CardInfo: Codable {
        var cardId: String
        /// Epoch milliseconds.
        var due: Int64
    }

    struct Day: Codable {
        var reviews: Int
        var introduced: Int
        var activeCount: Int
    }

    var schemaVersion: Int
    var entries: [Entry]
    var cards: [CardInfo]
    /// Active cards that have consolidated (kern `Statistics.isConsolidated`); resolved
    /// phone-side because, unlike due dates, it does not move with the clock.
    var consolidatedCount: Int
    /// Trailing ~70 days, keyed by ISO `yyyy-MM-dd`.
    var dailyStats: [String: Day]

    // MARK: - Render-time stats

    func dueCount(now: Date) -> Int {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return cards.filter { $0.due <= nowMillis }.count
    }

    /// Streak walk — DELIBERATE duplication of Kern `Statistics.streak`
    /// (kern/README.md §7). Walk back from today: a missed day is bridged,
    /// two in a row end the run, and a bridged day does not increment the
    /// count. Today without reviews is not a miss at all (the day isn't over).
    func streak(now: Date, timeZone: TimeZone = .current) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        var count = 0
        var previousWasMiss = false
        var day = calendar.startOfDay(for: now)
        var isToday = true
        while true {
            let reviews = dailyStats[Self.dayKey(day, calendar: calendar)]?.reviews ?? 0
            if reviews > 0 {
                count += 1
                previousWasMiss = false
            } else if !isToday {
                if previousWasMiss { break }
                previousWasMiss = true
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
              snapshot.schemaVersion == 2 else { return nil }
        return snapshot
    }
}

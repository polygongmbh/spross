import Foundation

/// Decode-only mirror of Kern's `WidgetSnapshotBuilder` JSON, written by the
/// app on every snapshot save. The widget extension links no Kotlin (no catalog in
/// its bundle, tight extension memory cap) — everything it renders is
/// pre-resolved phone-side, the streak for every day it may be drawn on included;
/// only `dueCount(now:)` runs here at render time.
struct WidgetSnapshot: Decodable {

    /// One pre-resolved exposure row (TARGET-side text; ♀ baked into
    /// `sourceText`; `gender` is what the `article` marks, and what tints the row).
    struct Entry: Decodable {
        var cardId: String
        var text: String
        var sourceText: String
        var emoji: String?
        var article: String?
        var gender: SnapshotGender?
    }

    /// One active card schedule — the render-time due-count input.
    struct CardInfo: Decodable {
        var cardId: String
        /// Epoch milliseconds.
        var due: Int64
    }

    struct Day: Decodable {
        var reviews: Int
    }

    /// The streak and its health on one day, as kern resolved them.
    struct StreakDay: Decodable {
        var streak: Int
        var health: StreakHealth
    }

    /// The one version this build reads (kern `WidgetSnapshotBuilder.SCHEMA_VERSION`).
    static let currentSchemaVersion = 9

    var schemaVersion: Int
    /// The language the widget's chrome is written in, the one the app's own chrome follows.
    var chromeLanguage: String
    var entries: [Entry]
    var cards: [CardInfo]
    /// Active cards that have settled (kern `Statistics.hasSettled`); resolved
    /// phone-side because, unlike due dates, it does not move with the clock.
    var allSettledCount: Int
    /// How many trailing days the activity strip shows (kern `ACTIVITY_WINDOW_DAYS`).
    var activityWindowDays: Int
    /// Answered days among the trailing fortnight and the day before, keyed by ISO `yyyy-MM-dd`.
    var dailyStats: [String: Day]
    /// The streak for each day from the snapshot's own through the first with no run left
    /// (kern `streakTimeline`), keyed by ISO `yyyy-MM-dd`.
    var streakByDay: [String: StreakDay]

    // MARK: - Render-time stats

    func dueCount(now: Date) -> Int {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return cards.filter { $0.due <= nowMillis }.count
    }

    /// The trailing `activityWindowDays` days, oldest first, today last — the header strip's input.
    /// A pure lookup over the day counts the snapshot carries.
    func recentDays(now: Date, timeZone: TimeZone = .current) -> [ActivityDay] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let today = calendar.startOfDay(for: now)
        return (0..<activityWindowDays).reversed().compactMap { offset in
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { return nil }
            return ActivityDay(day: day,
                               reviews: dailyStats[Self.dayKey(day, calendar: calendar)]?.reviews ?? 0,
                               isToday: offset == 0)
        }
    }

    /// The streak on `now`'s day: kern's entry for it, the last one past the end,
    /// the first one before the start (kern `streakOn`).
    func streakDay(now: Date, timeZone: TimeZone = .current) -> StreakDay {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let today = Self.dayKey(now, calendar: calendar)
        let days = streakByDay.keys.sorted()
        guard let key = days.last(where: { $0 <= today }) ?? days.first,
              let day = streakByDay[key] else { return StreakDay(streak: 0, health: .noRun) }
        return day
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
    static let appGroup = AppGroup.identifier

    static func load() -> WidgetSnapshot? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup) else { return nil }
        let url = container.appendingPathComponent("box", isDirectory: true)
            .appendingPathComponent("widget-snapshot.json")
        guard let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data),
              snapshot.schemaVersion == WidgetSnapshot.currentSchemaVersion else { return nil }
        return snapshot
    }
}

import Foundation

/// Decode-only mirror of Kern's `WidgetSnapshotBuilder` JSON, written by the
/// app on every snapshot save. The widget extension links no Kotlin (no catalog in
/// its bundle, tight extension memory cap) — everything it renders is
/// pre-resolved phone-side, the streak and the activity strip for every day it may be drawn on included;
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

    /// One bar of the header strip as kern sized it (`WidgetBar`): height in points, fill opacity.
    struct Bar: Decodable, Hashable {
        var reviews: Int
        var height: Double
        var fillOpacity: Double

        var worked: Bool { reviews > 0 }
    }

    /// The streak and its health on one day, as kern resolved them,
    /// with the grade the flame wears for that health (kern `StreakHealth.flameOpacity`/`flameSaturation`).
    struct StreakDay: Decodable {
        var streak: Int
        var health: StreakHealth
        var flameOpacity: Double
        var flameSaturation: Double
    }

    /// The one version this build reads (kern `WidgetSnapshotBuilder.SCHEMA_VERSION`).
    static let currentSchemaVersion = 11

    var schemaVersion: Int
    /// The language the widget's chrome is written in, the one the app's own chrome follows.
    var chromeLanguage: String
    var entries: [Entry]
    var cards: [CardInfo]
    /// Active cards that have settled (kern `Statistics.hasSettled`); resolved
    /// phone-side because, unlike due dates, it does not move with the clock.
    var allSettledCount: Int
    /// The header strip's bars, oldest first and today last, for each day from the snapshot's own
    /// through the first whose window holds no answer (kern `activityTimeline`), keyed by ISO `yyyy-MM-dd`.
    var activityByDay: [String: [Bar]]
    /// The height the strip's row reserves, its tallest bar's (kern `ActivityScale.widget`).
    var activityHeight: Double
    /// The streak for each day from the snapshot's own through the first with no run left
    /// (kern `streakTimeline`), keyed by ISO `yyyy-MM-dd`.
    var streakByDay: [String: StreakDay]

    // MARK: - Render-time stats

    func dueCount(now: Date) -> Int {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return cards.filter { $0.due <= nowMillis }.count
    }

    /// The header strip's bars on `now`'s day; empty only for a snapshot with no days, which kern never writes.
    func activityBars(now: Date, timeZone: TimeZone = .current) -> [Bar] {
        Self.onRenderDay(activityByDay, now: now, timeZone: timeZone) ?? []
    }

    /// The streak on `now`'s day. Nil only for a snapshot with no
    /// streak days at all, which kern never writes.
    func streakDay(now: Date, timeZone: TimeZone = .current) -> StreakDay? {
        Self.onRenderDay(streakByDay, now: now, timeZone: timeZone)
    }

    /// A render-day timeline's entry for `now`'s day: kern's entry for it, the last one past the end,
    /// the first one before the start (kern `onRenderDay`).
    private static func onRenderDay<T>(_ timeline: [String: T], now: Date, timeZone: TimeZone) -> T? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let today = dayKey(now, calendar: calendar)
        let days = timeline.keys.sorted()
        guard let key = days.last(where: { $0 <= today }) ?? days.first else { return nil }
        return timeline[key]
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

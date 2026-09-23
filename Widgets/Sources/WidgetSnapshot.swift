import Foundation

/// Flame states the widget renders, safest to most urgent.
enum FlameState {
    case lit        // reviewed today — streak safe until tomorrow.
    case dwindling  // not yet today, but yesterday had a review — a miss today just becomes the run's one bridge.
    case atRisk     // not yet today, and yesterday was already the bridge — a miss today ends the run.
    case unlit      // streak is zero — nothing to protect, a bare restart nudge.
}

/// Decode-only mirror of Kern's `WidgetSnapshotBuilder` JSON, written by the
/// app on every persist. The widget extension links no Kotlin (no catalog in
/// its bundle, tight extension memory cap) — everything it renders is
/// pre-resolved phone-side; only `dueCount(now:)` and the streak walk run here
/// at render time.
struct WidgetSnapshot: Codable {

    /// Gap thresholds the flame reads by, in whole days since `lastReviewDate`:
    /// 0 lit, 1 the one bridge day, 2 the bridge already spent, further unlit.
    private enum Gap {
        static let lit = 0
        static let dwindling = 1
        static let atRisk = 2
    }

    /// One pre-resolved exposure row (TARGET-side text; ♀ baked into
    /// `sourceText`; `gender` is what the `article` marks, and what tints the row).
    struct Entry: Codable {
        var cardId: String
        var text: String
        var sourceText: String
        var emoji: String?
        var article: String?
        var gender: SnapshotGender?
    }

    /// One active card schedule — the render-time due-count input.
    struct CardInfo: Codable {
        var cardId: String
        /// Epoch milliseconds.
        var due: Int64
    }

    struct Day: Codable {
        var reviews: Int
    }

    /// The one version this build reads (kern `WidgetSnapshotBuilder.SCHEMA_VERSION`).
    static let currentSchemaVersion = 4

    var schemaVersion: Int
    /// The language the widget's chrome is written in, the one the app's own chrome follows.
    var chromeLanguage: String
    var entries: [Entry]
    var cards: [CardInfo]
    /// Active cards that have consolidated (kern `Statistics.isConsolidated`); resolved
    /// phone-side because, unlike due dates, it does not move with the clock.
    var consolidatedCount: Int
    /// Trailing ~70 days, keyed by ISO `yyyy-MM-dd`.
    var dailyStats: [String: Day]
    /// The streak as of `lastReviewDate` — kern's own `Statistics.streak`, resolved
    /// phone-side. Stands until the run breaks; what decides whether it still stands
    /// is `lastReviewDate`, not this number.
    var streak: Int
    /// ISO `yyyy-MM-dd` of the most recent reviewed day, nil if there has never been
    /// one. The one fact that ages: how many days stand between it and "now" is all
    /// render time still has to ask.
    var lastReviewDate: String?

    // MARK: - Render-time stats

    func dueCount(now: Date) -> Int {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return cards.filter { $0.due <= nowMillis }.count
    }

    /// Trailing `count` days, oldest first, today last — the header strip's input.
    /// A pure lookup: the snapshot already carries ~10 weeks of day counts, so the
    /// strip costs nothing on the wire.
    func recentDays(count: Int, now: Date, timeZone: TimeZone = .current) -> [ActivityDay] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let today = calendar.startOfDay(for: now)
        return (0..<count).reversed().compactMap { offset in
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { return nil }
            return ActivityDay(day: day,
                               reviews: dailyStats[Self.dayKey(day, calendar: calendar)]?.reviews ?? 0,
                               isToday: offset == 0)
        }
    }

    /// `streak`, or 0 once the gap since `lastReviewDate` has spent the one bridge day —
    /// kern computed the number once; this only asks whether it still holds.
    func displayedStreak(now: Date, timeZone: TimeZone = .current) -> Int {
        guard let gap = gapSinceLastReview(now: now, timeZone: timeZone), gap <= Gap.atRisk else { return 0 }
        return streak
    }

    /// The flame's state from the gap alone — no walk, because kern already walked it
    /// as of `lastReviewDate` and nothing about the PAST changes between two renders.
    func flameState(now: Date, timeZone: TimeZone = .current) -> FlameState {
        switch gapSinceLastReview(now: now, timeZone: timeZone) {
        case Gap.lit: .lit
        case Gap.dwindling: .dwindling
        case Gap.atRisk: .atRisk
        default: .unlit
        }
    }

    /// Whole days between `lastReviewDate` and `now`, nil with no review on record.
    /// Never negative — a device clock behind kern's is read as "today", not the future.
    private func gapSinceLastReview(now: Date, timeZone: TimeZone) -> Int? {
        guard let lastReviewDate else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        guard let lastDay = Self.date(fromDayKey: lastReviewDate, calendar: calendar) else { return nil }
        let today = calendar.startOfDay(for: now)
        let gap = calendar.dateComponents([.day], from: lastDay, to: today).day ?? 0
        return max(0, gap)
    }

    /// Kern day keys are ISO `yyyy-MM-dd` regardless of the device calendar.
    private static func dayKey(_ date: Date, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d",
                      parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// The inverse of `dayKey`, at that day's local midnight.
    private static func date(fromDayKey key: String, calendar: Calendar) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        var components = DateComponents()
        components.year = parts[0]
        components.month = parts[1]
        components.day = parts[2]
        return calendar.date(from: components)
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

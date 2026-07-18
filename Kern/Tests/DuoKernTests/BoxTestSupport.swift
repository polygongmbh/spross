import Foundation
import DuoKern

// Shared builders for box-engine scenario tests. Fixed calendar & dates —
// engine APIs always receive explicit now/calendar.

enum Box {
    static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    static func date(_ y: Int, _ m: Int, _ d: Int, _ hour: Int = 12, _ minute: Int = 0) -> Date {
        DateComponents(calendar: calendar, year: y, month: m, day: d,
                       hour: hour, minute: minute).date!
    }

    /// noon, 2026-07-01 — the default "today" for scenarios.
    static let day1 = date(2026, 7, 1)

    static func word(_ n: Int, area: String = "area1", kind: CardKind = .noun) -> Card {
        Card(id: String(format: "w%02d", n), kind: kind, pair: .deSw, area: area,
             german: "g\(n)", translation: "t\(n)")
    }

    static func phrase(_ id: String, components: [String], area: String = "area1") -> Card {
        Card(id: id, kind: .phrase, pair: .deSw, area: area,
             german: id, translation: id, componentIDs: components)
    }

    static func config(maxLearning: Int = 8, dueSoftCap: Int = 30, sessionCap: Int = 30,
                       direction: Direction = .deToTarget) -> BoxConfig {
        BoxConfig(pair: .deSw, direction: direction, maxLearning: maxLearning,
                  dueSoftCap: dueSoftCap, sessionCap: sessionCap)
    }

    static func state(cards: [Card], _ config: BoxConfig = config()) -> BoxState {
        BoxEngine.bootstrap(cards: cards, config: config)
    }

    /// Hand-crafted scheduling entry for scenario setup.
    static func sched(_ cardID: String, direction: Direction = .deToTarget,
                      phase: CardPhase = .review, stability: Double = 10,
                      due: Date, lastReview: Date, lapses: Int = 0,
                      suspended: Bool = false) -> CardScheduling {
        CardScheduling(cardID: cardID, direction: direction, phase: phase,
                       memory: MemoryState(stability: stability, difficulty: 5),
                       due: due, addedAt: lastReview, lapses: lapses, suspended: suspended,
                       log: [ReviewLogEntry(date: lastReview, rating: .good, elapsedDays: 1)])
    }

    static func inject(_ state: inout BoxState, _ entry: CardScheduling) {
        let key = BoxState.schedulingKey(cardID: entry.cardID, direction: entry.direction)
        state.scheduling[key] = entry
    }

    static func dayKey(_ date: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: calendar.startOfDay(for: date))
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}

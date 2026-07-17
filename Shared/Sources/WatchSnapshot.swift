import Foundation
import DuoKern

/// Compact phone → watch state transfer ("snapshot down, events up").
/// The phone is the source of truth; the watch only drains this snapshot's
/// due list and queues answer events back. Compiled into the iOS app,
/// the watch app, and the watch widget extension.
struct WatchSnapshot: Codable, Sendable, Equatable {

    /// Card trimmed to what the watch UI/complication renders.
    struct Card: Codable, Sendable, Equatable, Identifiable {
        var id: String
        var german: String
        var article: String?
        var plural: String?
        var emoji: String?
        var translation: String
        var note: String?
    }

    /// Scheduling trimmed to due-list + exposure ranking inputs,
    /// active direction only.
    struct Scheduling: Codable, Sendable, Equatable {
        var cardID: String
        var direction: Direction
        var phase: CardPhase
        var due: Date?
        var stability: Double?
    }

    var pair: LanguagePair
    var direction: Direction
    var generated: Date
    var cards: [Card]
    var scheduling: [Scheduling]
    /// Card ids the watch already answered against THIS snapshot (queued as
    /// events, removed from the local due list). Always empty when the phone
    /// builds a snapshot.
    var answeredCardIDs: [String] = []

    // MARK: - Building (phone side)

    /// Trim a `BoxState` to the active direction's scheduled, non-suspended
    /// cards. New/unscheduled cards are omitted — the watch never introduces.
    static func make(from state: BoxState, now: Date) -> WatchSnapshot {
        let direction = state.config.direction
        let active = state.scheduling.values
            .filter { $0.direction == direction && !$0.suspended && $0.memory != nil
                      && state.cards[$0.cardID] != nil }
            .sorted { $0.cardID < $1.cardID }
        let cards: [Card] = active.compactMap { sched in
            guard let card = state.cards[sched.cardID] else { return nil }
            return Card(id: card.id, german: card.german, article: card.article,
                        plural: card.plural, emoji: card.emoji,
                        translation: card.translation, note: card.note)
        }
        let scheduling = active.map {
            Scheduling(cardID: $0.cardID, direction: $0.direction, phase: $0.phase,
                       due: $0.due, stability: $0.memory?.stability)
        }
        return WatchSnapshot(pair: state.config.pair, direction: direction,
                             generated: now, cards: cards, scheduling: scheduling)
    }

    // MARK: - Queries (watch side)

    func card(id: String) -> Card? {
        cards.first { $0.id == id }
    }

    /// Due card ids (`due <= now`), oldest first, minus locally answered ones.
    func dueCardIDs(now: Date) -> [String] {
        let answered = Set(answeredCardIDs)
        return scheduling
            .filter { ($0.due ?? .distantFuture) <= now && !answered.contains($0.cardID) }
            .sorted { ($0.due ?? now, $0.cardID) < ($1.due ?? now, $1.cardID) }
            .map(\.cardID)
    }

    /// Cards due by tomorrow evening (mirrors AppModel.tomorrowDueCount).
    func tomorrowDueCount(now: Date, calendar: Calendar) -> Int {
        guard let end = calendar.date(byAdding: .day, value: 2,
                                      to: calendar.startOfDay(for: now)) else { return 0 }
        return dueCardIDs(now: end).count
    }

    /// Attention-worthy cards for the complication: learning/relearning first,
    /// then by lowest stability — mirrors the iOS widget's exposure ranking
    /// (WidgetBoxReader.exposureCards), reimplemented against the snapshot.
    func exposureCards(limit: Int) -> [Card] {
        let ranked = scheduling.sorted { a, b in
            let aLearning = a.phase == .learning || a.phase == .relearning
            let bLearning = b.phase == .learning || b.phase == .relearning
            if aLearning != bLearning { return aLearning }
            return (a.stability ?? 0, a.cardID) < (b.stability ?? 0, b.cardID)
        }
        return ranked.prefix(limit).compactMap { card(id: $0.cardID) }
    }

    // MARK: - JSON

    static func decode(_ data: Data) throws -> WatchSnapshot {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(WatchSnapshot.self, from: data)
    }

    func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(self)
    }
}

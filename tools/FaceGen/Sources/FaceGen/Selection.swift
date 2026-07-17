import DuoKern
import Foundation

/// Picks which cards get rendered.
enum Selection {

    /// First `count` cards of `pair` in seed order.
    static func fromSeed(directory: URL, pair: LanguagePair, count: Int) throws -> [Card] {
        let all = try SeedContent.loadAll(from: directory)
        return Array(
            all.filter { $0.pair == pair }
                .sorted { $0.seedIndex < $1.seedIndex }
                .prefix(count)
        )
    }

    /// Attention-worthy cards for the box's configured direction:
    /// learning/relearning phase first, then review cards by lowest stability,
    /// deterministic tiebreak by card id.
    /// (Mirrors the app's WidgetBoxReader.exposureCards ranking against BoxState —
    /// that code lives in the app target and cannot be imported here.)
    static func fromBox(file: URL, count: Int) throws -> [Card] {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let state = try decoder.decode(BoxState.self, from: Data(contentsOf: file))
        return exposureCards(state: state, limit: count)
    }

    static func exposureCards(state: BoxState, limit: Int) -> [Card] {
        let direction = state.config.direction
        let active = state.scheduling.values
            .filter { $0.direction == direction && !$0.suspended && $0.memory != nil }
        let ranked = active.sorted { a, b in
            let aLearning = a.phase == .learning || a.phase == .relearning
            let bLearning = b.phase == .learning || b.phase == .relearning
            if aLearning != bLearning { return aLearning }
            let aStab = a.memory?.stability ?? 0
            let bStab = b.memory?.stability ?? 0
            return (aStab, a.cardID) < (bStab, b.cardID)
        }
        return ranked.prefix(limit).compactMap { state.cards[$0.cardID] }
    }
}

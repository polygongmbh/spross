import Foundation

extension BoxEngine {

    /// Absorb a fresh seed import into an existing box: new cards appear
    /// (introducible via the normal budget, seed order preserved), changed
    /// cards update their content, and cards that vanished from the seed are
    /// dropped ONLY if never studied — scheduled orphans keep their card so
    /// history and reviews stay intact.
    public static func reconcileSeed(state: BoxState, seed: [Card]) -> BoxState {
        var next = state
        let seedIDs = Set(seed.map(\.id))

        for card in seed where card.pair == state.config.pair {
            next.cards[card.id] = card
        }
        for (id, _) in state.cards where !seedIDs.contains(id) {
            let hasHistory = Direction.allCases.contains { direction in
                state.scheduling[BoxState.schedulingKey(cardID: id, direction: direction)] != nil
            }
            if !hasHistory {
                next.cards.removeValue(forKey: id)
                next.enqueued.removeAll { $0 == id }
            }
        }
        return next
    }
}

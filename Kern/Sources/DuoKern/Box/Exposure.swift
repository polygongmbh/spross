import Foundation

extension BoxEngine {

    /// Cards worth surfacing for passive exposure (home/lock-screen widgets,
    /// watch complications), most-urgent first and fully deterministic. Tiers:
    ///
    ///   0. relearning — just lapsed, reinforce first
    ///   1. new & queued — prime a word before its first study
    ///   2. learning — mid-acquisition
    ///   3. review — weakest (lowest stability) first
    ///   4. upcoming — next automatic new words in seed order, so the list is
    ///      never empty and previews what's coming
    ///
    /// Only the review tier has a memory to rank by; new/queued/upcoming cards
    /// have none, so they keep queue- resp. seed-order. Suspended cards and
    /// locked phrases are excluded. Pure: `now` is unused today but kept for
    /// parity with the rest of the engine and future due-weighting.
    public static func exposureCards(state: BoxState, now: Date, limit: Int) -> [Card] {
        let direction = state.config.direction
        var ranked: [(tier: Int, order: Double, id: String, card: Card)] = []

        // Introduced, active cards → tier by phase, order by stability.
        for sched in state.scheduling.values
            where sched.direction == direction && !sched.suspended && sched.memory != nil {
            guard let card = state.cards[sched.cardID] else { continue }
            let tier: Int
            switch sched.phase {
            case .relearning: tier = 0
            case .learning:   tier = 2
            default:          tier = 3   // review
            }
            ranked.append((tier, sched.memory?.stability ?? 0, sched.cardID, card))
        }

        // Queued-but-unstudied new cards → tier 1, in queue order.
        for (index, id) in enqueuedEligible(state).enumerated() {
            if let card = state.cards[id] { ranked.append((1, Double(index), id, card)) }
        }

        // Preview: the next automatic new words in seed order → tier 4.
        let alreadyRanked = Set(ranked.map(\.id))
        let upcoming = seedOrdered(state.cards.values.filter { card in
            scheduling(state, card.id) == nil && !alreadyRanked.contains(card.id)
                && (card.kind != .phrase || card.componentIDs.isEmpty || isPhraseUnlocked(state, card))
        }).prefix(limit)
        for (index, card) in upcoming.enumerated() {
            ranked.append((4, Double(index), card.id, card))
        }

        return ranked
            .sorted { ($0.tier, $0.order, $0.id) < ($1.tier, $1.order, $1.id) }
            .prefix(limit)
            .map(\.card)
    }
}

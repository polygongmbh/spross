import Foundation

extension BoxEngine {

    /// Which direction to SHOW for this card's next review.
    ///
    /// FIRST exposure always displays the language being learned (the unknown
    /// word is shown, the known one produced) — you can't recall a word you've
    /// never seen. Learner identity is `config.direction`, so first exposure
    /// presents its OPPOSITE; this holds even with `mixedDirections` off.
    /// Afterwards, mixed mode alternates per review with a stable per-card
    /// offset. Memory state stays shared (canonical `config.direction` key).
    public static func presentationDirection(state: BoxState, cardID: String) -> Direction {
        let key = BoxState.schedulingKey(cardID: cardID, direction: state.config.direction)
        let reviews = state.scheduling[key]?.log.count ?? 0
        guard reviews > 0 else { return opposite(of: state.config.direction) }
        guard state.config.mixedDirections else { return state.config.direction }
        let flip = (reviews + Int(stableHash(cardID) % 2)) % 2 == 1
        return flip ? opposite(of: state.config.direction) : state.config.direction
    }

    static func opposite(of direction: Direction) -> Direction {
        direction == .deToTarget ? .targetToDe : .deToTarget
    }

    /// Deterministic across processes (never Swift's seeded `hashValue`) —
    /// FNV-1a over UTF-8.
    static func stableHash(_ s: String) -> UInt64 {
        var hash: UInt64 = 0xcbf29ce484222325
        for byte in s.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x100000001b3
        }
        return hash
    }
}

import Foundation

extension BoxEngine {

    /// Which direction to SHOW for this card's next review. With
    /// `mixedDirections` the direction alternates per review, offset by a
    /// stable per-card hash so a session isn't all-one-way; the very first
    /// exposure of roughly half the cards is still the learner's primary
    /// direction. Memory state stays shared (canonical `config.direction` key).
    public static func presentationDirection(state: BoxState, cardID: String) -> Direction {
        guard state.config.mixedDirections else { return state.config.direction }
        let key = BoxState.schedulingKey(cardID: cardID, direction: state.config.direction)
        let reviews = state.scheduling[key]?.log.count ?? 0
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

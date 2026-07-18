import Foundation

/// Today's composed session. Pure data; ids resolve against `BoxState.cards`.
public struct SessionPlan: Codable, Sendable, Equatable {
    /// Due card ids, oldest due first, capped per design §Session 1.
    public var reviews: [String]
    /// Phrase ids entering via the unlock fast path (within the new budget).
    public var unlockedPhrases: [String]
    /// Remaining new-card candidates (within the new budget).
    public var newWords: [String]

    public var isEmpty: Bool {
        reviews.isEmpty && unlockedPhrases.isEmpty && newWords.isEmpty
    }

    public init(reviews: [String] = [], unlockedPhrases: [String] = [], newWords: [String] = []) {
        self.reviews = reviews
        self.unlockedPhrases = unlockedPhrases
        self.newWords = newWords
    }
}

extension BoxEngine {

    /// Compose today's session. Pure: same inputs → same plan.
    ///
    /// Up to 5 slots are reserved so a full due queue can't starve growth — for
    /// automatic new cards (load-based budget) OR enqueued cards the user packed
    /// (design §Session 1). New candidates are only *proposed* here; introduction
    /// happens at first answer (design §Box 6).
    public static func composeSession(state: BoxState, now: Date, calendar: Calendar) -> SessionPlan {
        let dueIDs = dueSchedulings(state, now: now).map(\.cardID)
        let budget = gatedNewBudget(state, now: now)

        // Reserve growth headroom whenever there is any new work — budgeted
        // automatic cards or user-enqueued ones (the latter bypass the budget).
        let growthReserve = min(max(budget, enqueuedEligible(state).count), 5)
        let reviewCap = max(0, state.config.sessionCap - growthReserve)
        let reviews = Array(dueIDs.prefix(reviewCap))

        let capacity = max(0, state.config.sessionCap - reviews.count)
        let (phrases, words) = newCandidates(state, budget: budget, capacity: capacity)

        return SessionPlan(reviews: reviews, unlockedPhrases: phrases, newWords: words)
    }

    /// Drain loop (design §Session 2): ids with `due <= now` — learning and
    /// relearning steps land here — oldest due first, ties broken by card id.
    /// Excludes suspended cards.
    public static func dueNow(state: BoxState, now: Date) -> [String] {
        dueSchedulings(state, now: now).map(\.cardID)
    }
}

import Foundation

// Growth rules: day keys, direction-scoped scheduling access, health gate,
// new-card budget, phrase unlock, and deterministic candidate ordering.

extension BoxEngine {

    // MARK: - Day keys

    /// Day key = startOfDay in the caller's calendar, formatted yyyy-MM-dd.
    static func dayKey(for now: Date, calendar: Calendar) -> String {
        let start = calendar.startOfDay(for: now)
        let c = calendar.dateComponents([.year, .month, .day], from: start)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    // MARK: - Direction-scoped scheduling access

    /// Scheduling for a card in the current direction, if any.
    static func scheduling(_ state: BoxState, _ cardID: String) -> CardScheduling? {
        state.scheduling[BoxState.schedulingKey(cardID: cardID, direction: state.config.direction)]
    }

    /// All scheduling entries for the current direction whose card still exists.
    static func directionSchedulings(_ state: BoxState) -> [CardScheduling] {
        state.scheduling.values.filter {
            $0.direction == state.config.direction && state.cards[$0.cardID] != nil
        }
    }

    /// Current-direction, non-suspended scheduling entries.
    static func activeSchedulings(_ state: BoxState) -> [CardScheduling] {
        directionSchedulings(state).filter { !$0.suspended }
    }

    /// Active entries with `due <= now`, oldest due first, ties broken by card id.
    static func dueSchedulings(_ state: BoxState, now: Date) -> [CardScheduling] {
        activeSchedulings(state)
            .filter { ($0.due ?? .distantFuture) <= now }
            .sorted { ($0.due ?? now, $0.cardID) < ($1.due ?? now, $1.cardID) }
    }

    // MARK: - Health gate & budget

    /// Design §Box 2: introduce new cards only if
    /// (a) projected post-session backlog < dueSoftCap, and
    /// (b) relearning share < 20% — sub-gate applies only once activeCount ≥ 10
    ///     (else it passes; day-one bootstrap).
    static func healthGateOpen(_ state: BoxState, now: Date) -> Bool {
        let active = activeSchedulings(state)
        let dueCount = active.filter { ($0.due ?? .distantFuture) <= now }.count
        let projectedBacklog = dueCount - min(dueCount, state.config.sessionCap)
        guard projectedBacklog < state.config.dueSoftCap else { return false }
        if active.count >= 10 {
            let relearning = active.filter { $0.phase == .relearning }.count
            guard Double(relearning) < 0.2 * Double(active.count) else { return false }
        }
        return true
    }

    /// Load-based budget: free slots in the learning pool, ignoring the health
    /// gate. Growth tops the `.learning` pool back up to `maxLearning` — so a
    /// day where cards graduate frees room for more, and a full pool adds none.
    static func learningPoolBudget(_ state: BoxState) -> Int {
        let learning = activeSchedulings(state).filter { $0.phase == .learning }.count
        return max(0, state.config.maxLearning - learning)
    }

    /// Budget after the health gate: 0 when the gate is closed.
    static func gatedNewBudget(_ state: BoxState, now: Date) -> Int {
        healthGateOpen(state, now: now) ? learningPoolBudget(state) : 0
    }

    // MARK: - Phrase unlock (design §Box 3)

    /// A component counts as stable only with current-direction scheduling in
    /// `.review` phase, not suspended, and stability ≥ phraseUnlockStability.
    /// Missing scheduling or suspended = not stable.
    static func isComponentStable(_ state: BoxState, _ componentID: String) -> Bool {
        guard let sched = scheduling(state, componentID),
              !sched.suspended,
              sched.phase == .review,
              let memory = sched.memory else { return false }
        return memory.stability >= state.config.phraseUnlockStability
    }

    /// Unlock fast path: phrases with ZERO components never take it
    /// (they follow normal seed order instead).
    static func isPhraseUnlocked(_ state: BoxState, _ card: Card) -> Bool {
        guard card.kind == .phrase, !card.componentIDs.isEmpty else { return false }
        return card.componentIDs.allSatisfy { isComponentStable(state, $0) }
    }

    // MARK: - Deterministic seed order

    /// Nouns/verbs sort ahead of phrases within an area (design §Box 5).
    static func kindRank(_ kind: CardKind) -> Int {
        switch kind {
        case .noun: return 0
        case .verb: return 1
        case .phrase: return 2
        }
    }

    /// Curated import order via `seedIndex`; ties (hand-built cards default to 0)
    /// fall back to area name, nouns/verbs before phrases, then card id.
    static func seedOrdered(_ cards: some Sequence<Card>) -> [Card] {
        cards.sorted {
            ($0.seedIndex, $0.area, kindRank($0.kind), $0.id)
                < ($1.seedIndex, $1.area, kindRank($1.kind), $1.id)
        }
    }

    // MARK: - New-card candidates

    /// Enqueued ids that could actually enter now: unscheduled, and — for
    /// phrases with components — unlocked. User agency: these BYPASS the budget.
    static func enqueuedEligible(_ state: BoxState) -> [String] {
        state.enqueued.filter { id in
            guard let card = state.cards[id], scheduling(state, id) == nil else { return false }
            return card.kind != .phrase || card.componentIDs.isEmpty || isPhraseUnlocked(state, card)
        }
    }

    /// Candidate selection, bounded by `capacity` total introductions:
    /// 1. Enqueued eligible ids (queue order) — the user asked for them, so they
    ///    bypass `budget` (matching `answer()` and the extra round).
    /// 2. Automatic growth within `budget`: unlocked phrases (fast path), then
    ///    seed-order words. Locked phrases never enter, even enqueued.
    static func newCandidates(_ state: BoxState, budget: Int, capacity: Int)
        -> (unlockedPhrases: [String], newWords: [String]) {
        guard capacity > 0 else { return ([], []) }

        let unscheduled = state.cards.values.filter { scheduling(state, $0.id) == nil }
        var taken = Set<String>()
        var slots = capacity

        // 1. Enqueued — bypass the budget, bounded only by session capacity.
        var words: [String] = []
        for id in enqueuedEligible(state) where slots > 0 {
            guard !taken.contains(id) else { continue }
            words.append(id)
            taken.insert(id)
            slots -= 1
        }

        // 2. Automatic growth within the (load-based) budget.
        var autoRemaining = min(budget, slots)
        let phrases = Array(
            seedOrdered(unscheduled.filter { !taken.contains($0.id) && isPhraseUnlocked(state, $0) })
                .map(\.id).prefix(autoRemaining))
        phrases.forEach { taken.insert($0) }
        autoRemaining -= phrases.count

        for card in seedOrdered(unscheduled) {
            guard autoRemaining > 0 else { break }
            guard !taken.contains(card.id) else { continue }
            if card.kind == .phrase, !card.componentIDs.isEmpty, !isPhraseUnlocked(state, card) {
                continue // locked phrase: waits for its components
            }
            words.append(card.id)
            taken.insert(card.id)
            autoRemaining -= 1
        }
        return (phrases, words)
    }
}

import Foundation

/// The growing-box engine: pure functions over `BoxState`.
///
/// Time discipline: every API takes `now`/`calendar` from the caller —
/// nothing in here ever calls `Date()` or `Calendar.current`.
/// All scheduling reads/writes are scoped to `state.config.direction`.
public enum BoxEngine {

    /// Fresh state from imported cards (nothing scheduled yet).
    public static func bootstrap(cards: [Card], config: BoxConfig) -> BoxState {
        var byID = [String: Card](minimumCapacity: cards.count)
        for card in cards {
            byID[card.id] = card
        }
        return BoxState(config: config, cards: byID)
    }

    /// Append ids to the user priority queue.
    /// Enqueuing a phrase auto-prioritizes its missing (unscheduled) component
    /// words ahead of it (design §Box 4). Already-scheduled or unknown ids are
    /// skipped; duplicates are dropped. Enqueued cards lead composition and
    /// bypass the health gate, but respect the load throttle (`maxLearning`):
    /// a pack enrolls and drips in at the pool rate, it is not dumped at once.
    public static func enqueue(state: BoxState, cardIDs: [String]) -> BoxState {
        var next = state
        var queued = Set(next.enqueued)

        func append(_ id: String) {
            guard next.cards[id] != nil,
                  scheduling(next, id) == nil,
                  !queued.contains(id) else { return }
            next.enqueued.append(id)
            queued.insert(id)
        }

        for id in cardIDs {
            for componentID in next.cards[id]?.componentIDs ?? [] {
                append(componentID)
            }
            append(id)
        }
        return next
    }

    /// Suspend or revive a card in the current direction.
    /// No-op for cards without scheduling (a card that was never introduced
    /// has nothing to suspend).
    public static func setSuspended(state: BoxState, cardID: String, suspended: Bool) -> BoxState {
        var next = state
        let key = BoxState.schedulingKey(cardID: cardID, direction: state.config.direction)
        guard var sched = next.scheduling[key] else { return next }
        sched.suspended = suspended
        next.scheduling[key] = sched
        return next
    }

    /// Session end: fold today's counters into `dailyStats` and prune
    /// `newIntroduced` to the trailing 60 days. Multiple sessions on one day
    /// accumulate `reviews`; `introduced`/`activeCount` reflect the latest state.
    public static func endSession(state: BoxState, reviewsDone: Int, now: Date, calendar: Calendar) -> BoxState {
        var next = state
        let day = dayKey(for: now, calendar: calendar)

        var stats = next.dailyStats[day] ?? DayStats()
        stats.reviews += reviewsDone
        stats.introduced = next.newIntroduced[day] ?? 0
        stats.activeCount = activeSchedulings(next).count
        next.dailyStats[day] = stats

        if let cutoff = calendar.date(byAdding: .day, value: -59, to: calendar.startOfDay(for: now)) {
            let cutoffKey = dayKey(for: cutoff, calendar: calendar)
            // why: yyyy-MM-dd keys compare chronologically as strings,
            // so pruning is a plain string comparison.
            next.newIntroduced = next.newIntroduced.filter { $0.key >= cutoffKey }
        }
        return next
    }
}

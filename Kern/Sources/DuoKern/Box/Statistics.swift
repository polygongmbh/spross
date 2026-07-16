import Foundation

/// Aggregates for the Fortschritt/Box tabs, scoped to the current direction.
public struct BoxStatistics: Sendable, Equatable {
    /// Scheduled, non-suspended cards in the current direction.
    public var activeCount: Int
    public var dueCount: Int
    public var suspendedCount: Int
    /// New-card introductions still possible today; 0 if the health gate is closed.
    public var newBudgetRemainingToday: Int
    /// Consecutive days with reviews > 0; one missed day is forgiven.
    public var streak: Int
    /// Mean FSRS retrievability over active review-phase cards; nil if none.
    public var averageRetrievability: Double?
    public var areas: [AreaStatistics]

    public init(activeCount: Int, dueCount: Int, suspendedCount: Int,
                newBudgetRemainingToday: Int, streak: Int,
                averageRetrievability: Double?, areas: [AreaStatistics]) {
        self.activeCount = activeCount
        self.dueCount = dueCount
        self.suspendedCount = suspendedCount
        self.newBudgetRemainingToday = newBudgetRemainingToday
        self.streak = streak
        self.averageRetrievability = averageRetrievability
        self.areas = areas
    }
}

public struct AreaStatistics: Sendable, Equatable {
    public var name: String
    /// Cards in the area (any status).
    public var total: Int
    /// Scheduled, non-suspended in the current direction.
    public var active: Int
    /// Review phase with stability ≥ phraseUnlockStability ("sitting" material).
    public var sitting: Int
    /// Component phrases still waiting for their components to stabilize.
    public var phrasesLocked: Int
    /// Phrases already introduced, component-free, or with all components stable.
    public var phrasesUnlocked: Int

    public init(name: String, total: Int, active: Int, sitting: Int,
                phrasesLocked: Int, phrasesUnlocked: Int) {
        self.name = name
        self.total = total
        self.active = active
        self.sitting = sitting
        self.phrasesLocked = phrasesLocked
        self.phrasesUnlocked = phrasesUnlocked
    }
}

extension BoxEngine {

    public static func statistics(state: BoxState, now: Date, calendar: Calendar) -> BoxStatistics {
        let active = activeSchedulings(state)
        let dueCount = active.filter { ($0.due ?? .distantFuture) <= now }.count
        let suspendedCount = directionSchedulings(state).filter(\.suspended).count

        return BoxStatistics(
            activeCount: active.count,
            dueCount: dueCount,
            suspendedCount: suspendedCount,
            newBudgetRemainingToday: gatedNewBudget(state, now: now, calendar: calendar),
            streak: streak(state.dailyStats, now: now, calendar: calendar),
            averageRetrievability: averageRetrievability(active, config: state.config, now: now),
            areas: areaStatistics(state)
        )
    }

    /// Mean retrievability over active review-phase cards, elapsed measured
    /// from each card's last review (last log entry date).
    static func averageRetrievability(_ active: [CardScheduling], config: BoxConfig, now: Date) -> Double? {
        let fsrs = FSRS(parameters: FSRSParameters(desiredRetention: config.desiredRetention))
        let reviewPhase = active.filter { $0.phase == .review && $0.memory != nil }
        guard !reviewPhase.isEmpty else { return nil }
        let sum = reviewPhase.reduce(0.0) { acc, sched in
            let last = sched.log.last?.date ?? sched.addedAt
            let elapsed = max(0, now.timeIntervalSince(last) / 86_400)
            return acc + fsrs.retrievability(state: sched.memory ?? MemoryState(stability: 0.1, difficulty: 5), elapsedDays: elapsed)
        }
        return sum / Double(reviewPhase.count)
    }

    /// Walk back from today. Today without reviews neither breaks the streak
    /// nor consumes forgiveness (the day isn't over); after that, exactly ONE
    /// missed day is forgiven, the next miss ends the streak.
    static func streak(_ dailyStats: [String: DayStats], now: Date, calendar: Calendar) -> Int {
        var count = 0
        var forgivenessLeft = 1
        var day = calendar.startOfDay(for: now)
        var isToday = true
        while true {
            let reviews = dailyStats[dayKey(for: day, calendar: calendar)]?.reviews ?? 0
            if reviews > 0 {
                count += 1
            } else if !isToday {
                if forgivenessLeft > 0 {
                    forgivenessLeft -= 1
                } else {
                    break
                }
            }
            isToday = false
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return count
    }

    static func areaStatistics(_ state: BoxState) -> [AreaStatistics] {
        let byArea = Dictionary(grouping: state.cards.values, by: \.area)
        return byArea.keys.sorted().map { area in
            let cards = byArea[area] ?? []
            var active = 0, sitting = 0, locked = 0, unlocked = 0
            for card in cards {
                let sched = scheduling(state, card.id)
                if let sched, !sched.suspended {
                    active += 1
                    if sched.phase == .review, let memory = sched.memory,
                       memory.stability >= state.config.phraseUnlockStability {
                        sitting += 1
                    }
                }
                if card.kind == .phrase {
                    if sched != nil || card.componentIDs.isEmpty || isPhraseUnlocked(state, card) {
                        unlocked += 1
                    } else {
                        locked += 1
                    }
                }
            }
            return AreaStatistics(name: area, total: cards.count, active: active,
                                  sitting: sitting, phrasesLocked: locked, phrasesUnlocked: unlocked)
        }
    }
}

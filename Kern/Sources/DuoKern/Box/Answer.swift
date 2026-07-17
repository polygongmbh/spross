import Foundation

// Answering: every answer event is an FSRS review (design §Session 3).
// Introduction = first answer (design §Box 6).

extension BoxEngine {

    static let leechLapseThreshold = 8
    static let againStepSeconds: TimeInterval = 60
    static let goodStepSeconds: TimeInterval = 600

    /// Apply one answer. Creates scheduling (introduction) if the card is
    /// unscheduled — with a defensive budget re-check, because composition
    /// and answering may straddle midnight. Increments lapses on `.again`
    /// for review-phase cards; auto-suspends at lapses ≥ 8 (leech).
    public static func answer(state: BoxState, cardID: String, rating: Rating, now: Date, calendar: Calendar) -> BoxState {
        guard state.cards[cardID] != nil else { return state }
        let fsrs = FSRS(parameters: FSRSParameters(desiredRetention: state.config.desiredRetention))
        let key = BoxState.schedulingKey(cardID: cardID, direction: state.config.direction)

        if let sched = state.scheduling[key], sched.memory != nil {
            var next = state
            next.scheduling[key] = reviewed(sched, rating: rating, fsrs: fsrs, config: state.config, now: now)
            return next
        }
        return introduce(state: state, cardID: cardID, key: key, rating: rating,
                         fsrs: fsrs, now: now, calendar: calendar)
    }

    // MARK: - First answer (introduction)

    private static func introduce(state: BoxState, cardID: String, key: String, rating: Rating,
                                  fsrs: FSRS, now: Date, calendar: Calendar) -> BoxState {
        // why: budget is re-checked at answer time so a plan composed yesterday
        // can't over-introduce after midnight; over-budget answers are no-ops.
        // Explicitly enqueued cards bypass the budget (user agency): an extra
        // round the user asked for must never silently drop answers.
        guard newBudgetRemaining(state, now: now, calendar: calendar) > 0
                || state.enqueued.contains(cardID) else { return state }

        let memory = fsrs.initialState(rating: rating)
        let phase = fsrs.nextPhase(current: .new, rating: rating)
        var sched = state.scheduling[key]
            ?? CardScheduling(cardID: cardID, direction: state.config.direction, addedAt: now)
        sched.phase = phase
        sched.memory = memory
        sched.due = due(for: phase, memory: memory, rating: rating,
                        fsrs: fsrs, config: state.config, now: now)
        sched.log.append(ReviewLogEntry(date: now, rating: rating, elapsedDays: 0))

        var next = state
        next.scheduling[key] = sched
        next.newIntroduced[dayKey(for: now, calendar: calendar), default: 0] += 1
        next.enqueued.removeAll { $0 == cardID }
        return next
    }

    // MARK: - Subsequent reviews

    private static func reviewed(_ sched: CardScheduling, rating: Rating,
                                 fsrs: FSRS, config: BoxConfig, now: Date) -> CardScheduling {
        var next = sched
        // elapsedDays from the last log entry's date, never from `due`.
        let lastDate = sched.log.last?.date ?? sched.addedAt
        let elapsedDays = max(0, now.timeIntervalSince(lastDate) / 86_400)

        let memory = fsrs.nextState(state: sched.memory ?? fsrs.initialState(rating: rating),
                                    elapsedDays: elapsedDays, rating: rating)
        let phase = fsrs.nextPhase(current: sched.phase, rating: rating)

        if sched.phase == .review, rating == .again {
            next.lapses += 1
            if next.lapses >= leechLapseThreshold {
                next.suspended = true // leech (design §Box 7)
            }
        }
        next.memory = memory
        next.phase = phase
        next.due = due(for: phase, memory: memory, rating: rating,
                       fsrs: fsrs, config: config, now: now)
        next.log.append(ReviewLogEntry(date: now, rating: rating, elapsedDays: elapsedDays))
        return next
    }

    /// Review phase → FSRS interval; learning/relearning → short step
    /// (1 min on again, 10 min otherwise) so the drain loop picks it up.
    private static func due(for phase: CardPhase, memory: MemoryState, rating: Rating,
                            fsrs: FSRS, config: BoxConfig, now: Date) -> Date {
        switch phase {
        case .review:
            let days = fsrs.nextIntervalDays(stability: memory.stability,
                                             desiredRetention: config.desiredRetention)
            return now.addingTimeInterval(days * 86_400)
        case .learning, .relearning:
            return now.addingTimeInterval(rating == .again ? againStepSeconds : goodStepSeconds)
        case .new:
            return now // unreachable: nextPhase never returns .new
        }
    }
}

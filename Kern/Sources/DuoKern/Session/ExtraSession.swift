import Foundation

extension BoxEngine {

    /// Compose an on-demand extra practice round (user agency: "another round
    /// whenever I want, especially after adding vocabulary"). Contents:
    ///
    /// 1. Anything actually due now.
    /// 2. Explicitly enqueued unscheduled cards — these BYPASS the daily new
    ///    budget and health gate (the user just asked for them; `answer()`
    ///    honors the bypass for enqueued ids). Locked phrases still wait.
    /// 3. Review-ahead: active non-suspended cards by soonest due, so the
    ///    round is never empty while the box has cards. Early reviews are
    ///    honest FSRS reviews (short elapsed → small stability gain).
    ///
    /// Automatic seed-order cards are NOT pulled in — unrequested growth
    /// stays governed by the daily budget and health gate.
    public static func composeExtraSession(state: BoxState, now: Date, calendar: Calendar) -> SessionPlan {
        let cap = state.config.sessionCap
        let due = dueSchedulings(state, now: now).map(\.cardID)

        let scheduledKeys = Set(due)
        let enqueuedNew = state.enqueued.filter { id in
            guard let card = state.cards[id], scheduling(state, id) == nil else { return false }
            // Locked phrases wait for their components even when enqueued.
            return card.kind != .phrase || card.componentIDs.isEmpty || isPhraseUnlocked(state, card)
        }

        let remaining = max(0, cap - due.count - enqueuedNew.count)
        // Review-ahead: soonest-due active cards not already in the round.
        let ahead = activeSchedulings(state)
            .filter { $0.due != nil && !scheduledKeys.contains($0.cardID) }
            .sorted { ($0.due ?? now, $0.cardID) < ($1.due ?? now, $1.cardID) }
            .prefix(remaining)
            .map(\.cardID)

        return SessionPlan(reviews: Array((due + ahead).prefix(cap)),
                           unlockedPhrases: [],
                           newWords: Array(enqueuedNew.prefix(cap)))
    }

    /// Learning/relearning steps coming due within `horizon` (exclusive of
    /// already-due), soonest first — the app's pause state reads this instead
    /// of raw scheduling (current direction, suspended excluded).
    public static func upcomingSteps(state: BoxState, now: Date, within horizon: TimeInterval) -> [CardScheduling] {
        let limit = now.addingTimeInterval(horizon)
        return state.scheduling.values
            .filter { sched in
                sched.direction == state.config.direction
                    && !sched.suspended
                    && (sched.phase == .learning || sched.phase == .relearning)
                    && sched.due.map { $0 > now && $0 <= limit } == true
                    && state.cards[sched.cardID] != nil
            }
            .sorted { ($0.due ?? now, $0.cardID) < ($1.due ?? now, $1.cardID) }
    }
}

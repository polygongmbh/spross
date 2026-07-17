import Foundation
import DuoKern

// Session flow: composed plan → card queue → drain loop → completion.
//
// Drain-pause choice (documented per task spec): when the composed queue is
// empty and nothing is due *right now*, but learning/relearning steps come due
// within the next 15 minutes, the session shows a lightweight "Kurze Pause"
// state with a countdown (plus a "Jetzt weitermachen" button that pulls those
// cards early). Only when no such step is pending does the session end.

extension AppModel {

    /// How far ahead the pause state waits for learning steps (covers the
    /// 10-minute good-step) before the session simply ends.
    private static let pauseHorizon: TimeInterval = 15 * 60

    var currentCard: Card? {
        guard case .card(let id)? = sessionStep else { return nil }
        return box?.cards[id]
    }

    /// 1-based position in (composed plan + drained cards so far).
    var sessionPosition: Int {
        min(sessionAnswered + 1, max(sessionTotal, 1))
    }

    // MARK: - Lifecycle

    func startSession() {
        guard let box else { return }
        let now = Date()
        let plan = BoxEngine.composeSession(state: box, now: now, calendar: calendar)
        sessionQueue = plan.reviews + plan.unlockedPhrases + plan.newWords
        sessionTotal = sessionQueue.count
        sessionAnswered = 0
        sessionFolded = 0
        sessionEnded = false
        advanceSession(now: now)
        sessionPresented = true
    }

    /// Apply one answer (every answer event is an FSRS review), then advance.
    func answerCurrent(_ rating: Rating) {
        guard case .card(let id)? = sessionStep, let current = box else { return }
        let now = Date()
        let next = BoxEngine.answer(state: current, cardID: id, rating: rating,
                                    now: now, calendar: calendar)
        box = next
        sessionAnswered += 1
        if !sessionQueue.isEmpty {
            sessionQueue.removeFirst()
        }
        persist(next) // debounced (≥5 s) per design.md save cadence
        advanceSession(now: now)
    }

    /// Next step: composed queue → drain loop (`dueNow`) → pause → done.
    func advanceSession(now: Date = Date()) {
        guard let box else {
            sessionStep = .completed
            return
        }
        if let nextID = sessionQueue.first {
            sessionStep = .card(nextID)
            return
        }
        let due = BoxEngine.dueNow(state: box, now: now)
        if !due.isEmpty {
            sessionQueue = due
            sessionTotal += due.count
            sessionStep = .card(due[0])
            return
        }
        if let nextDue = upcomingLearningSchedulings(now: now).first?.due {
            sessionStep = .pause(until: nextDue)
            return
        }
        finishSession(now: now)
        sessionStep = .completed
    }

    /// Countdown tick: resume once the pause target is due.
    func resumePauseIfDue(now: Date = Date()) {
        guard case .pause(let until)? = sessionStep, now >= until else { return }
        advanceSession(now: now)
    }

    /// "Jetzt weitermachen": pull the pending learning steps early.
    /// Elapsed time at answer stays real, so FSRS input is still honest.
    func skipPause() {
        guard case .pause? = sessionStep else { return }
        let now = Date()
        let upcoming = upcomingLearningSchedulings(now: now).map(\.cardID)
        guard !upcoming.isEmpty else {
            advanceSession(now: now)
            return
        }
        sessionQueue = upcoming
        sessionTotal += upcoming.count
        sessionStep = .card(upcoming[0])
    }

    /// Fold today's counters into dailyStats exactly once per session.
    /// Only the not-yet-folded delta is booked (`foldPartialSession` may have
    /// already folded earlier answers when the app was backgrounded).
    func finishSession(now: Date = Date()) {
        guard !sessionEnded, let current = box else { return }
        sessionEnded = true
        let next = BoxEngine.endSession(state: current,
                                        reviewsDone: sessionAnswered - sessionFolded,
                                        now: now, calendar: calendar)
        sessionFolded = sessionAnswered
        box = next
        persist(next, immediate: true)
        refreshStats()
    }

    /// Backgrounding mid-session: fold answered-so-far into dailyStats so an
    /// evicted app never loses demonstrated reviews (streak stays honest).
    /// Kern's endSession accumulates `reviews`, so later folds add deltas only.
    func foldPartialSession(now: Date = Date()) {
        guard !sessionEnded, sessionAnswered > sessionFolded, let current = box else { return }
        box = BoxEngine.endSession(state: current,
                                   reviewsDone: sessionAnswered - sessionFolded,
                                   now: now, calendar: calendar)
        sessionFolded = sessionAnswered
    }

    /// Close button or "Fertig" on the completion view.
    /// Leaves sessionStep/queue intact — the fullScreenCover is still animating
    /// out and must keep showing its content; startSession resets everything.
    func closeSession() {
        if !sessionEnded, sessionAnswered > 0 {
            finishSession()
        }
        sessionEnded = true
        sessionPresented = false
        refreshStats()
    }

    // MARK: - Helpers

    /// Learning/relearning steps due within the pause horizon, soonest first.
    private func upcomingLearningSchedulings(now: Date) -> [CardScheduling] {
        guard let box else { return [] }
        let horizon = now.addingTimeInterval(Self.pauseHorizon)
        return box.scheduling.values
            .filter { sched in
                sched.direction == box.config.direction
                    && !sched.suspended
                    && (sched.phase == .learning || sched.phase == .relearning)
                    && sched.due.map { $0 > now && $0 <= horizon } == true
                    && box.cards[sched.cardID] != nil
            }
            .sorted { ($0.due ?? now, $0.cardID) < ($1.due ?? now, $1.cardID) }
    }
}

import Foundation
import DuoKern
import WidgetKit

// Session flow: composed plan → card queue → drain loop → completion.
//
// When the composed queue empties and nothing is due right now, the session
// ends with a summary (no mid-session pause). From the summary the user can
// keep going in endless mode, which pulls due cards, soon-due learning steps,
// and new cards (respecting the pool) until they stop.

extension AppModel {

    /// Endless mode pulls learning steps coming due within this window ahead,
    /// so practice keeps flowing instead of stalling on the 10-minute step.
    private static let endlessHorizon: TimeInterval = 30 * 60

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
        begin(plan, now: now)
    }

    /// On-demand extra round: due + enqueued (budget-bypassing) + review-ahead.
    /// Never empty while the box has active cards — user agency over the gate.
    func startExtraSession() {
        guard let box else { return }
        let now = Date()
        let plan = BoxEngine.composeExtraSession(state: box, now: now, calendar: calendar)
        guard !plan.isEmpty else { return }
        begin(plan, now: now)
    }

    private func begin(_ plan: SessionPlan, now: Date) {
        sessionQueue = plan.reviews + plan.unlockedPhrases + plan.newWords
        sessionTotal = sessionQueue.count
        sessionAnswered = 0
        sessionFolded = 0
        sessionRatings = []
        sessionNew = 0
        sessionGraduated = 0
        sessionReviews = 0
        sessionEndless = false
        sessionEnded = false
        advanceSession(now: now)
        sessionPresented = true
    }

    /// Apply one answer (every answer event is an FSRS review), then advance.
    func answerCurrent(_ rating: Rating) {
        guard case .card(let id)? = sessionStep, let current = box else { return }
        let now = Date()
        let beforePhase = scheduling(for: id)?.phase
        let next = BoxEngine.answer(state: current, cardID: id, rating: rating,
                                    now: now, calendar: calendar)
        box = next
        tallySummary(before: beforePhase, after: scheduling(for: id)?.phase)
        sessionRatings.append(rating)
        sessionAnswered += 1
        if !sessionQueue.isEmpty {
            sessionQueue.removeFirst()
        }
        persist(next) // debounced (≥5 s) per design.md save cadence
        advanceSession(now: now)
    }

    /// Bucket each answer for the end summary: first-ever answer = new,
    /// learning/relearning → review = graduated ("gefestigt"), else a review rep.
    private func tallySummary(before: CardPhase?, after: CardPhase?) {
        if before == nil {
            sessionNew += 1
        } else if (before == .learning || before == .relearning) && after == .review {
            sessionGraduated += 1
        } else {
            sessionReviews += 1
        }
    }

    /// Next step: composed queue → drain loop (`dueNow`) → endless refill → done.
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
        // Endless: refill with due + soon-due steps + new cards until dry.
        if sessionEndless, enqueueEndlessBatch(from: box, now: now) {
            return
        }
        finishSession(now: now)
        sessionStep = .completed
    }

    /// "Weiter üben": switch the finished session into endless mode and pull the
    /// first refill batch. No-op (stays on the summary) if nothing is available.
    func continueEndless() {
        guard let box else { return }
        let now = Date()
        sessionEndless = true
        guard enqueueEndlessBatch(from: box, now: now) else { return }
        sessionEnded = false // re-open so finishSession books the new delta
    }

    /// Whether an endless refill would yield anything right now (drives the
    /// "Weiter üben" button's presence on the summary).
    var canPracticeMore: Bool {
        guard let box else { return false }
        return !BoxEngine.composeEndless(state: box, now: Date(), within: Self.endlessHorizon).isEmpty
    }

    /// Pull the next endless batch onto the queue; returns false if dry.
    private func enqueueEndlessBatch(from box: BoxState, now: Date) -> Bool {
        let plan = BoxEngine.composeEndless(state: box, now: now, within: Self.endlessHorizon)
        let more = plan.reviews + plan.unlockedPhrases + plan.newWords
        guard !more.isEmpty else { return false }
        sessionQueue = more
        sessionTotal += more.count
        sessionStep = .card(more[0])
        return true
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
        // why: the box just changed materially — the widget's word rotation
        // should reflect fresh learning immediately, not at timeline end.
        WidgetCenter.shared.reloadTimelines(ofKind: "WordWidget")
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
        sessionEndless = false
        sessionPresented = false
        refreshStats()
    }
}

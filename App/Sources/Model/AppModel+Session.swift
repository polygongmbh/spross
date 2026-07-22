import Foundation
import SprossKern
import WidgetKit

// Session flow: composed plan → card queue → drain loop → completion.
//
// Composition is role-agnostic (plans carry card ids); whether a card is
// produced or recognized is resolved at render time from its log count.
// When the composed queue empties and nothing is due right now, the session
// ends with a summary (no mid-session pause). From the summary the user can
// keep going in endless mode, which pulls whatever is genuinely due plus new
// cards (respecting the pool) until they stop.

extension AppModel {

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
        let plan = SessionComposer.shared.composeSession(state: box,
                                                         nowEpochMillis: now.epochMillis)
        begin(plan, now: now)
    }

    /// On-demand extra round: due + enqueued (budget-bypassing) + review-ahead.
    /// Never empty while the box has active cards — user agency over the gate.
    func startExtraSession() {
        guard let box else { return }
        let now = Date()
        let plan = SessionComposer.shared.composeExtraSession(state: box,
                                                              nowEpochMillis: now.epochMillis)
        guard !plan.isEmpty else { return }
        begin(plan, now: now)
    }

    private func begin(_ plan: SessionPlan, now: Date) {
        sessionQueue = plan.reviews + plan.unlockedPhrases + plan.newCards
        sessionTotal = sessionQueue.count
        sessionAnswered = 0
        sessionFolded = 0
        sessionRatings = []
        sessionNew = 0
        sessionGraduated = 0
        sessionReviews = 0
        sessionEndless = false
        sessionEnded = false
        sessionJoinStamp = plan.joinStamp
        advanceSession(now: now)
        sessionPresented = true
    }

    /// The box's join moved under a running session (source switch, catalog
    /// update) → recompose against the live join; stale ids would no-op.
    func recomposeSessionIfStale() {
        guard sessionPresented, !sessionEnded, let box,
              let stamp = sessionJoinStamp, stamp != box.joinStamp else { return }
        let now = Date()
        let plan = SessionComposer.shared.composeSession(state: box,
                                                         nowEpochMillis: now.epochMillis)
        sessionQueue = plan.reviews + plan.unlockedPhrases + plan.newCards
        sessionTotal = sessionAnswered + sessionQueue.count
        sessionJoinStamp = plan.joinStamp
        advanceSession(now: now)
    }

    /// Apply one answer (every answer event is an FSRS review), then advance.
    func answerCurrent(_ rating: Rating) {
        guard case .card(let id)? = sessionStep, let current = box else { return }
        let now = Date()
        let beforePhase = scheduling(for: id)?.phase
        let outcome = BoxEngine.shared.answer(state: current, cardId: id, rating: rating,
                                              nowEpochMillis: now.epochMillis,
                                              tzId: currentTzId())
        box = outcome.state
        if outcome.status == .applied {
            tallySummary(before: beforePhase, after: scheduling(for: id)?.phase)
            sessionRatings.append(rating)
            sessionAnswered += 1
        } else {
            // Stale/dropped answers leave the run silently — shrink the total
            // so the progress counter stays honest.
            sessionTotal = max(1, sessionTotal - 1)
        }
        if !sessionQueue.isEmpty {
            sessionQueue.removeFirst()
        }
        persist(outcome.state) // debounced (≥5 s) per design.md save cadence
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
        let due = BoxEngine.shared.dueNow(state: box, nowEpochMillis: now.epochMillis)
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
        return !SessionComposer.shared.composeEndless(state: box,
                                                      nowEpochMillis: Date().epochMillis).isEmpty
    }

    /// Pull the next endless batch onto the queue; returns false if dry.
    private func enqueueEndlessBatch(from box: BoxState, now: Date) -> Bool {
        let plan = SessionComposer.shared.composeEndless(state: box,
                                                         nowEpochMillis: now.epochMillis)
        let more = plan.reviews + plan.unlockedPhrases + plan.newCards
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
        let next = BoxEngine.shared.endSession(state: current,
                                               reviewsDone: Int32(sessionAnswered - sessionFolded),
                                               nowEpochMillis: now.epochMillis,
                                               tzId: currentTzId())
        sessionFolded = sessionAnswered
        box = next
        persist(next, immediate: true)
        refreshStats()
        // why: the box just changed materially — the widget's word rotation
        // should reflect fresh learning immediately, not at timeline end.
        WidgetCenter.shared.reloadTimelines(ofKind: "SprossWordWidget")
    }

    /// Backgrounding mid-session: fold answered-so-far into dailyStats so an
    /// evicted app never loses demonstrated reviews (streak stays honest).
    /// Kern's endSession accumulates `reviews`, so later folds add deltas only.
    func foldPartialSession(now: Date = Date()) {
        guard !sessionEnded, sessionAnswered > sessionFolded, let current = box else { return }
        box = BoxEngine.shared.endSession(state: current,
                                          reviewsDone: Int32(sessionAnswered - sessionFolded),
                                          nowEpochMillis: now.epochMillis,
                                          tzId: currentTzId())
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

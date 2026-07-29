import Foundation
import SprossKern
import WidgetKit

// Session flow: composed plan → card queue → completion.
//
// Composition is role-agnostic (plans carry card ids); whether a card is
// produced or recognized is resolved at render time from its log count.
// THE COMPOSED PLAN IS THE WHOLE RUN: the count on screen is a promise, so
// nothing joins a session already under way — work that comes due while the
// learner is sitting there waits for the summary, where "Weiter üben" pulls it
// in on purpose. Only endless mode refills, and only once it has been asked for.

extension AppModel {

    var currentCard: Card? {
        guard case .card(let id)? = sessionStep else { return nil }
        return box?.cards[id]
    }

    /// 1-based position in the composed plan — fixed for the run.
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

    /// On-demand extra round from the Heute done card. Prefers endless-style
    /// composition (due cards plus NEW vocab within the pool budget and health
    /// gate); when the done card shows, that is usually empty — then it falls
    /// back to kern's review-ahead extra round (soonest-due first, enqueued
    /// cards included), which has content whenever the box holds active cards.
    func startExtraSession() {
        guard let box else { return }
        let now = Date()
        let plan = extraSessionPlan(state: box, now: now)
        guard !plan.isEmpty else { return }
        begin(plan, now: now)
    }

    /// Endless plan when it has content, else the review-ahead extra round.
    private func extraSessionPlan(state: BoxState, now: Date) -> SessionPlan {
        let endless = SessionComposer.shared.composeEndless(state: state,
                                                            nowEpochMillis: now.epochMillis)
        if !endless.isEmpty { return endless }
        return SessionComposer.shared.composeExtraSession(state: state,
                                                          nowEpochMillis: now.epochMillis)
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
        let wasSettled = isSettled(id)
        let outcome = BoxEngine.shared.answer(state: current, cardId: id, rating: rating,
                                              nowEpochMillis: now.epochMillis,
                                              tzId: currentTzId())
        box = outcome.state
        if outcome.status == .applied {
            tallySummary(firstAnswer: scheduling(for: id)?.reviewCount == 1,
                         wasSettled: wasSettled, isSettled: isSettled(id))
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

    /// Bucket each answer for the end summary: first-ever answer = new, a word
    /// crossing into settled = "gefestigt", else a review rep.
    ///
    /// why: the crossing, not a phase transition — with one learning step a word
    /// reaches Review on its first pass while its stability is still tiny, so the
    /// phase edge would have called that settled and the summary would have said
    /// "gefestigt" about a word that had barely landed.
    private func tallySummary(firstAnswer: Bool, wasSettled: Bool, isSettled: Bool) {
        if firstAnswer {
            sessionNew += 1
        } else if !wasSettled && isSettled {
            sessionGraduated += 1
        } else {
            sessionReviews += 1
        }
    }

    /// Next step: composed queue → endless refill (only once asked for) → done.
    ///
    /// why: no mid-run drain. Cards coming due while the learner sits there used to
    /// be pulled straight in, so "12/30" quietly became "12/37" and the finish line
    /// moved away from someone already counting down to it. They are still due —
    /// the summary offers them under "Weiter üben".
    func advanceSession(now: Date = Date()) {
        guard let box else {
            sessionStep = .completed
            return
        }
        if let nextID = sessionQueue.first {
            sessionStep = .card(nextID)
            return
        }
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

    /// Whether `startExtraSession` would yield anything (drives the done
    /// card's "Extra-Runde üben" button). Unlike `canPracticeMore`, this
    /// includes the review-ahead fallback, so it holds in every done state
    /// with active cards.
    var canPracticeExtra: Bool {
        guard let box else { return false }
        return !extraSessionPlan(state: box, now: Date()).isEmpty
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

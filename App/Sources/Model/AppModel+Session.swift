import Foundation
import SprossKern
import WidgetKit

// Session flow: kern's `SessionRun` IS the machine — composition, the queue, the
// endless refill, the summary tallies and the day's fold all live there, and every
// command below reduces one intent against `run` and honours what comes back.
//
// What stays here is what kern deliberately cannot name: the fullScreenCover's
// presentation flag, WidgetKit, and the Int/enum bridging the screens read.

extension AppModel {

    // MARK: - The reducer

    /// One intent against the live run: the returned state replaces `run` (and
    /// with it `box`), the returned effects are carried out. Handing the effects
    /// back lets a caller see what the reduction already did.
    @discardableResult
    func reduce(_ intent: SessionIntent) -> [SessionEffect] {
        guard let run else { return [] }
        let reduction = SessionRun.shared.reduce(state: run, intent: intent,
                                                 nowEpochMillis: Date().epochMillis,
                                                 tzId: currentTzId())
        self.run = reduction.state
        // why: closing an unfinished run books the day and then says so again —
        // the day moved once, and every reload costs the widget a redraw.
        var booked = false
        for effect in reduction.effects {
            switch onEnum(of: effect) {
            case .persist(let write):
                persist(reduction.state.box, immediate: write.immediate)
            case .dayBooked:
                booked = true
            }
        }
        if booked {
            refreshStats()
            // why: the box just changed materially — the widget's word rotation
            // should reflect fresh learning immediately, not at timeline end.
            WidgetCenter.shared.reloadTimelines(ofKind: "SprossWordWidget")
        }
        return reduction.effects
    }

    // MARK: - Lifecycle

    func startSession() {
        begin(SessionIntent.Start.shared)
    }

    /// On-demand extra round from the Heute done card: kern's review-ahead round —
    /// everything due, then packed vocab within the new-word budget, then pull-aheads
    /// by soonest due. Composing empty is a no-op there, so nothing gets presented.
    func startExtraSession() {
        begin(SessionIntent.StartExtra.shared)
    }

    private func begin(_ intent: SessionIntent) {
        #if DEBUG
        uitestFinished = false
        #endif
        reduce(intent)
        // why: a run kern refused to start (no box, or an extra round that came back
        // empty) must not raise the cover over nothing.
        if run?.active == true { sessionPresented = true }
    }

    /// Apply one answer (every answer event is an FSRS review), then advance.
    func answerCurrent(_ rating: Rating) {
        reduce(SessionIntent.Answer(rating: rating))
    }

    /// "Weiter üben": switch the finished session into endless mode and pull the
    /// first refill batch. Staying on the summary is kern's answer to a dry refill.
    func continueEndless() {
        reduce(SessionIntent.ContinueEndless.shared)
    }

    /// The box's join moved under a running session (source switch, catalog
    /// update) → kern recomposes against the live join; stale ids would no-op.
    func recomposeSessionIfStale() {
        reduce(SessionIntent.RecomposeIfStale.shared)
    }

    /// Close button or "Fertig" on the completion view. The step and queue stay as
    /// they were — the fullScreenCover is still animating out and must keep showing
    /// its content; a start resets everything.
    func closeSession() {
        reduce(SessionIntent.Close.shared)
        sessionPresented = false
    }

    // MARK: - What the session screen reads

    var currentCard: Card? {
        guard let id = run?.currentCardId else { return nil }
        return box?.cards[id]
    }

    var sessionStep: SessionStep? {
        #if DEBUG
        if uitestFinished { return .completed }
        #endif
        guard let run else { return nil }
        return run.currentCardId.map(SessionStep.card) ?? .completed
    }

    /// 1-based position in the composed plan — fixed for the run.
    var sessionPosition: Int { Int(run?.position ?? 1) }

    var sessionTotal: Int { Int(run?.total ?? 0) }

    /// Ratings in answer order, feeding the segmented progress bar.
    var sessionRatings: [Rating] { run?.ratings ?? [] }

    /// End-of-session summary tallies (design §Session): new cards started,
    /// cards graduated to review ("gefestigt"), and review answers.
    var sessionNew: Int { Int(run?.newCards ?? 0) }
    var sessionGraduated: Int { Int(run?.graduated ?? 0) }
    var sessionReviews: Int { Int(run?.reviews ?? 0) }

    /// Whether an endless refill would yield anything right now (drives the
    /// "Weiter üben" button's presence on the summary).
    var canPracticeMore: Bool {
        guard let box else { return false }
        return SessionOffers.shared.canPracticeMore(state: box,
                                                    nowEpochMillis: Date().epochMillis)
    }

    /// Whether an extra round would yield anything (drives the done card's extra-round
    /// button). Unlike `canPracticeMore` this counts pull-aheads too, so it holds in
    /// every done state with active cards.
    var canPracticeExtra: Bool {
        guard let box else { return false }
        return SessionOffers.shared.canPracticeExtra(state: box,
                                                     nowEpochMillis: Date().epochMillis)
    }

    /// The day streak standing at its all-time best, which the finish screen names.
    var streakIsRecord: Bool {
        guard let stats else { return false }
        return SessionRun.shared.streakIsRecord(stats: stats)
    }
}

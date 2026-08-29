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

    /// The day's round taken short from the Heute session card: its due work alone,
    /// a round's worth of it. Composing empty is a no-op, so nothing gets presented.
    func startShortSession() {
        begin(SessionIntent.StartShort.shared)
    }

    private func begin(_ intent: SessionIntent) {
        #if DEBUG
        uitestFinished = false
        #endif
        // why: the summary shows what THIS round did to an area, so the before
        // has to be taken while it still is the before — one snapshot at the
        // door, since which area the round will favour is not knowable yet.
        treesBeforeSession = Dictionary(uniqueKeysWithValues: areaTrees.map { ($0.id, $0) })
        reduce(intent)
        // why: a run kern refused to start (no box, or an extra round that came back
        // empty) must not raise the cover over nothing.
        if run?.active == true { sessionPresented = true }
    }

    /// Apply one answer (every answer event is an FSRS review), then advance.
    func answerCurrent(_ rating: Rating) {
        reduce(SessionIntent.Answer(rating: rating))
    }

    /// Take the card on screen out of the round: suspend it and step past it with no
    /// rating at all. Never a grade — the learner is saying it should not be ASKED,
    /// not that they failed it (`SessionIntent.SuspendCurrent`).
    func suspendCurrentCard() {
        reduce(SessionIntent.SuspendCurrent.shared)
    }

    /// "Weiter üben": switch the finished session into endless mode and pull the
    /// first refill batch. Staying on the summary is kern's answer to a dry refill.
    func continueEndless() {
        reduce(SessionIntent.ContinueEndless.shared)
    }

    /// The box's join moved under a running session (source switch, catalog
    /// update) → kern recomposes against the live join.
    func recomposeSessionIfStale() {
        reduce(SessionIntent.RecomposeIfStale.shared)
    }

    /// Close button or "Fertig" on the completion view. The step and queue stay as
    /// they were — the fullScreenCover is still animating out and must keep showing
    /// its content; a start resets everything.
    func closeSession() {
        reduce(SessionIntent.Close.shared)
        sessionPresented = false
        // why: one round is what the coaching is for, and leaving is what says it was
        // read — a learner who quits after two cards still comes back to a quiet screen.
        coachPending = false
    }

    /// Whether the round on screen still owes its coaching lines (`SessionCoach`) —
    /// the round onboarding opened, from its first card to the last one it hands out.
    var coachActive: Bool { coachPending }

    // MARK: - What the session screen reads

    /// The card the run stands on, or nil once it has reached its summary.
    var currentCardId: String? {
        #if DEBUG
        if uitestFinished { return nil }
        #endif
        return run?.currentCardId
    }

    var currentCard: Card? {
        guard let id = currentCardId else { return nil }
        return box?.cards[id]
    }

    /// Whether the run is showing its summary rather than a card — the session
    /// screen's one branch. No run at all is not a summary.
    var sessionCompleted: Bool { run != nil && currentCardId == nil }

    /// 1-based position in the composed plan — fixed for the run.
    var sessionPosition: Int { Int(run?.position ?? 1) }

    var sessionTotal: Int { Int(run?.total ?? 0) }

    /// The answered stretch as the progress bar draws it. Which rating reads as
    /// which outcome is kern's grouping (`AnswerOutcome`); only the hues are ours.
    var sessionSegments: [SessionOutcome] {
        (run?.segments ?? []).map(SessionOutcome.init)
    }

    /// End-of-session summary tallies (design §Session): new cards started,
    /// cards graduated to review ("gefestigt"), and review answers.
    var sessionNew: Int { Int(run?.newCards ?? 0) }
    var sessionGraduated: Int { Int(run?.graduated ?? 0) }
    var sessionReviews: Int { Int(run?.reviews ?? 0) }

    /// The area this round worked hardest, before the round and after it —
    /// what the summary draws. Nil when the round touched nothing joinable.
    var sessionGrowth: TreeTransition? {
        guard let area = sessionArea, let after = areaTree(area) else { return nil }
        let empty = AreaTree(id: area, emoji: after.emoji, title: after.title,
                             leaves: 0, blossoms: 0, fruit: 0, growing: 0, fallen: 0,
                             mass: 0, tendedToday: false)
        return TreeTransition(before: treesBeforeSession[area] ?? empty, after: after)
    }

    /// The area this round worked hardest — what the summary draws a tree of.
    var sessionArea: String? {
        guard let box, let touched = run?.answeredIds, !touched.isEmpty else { return nil }
        var byArea: [String: Int] = [:]
        for id in touched {
            guard let area = box.cards[id]?.area else { continue }
            byArea[area, default: 0] += 1
        }
        // why: walk in catalog order keeping a STRICT >, so a round split evenly
        // between two areas names the same one every time it is shown.
        var best: (area: String, count: Int)?
        for area in areaNames where (byArea[area] ?? 0) > (best?.count ?? 0) {
            best = (area, byArea[area] ?? 0)
        }
        return best?.area
    }

    /// Whether a round the learner asks for would yield anything — drives both the summary's
    /// "Weiter üben" and the done card's extra round, which open the same composition.
    var canPracticeMore: Bool {
        guard let box else { return false }
        return SessionOffers.shared.canPracticeMore(state: box,
                                                    nowEpochMillis: Date().epochMillis,
                                                    tzId: currentTzId())
    }

    /// Whether words the learner packed are still waiting to enter a round.
    var hasPackedWords: Bool {
        guard let box else { return false }
        return SessionOffers.shared.packedWordsPending(state: box)
    }

    /// The day streak standing at its all-time best, which the finish screen names.
    var streakIsRecord: Bool {
        guard let stats else { return false }
        return SessionRun.shared.streakIsRecord(stats: stats)
    }
}

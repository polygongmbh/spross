import Foundation
import Observation
import WidgetKit
import WatchKit

/// Watch app state: holds the latest phone snapshot and drains it as a graded
/// multiple-choice run. Each tap is scored from correctness + response time
/// (`WatchGrading`), answered back in the shape of that rating (`WatchFeedback`
/// — a haptic always, a red wash on a miss), and queued to the phone as an FSRS
/// review. No watch-local FSRS — the phone reschedules; an answered card simply
/// leaves the local due list until the next snapshot.
///
/// Two runs, one progress indicator each (`WatchRun`): the due batch counts to
/// an end — rounds of its own misses following while there are enough of them —
/// free practice recycles and counts the answer streak.
@MainActor
@Observable
final class WatchModel {

    /// Which run is on screen — the due batch (finite, ends in a celebration)
    /// or free practice (recycles until the user leaves).
    enum WatchRun { case session, practice }

    private(set) var snapshot: WatchSnapshot?

    // MARK: Run state (one graded multiple-choice loop)
    var sessionPresented = false
    private(set) var run: WatchRun = .session
    private(set) var queue: [String] = []
    private(set) var currentID: String?
    private(set) var currentQuestion: WatchPracticeQuestion?
    /// The tapped option index; non-nil freezes the tiles into feedback state.
    private(set) var selectedIndex: Int?
    /// The rating the last tap earned (raw FSRS 1–4), for the tile's badge —
    /// the quiz marks a quick one's speed, never names it (`WatchFeedback`).
    private(set) var lastRating: WatchRating?
    /// Raised for a moment after a wrong pick; the quiz washes the screen red.
    private(set) var wrongFlash = false
    private(set) var answerStreak = 0
    private(set) var answeredCount = 0
    /// Cards the current round of the due batch set out to answer — the counter's denominator.
    private(set) var sessionTotal = 0
    /// Answers given in the current round — the counter's numerator;
    /// `answeredCount` keeps the whole run's tally for the celebration.
    var roundAnswered: Int { answeredCount - roundStart }
    private var roundStart = 0
    /// Cards the current round of the due batch missed, in the order they were missed.
    private var roundMisses: [String] = []

    /// Fewest misses that earn another round. Below it each would come back within
    /// three questions of its own reveal, answered from the screen rather than from
    /// memory — those few lead free practice instead.
    static let retryFloor = 5

    /// When the current question became visible — the response-time clock.
    private var questionShownAt = Date()
    private var rng = SystemRandomNumberGenerator()
    private var autoAdvance: Task<Void, Never>?
    private var flash: Task<Void, Never>?

    /// How long the wrong-answer wash stays up — long enough to register, short
    /// enough that it is gone before the eye returns to the tiles.
    private static let flashMillis = 260

    let connectivity = WatchConnectivityClient()
    let calendar = Calendar.current

    // MARK: - Launch

    func start() {
        #if DEBUG
        // UI-test hooks: `-uitest-snapshot` loads the bundled fixture instead
        // of stored/synced state; `-uitest-autostart` opens the due batch and
        // `-uitest-practice` free practice; `-uitest-streak N` presets the
        // answer streak (screenshot verification without a paired phone — simctl
        // cannot tap).
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-uitest-snapshot") {
            snapshot = Self.loadFixture()
            if arguments.contains("-uitest-autostart") {
                startSession()
            } else if arguments.contains("-uitest-practice") {
                startPractice()
            }
            if sessionPresented, let i = arguments.firstIndex(of: "-uitest-streak"),
               i + 1 < arguments.count, let n = Int(arguments[i + 1]) {
                answerStreak = n
            }
            return
        }
        #endif
        snapshot = WatchSnapshotStore.load()
        connectivity.onSnapshotData = { data in
            Task { @MainActor [weak self] in self?.receiveSnapshot(data) }
        }
        connectivity.activate()
    }

    static func loadFixture() -> WatchSnapshot? {
        guard let url = Bundle.main.url(forResource: "WatchFixture", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? WatchSnapshot.decode(data)
    }

    /// Fresh snapshot from the phone: replaces local state (the phone is the
    /// source of truth and already folded in applied events). Stale or
    /// out-of-order deliveries are dropped.
    func receiveSnapshot(_ data: Data) {
        guard let incoming = try? WatchSnapshot.decode(data) else { return }
        if let current = snapshot, current.generated > incoming.generated { return }
        snapshot = incoming
        WatchSnapshotStore.save(incoming)
        WidgetCenter.shared.reloadAllTimelines()
        if sessionPresented {
            // why: mid-session the queue must not resurrect cards the user just
            // answered (their events may not be applied phone-side yet).
            // Practice is exempt: replaying answered cards is what it does.
            let answered = run == .session ? Set(incoming.answers.keys) : []
            let present = Set(incoming.entries.map(\.cardId))
            queue = queue.filter { present.contains($0) && !answered.contains($0) }
            if let id = currentID, !present.contains(id) {
                advance()
            }
        }
    }

    // MARK: - Derived

    /// The clock is the caller's, so a view that redraws on a timeline counts
    /// the cards that came due while nobody touched the watch.
    func dueCount(at now: Date) -> Int {
        snapshot?.dueEntries(now: now).count ?? 0
    }

    func tomorrowDueCount(at now: Date) -> Int {
        snapshot?.tomorrowDueCount(now: now, calendar: calendar) ?? 0
    }

    /// The moments the counts can change without a sync — each card's due
    /// moment — led by `now`, since a timeline renders its first date even
    /// when it lies ahead.
    func countChanges(after now: Date) -> [Date] {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        let dues = (snapshot?.entries ?? [])
            .map(\.due)
            .filter { $0 > nowMillis }
            .sorted()
            .map { Date(timeIntervalSince1970: Double($0) / 1000) }
        return [now] + dues
    }

    var currentEntry: WatchSnapshot.Entry? {
        currentID.flatMap { snapshot?.entry(id: $0) }
    }

    /// Enough on-watch vocab to build a multiple-choice question — the floor
    /// under both runs.
    private var hasPool: Bool { (snapshot?.entries.count ?? 0) >= 2 }

    /// A due batch to work through.
    func canStart(at now: Date) -> Bool { hasPool && dueCount(at: now) > 0 }

    /// Free practice needs no due card — it draws on the whole snapshot.
    var canPractice: Bool { hasPool }

    // MARK: - Runs

    /// The due batch: exactly the cards due now, so its counter names a goal
    /// that can be reached. Review-ahead is free practice's job, not this run's.
    func startSession() {
        guard let snapshot, hasPool else { return }
        queue = snapshot.dueEntries(now: Date()).map(\.cardId)
        guard !queue.isEmpty else { return }
        sessionTotal = queue.count
        begin(.session)
    }

    /// Free practice: the weakest words first, lap after lap — no
    /// total, so the answer streak carries the progress indicator instead.
    func startPractice() {
        guard hasPool else { return }
        queue = practiceLap(avoiding: nil)
        sessionTotal = 0
        begin(.practice)
    }

    private func begin(_ run: WatchRun) {
        self.run = run
        answeredCount = 0
        roundStart = 0
        roundMisses = []
        answerStreak = 0
        currentID = queue.first
        makeQuestionForCurrent()
        sessionPresented = true
    }

    /// Score the tapped option from correctness + response time, queue the
    /// FSRS review to the phone, drop the card locally, then flip to the next
    /// question. A second tap while feedback shows is ignored.
    func choose(_ index: Int) {
        guard selectedIndex == nil, let question = currentQuestion,
              let id = currentID, var snap = snapshot else { return }
        selectedIndex = index
        let correct = index == question.correctIndex
        answerStreak = correct ? answerStreak + 1 : 0

        let elapsedMs = Int(Date().timeIntervalSince(questionShownAt) * 1000)
        let optionChars = question.options.joined().count
        let rating = WatchGrading.rating(correct: correct, elapsedMs: elapsedMs,
                                         optionChars: optionChars)
        lastRating = rating
        // why: every answer answers back, and in the shape of the rating it
        // earned — a silent correct tap used to feel the same as no tap at all.
        WKInterfaceDevice.current().play(WatchFeedback.haptic(forRating: rating))
        if !correct {
            raiseWrongFlash()
            if run == .session { roundMisses.append(id) }
        }

        connectivity.send(WatchAnswerEvent(cardId: id, rating: rating, date: Date()))
        // Every answer is an FSRS review, second lap included; locally only the
        // latest rating stays — the due count skips the card, practice orders by it.
        snap.answers[id] = rating
        snapshot = snap
        WatchSnapshotStore.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
        answeredCount += 1

        // why: linger longer on a wrong pick so the green-highlighted correct
        // tile has time to register before the next question.
        let delay = correct ? 900 : 2000
        autoAdvance = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(delay))
            guard !Task.isCancelled else { return }
            self?.advance()
        }
    }

    /// Wash the screen red, then take it down again — the flash is the alarm a
    /// tile tint alone was too quiet to raise.
    private func raiseWrongFlash() {
        flash?.cancel()
        wrongFlash = true
        flash = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(Self.flashMillis))
            guard !Task.isCancelled else { return }
            self?.wrongFlash = false
        }
    }

    func endSession() {
        autoAdvance?.cancel()
        flash?.cancel()
        wrongFlash = false
        sessionPresented = false
        currentID = nil
        currentQuestion = nil
        queue = []
        roundMisses = []
    }

    private func advance() {
        autoAdvance?.cancel()
        let previous = currentID
        if let id = previous, let index = queue.firstIndex(of: id) {
            queue.remove(at: index)
        }
        // why: practice has no end — a drained queue starts another lap over the
        // whole snapshot, so the run only stops when the user leaves.
        if queue.isEmpty, run == .practice {
            queue = practiceLap(avoiding: previous)
            // The snapshot emptied under a running lap — nothing left to ask.
            guard !queue.isEmpty else { return endSession() }
        }
        if queue.isEmpty, run == .session, roundMisses.count >= Self.retryFloor {
            queue = roundMisses.filter { snapshot?.entry(id: $0) != nil }
            roundMisses = []
            sessionTotal = queue.count
            roundStart = answeredCount
        }
        currentID = queue.first
        makeQuestionForCurrent()
    }

    /// One practice lap over the whole snapshot: what the watch missed since the
    /// phone last synced, then what it has not asked yet, then what it got right —
    /// each part in the phone's order (weakest first). The snapshot's own schedule
    /// knows nothing of those answers until the phone reschedules them.
    /// `avoiding` is the card just answered — swapped with its neighbor (not
    /// sent to the back, which would demote the very word practice is for) so
    /// no card asks twice in a row across the lap edge.
    private func practiceLap(avoiding previous: String?) -> [String] {
        guard let snapshot else { return [] }
        let order = snapshot.entries.map(\.cardId)
        let missed = order.filter { snapshot.answers[$0] == .again }
        let unasked = order.filter { snapshot.answers[$0] == nil }
        let known = order.filter { snapshot.answers[$0].map { $0 != .again } ?? false }
        var ids = missed + unasked + known
        if ids.count > 1, ids.first == previous {
            ids.swapAt(0, 1)
        }
        return ids
    }

    /// Build the question for the current card and (re)start the response clock.
    private func makeQuestionForCurrent() {
        selectedIndex = nil
        lastRating = nil
        guard let entry = currentEntry else {
            currentQuestion = nil
            return
        }
        currentQuestion = WatchPracticeGenerator.makeQuestion(promptEntry: entry, using: &rng)
        questionShownAt = Date()
    }
}

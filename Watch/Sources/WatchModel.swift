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
/// an end, free practice recycles and counts the answer streak.
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
    /// Cards the due batch set out to answer — the counter's denominator.
    private(set) var sessionTotal = 0

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
            let answered = run == .session ? Set(incoming.answeredCardIDs) : []
            let present = Set(incoming.entries.map(\.cardId))
            queue = queue.filter { present.contains($0) && !answered.contains($0) }
            if let id = currentID, !present.contains(id) {
                advance()
            }
        }
    }

    // MARK: - Derived

    var dueCount: Int {
        snapshot?.dueEntries(now: Date()).count ?? 0
    }

    var tomorrowDueCount: Int {
        snapshot?.tomorrowDueCount(now: Date(), calendar: calendar) ?? 0
    }

    var currentEntry: WatchSnapshot.Entry? {
        currentID.flatMap { snapshot?.entry(id: $0) }
    }

    /// Enough on-watch vocab to build a multiple-choice question — the floor
    /// under both runs.
    private var hasPool: Bool { (snapshot?.entries.count ?? 0) >= 2 }

    /// A due batch to work through.
    var canStart: Bool { hasPool && dueCount > 0 }

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
        if !correct { raiseWrongFlash() }

        connectivity.send(WatchAnswerEvent(cardId: id, rating: rating, date: Date()))
        // Every answer is an FSRS review, second lap included; the local list is
        // a set of ids the due count must skip, so a repeat adds nothing to it.
        if !snap.answeredCardIDs.contains(id) { snap.answeredCardIDs.append(id) }
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
        currentID = queue.first
        makeQuestionForCurrent()
    }

    /// One practice lap over the whole snapshot, in the phone's order (weakest first).
    /// `avoiding` is the card just answered — swapped with its neighbor (not
    /// sent to the back, which would demote the very word practice is for) so
    /// no card asks twice in a row across the lap edge.
    private func practiceLap(avoiding previous: String?) -> [String] {
        guard let snapshot else { return [] }
        var ids = snapshot.entries.map(\.cardId)
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
